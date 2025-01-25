package com.lagradost

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import android.util.Log



class TantifilmProvider : MainAPI() {
    override var lang = "it"
    override var mainUrl = "https://tantifilm.name"
    override var name = "Tantifilm"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    override val mainPage = mainPageOf(
        Pair("$mainUrl/cinema/page/", "Ultimi Film"),
        Pair("$mainUrl/serie-tv/page/", "Ultime Serie Tv"),
        //Pair("$mainUrl/watch-genre/film-aggiornati/page/", "Ultimi Film Aggiornati"),
    )

    private val interceptor = CloudflareKiller()

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = request.data + page
        val soup = app.get(url, interceptor = interceptor).document
        val home = soup.select("div.media3").map {
            val title = it.selectFirst("p")!!.text().substringBefore("(")
            val link = fixUrl(it.selectFirst("a")!!.attr("href"))
            val posterUrl = fixUrl(it.selectFirst("img")!!.attr("src"))
            newTvSeriesSearchResponse(
                title,
                link,
                TvType.Movie,
            ){
                this.posterUrl = posterUrl
                this.posterHeaders = interceptor.getCookieHeaders(url).toMap()
            }
        }
        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val queryformatted = query.replace(" ", "+")
        val url = "$mainUrl/?story=$queryformatted&do=search&subaction=search"

        val doc = app.get(url, interceptor = interceptor).document
        return doc.select("div.film.film-2").map {
            val href = it.selectFirst("a")!!.attr("href")
            val poster = fixUrl(it.selectFirst("img")!!.attr("src").replace("/224x320-0-85/", "/203x293-0-70/")) //203x293-0-70
            val name = it.selectFirst("a > p")!!.text().substringBeforeLast("(")
            newMovieSearchResponse(
                name,
                href,
                TvType.Movie
            ){
                this.posterUrl = poster
                this.posterHeaders = interceptor.getCookieHeaders(url).toMap()
            }

        }
    }

    override suspend fun load(url: String): LoadResponse {
        
        val document = app.get(url, interceptor = interceptor).document
        val type = if (document.selectFirst("div.category-film")!!.text().contains("Serie")
                .not()
        ) TvType.Movie else TvType.TvSeries
        val title = document.selectFirst("div.title-film-left")!!.text().substringBefore("(")
        val descipt = document.select("div.content-left-film > p").map { it.text() }
        val rating =
            document.selectFirst(".current-rating")!!
                .text().toFloatOrNull()
                ?.times(2000)?.toInt()?.let { minOf(it, 10000) }

        var year = document.selectFirst("div.title-film-left")!!.text().substringAfter("(")
            .filter { it.isDigit() }
        year = if (year.length > 4) {
            year.dropLast(4)
        } else {
            year
        }
        // ?: does not wor
        val poster = document.selectFirst("div.image-right-film > img")!!.attr("src")

        val recomm = document.select("div.mediaWrap.mediaWrapAlt.recomended_videos").map {
            val href = it.selectFirst("a")!!.attr("href")
            val poster = it.selectFirst("img")!!.attr("src")
            val name = it.selectFirst("a > p")!!.text().substringBeforeLast("(")
            MovieSearchResponse(
                name,
                href,
                this.name,
                TvType.Movie,
                poster,
                null,
                posterHeaders = interceptor.getCookieHeaders(url).toMap()
            )

        }

        val trailerurl = document.selectFirst(".trailer > iframe")?.attr("src")

        if (type == TvType.TvSeries) {




            //val list = ArrayList<Pair<Int, String>>()
            //val urlvideocontainer = document.selectFirst("iframe")!!.attr("src")
            //val videocontainer = app.get(urlvideocontainer).document

            val episodeList = ArrayList<Episode>()


            document.select(".tt_series .tab-pane").forEach { seasonTab ->
                val season = seasonTab.attr("id").substringAfter("-").toInt()
                seasonTab.select("li").forEach { episode -> 
                    val _episode = episode.selectFirst("a")
                    val hrefs = episode.select(".mirrors").select("a").map { it.attr("data-link") }
                    val epNum = _episode?.text()?.toIntOrNull()
                    val data = LoadLinkData(
                        links = hrefs,
                        type = TvType.TvSeries
                    ).toJson()
                    episodeList.add(
                        Episode(
                            name = "Episodio $epNum",
                            data = data,
                            season = season,
                            episode = epNum,
                        )
                    )
                }
            }
            return newTvSeriesLoadResponse(
                title,
                url,
                type,
                episodeList
            ) {
                this.posterUrl = fixUrlNull(poster)
                this.year = year.toIntOrNull()
                this.plot = descipt[0]
                this.rating = rating
                this.recommendations = recomm
                addTrailer(trailerurl)
                this.posterHeaders = interceptor.getCookieHeaders(url).toMap()
            }
        } else {
            val url2 = document.selectFirst("iframe:not([id=vid])")!!.attr("src")
            val data = LoadLinkData(
                links = listOf(url2),
                type = TvType.Movie
            ).toJson()

            val duratio: Int? = if (descipt.size == 2) {
                descipt[0].filter { it.isDigit() }.toInt()
            } else {
                null
            }
            val tags: List<String>? = if (descipt.size == 2) {
                mutableListOf(descipt[0].substringBefore(" "))
            } else {
                null
            }
            val plot: String = if (descipt.size == 2) {
                descipt[1]
            } else {
                descipt[0]
            }
            return newMovieLoadResponse(
                title,
                data,
                type,
                data
            ) {
                posterUrl = fixUrlNull(poster)
                this.year = year.toIntOrNull()
                this.plot = plot
                this.rating = rating
                this.recommendations = recomm
                this.tags = tags
                this.duration = duratio
                this.posterHeaders = interceptor.getCookieHeaders(url).toMap()
                addTrailer(trailerurl)

            }
        }
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
        
        Log.d("Tantifilm123", "Tantifilm123: $data")
        val dataJson = parseJson<LoadLinkData>(data) // Qui ci sono tutti i link mirrors
        

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
            Log.d("Tantifilm123", "Tantifilm123: $data")
            Log.d("Tantifilm123", "Tantifilm123: $links")
        }
        //val doc2 = app.get(iframe).document
        //val id2 = app.get(doc2.selectFirst("iframe")!!.attr("src")).url
        //loadExtractor(fixUrl(iframe), data, subtitleCallback, callback)
        links.forEach { link ->
            //val doc2 = app.get(link).document
            //val id2 = app.get(doc2.selectFirst("iframe")!!.attr("src")).url
            loadExtractor(link, data, subtitleCallback, callback)
        }
        return true
    }
}
