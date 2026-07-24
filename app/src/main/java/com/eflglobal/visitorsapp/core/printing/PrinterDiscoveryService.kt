package com.eflglobal.visitorsapp.core.printing

import android.content.Context
import android.hardware.usb.UsbManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Represents a printer discovered on the network or USB.
 *
 * @param brand       Detected brand (BROTHER / ZEBRA)
 * @param model       Detected model name (e.g. "QL-820NWB", "ZT230")
 * @param ipAddress   Network IP address (null for USB)
 * @param port        Network port (default 9100)
 * @param serialOrNode  Unique identifier — serial number, MAC, or node name.
 *                      Used to re-identify the same physical printer across IP changes.
 * @param connectionType  How it was discovered (NETWORK / USB)
 * @param displayName Human-readable display string
 */
data class DiscoveredPrinter(
    val brand: PrinterConfig.PrinterBrand,
    val model: String,
    val ipAddress: String? = null,
    val port: Int = PrinterConfig.DEFAULT_PORT,
    val serialOrNode: String = "",
    val connectionType: PrinterConfig.ConnectionType = PrinterConfig.ConnectionType.NETWORK,
    val displayName: String = "$model (${ipAddress ?: "USB"})"
)

/**
 * Network & USB discovery service for Brother and Zebra printers.
 *
 * Strategy (executed in parallel):
 *  1. **Brother SDK discovery** — uses `com.brother.ptouch.sdk.Printer.getNetPrinters("QL")`
 *     via reflection (since the SDK is loaded as an AAR).
 *  2. **Zebra SDK discovery** — uses `com.zebra.sdk.printer.discovery.NetworkDiscoverer.localBroadcast()`
 *     which broadcasts and collects responses from Zebra Link-OS devices.
 *  3. **Port-scan fallback** — scans the local /24 subnet on port 9100 and probes
 *     each responder with SNMP or a simple ZPL/Brother handshake.
 *  4. **USB detection** — checks Android USB Host for known vendor IDs.
 *
 * All heavy work runs on [Dispatchers.IO].
 */
object PrinterDiscoveryService {

    private const val TAG = "PrinterDiscovery"
    private const val DISCOVERY_TIMEOUT_MS = 12_000L

    // Zebra USB vendor ID
    private const val ZEBRA_VENDOR_ID = 0x0A5F
    // Brother USB vendor ID
    private const val BROTHER_VENDOR_ID = 0x04F9

    // ─────────────────────────────────────────────────────────────────────
    //  PUBLIC API
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Runs all discovery strategies in parallel and returns a merged,
     * deduplicated list of printers found.
     *
     * Pipeline:
     * 1. Brother SDK + Zebra SDK + USB in parallel (~12 s max)
     * 2. If SDK discovery returned nothing → automatic subnet port-scan fallback
     *    using the device's own WiFi IP to derive the /24 subnet.
     *
     * Timeout: ~15 seconds total.
     */
    suspend fun discoverAll(
        context: Context,
        targetSubnet: String? = null,
        targetHosts: List<String> = emptyList()
    ): List<DiscoveredPrinter> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<DiscoveredPrinter>()

            // ── Phase 1: SDK-based discovery (broadcast — same subnet only) ──
            try {
                coroutineScope {
                    val brotherJob = async { discoverBrother() }
                    val zebraJob   = async { discoverZebra() }
                    val usbJob     = async { discoverUsb(context) }

                    val brother = withTimeoutOrNull(DISCOVERY_TIMEOUT_MS) { brotherJob.await() } ?: emptyList()
                    val zebra   = withTimeoutOrNull(DISCOVERY_TIMEOUT_MS) { zebraJob.await() } ?: emptyList()
                    val usb     = withTimeoutOrNull(5_000L) { usbJob.await() } ?: emptyList()

                    results.addAll(brother)
                    results.addAll(zebra)
                    results.addAll(usb)
                }
            } catch (e: Exception) {
                Log.e(TAG, "SDK discovery error: ${e.message}", e)
            }

            Log.i(TAG, "SDK discovery found ${results.size} printer(s)")

