package com.lagrados

import com.lagradost.api.Log
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addDuration
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.addRating
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.NextAiring
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDubStatus
import com.lagradost.cloudstream3.addEpisodes
import com.lagradost.cloudstream3.addPoster
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.nicehttp.NiceResponse
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

abstract class AnimeWorldCore : MainAPI() {
    override var mainUrl = Companion.mainUrl
    override val hasMainPage = true
    override val hasQuickSearch = true

    open val isDubbed = false

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    companion object {
        private var mainUrl = "https://www.animeworld.so"
        private var cookies = mutableMapOf<String, String>()
        private var headers = mutableMapOf<String, String>()

        private suspend fun request(url: String): NiceResponse {
            if (cookies.isEmpty()) {
                headers["Cookie"] = getSecurityCookie()
//                Log.d("AnimeWorld:Cookie", "Cookie: ${headers["Cookie"]}")
            }
            return app.get(url, headers = headers)
        }

        private suspend fun getSecurityCookie(): String {
            val r = app.get(mainUrl, allowRedirects = false)
//            Log.d("AnimeWorld:getSecurityCookie", "Cookie: ${r.headers["set-cookie"]}")
            val securityCookie = r.headers["set-cookie"]!!.substringBefore(";")
//            Log.d("AnimeWorld:getSecurityCookie", "Cookie: ${securityCookie}")
            val r2 = app.get("$mainUrl/?d=1", headers = headers, allowRedirects = false)
//            Log.d("AnimeWorld:getSecurityCookie", "Cookie: ${r2.headers["set-cookie"]}")
            val sessionCookie = r2.headers["set-cookie"]!!.substringBefore(";")
            return "$securityCookie; $sessionCookie"
        }
    }

    private fun getType(t: String?): TvType {
        return when (t?.lowercase()) {
            "movie" -> TvType.AnimeMovie
            "ova" -> TvType.OVA
            else -> TvType.Anime
        }
    }

