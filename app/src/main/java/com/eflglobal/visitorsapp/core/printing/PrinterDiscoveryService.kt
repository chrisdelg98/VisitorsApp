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
    suspend fun discoverAll(context: Context): List<DiscoveredPrinter> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<DiscoveredPrinter>()

            // ── Phase 1: SDK-based discovery ─────────────────────────────
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

            // ── Phase 2: Subnet port-scan fallback ───────────────────────
            if (results.none { it.connectionType == PrinterConfig.ConnectionType.NETWORK }) {
                val subnet = getDeviceSubnet(context)
                if (subnet != null) {
                    Log.i(TAG, "No network printers from SDK — falling back to subnet scan: $subnet.*")
                    try {
                        val portScanResults = withTimeoutOrNull(18_000L) {
                            scanSubnet(subnet, 9100, 600)
                        } ?: emptyList()
                        results.addAll(portScanResults)
                    } catch (e: Exception) {
                        Log.w(TAG, "Subnet scan failed: ${e.message}")
                    }
                } else {
                    Log.w(TAG, "Cannot determine device subnet — skipping port scan")
                }
            }

            // Deduplicate by (brand + ip + serial)
            results.distinctBy { "${it.brand}|${it.ipAddress}|${it.serialOrNode}" }
                .also { Log.i(TAG, "Discovery complete: ${it.size} printer(s) found") }
        }

    /**
     * Returns the /24 subnet prefix of the device's WiFi IP, e.g. "10.20.21".
     * Returns null if the device has no usable network interface.
     */
    private fun getDeviceSubnet(context: Context): String? {
        try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            val ip = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (ip != 0) {
                val a = ip and 0xFF
                val b = (ip shr 8) and 0xFF
                val c = (ip shr 16) and 0xFF
                return "$a.$b.$c"
            }

            // Fallback: check all network interfaces
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                for (addr in iface.inetAddresses) {
                    if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                        val parts = addr.hostAddress?.split(".") ?: continue
                        if (parts.size == 4) return "${parts[0]}.${parts[1]}.${parts[2]}"
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "getDeviceSubnet error: ${e.message}")
        }
        return null
    }

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



