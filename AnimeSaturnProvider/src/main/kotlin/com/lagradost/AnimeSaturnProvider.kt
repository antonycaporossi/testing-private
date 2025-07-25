package com.lagradost

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addDuration
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.addRating
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Element
import com.lagradost.api.Log
import com.lagradost.cloudstream3.CommonActivity.showToast
import org.mozilla.javascript.ConsString
import org.mozilla.javascript.Context
import com.lagradost.nicehttp.NiceResponse
import org.mozilla.javascript.Scriptable

class AnimeSaturnProvider : MainAPI() {
    override var mainUrl = "https://www.animesaturn.cx"
    override var name = "AnimeSaturn"
    override var lang = "it"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    private data class QuickSearchParse(
        @JsonProperty("link") val link: String,
        @JsonProperty("image") val image: String,
        @JsonProperty("name") val name: String
    )
    
    companion object {
        private var mainUrl = AnimeSaturnProvider().mainUrl
        private var securityCookie = ""
        private val userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36"
        private var headers = mutableMapOf(
                "cookie" to securityCookie,
                "user-agent" to userAgent,
                "x-requested-with" to "XMLHttpRequest"
            )
        private suspend fun request(url: String): NiceResponse {
            /*if (securityCookie.isEmpty()) {
                securityCookie = getCookies(mainUrl)
                headers["cookie"] = securityCookie.toString()
            }*/
            return app.get(url)
        }

        
        fun getStatus(t: String?): ShowStatus? {
            return when (t?.lowercase()) {
                "finito" -> ShowStatus.Completed
                "in corso" -> ShowStatus.Ongoing
                else -> null
            }
        }
        private suspend fun getCookies(url: String): String {
            val rhino = Context.enter()
            rhino.optimizationLevel = -1
            val scope: Scriptable = rhino.initSafeStandardObjects()

            val slowAes = app.get("$mainUrl/min.js").text
            val doc = "var document = {};"

            val siteScriptRegex = Regex("""<script>(.*)\+";\s*path""")
            val html = app.get(url, headers = headers).text
            val siteScript = siteScriptRegex.find(html)?.groupValues?.getOrNull(1)!!.trim()
            rhino.evaluateString(
                scope,
                "$doc;$slowAes;$siteScript",
                "JavaScript",
                1,
                null
            )
            val jsEval = scope.get("document", scope) as? Scriptable
            val cookies = jsEval?.get("cookie", jsEval) as? ConsString
            return cookies?.toString() ?: ""
        }
    }

    private fun Element.toSearchResponse(): AnimeSearchResponse? {
        val url = this.select("a").first()?.attr("href")
            ?: return null
        val title = this.select("a[title]").first()?.attr("title")?.removeSuffix("(ITA)")
            ?: return null
        val posterUrl = this.select("img.new-anime").first()!!.attr("src")
        val isDubbed = this.select("a[title]").first()?.attr("title")?.contains("(ITA)")
            ?: false

        return newAnimeSearchResponse(title, url, TvType.Anime){
            addDubStatus(isDubbed)
            addPoster(posterUrl)
        }
    }

    private fun Element.toSearchResult(): AnimeSearchResponse {
        var title = this.select("a.badge-archivio").first()!!.text()
        var isDubbed = false

        if (title.contains(" (ITA)")){
            title = title.replace(" (ITA)", "")
            isDubbed = true
        }

        val url = this.select("a.badge-archivio").first()!!.attr("href")
        val posterUrl = this.select("img.locandina-archivio[src]").first()!!.attr("src")

        return newAnimeSearchResponse(title, url, TvType.Anime) {
            addDubStatus(isDubbed)
            addPoster(posterUrl)
        }
    }

    private fun Element.toEpisode(): Episode? {
        var episode = this.text().split(" ")[1]
        if (episode.contains(".")) return null
        if (episode.contains("-"))
            episode = episode.split("-")[0]

        return newEpisode(this.attr("href")) {
                this.episode = episode.toInt()
        }

    }

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val list = mutableListOf<HomePageList>()
        val documentLastEpisode = request("$mainUrl/fetch_pages.php?request=episodes",).document
        
        val lastedEpisode = documentLastEpisode.select(".anime-card").mapNotNull {
            val url = it.select("a").first()?.attr("href")?.let { href ->
                href.split("-ep-")[0].replace("/ep/", "/anime/")
            } ?: return@mapNotNull null
            val title = it.select("a").first()?.attr("title")?.removeSuffix(" (ITA)")
                ?: return@mapNotNull null
            val posterUrl = it.select("img").first()?.attr("src")

            val dub = it.select("a").first()?.attr("title")?.contains("(ITA)") ?: false
            val episode = it.select(".anime-episode").text().split(" ").last().toIntOrNull()

            newAnimeSearchResponse(title, url, TvType.Anime) {
                addPoster(posterUrl)
                addDubStatus(dub, episode)
            }
        }
        list.add(HomePageList("Ultimi episodi", lastedEpisode, isHorizontalImages = true))