    private fun getStatus(t: String?): ShowStatus? {
        return when (t?.lowercase()) {
            "finito" -> ShowStatus.Completed
            "in corso" -> ShowStatus.Ongoing
            else -> null
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val pageData: NiceResponse = if (page > 1) {
            request(request.data + "&page=$page")
        } else {
            request(request.data)
        }

        val document = pageData.document
//        Log.d("AnimeWorld:MainPage", "Document: $document")
        val list = ArrayList<AnimeSearchResponse>()
        var hasNextPage = false
        if (request.name.contains("Top")) {
            val items = document.select("div.row  .content")
//            Log.d("AnimeWorld:MainPage", "Items: ${items[0]}")
            items.map { list.add(it.toSearchResult(true)) }
        } else {
            val items = document.select("div.film-list > .item")
//            Log.d("AnimeWorld:MainPage", "Items: ${items[0]}")
            items.map { list.add(it.toSearchResult(false)) }

            val pagingWrapper = document.select("#paging-form").firstOrNull()
            val totalPages = pagingWrapper?.select("span.total")?.text()?.toIntOrNull()
            hasNextPage = totalPages != null && (page + 1) < totalPages
        }

        val finalList = list.filter { anime ->
            filterByDubStatus(anime)
        }

        return newHomePageResponse(
            HomePageList(
                name = request.name,
                list = finalList,
                isHorizontalImages = false
            ), hasNextPage
        )
    }

    private fun Element.toSearchResult(isTopPage: Boolean): AnimeSearchResponse {
        // Extension function for parsing href.
        fun String.parseHref(): String {
            val parts = this.split('.').toMutableList()
            parts[1] = parts[1].substringBeforeLast('/')
            return parts.joinToString(".")
        }

        // Determine anchor and throw an error if not found.
        val anchor = this.selectFirst(if (isTopPage) "a" else "a.name")
            ?: throw ErrorLoadingException("Error parsing the page")

        val url = fixUrl(anchor.attr("href").parseHref())

        // Simplify title and otherTitle selection.
        val titleText = if (isTopPage) {
            this.select("div.info > div.main > a").text()
        } else {
            anchor.text()
        }

        val title = if (isDubbed) titleText.replace(" (ITA)", "") else titleText
        val otherTitle =
            if (isDubbed) anchor.attr("data-jtitle").replace(" (ITA)", "") else titleText

        // Use when for `poster` selection.
        val poster = when {
            isTopPage -> anchor.select("img").attr("src")
            else -> this.select("a.poster img").attr("src")
        }

        // Select typeElement based on `isTopPage`.
        val typeElement = this.select(if (isTopPage) "div.type" else "div.status")

        val dub = typeElement.select(".dub").isNotEmpty()
        val type = when {
            typeElement.select(".movie").isNotEmpty() -> TvType.AnimeMovie
            typeElement.select(".ova").isNotEmpty() -> TvType.OVA
            else -> TvType.Anime
        }

        // Construct and return AnimeSearchResponse.
        return newAnimeSearchResponse(title, url, type) {
            addDubStatus(dub)
            this.otherName = otherTitle
            this.posterUrl = poster
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? {
        val document = app.post(
            "$mainUrl/api/search/v2?keyword=${query}",
            referer = mainUrl,
            cookies = cookies
        ).text

        return tryParseJson<SearchJson>(document)?.animes?.map { anime ->
            val type = when (anime.type) {
                "Movie" -> TvType.AnimeMovie
                "OVA" -> TvType.OVA
                else -> TvType.Anime
            }
            val dub = when (anime.language) {
                "it" -> true
                else -> false
            }
            newAnimeSearchResponse(anime.name, "$mainUrl/play/${anime.link}.${anime.id}", type) {
                addDubStatus(dub)
                this.otherName = anime.otherTitle
                this.posterUrl = anime.image
            }
        }?.filter { anime ->
            filterByDubStatus(anime)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = request("$mainUrl/search?keyword=$query").document

        val list = document.select(".film-list > .item").map {
            it.toSearchResult(false)
        }
        return list.filter { anime ->
            filterByDubStatus(anime)
        }
    }

    private fun filterByDubStatus(anime: AnimeSearchResponse): Boolean {
        return anime.dubStatus?.any {
            if (isDubbed) {
                it == DubStatus.Dubbed
            } else {
                it == DubStatus.Subbed
            }
        } ?: true
    }

    override suspend fun load(url: String): LoadResponse {
        val document = request(url).document
//        Log.d("AnimeWorld:load", "Url: $url")


        val widget = document.select("div.widget.info")
        val title = widget.select(".info .title").text().removeSuffix(" (ITA)")
        val otherTitle = widget.select(".info .title").attr("data-jtitle").removeSuffix(" (ITA)")
        val description =
            widget.select(".desc .long").first()?.text() ?: widget.select(".desc").text()
        val poster = document.select(".thumb img").attr("src")

        val type: TvType = getType(widget.select("dd").first()?.text())
        val genres = widget.select(".meta").select("a[href*=\"/genre/\"]").map { it.text() }
        val rating = widget.select("#average-vote").text()

        val trailerUrl = document.select(".trailer[data-url]").attr("data-url")
        val malId = document.select("#mal-button").attr("href")
            .split('/').last().toIntOrNull()
        val anlId = document.select("#anilist-button").attr("href")
            .split('/').last().toIntOrNull()

        var dub = false
        var year: Int? = null
        var status: ShowStatus? = null
        var duration: String? = null

        for (meta in document.select(".meta dt, .meta dd")) {
            val text = meta.text()
            if (text.contains("Audio"))
                dub = meta.nextElementSibling()?.text() == "Italiano"
            else if (year == null && text.contains("Data"))
                year = meta.nextElementSibling()?.text()?.split(' ')?.last()?.toIntOrNull()
            else if (status == null && text.contains("Stato"))
                status = getStatus(meta.nextElementSibling()?.text())
            else if (status == null && text.contains("Durata"))
                duration = meta.nextElementSibling()?.text()
        }
        if (duration != null) {
            if (duration.contains("/ep")) duration = duration.replace("/ep", "")
            else if (duration.contains("h e ")) {
                val d = duration.split("h e ")
                val h = d[0].toInt() * 60
                val m = d[1].removeSuffix(" min").toInt()
                duration = (h + m).toString() + " min"
            }
        }

        val servers = document.select(".widget.servers > .widget-body")

        val episodes = servers.select(".server[data-name=\"9\"] .episode").map {
            val number = it.select("a").attr("data-episode-num").toIntOrNull()
            Episode(
                "$number¿$url",
                episode = number,
            )
        }
        val comingSoon = episodes.isEmpty()
        val nextAiringDate = document.select("#next-episode").attr("data-calendar-date")
        val nextAiringTime = document.select("#next-episode").attr("data-calendar-time")
//        Log.d("AnimeWorld:load", "NextAiring: $nextAiringDate $nextAiringTime")

        val nextAiringUnix = try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
                .parse(nextAiringDate + "T" + nextAiringTime)?.time?.div(1000)
        } catch (e: Exception) {
            null
        }

        val recommendations = document.select(".film-list.interesting .item").map {
            it.toSearchResult(false)
        }
        return newAnimeLoadResponse(title, url, type) {
            engName = title
            japName = otherTitle
            addPoster(poster)
            this.year = year
            addEpisodes(if (dub) DubStatus.Dubbed else DubStatus.Subbed, episodes)
            showStatus = status
            plot = description
            tags = genres
            addMalId(malId)
            addAniListId(anlId)
            addRating(rating)
            duration?.let { addDuration(duration) }
            addTrailer(trailerUrl)
            this.recommendations = recommendations
            this.comingSoon = comingSoon
            if (episodes.isNotEmpty() && nextAiringUnix != null && episodes.last().episode != null) {
                this.nextAiring = NextAiring(episodes.last().episode!! + 1, nextAiringUnix)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
//        Log.d("AnimeWorld:loadLinks", "DATA : $data")
        val epNumber = data.split("¿")[0].toInt()
        val pageUrl = data.split("¿")[1]

        val serverElem = request(pageUrl).document.select(".widget.servers")
        val servers = serverElem.select(".widget-body > .server")
        val epElems = servers.select("a[data-episode-num=\"$epNumber\"]")

        val apiLinks = epElems.map {
            "https://www.animeworld.so/api/episode/info?id=" + it.attr("data-id")
        }
        val apiResults = apiLinks.mapNotNull {
            tryParseJson<Json>(request(it).text)
        }
        if (apiResults.isEmpty()) return false

        apiResults.amap {
            if (it.target.contains("AnimeWorld")) {
                callback.invoke(
                    ExtractorLink(
                        name,
                        "AnimeWorld",
                        it.grabber,
                        referer = mainUrl,
                        quality = Qualities.Unknown.value
                    )
                )

            } else if (it.target.contains("listeamed.net")) {
                VidguardExtractor().getUrl(it.grabber, null, subtitleCallback, callback)
            } else {
                null
            }
        }.filterNotNull()
        return true
    }
}