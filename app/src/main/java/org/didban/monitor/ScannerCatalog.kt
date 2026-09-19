package org.didban.monitor

import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Offline candidates, NOT a claim of reachability, TLS suitability, or clean IPs. */
internal object ScannerCatalog {
    const val VERSION = "2026-09-19"
    const val CF_SOURCE = "https://www.cloudflare.com/ips-v4/"
    const val MAX_TEXT_BYTES = 65_536
    const val MAX_SNI_TARGETS = 256
    const val SNI_CONCURRENCY = 4

    enum class Group { ALL, TECHNOLOGY, DEVELOPMENT, KNOWLEDGE, SERVICES }

    private fun names(text: String) = text.trimIndent().trim().split(Regex("\\s+"))

    val groups: Map<Group, List<String>> = linkedMapOf(
        Group.TECHNOLOGY to names("""
            www.microsoft.com www.apple.com www.icloud.com updates.cdn-apple.com
            www.samsung.com www.cisco.com www.asus.com www.amd.com www.nvidia.com
            www.intel.com www.ibm.com www.hp.com www.dell.com www.lenovo.com
            www.acer.com www.lg.com www.sony.com www.panasonic.com www.philips.com
            www.siemens.com www.bosch.com www.toshiba.com www.hitachi.com
            www.fujitsu.com www.nec.com www.nokia.com www.ericsson.com www.huawei.com
            www.qualcomm.com www.broadcom.com www.arm.com www.analog.com www.ti.com
            www.st.com www.microchip.com www.nxp.com www.infineon.com
            www.keysight.com www.tek.com www.juniper.net www.arista.com
            www.netapp.com www.seagate.com www.westerndigital.com www.synology.com
            www.qnap.com www.tp-link.com www.ui.com www.fortinet.com
            www.paloaltonetworks.com www.vmware.com www.oracle.com www.redhat.com
        """),
        Group.DEVELOPMENT to names("""
            github.com gitlab.com bitbucket.org codeberg.org sourceforge.net
            www.apache.org www.eclipse.org www.mozilla.org addons.mozilla.org
            developer.mozilla.org www.firefox.com www.python.org docs.python.org
            pypi.org www.rust-lang.org doc.rust-lang.org go.dev pkg.go.dev
            kotlinlang.org www.jetbrains.com www.java.com openjdk.org adoptium.net
            spring.io gradle.org maven.apache.org www.docker.com docs.docker.com
            kubernetes.io helm.sh www.terraform.io www.hashicorp.com www.ansible.com
            ubuntu.com www.debian.org fedoraproject.org archlinux.org www.opensuse.org
            www.freebsd.org www.netbsd.org www.openbsd.org alpinelinux.org
            www.kernel.org www.linuxfoundation.org www.gnu.org www.gnome.org
            kde.org www.qt.io www.lua.org www.ruby-lang.org www.perl.org www.php.net
            nodejs.org deno.com bun.sh www.npmjs.com yarnpkg.com pnpm.io ziglang.org
            www.swift.org www.typescriptlang.org dart.dev flutter.dev
        """),
        Group.KNOWLEDGE to names("""
            www.wikipedia.org www.wikimedia.org www.wikisource.org www.wiktionary.org
            archive.org openlibrary.org www.gutenberg.org www.britannica.com
            www.khanacademy.org www.coursera.org www.edx.org www.udacity.com
            www.mit.edu www.stanford.edu www.harvard.edu www.berkeley.edu
            www.ox.ac.uk www.cam.ac.uk www.caltech.edu www.princeton.edu www.yale.edu
            www.columbia.edu www.cornell.edu www.uchicago.edu www.ucla.edu ethz.ch
            www.epfl.ch www.nasa.gov www.esa.int www.noaa.gov www.weather.gov
            www.usgs.gov home.cern www.who.int www.un.org www.unesco.org
            www.worldbank.org www.imf.org www.oecd.org ourworldindata.org
            www.nature.com www.science.org www.scientificamerican.com
            www.nationalgeographic.com www.bbc.com www.reuters.com apnews.com
            www.theguardian.com www.npr.org www.dw.com www.france24.com
            www.aljazeera.com www.euronews.com www.ted.com
        """),
        Group.SERVICES to names("""
            www.amazon.com www.ebay.com www.walmart.com www.target.com
            www.bestbuy.com www.costco.com www.ikea.com www.homedepot.com
            www.lowes.com www.wayfair.com www.etsy.com www.shopify.com
            www.aliexpress.com www.alibaba.com www.rakuten.com www.mercadolibre.com
            www.zalando.com www.otto.de www.mediamarkt.com www.decathlon.com
            www.nike.com www.adidas.com www.puma.com www.reebok.com
            www.underarmour.com www.newbalance.com www.patagonia.com
            www.uniqlo.com www.zara.com www.hm.com www.gap.com www.levi.com
            www.booking.com www.expedia.com www.tripadvisor.com www.airbnb.com
            www.trip.com www.agoda.com www.hotels.com www.marriott.com
            www.hilton.com www.hyatt.com www.accor.com www.ihg.com
            www.singaporeair.com www.emirates.com www.qatarairways.com
            www.lufthansa.com www.klm.com www.qantas.com www.airfrance.com
            www.cathaypacific.com www.jetblue.com www.southwest.com
            www.united.com www.delta.com
        """)
    )

