package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import org.jsoup.nodes.Element


class CalcioStreamingLatProvider : MainAPI() {
    override var lang = "it"
    override var mainUrl = "https://calciostreaming.lat"
    override var name = "CalcioStreamingLat"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Live)
    override val hasQuickSearch = false

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl).document
        val sections = document.select("div.panel")
        if (sections.isEmpty()) throw ErrorLoadingException()

        return HomePageResponse(sections.map {it ->
            val sectionName = it.select("h4 b")!!.text()
            val shows = it.select("tr.m3u8").map {
                val href = it.select("a")!!.attr("href")
                val name = it.select("a")!!.text()
                val posterUrl = fixUrl("https://i.imgur.com/YTz3Wp7.jpeg")
                LiveSearchResponse(
                    name,
                    href,
                    this@CalcioStreamingLatProvider.name,
                    TvType.Live,
                    posterUrl,
                )
            }
            HomePageList(
                sectionName,
                shows,
                isHorizontalImages = true
            )
        })
    }
    

    override suspend fun load(url: String): LoadResponse {
        // Prende la pagina e segue eventuali redirect per avere il dominio corretto
        val response = app.get(url)
        val finalUrl = response.url

        return newLiveStreamLoadResponse(
            name = response.document.title(),
            url = finalUrl,
            dataUrl = finalUrl
        ) {
            plot = "Stream live da CalcioStreamingLat"
            posterUrl = fixUrl("https://i.imgur.com/YTz3Wp7.jpeg")
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val response = app.get(data)
        val doc = response.document
        val html = doc.toString()
        val finalUrl = response.url
        val m3u8Regex = Regex("""https?:\/\/[^\s"'\\]+\.m3u8""")

        val headers = mapOf("Referer" to finalUrl)

        // 1. Link .m3u8 diretti
        m3u8Regex.findAll(html).map { it.value }.distinct().forEach { m3u8 ->
            callback(
                newExtractorLink(
                    source = this.name,
                    name = "Diretto",
                    url = m3u8,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = finalUrl
                    this.quality = Qualities.Unknown.value
                    this.headers = headers
                }
            )
        }

        // 2. fetch() JS
        val fetchRegex = Regex("""fetch\((.*?)\)""")
        val fetchCalls = fetchRegex.findAll(html).mapNotNull { match ->
            val raw = match.groupValues[1].trim()
            try {
                if (raw.startsWith("{")) {
                    val fixed = raw.replace("'", "\"").replace(Regex("""(\w+):"""), "\"$1\":")
                    val json = JSONObject(fixed)
                    val url = json.optString("url")
                    val method = json.optString("method", "GET").uppercase()
                    if (url.isNotBlank()) Pair(method, url) else null
                } else if (raw.startsWith("\"")) {
                    Pair("GET", raw.removeSurrounding("\""))
                } else null
            } catch (_: Exception) {
                null
            }
        }

        for ((method, fetchUrl) in fetchCalls) {
            try {
                val responseText = if (method == "POST") {
                    app.post(fixUrl(fetchUrl), data = mapOf<String, String>()).text
                } else {
                    app.get(fixUrl(fetchUrl)).text
                }

                m3u8Regex.findAll(responseText).map { it.value }.distinct().forEach { m3u8 ->
                    callback(
                        newExtractorLink(
                            source = this.name,
                            name = "Fetch",
                            url = m3u8,
                            type = ExtractorLinkType.M3U8
                        ) {
                            this.referer = fetchUrl
                            this.quality = Qualities.Unknown.value
                            this.headers = headers
                        }
                    )
                }
            } catch (_: Exception) {
                // fetch fallita, ignorata
            }
        }

        return true
    }
}
