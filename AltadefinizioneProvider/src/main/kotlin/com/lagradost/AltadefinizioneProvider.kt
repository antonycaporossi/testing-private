package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addRating
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import okhttp3.FormBody
import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.network.CloudflareKiller


class AltadefinizioneProvider : MainAPI() {
    override var lang = "it"
    override var mainUrl = "https://altadefinizione.gent"
    override var name = "Altadefinizione"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(
        TvType.Movie
    )
    private val interceptor = CloudflareKiller()

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {

        val post = listOf(
                //mapOf("name" to "Cinema", "url" to "$mainUrl/cinema/"),
                //mapOf("name" to "Serie TV", "url" to "$mainUrl/serie-tv/"),
                mapOf("name" to "Film", "url" to "$mainUrl/film/"),
                //mapOf("name" to "Azione", "url" to "$mainUrl/azione/"),
                //mapOf("name" to "Animazione", "url" to "$mainUrl/animazione/"),
                //mapOf("name" to "Avventura", "url" to "$mainUrl/avventura/"),
                //mapOf("name" to "Biografico", "url" to "$mainUrl/biografico/"),
                //mapOf("name" to "Commedia", "url" to "$mainUrl/commedia/"),
                //mapOf("name" to "Crime", "url" to "$mainUrl/crime/"),
                //mapOf("name" to "Documentario", "url" to "$mainUrl/documentario/"),
                //mapOf("name" to "Drammatico", "url" to "$mainUrl/drammatico/"),
                //mapOf("name" to "Famiglia", "url" to "$mainUrl/famiglia/"),
                //mapOf("name" to "Fantascienza", "url" to "$mainUrl/fantascienza/"),
                //mapOf("name" to "Fantasy", "url" to "$mainUrl/fantasy/"),
                //mapOf("name" to "Intrattenimento", "url" to "$mainUrl/intrattenimento/"),
                //mapOf("name" to "Giallo", "url" to "$mainUrl/giallo/"),
                //mapOf("name" to "Guerra", "url" to "$mainUrl/guerra/"),
                //mapOf("name" to "Horror", "url" to "$mainUrl/horror/"),
                //mapOf("name" to "Poliziesco", "url" to "$mainUrl/poliziesco/"),
                //mapOf("name" to "Romantico", "url" to "$mainUrl/romantico/"),
                //mapOf("name" to "Sitcom", "url" to "$mainUrl/sitcom/"),
                //mapOf("name" to "Soap opera", "url" to "$mainUrl/soap-opera/"),
                //mapOf("name" to "Spionaggio", "url" to "$mainUrl/spionaggio/"),
                //mapOf("name" to "Sentimentale", "url" to "$mainUrl/sentimentale/"),
                //mapOf("name" to "Sportivo", "url" to "$mainUrl/sportivo/"),
                //mapOf("name" to "Thriller", "url" to "$mainUrl/thriller/"),
                //mapOf("name" to "Western", "url" to "$mainUrl/western/")
        )

            val items: List<HomePageList> = post.map { postData ->
                Log.d("teest", postData["url"]!!)
                val soup = app.get(postData["url"]!!, interceptor=interceptor).document
                val home = soup.select(".movie").mapNotNull { item ->
                    item.toSearchResult()
                }
                HomePageList(postData["name"]!!, home)

            }
            if (items.isEmpty()) {
                Log.d("teest", "error")
                throw ErrorLoadingException()
            }
            return HomePageResponse(items, hasNext = true)
        /*val soup = app.get(mainUrl).document
        val items: List<HomePageList> = soup.select("main section:not(.slider)").mapNotNull { section ->
            val name = section.selectFirst(".section-head h4")?.text()
            val home = section.select(".swiper-slide").mapNotNull {
                it.toSearchResult()
            }
            HomePageList(name ?: "", home)
        }
        return HomePageResponse(items, hasNext = false)*/
    }

    private fun Element.toSearchResult(isSwiper: Boolean = true): SearchResponse? {

        val title = this.selectFirst("h2")?.text() ?: return null
        val link = this.selectFirst("a")?.attr("href") ?: return null
        val image = fixUrl(this.selectFirst("img")?.attr("data-src") ?: "")
        val type = if (link.contains("serie-tv"))  TvType.TvSeries else TvType.Movie
        return newMovieSearchResponse(title, link, type) {
            this.posterUrl = image
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val body = FormBody.Builder()
            .addEncoded("do", "search")
            .addEncoded("subaction", "search")
            .addEncoded("story", query)
            .build()
            
        val doc = app.post(mainUrl,
            requestBody = body
        ).document

        return doc.select("#dle-content .movie").mapNotNull {
            it.toSearchResult(isSwiper = false)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, interceptor = interceptor).document

        val title = document.selectFirst(" h1")?.text()?.replace("streaming", "")
            ?: throw ErrorLoadingException("No Title found")
        Log.d("teest", title)
        val container = document.selectFirst(".text-container")
        container?.select(".dots, .more-link")?.remove() // rimuove i pezzi inutili
        val description = container?.text()
        val rating = document.select("span.imdb").text()
        val poster = fixUrl(document.selectFirst("img.movie_entry-poster")?.attr("data-src")?: throw ErrorLoadingException("No Poster found") )

        val year = document.select("div.movie_entry-details > div:nth-child(1) > div:nth-child(2)").text().toIntOrNull()
        val tags: List<String> = document.select(".movie_entry-details > div:nth-child(2) a").map { it.text() }
        val trailerUrl = document.selectFirst("iframe#trailer-embed")?.attr("src")
        val iframeSrc = document
        .select(".guardahd-player iframe")
        .first() // Get the first matching iframe
        ?.attr("data-src")
        
        val type = if (url.contains("serie-tv"))  TvType.TvSeries else TvType.Movie

        if (type == TvType.TvSeries) {
            val episodeList = ArrayList<Episode>()


            document.select(".dropdown.mirrors").forEach { episode ->
                val season = episode.attr("data-season").toInt()
                val epNum = episode.attr("data-episode").substringAfter("-").toInt()
                val title = "Episodio $epNum"
                val hrefs = episode.select(".mirrors").select(".dropdown-item").map { it.attr("data-link") }

                val data = LoadLinkData(
                    links = hrefs,
                    type = TvType.TvSeries
                ).toJson()
                episodeList.add(
                    newEpisode(data) {
                        this.name = "Episodio $epNum"
                        this.season = season
                        this.episode = epNum
                    }
                )
            }
            return newTvSeriesLoadResponse(
                title,
                url,
                TvType.TvSeries,
                episodeList
            ) {
                addPoster(poster)
                this.year = year
                this.plot = description
                addRating(rating)
                this.tags = tags
                addTrailer(trailerUrl)
            }
        }

        val data = LoadLinkData(
            links = listOf(iframeSrc!!),
            type = TvType.Movie
        ).toJson()

        return newMovieLoadResponse(
            title,
            url,
            TvType.Movie,
            data
        )
    }
    private data class LoadLinkData(
        val type: TvType = TvType.Movie, val links: List<String>? = null
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var links = ArrayList<String>()
        val dataJson = parseJson<LoadLinkData>(data)
        if (dataJson.type == TvType.TvSeries) {
            links.addAll(dataJson.links!!)
        } else {
            val _url = dataJson.links?.joinToString("") ?: ""
            val doc = app.get(_url).document
            doc.select("._player-mirrors li").forEach{
                links.add(
                    fixUrl(it.attr("data-link"))
                )
            }
        }
        links.forEach { link ->
            loadExtractor(link, data, subtitleCallback, callback)
        }
        return true
    }
}