            // ── Phase 1.5: direct unicast probe of known hosts ───────────
            // The SDK discovery above relies on UDP broadcast, which routers do
            // NOT forward across subnets. A direct unicast probe of a known IP
            // (e.g. a Brother on a different subnet) is routable and works.
            val cleanHosts = targetHosts.map { it.trim() }.filter { it.isNotBlank() }.distinct()
            if (cleanHosts.isNotEmpty()) {
                Log.i(TAG, "Probing ${cleanHosts.size} known host(s) directly: $cleanHosts")
                try {
                    val probed = withTimeoutOrNull(10_000L) { probeHosts(cleanHosts, 9100) } ?: emptyList()
                    results.addAll(probed)
                } catch (e: Exception) {
                    Log.w(TAG, "Direct host probe failed: ${e.message}")
                }
            }

            // ── Phase 2: subnet port-scan ────────────────────────────────
            // Scan the explicitly requested subnet plus the subnet(s) derived
            // from the known hosts (so a cross-subnet printer is reachable).
            // The device's own subnet is only scanned as a last resort when
            // nothing else turned up.
            val subnets = LinkedHashSet<String>()
            targetSubnet?.let { normalizeSubnet(it) }?.let { subnets.add(it) }
            cleanHosts.forEach { host -> subnetOf(host)?.let { subnets.add(it) } }
            if (subnets.isEmpty() && results.none { it.connectionType == PrinterConfig.ConnectionType.NETWORK }) {
                getDeviceSubnet(context)?.let { subnets.add(it) }
            }

            for (sn in subnets) {
                Log.i(TAG, "Subnet scan: $sn.*")
                try {
                    val scan = withTimeoutOrNull(25_000L) { scanSubnet(sn, 9100, 600) } ?: emptyList()
                    results.addAll(scan)
                } catch (e: Exception) {
                    Log.w(TAG, "Subnet scan failed for $sn: ${e.message}")
                }
            }

