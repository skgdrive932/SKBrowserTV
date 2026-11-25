package com.skkaushal.tvbrowser

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Local "DNS shield" VPN.
 *
 * Only DNS traffic is routed into the tunnel (a single fake DNS address). Blocked domains
 * get an NXDOMAIN answer, everything else is forwarded to the upstream DNS server chosen in
 * Settings. No other traffic is touched and nothing is sent to any remote VPN server.
 */
class AdBlockVpnService : VpnService() {

    companion object {
        const val ACTION_STOP = "com.skkaushal.tvbrowser.STOP_VPN"
        private const val VPN_ADDR = "10.111.0.2"
        private const val DNS_ADDR = "10.111.0.1"
    }

    private var tun: ParcelFileDescriptor? = null
    @Volatile private var running = false
    private var pool: ExecutorService? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }
        if (intent == null && !VpnHelper.isOn(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (running) return
        AdBlocker.load(this)
        val pfd = try {
            Builder()
                .setSession("SK Browser DNS Shield")
                .addAddress(VPN_ADDR, 32)
                .addDnsServer(DNS_ADDR)
                .addRoute(DNS_ADDR, 32)
                .setBlocking(true)
                .establish()
        } catch (e: Exception) {
            null
        }
        if (pfd == null) {
            Prefs.sp(this).edit().putBoolean("vpn_enabled", false).apply()
            stopSelf()
            return
        }
        tun = pfd
        running = true
        pool = Executors.newCachedThreadPool()
        Thread({ loop(pfd) }, "sk-dns-vpn").start()
    }

    private fun loop(pfd: ParcelFileDescriptor) {
        val input = FileInputStream(pfd.fileDescriptor)
        val output = FileOutputStream(pfd.fileDescriptor)
        val buf = ByteArray(32767)
        while (running) {
            val n = try { input.read(buf) } catch (e: Exception) { break }
            if (n <= 0) continue
            handlePacket(buf.copyOf(n), output)
        }
    }

    private fun handlePacket(p: ByteArray, out: FileOutputStream) {
        if (p.size < 28 || (p[0].toInt() shr 4) != 4) return          // IPv4 only
        val ihl = (p[0].toInt() and 0x0F) * 4
        if (p[9].toInt() != 17 || p.size < ihl + 8) return             // UDP only
        if (u16(p, ihl + 2) != 53) return                              // DNS only
        val dnsLen = u16(p, ihl + 4) - 8
        val off = ihl + 8
        if (dnsLen < 12 || off + dnsLen > p.size) return
        val query = p.copyOfRange(off, off + dnsLen)

        val q = parseQuestion(query)
        if (q != null && AdBlocker.isBlocked(q.first)) {
            writePacket(out, buildIpUdp(p, ihl, nxdomain(query, q.second)))
            return
        }

        val upstream = Prefs.upstreamDns(this)
        pool?.execute {
            try {
                DatagramSocket().use { s ->
                    protect(s)
                    s.soTimeout = 4000
                    s.send(DatagramPacket(query, query.size, InetAddress.getByName(upstream), 53))
                    val rb = ByteArray(4096)
                    val rp = DatagramPacket(rb, rb.size)
                    s.receive(rp)
                    writePacket(out, buildIpUdp(p, ihl, rb.copyOf(rp.length)))
                }
            } catch (_: Exception) {
            }
        }
    }

    /** Returns (domain name, offset just after the question section) or null. */
    private fun parseQuestion(d: ByteArray): Pair<String, Int>? {
        if (d.size < 17 || u16(d, 4) < 1) return null
        var i = 12
        val sb = StringBuilder()
        while (i < d.size) {
            val len = d[i].toInt() and 0xFF
            if (len == 0) { i++; break }
            if ((len and 0xC0) != 0) return null
            if (i + 1 + len > d.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            sb.append(String(d, i + 1, len, Charsets.US_ASCII))
            i += 1 + len
        }
        if (i + 4 > d.size) return null
        return Pair(sb.toString().lowercase(), i + 4)
    }

    private fun nxdomain(q: ByteArray, qEnd: Int): ByteArray {
        val r = q.copyOfRange(0, qEnd)
        r[2] = 0x81.toByte()                       // QR=1, RD=1
        r[3] = 0x83.toByte()                       // RA=1, RCODE=3 (NXDOMAIN)
        r[4] = 0; r[5] = 1                         // QDCOUNT = 1
        for (i in 6..11) r[i] = 0                  // no answer/authority/additional
        return r
    }

    private fun buildIpUdp(req: ByteArray, ihl: Int, dns: ByteArray): ByteArray {
        val total = 28 + dns.size
        val o = ByteArray(total)
        o[0] = 0x45
        put16(o, 2, total)
        put16(o, 6, 0x4000)
        o[8] = 64
        o[9] = 17
        System.arraycopy(req, 16, o, 12, 4)        // src = original dst
        System.arraycopy(req, 12, o, 16, 4)        // dst = original src
        put16(o, 10, checksum(o, 20))
        put16(o, 20, 53)
        put16(o, 22, u16(req, ihl))                // original source port
        put16(o, 24, 8 + dns.size)
        put16(o, 26, 0)                            // UDP checksum optional over IPv4
        System.arraycopy(dns, 0, o, 28, dns.size)
        return o
    }

    private fun checksum(b: ByteArray, len: Int): Int {
        var sum = 0
        var i = 0
        while (i < len) { sum += u16(b, i); i += 2 }
        while ((sum shr 16) != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }

    private fun u16(b: ByteArray, i: Int) =
        ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)

    private fun put16(b: ByteArray, i: Int, v: Int) {
        b[i] = (v shr 8).toByte()
        b[i + 1] = v.toByte()
    }

    private fun writePacket(out: FileOutputStream, pkt: ByteArray) {
        synchronized(out) {
            try { out.write(pkt) } catch (_: Exception) { }
        }
    }

    private fun stopVpn() {
        running = false
        try { tun?.close() } catch (_: Exception) { }
        tun = null
        pool?.shutdownNow()
        pool = null
        Prefs.sp(this).edit().putBoolean("vpn_enabled", false).apply()
        stopSelf()
    }

    override fun onRevoke() {
        stopVpn()
    }

    override fun onDestroy() {
        running = false
        try { tun?.close() } catch (_: Exception) { }
        super.onDestroy()
    }
}

/** Small helper used by the home screen and Settings to start/stop the DNS shield. */
object VpnHelper {
    fun start(c: Context) {
        Prefs.sp(c).edit().putBoolean("vpn_enabled", true).apply()
        c.startService(Intent(c, AdBlockVpnService::class.java))
    }

    fun stop(c: Context) {
        c.startService(Intent(c, AdBlockVpnService::class.java).setAction(AdBlockVpnService.ACTION_STOP))
        Prefs.sp(c).edit().putBoolean("vpn_enabled", false).apply()
    }

    fun isOn(c: Context) = Prefs.sp(c).getBoolean("vpn_enabled", false)
}