        val document = app.get(mainUrl, headers = headers).document
        document.select("div.container:has(span.saturn-title-special)").forEach {
            val tabName = it.select("span.saturn-title-special").first()!!.text()
            if (tabName.equals("Ultimi episodi")) return@forEach

            val results = it.select(".main-anime-card").mapNotNull { card ->
                card.toSearchResponse()
            }
            list.add(HomePageList(tabName, results))
        }
        return HomePageResponse(list)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? {
        val quickSearchJ = app.get("$mainUrl/index.php?search=1&key=$query", headers = headers).text
        return tryParseJson<List<QuickSearchParse>>(quickSearchJ)?.map {
            newAnimeSearchResponse(it.name.removeSuffix("(ITA)"), it.link, TvType.Anime) {
                addDubStatus(it.name.contains(" (ITA)"))
                addPoster(it.image)
            }
        }

    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/animelist?search=$query", headers = headers).document
        return document.select("div.item-archivio").map {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, headers = headers).document

        val title = document.select("img.cover-anime").first()!!.attr("alt").removeSuffix("(ITA)")
        val japTitle = document.select("div.box-trasparente-alternativo").first()!!.text().removeSuffix("(ITA)")
        val posterUrl = document.select("img.cover-anime[src]").first()!!.attr("src")
        var malId : Int? = null
        var aniListId : Int? = null

        document.select("[rel=\"noopener noreferrer\"]").forEach {
            if(it.attr("href").contains("myanimelist"))
                malId = it.attr("href").removeSuffix("/").split('/').last().toIntOrNull()
            else
                aniListId = it.attr("href").removeSuffix("/").split('/').last().toIntOrNull()
        }

        val plot = document.select("div#shown-trama").first()?.text()
        val tags = document.select("a.generi-as").map { it.text() }
        val isDubbed = document.select("div.anime-title-as").first()!!.text().contains("(ITA)")
        val trailerUrl = document.select("#trailer-iframe").first()?.attr("src")

        val details : List<String>? = document.select("div.container:contains(Stato: )").first()?.text()?.split(" ")
        var status : String? = null
        var duration : String? = null
        var year : String? = null
        var score : String? = null

        if (!details.isNullOrEmpty()) {
            details.forEach {
                val index = details.indexOf(it) +1
                when (it) {
                    "Stato:" -> status = details[index]
                    "episodi:" -> duration = details[index]
                    "uscita:" -> year = details[index + 2]
                    "Voto:" -> score = details[index].split("/")[0]
                    else -> return@forEach
                }
            }
        }

        val episodes = document.select("a.bottone-ep").mapNotNull{ it.toEpisode() }

        val recommendations = document.select("#carousel > .main-anime-card").mapNotNull {
            it.toSearchResponse()
        }

        return newAnimeLoadResponse(title, url, TvType.Anime) {
            this.engName = title
            this.japName = japTitle
            this.year = year?.toIntOrNull()
            this.plot = plot
            this.tags = tags
            this.showStatus = getStatus(status)
            addPoster(posterUrl)
            addRating(score)
            addEpisodes(if (isDubbed) DubStatus.Dubbed else DubStatus.Subbed, episodes)
            addMalId(malId)
            addAniListId(aniListId)
            addDuration(duration)
            addTrailer(trailerUrl)
            this.recommendations = recommendations
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val page = app.get(data, headers = headers).document
        val episodeLink = page.select("div.card-body > a[href]").find {it1 ->
            it1.attr("href").contains("watch?")
        }?.attr("href") ?: return false

        val episodePage = app.get(episodeLink, headers = headers).document
        val episodeUrl: String?
        var isM3U8 = false

        if (episodePage.select("video > source").isNotEmpty()) // Old player
            episodeUrl = episodePage.select("video > source").first()!!.attr("src")
        else { // New player
            val script = episodePage.select("script").find {
                it.toString().contains("jwplayer('player_hls').setup({")
            }!!.toString()
            episodeUrl = script.split(" ").find { it.contains(".m3u8") and !it.contains(".replace") }!!.replace("\"","").replace(",", "")
            isM3U8 = true
        }

        callback.invoke(
            newExtractorLink(
                name,
                name,
                episodeUrl!!,
                if (isM3U8) ExtractorLinkType.M3U8 else INFER_TYPE
            ){
                this.referer = data //Some servers need the old host as referer, and the new ones accept it too
                this.quality = Qualities.Unknown.value
            }
        )
        return true
    }
}