            // Deduplicate by (brand + ip + serial)
            results.distinctBy { "${it.brand}|${it.ipAddress}|${it.serialOrNode}" }
                .also { Log.i(TAG, "Discovery complete: ${it.size} printer(s) found") }
        }

    /** Probes a fixed set of hosts on [port] in parallel (unicast, cross-subnet). */
    private suspend fun probeHosts(hosts: List<String>, port: Int): List<DiscoveredPrinter> =
        coroutineScope {
            hosts.map { ip ->
                async(Dispatchers.IO) {
                    try {
                        Socket().use { it.connect(InetSocketAddress(ip, port), 1500) }
                        Log.i(TAG, "Host $ip: port $port open — probing brand")
                        probePrinter(ip, port)
                    } catch (e: Exception) {
                        Log.d(TAG, "Host $ip not reachable on $port: ${e.message}")
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }

    /** "10.20.21.49" -> "10.20.21"; null if not a dotted IPv4. */
    private fun subnetOf(host: String): String? {
        val parts = host.trim().split(".")
        val valid = parts.size == 4 && parts.all { val n = it.toIntOrNull(); n != null && n in 0..255 }
        return if (valid) "${parts[0]}.${parts[1]}.${parts[2]}" else null
    }

    /**
     * Accepts "10.20.21" or a full "10.20.21.x" (even the operator's own full
     * IP) and returns the /24 prefix; blank/invalid → null. Public so the UI can
     * treat a full IP as a valid subnet entry.
     */
    fun normalizeSubnet(input: String): String? {
        val parts = input.trim().split(".").filter { it.isNotBlank() }
        return if (parts.size >= 3) "${parts[0]}.${parts[1]}.${parts[2]}" else null
    }

    /**
     * Full IPv4 address of the device's active WiFi/network interface, e.g.
     * "10.20.21.49". Null if there is no usable interface. Exposed so the
     * printer UI can show the operator their own IP as a scan hint.
     */
    fun deviceIp(context: Context): String? {
        try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            @Suppress("DEPRECATION")
            val ip = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (ip != 0) {
                val a = ip and 0xFF
                val b = (ip shr 8) and 0xFF
                val c = (ip shr 16) and 0xFF
                val d = (ip shr 24) and 0xFF
                return "$a.$b.$c.$d"
            }

            // Fallback: first non-loopback IPv4 across all network interfaces.
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                for (addr in iface.inetAddresses) {
                    if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                        addr.hostAddress?.let { return it }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "deviceIp error: ${e.message}")
        }
        return null
    }

    /**
     * Returns the /24 subnet prefix of the device's WiFi IP, e.g. "10.20.21".
     * Returns null if the device has no usable network interface.
     */
    private fun getDeviceSubnet(context: Context): String? =
        deviceIp(context)?.let { subnetOf(it) }

    /**
     * Discovers only Brother printers on the network.
     */
    suspend fun discoverBrother(): List<DiscoveredPrinter> = withContext(Dispatchers.IO) {
        val found = mutableListOf<DiscoveredPrinter>()
        try {
            // Load Brother SDK via reflection
            val printerClass = Class.forName("com.brother.ptouch.sdk.Printer")
            val netPrinterClass = Class.forName("com.brother.ptouch.sdk.NetPrinter")
            val printer = printerClass.getDeclaredConstructor().newInstance()

            // Brother SDK: getNetPrinters(String modelPrefix) returns NetPrinter[]
            // Try multiple model prefixes to cover all supported printer families
            val modelPrefixes = listOf("QL", "TD", "RJ", "PT", "MW")

            for (prefix in modelPrefixes) {
                try {
                    val getNetPrintersMethod = printerClass.getMethod(
                        "getNetPrinters", String::class.java
                    )
                    val netPrinters = getNetPrintersMethod.invoke(printer, prefix) as? Array<*>

                    if (netPrinters != null) {
                        for (np in netPrinters) {
                            if (np == null) continue
                            try {
                                val modelName = netPrinterClass.getField("modelName").get(np)?.toString() ?: "Brother"
                                val ipAddr    = netPrinterClass.getField("ipAddress").get(np)?.toString() ?: continue
                                val nodeName  = netPrinterClass.getField("nodeName").get(np)?.toString() ?: ""
                                val macAddr   = netPrinterClass.getField("macAddress").get(np)?.toString() ?: ""

                                // Use MAC or node name as stable identifier
                                val serial = macAddr.ifBlank { nodeName }

                                found.add(
                                    DiscoveredPrinter(
                                        brand          = PrinterConfig.PrinterBrand.BROTHER,
                                        model          = modelName,
                                        ipAddress      = ipAddr,
                                        port           = PrinterConfig.DEFAULT_PORT,
                                        serialOrNode   = serial,
                                        connectionType = PrinterConfig.ConnectionType.NETWORK,
                                        displayName    = "$modelName — $ipAddr"
                                    )
                                )
                                Log.i(TAG, "Brother found: $modelName @ $ipAddr (node=$nodeName, mac=$macAddr)")
                            } catch (e: Exception) {
                                Log.w(TAG, "Error reading Brother NetPrinter field: ${e.message}")
                            }
                        }
                    }
                } catch (e: NoSuchMethodException) {
                    Log.w(TAG, "Brother getNetPrinters not available for prefix '$prefix'")
                } catch (e: Exception) {
                    Log.w(TAG, "Brother discovery for '$prefix' failed: ${e.message}")
                }
            }
        } catch (e: ClassNotFoundException) {
            Log.w(TAG, "Brother SDK not available — skipping Brother discovery")
        } catch (e: Exception) {
            Log.e(TAG, "Brother discovery error: ${e.message}", e)
        }

        found.also { Log.i(TAG, "Brother discovery: ${it.size} printer(s)") }
    }

    /**
     * Discovers Zebra printers on the local network using the Link-OS SDK.
     */
    suspend fun discoverZebra(): List<DiscoveredPrinter> = withContext(Dispatchers.IO) {
        val found = mutableListOf<DiscoveredPrinter>()
        try {
            // Zebra SDK: NetworkDiscoverer.localBroadcast(DiscoveryHandler, timeout)
            val discovererClass = Class.forName("com.zebra.sdk.printer.discovery.NetworkDiscoverer")

            // Use the simpler subnetSearch or localBroadcast
            // localBroadcast(DiscoveryHandler handler) is callback-based
            // Let's use a direct approach: try known Zebra discovery via direct TCP probe
            // Actually, let's try the SDK's synchronous localBroadcast with a handler

            val handlerInterface = Class.forName("com.zebra.sdk.printer.discovery.DiscoveryHandler")

            // Create a proxy for the DiscoveryHandler interface
            val printers = java.util.concurrent.CopyOnWriteArrayList<Any>()
            val latch = java.util.concurrent.CountDownLatch(1)

            val handler = java.lang.reflect.Proxy.newProxyInstance(
                handlerInterface.classLoader,
                arrayOf(handlerInterface)
            ) { _, method, args ->
                when (method.name) {
                    "foundPrinter" -> {
                        if (args != null && args.isNotEmpty()) {
                            printers.add(args[0])
                        }
                    }
                    "discoveryFinished" -> {
                        latch.countDown()
                    }
                    "discoveryError" -> {
                        Log.w(TAG, "Zebra discovery error: ${args?.firstOrNull()}")
                        latch.countDown()
                    }
                }
                null
            }

            // Call NetworkDiscoverer.localBroadcast(handler)
            val localBroadcastMethod = discovererClass.getMethod("localBroadcast", handlerInterface)
            localBroadcastMethod.invoke(null, handler)

            // Wait for discovery to finish (max 10s)
            latch.await(10, java.util.concurrent.TimeUnit.SECONDS)

            // Parse results
            for (dp in printers) {
                try {
                    // DiscoveredPrinter has getDiscoveryDataMap() returning Map<String,String>
                    val getMap = dp.javaClass.getMethod("getDiscoveryDataMap")
                    @Suppress("UNCHECKED_CAST")
                    val dataMap = getMap.invoke(dp) as? Map<String, String> ?: continue

                    val ipAddr   = dataMap["ADDRESS"] ?: dataMap["DNS_NAME"] ?: continue
                    val model    = dataMap["SYSTEM_NAME"] ?: dataMap["MODEL"] ?: "Zebra"
                    val serial   = dataMap["SERIAL_NUMBER"] ?: dataMap["HARDWARE_ADDRESS"] ?: ""
                    val firmware = dataMap["FIRMWARE_VER"] ?: ""

                    found.add(
                        DiscoveredPrinter(
                            brand          = PrinterConfig.PrinterBrand.ZEBRA,
                            model          = model,
                            ipAddress      = ipAddr,
                            port           = PrinterConfig.DEFAULT_PORT,
                            serialOrNode   = serial,
                            connectionType = PrinterConfig.ConnectionType.NETWORK,
                            displayName    = "$model — $ipAddr"
                        )
                    )
                    Log.i(TAG, "Zebra found: $model @ $ipAddr (serial=$serial, fw=$firmware)")
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing Zebra discovered printer: ${e.message}")
                }
            }
        } catch (e: ClassNotFoundException) {
            Log.w(TAG, "Zebra SDK discovery classes not available — trying TCP probe")
            // Fallback: simple TCP probe won't find printers without knowing IPs
        } catch (e: Exception) {
            Log.e(TAG, "Zebra discovery error: ${e.message}", e)
        }

        found.also { Log.i(TAG, "Zebra discovery: ${it.size} printer(s)") }
    }

    /**
     * Detects printers connected via USB.
     */
    suspend fun discoverUsb(context: Context): List<DiscoveredPrinter> =
        withContext(Dispatchers.IO) {
            val found = mutableListOf<DiscoveredPrinter>()
            try {
                val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
                for (device in usbManager.deviceList.values) {
                    when (device.vendorId) {
                        ZEBRA_VENDOR_ID -> {
                            val model = device.productName ?: "Zebra (USB)"
                            val serial = device.serialNumber ?: "usb_${device.deviceId}"
                            found.add(
                                DiscoveredPrinter(
                                    brand          = PrinterConfig.PrinterBrand.ZEBRA,
                                    model          = model,
                                    serialOrNode   = serial,
                                    connectionType = PrinterConfig.ConnectionType.USB,
                                    displayName    = "$model (USB)"
                                )
                            )
                            Log.i(TAG, "Zebra USB found: $model (serial=$serial)")
                        }
                        BROTHER_VENDOR_ID -> {
                            val model = device.productName ?: "Brother (USB)"
                            val serial = device.serialNumber ?: "usb_${device.deviceId}"
                            found.add(
                                DiscoveredPrinter(
                                    brand          = PrinterConfig.PrinterBrand.BROTHER,
                                    model          = model,
                                    serialOrNode   = serial,
                                    connectionType = PrinterConfig.ConnectionType.USB,
                                    displayName    = "$model (USB)"
                                )
                            )
                            Log.i(TAG, "Brother USB found: $model (serial=$serial)")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "USB discovery error: ${e.message}")
            }
            found
        }

    /**
     * Port-scan fallback: scans a /24 subnet on port 9100 to find any
     * responding device. After finding open ports, probes each device
     * to try to identify the brand (Zebra or Brother).
     *
     * @param subnetPrefix e.g. "10.20.21" — will scan .1 through .254
     */
    suspend fun scanSubnet(
        subnetPrefix: String,
        port: Int = 9100,
        timeoutMs: Int = 600
    ): List<DiscoveredPrinter> = withContext(Dispatchers.IO) {
        val found = java.util.concurrent.CopyOnWriteArrayList<DiscoveredPrinter>()
        Log.i(TAG, "Subnet scan: $subnetPrefix.1-254 on port $port")

        coroutineScope {
            // Scan in batches to avoid socket exhaustion
            val batchSize = 32
            for (batch in (1..254).chunked(batchSize)) {
                val jobs = batch.map { host ->
                    async {
                        val ip = "$subnetPrefix.$host"
                        try {
                            val socket = Socket()
                            socket.connect(InetSocketAddress(ip, port), timeoutMs)
                            socket.close()
                            Log.i(TAG, "Subnet scan: open port $port at $ip — probing...")

                            // Try to identify the brand
                            val probeResult = probePrinter(ip, port)
                            found.add(probeResult)
                        } catch (_: Exception) {
                            // No response — not a printer
                        }
                    }
                }
                jobs.awaitAll()
            }
        }

        found.toList().also { Log.i(TAG, "Subnet scan complete: ${it.size} device(s)") }
    }

    /**
     * Probes a device at [ip]:[port] to identify brand/model.
     * - Sends `~HI\r\n` (Zebra Host Identification) — Zebra printers respond with model info.
     * - If no Zebra response, checks SNMP sysDescr on port 161 (optional).
     * - Falls back to generic "Printer" if unidentifiable.
     */
    private fun probePrinter(ip: String, port: Int): DiscoveredPrinter {
        // ── Try SNMP sysDescr (port 161) — reliable cross-subnet brand ID ──
        // A unicast SNMP GET is routable across subnets, so a Brother/Zebra on
        // a different subnet still gets its brand identified here.
        snmpSysDescr(ip)?.let { sys ->
            Log.d(TAG, "SNMP sysDescr $ip: ${sys.take(80)}")
            if (sys.contains("Brother", ignoreCase = true)) {
                val model = extractBrotherModel(sys)
                return DiscoveredPrinter(
                    brand          = PrinterConfig.PrinterBrand.BROTHER,
                    model          = model,
                    ipAddress      = ip,
                    port           = port,
                    connectionType = PrinterConfig.ConnectionType.NETWORK,
                    displayName    = "$model — $ip"
                )
            }
            if (sys.contains("Zebra", ignoreCase = true)) {
                val model = extractZebraModel(sys)
                return DiscoveredPrinter(
                    brand          = PrinterConfig.PrinterBrand.ZEBRA,
                    model          = model,
                    ipAddress      = ip,
                    port           = port,
                    connectionType = PrinterConfig.ConnectionType.NETWORK,
                    displayName    = "$model — $ip"
                )
            }
        }

        // ── Try Zebra identification: send ~HI ──
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, port), 1500)
            socket.soTimeout = 2000
            val out = socket.getOutputStream()
            val inp = socket.getInputStream()

            // Zebra Host Identification command
            out.write("~HI\r\n".toByteArray())
            out.flush()

            Thread.sleep(300)

            val buffer = ByteArray(512)
            val bytesRead = try { inp.read(buffer) } catch (_: Exception) { -1 }
            socket.close()

            if (bytesRead > 0) {
                val response = String(buffer, 0, bytesRead).trim()
                Log.d(TAG, "Probe $ip response: $response")

                // Zebra printers respond with something like "V84.20.21Z,..."
                // or contain "Zebra", model names like "ZT230", etc.
                val isZebra = response.contains("Zebra", ignoreCase = true)
                        || response.contains("ZT", ignoreCase = true)
                        || response.contains("ZD", ignoreCase = true)
                        || response.contains("ZQ", ignoreCase = true)
                        || response.matches(Regex(".*V\\d+\\.\\d+\\.\\d+Z.*"))

                if (isZebra) {
                    val model = extractZebraModel(response)
                    return DiscoveredPrinter(
                        brand          = PrinterConfig.PrinterBrand.ZEBRA,
                        model          = model,
                        ipAddress      = ip,
                        port           = port,
                        serialOrNode   = "",
                        connectionType = PrinterConfig.ConnectionType.NETWORK,
                        displayName    = "$model — $ip"
                    )
                }

                // Check for Brother in response
                val isBrother = response.contains("Brother", ignoreCase = true)
                        || response.contains("QL-", ignoreCase = true)
                        || response.contains("TD-", ignoreCase = true)
                        || response.contains("PT-", ignoreCase = true)

                if (isBrother) {
                    val model = extractBrotherModel(response)
                    return DiscoveredPrinter(
                        brand          = PrinterConfig.PrinterBrand.BROTHER,
                        model          = model,
                        ipAddress      = ip,
                        port           = port,
                        serialOrNode   = "",
                        connectionType = PrinterConfig.ConnectionType.NETWORK,
                        displayName    = "$model — $ip"
                    )
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Probe $ip failed: ${e.message}")
        }

        // ── Fallback: unknown brand but port 9100 is open ──
        return DiscoveredPrinter(
            brand          = PrinterConfig.PrinterBrand.NONE,
            model          = "Printer @ $ip",
            ipAddress      = ip,
            port           = port,
            serialOrNode   = "",
            connectionType = PrinterConfig.ConnectionType.NETWORK,
            displayName    = "Printer @ $ip:$port"
        )
    }

    private fun extractZebraModel(response: String): String {
        // Try to find model patterns like ZT230, ZD420, ZQ520, etc.
        val modelRegex = Regex("(Z[TDQ]\\d{3,4}\\w*)")
        val match = modelRegex.find(response)
        return match?.value ?: "Zebra"
    }

    private fun extractBrotherModel(response: String): String {
        // Try to find model patterns like QL-820NWB, TD-4550DNWB, etc.
        val modelRegex = Regex("((?:QL|TD|RJ|PT|MW)-\\w+)")
        val match = modelRegex.find(response)
        return match?.value ?: "Brother"
    }

    /**
     * Sends an SNMPv1 GET for sysDescr (OID 1.3.6.1.2.1.1.1.0, community
     * "public") and returns the raw response text. SNMP is enabled by default
     * on Brother/Zebra network print servers and is **unicast**, so this works
     * across subnets where broadcast discovery cannot. Returns null on timeout
     * or when SNMP is disabled/blocked.
     */
    private fun snmpSysDescr(ip: String, timeoutMs: Int = 1200): String? {
        // Fixed SNMPv1 GetRequest packet for sysDescr.0, community "public".
        val request = byteArrayOf(
            0x30, 0x29,
            0x02, 0x01, 0x00,                                       // version: SNMPv1 (0)
            0x04, 0x06, 0x70, 0x75, 0x62, 0x6C, 0x69, 0x63,         // community: "public"
            0xA0.toByte(), 0x1C,                                    // GetRequest PDU
            0x02, 0x04, 0x00, 0x00, 0x00, 0x01,                     // request-id
            0x02, 0x01, 0x00,                                       // error-status
            0x02, 0x01, 0x00,                                       // error-index
            0x30, 0x0E,                                             // varbind list
            0x30, 0x0C,                                             // varbind
            0x06, 0x08, 0x2B, 0x06, 0x01, 0x02, 0x01, 0x01, 0x01, 0x00, // OID 1.3.6.1.2.1.1.1.0
            0x05, 0x00                                              // value: NULL
        )
        return try {
            DatagramSocket().use { sock ->
                sock.soTimeout = timeoutMs
                val addr = InetAddress.getByName(ip)
                sock.send(DatagramPacket(request, request.size, addr, 161))
                val buf = ByteArray(1024)
                val resp = DatagramPacket(buf, buf.size)
                sock.receive(resp)
                String(buf, 0, resp.length, Charsets.ISO_8859_1)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Checks if a previously known printer (by its saved serial/MAC/node)
     * is among the newly discovered printers, regardless of IP change.
     */
    fun findKnownPrinter(
        discovered: List<DiscoveredPrinter>,
        savedSerial: String
    ): DiscoveredPrinter? {
        if (savedSerial.isBlank()) return null
        return discovered.firstOrNull {
            it.serialOrNode.isNotBlank() && it.serialOrNode.equals(savedSerial, ignoreCase = true)
        }
    }
}