    /** Interleave categories so small ALL scans do not only test the first category. */
    fun domains(group: Group): List<String> {
        if (group != Group.ALL) return groups[group].orEmpty()
        return (0 until groups.values.maxOf { it.size }).flatMap { index ->
            groups.values.mapNotNull { it.getOrNull(index) }
        }.distinct()
    }

    fun selectedRanges(encoded: String): List<String> {
        val selected = encoded.split(',').toSet()
        return CloudflareRanges.V4.filter { it in selected }
    }

    fun sniPlan(group: Group, limit: Int, port: Int): List<Pair<String, Int>> =
        if (port !in 1..65535) emptyList()
        else domains(group).take(limit.coerceIn(1, MAX_SNI_TARGETS)).map { it to port }

    data class ParsedTargets(val targets: List<Pair<String, Int>>, val rejected: Int, val truncated: Boolean)
    fun parseSniList(text: String, defaultPort: Int, cap: Int = MAX_SNI_TARGETS): ParsedTargets {
        val found = linkedSetOf<Pair<String, Int>>()
        var rejected = 0
        val limit = cap.coerceIn(1, MAX_SNI_TARGETS)
        var truncated = false
        text.take(MAX_TEXT_BYTES).lineSequence().forEach { raw ->
            val line = raw.substringBefore('#').trim()
            if (line.isNotEmpty()) {
                val parsed = RealitySniScanner.parseTarget(line, defaultPort)
                if (parsed == null || !validDomain(parsed.first)) rejected++
                else if (parsed !in found) {
                    if (found.size < limit) found += parsed else truncated = true
                }
            }
        }
        return ParsedTargets(found.toList(), rejected, truncated || text.length > MAX_TEXT_BYTES)
    }

    fun validDomain(host: String): Boolean = host.length <= 253 && host.contains('.') &&
        Cidr.parseIp(host) == null && host.split('.').all {
            it.length in 1..63 && it.first().isLetterOrDigit() && it.last().isLetterOrDigit() &&
                it.all { c -> c in 'a'..'z' || c in '0'..'9' || c == '-' }
        }

    /** Document-provider streams can lie about size: bound the actual bytes read. */
    fun readText(input: InputStream): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val n = input.read(buffer, 0, minOf(buffer.size, MAX_TEXT_BYTES + 1 - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
            require(out.size() <= MAX_TEXT_BYTES) { "Scanner list exceeds 64 KiB" }
        }
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(out.toByteArray()))
            .toString().removePrefix("\uFEFF")
    }
}
