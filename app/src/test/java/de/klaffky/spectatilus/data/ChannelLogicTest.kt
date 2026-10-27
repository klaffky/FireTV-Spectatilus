package de.klaffky.spectatilus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader

class ChannelLogicTest {

    private val sample = """
        #EXTM3U
        #EXTINF:-1 group-title="FR | France",FR: TF1 HD
        http://x/tf1
        #EXTINF:-1 group-title="DE | Vollprogramm",DE: ProSieben SD
        http://x/p7sd
        #EXTINF:-1 tvg-logo="http://l/p7.png" group-title="DE | Vollprogramm",DE: ProSieben FHD
        http://x/p7fhd
        #EXTINF:-1 group-title="AT | Austria",AT: ProSieben Austria HD
        http://x/p7at
        #EXTINF:-1 group-title="UK",UK: BBC One HD
        http://x/bbc
        #EXTINF:-1 group-title="DE | Sport",DE: Sky Sport Bundesliga 10 HD
        http://x/bl10
        #EXTINF:-1 group-title="DE | Sport",DE: Sky Sport Bundesliga 2 HD
        http://x/bl2
        #EXTINF:-1 group-title="DE | Sport",DE: Sky Sport Bundesliga 1 HD
        http://x/bl1
        #EXTINF:-1 tvg-id="DasErste.de" group-title="Allgemein",Das Erste HD
        http://x/ard
        #EXTINF:-1 tvg-name="ZDF",ZDF
        http://x/zdf
        #EXTINF:-1 group-title="Germany",SAT.1 HD
        http://x/sat1
        #EXTINF:-1 group-title="Deutschland",RTL ZWEI HD
        http://x/rtl2
        #EXTINF:-1 group-title="Deutschland",RTL HD
        http://x/rtl
        #EXTINF:-1 group-title="Deutschland",RTL+ HD
        http://x/rtlplus
        #EXTINF:-1 group-title="Deutschland",kabel eins HD
        http://x/k1
        #EXTINF:-1 group-title="Deutschland",WDR Köln HD
        http://x/wdr
        #EXTINF:-1 group-title="Musik",Deluxe Music
        http://x/deluxe
        #EXTINF:-1 group-title="Österreich",ORF 1 HD
        http://x/orf
        #EXTINF:-1 group-title="Filme",Random Movie Channel
        http://x/random
        #EXTINF:-1 group-title="DE | Doku",DE: Discovery Channel HD
        http://x/disc
        #EXTINF:-1 group-title="DE | Doku",[DE] Animal Planet
        http://x/animal
        #EXTINF:-1 tvg-country="DE",Bibel TV
        #EXTVLCOPT:http-user-agent=TestUA
        http://x/bibel
        #EXTINF:-1 group-title="DE | Vollprogramm",DE: ProSieben +1
        http://x/p7plus1
    """.trimIndent()

    private fun build(text: String = sample, start: Int = 100): ChannelList {
        val store = HashMap<String, Int>()
        return ChannelBuilder.build(BufferedReader(StringReader(text))) { keys, names ->
            var next = maxOf(start, (store.values.maxOrNull() ?: 0) + 1)
            keys.sortedBy { names[it]?.lowercase() }.associateWith { k -> store.getOrPut(k) { next++ } }
        }
    }

    @Test
    fun bundesligaFirstThenClassicNumbers() {
        val list = build()
        val names = list.channels.map { it.name }
        assertEquals(listOf("Sky Sport Bundesliga 1", "Sky Sport Bundesliga 2", "Sky Sport Bundesliga 10"), names.take(3))
        assertEquals("★1", list.label(list.channels[0]))
        val byName = list.channels.associateBy { it.name }
        assertEquals(1, byName.getValue("Das Erste").number)
        assertEquals(2, byName.getValue("ZDF").number)
        assertEquals(3, byName.getValue("RTL").number)
        assertEquals(4, byName.getValue("SAT.1").number)
        assertEquals(6, byName.getValue("RTLZWEI").number)
        assertEquals(7, byName.getValue("ProSieben").number)
        assertEquals("7", list.label(byName.getValue("ProSieben")))
        assertEquals(8, byName.getValue("kabel eins").number)
        assertEquals("RTLup", list.channels.first { it.urls.contains("http://x/rtlplus") }.name)
        assertEquals("WDR", list.channels.first { it.urls.contains("http://x/wdr") }.name)
        // nach den Favoriten streng aufsteigend
        val regular = list.channels.drop(3).map { it.number }
        assertEquals(regular.sorted(), regular)
    }

    @Test
    fun onlyGermanChannels() {
        val list = build()
        val urls = list.channels.flatMap { it.urls }
        listOf("http://x/tf1", "http://x/p7at", "http://x/bbc", "http://x/orf", "http://x/random").forEach {
            assertFalse("$it sollte ausgeblendet sein", it in urls)
        }
        listOf("http://x/deluxe", "http://x/disc", "http://x/animal", "http://x/bibel", "http://x/p7plus1").forEach {
            assertTrue("$it sollte enthalten sein", it in urls)
        }
        assertEquals(5, list.hidden)
    }

    @Test
    fun variantsMergedBestQualityFirst() {
        val p7 = build().channels.first { it.name == "ProSieben" }
        assertEquals(listOf("http://x/p7fhd", "http://x/p7sd"), p7.urls)
        assertEquals("http://l/p7.png", p7.logo)
        assertEquals("TestUA", build().channels.first { it.name == "Bibel TV" }.userAgent)
    }

    @Test
    fun extraChannelsGetStableNumbersAcrossPlaylists() {
        val store = HashMap<String, Int>()
        val provider = { keys: Collection<String>, names: Map<String, String> ->
            var next = maxOf(100, (store.values.maxOrNull() ?: 99) + 1)
            keys.sortedBy { names[it]?.lowercase() }.associateWith { k -> store.getOrPut(k) { next++ } }
        }
        val first = ChannelBuilder.build(BufferedReader(StringReader(sample)), provider)
        val other = "#EXTM3U\n#EXTINF:-1 group-title=\"DE\",DE: Animal Planet HD\nhttp://y/a\n#EXTINF:-1 group-title=\"DE\",DE: Pro7 HD\nhttp://y/p7\n"
        val second = ChannelBuilder.build(BufferedReader(StringReader(other)), provider)
        val a1 = first.channels.first { it.name == "Animal Planet" }.number
        val a2 = second.channels.first { it.name == "Animal Planet" }.number
        assertEquals(a1, a2)
        assertTrue(a1 >= 100)
        assertEquals(7, second.channels.first { it.name == "ProSieben" }.number)
    }

    @Test
    fun cleanNames() {
        assertEquals("ProSieben", ChannelOrder.cleanName("DE: ProSieben FHD"))
        assertEquals("Animal Planet", ChannelOrder.cleanName("[DE] Animal Planet"))
        assertEquals("Sky Sport Bundesliga 1", ChannelOrder.cleanName("DE | Sky Sport Bundesliga 1 HD"))
        assertEquals("sat1", ChannelOrder.keyOf(ChannelOrder.cleanName("SAT.1 HD")))
        assertEquals("arte", ChannelOrder.keyOf(ChannelOrder.cleanName("arte DE")))
        assertEquals("Das Erste", ChannelOrder.cleanName("Das Erste"))
    }
}
