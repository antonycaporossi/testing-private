package com.lagradost

//import androidx.core.text.parseAsHtml
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addRating
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ShortLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element
//import com.lagradost.cloudstream3.utils.AppUtils.html
import com.lagradost.cloudstream3.network.CloudflareKiller


class FilmpertuttiProvider : MainAPI() {
    override var lang = "it"
    override var mainUrl = "https://filmpertutti.motorcycles"
    override var name = "FilmPerTutti"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )
    override var sequentialMainPage = true
    override var sequentialMainPageDelay: Long = 50
    override val mainPage = mainPageOf(
        Pair("https://filmpertutti.motorcycles/category/film/", "Film Popolari"),
        Pair("https://filmpertutti.motorcycles/category/serie-tv/", "Serie Tv Popolari"),
        Pair("https://filmpertutti.motorcycles/category/ora-al-cinema/", "Ora al cinema")
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = request.data
        val soup = app.get(url).document
        val home = soup.select("article.post").map {
            val title = ""
            val link = it.selectFirst("a")!!.attr("href")
            val image = it.selectFirst("img")!!.attr("src")
            val qualitydata = it.selectFirst("div.hd")
            val quality = if (qualitydata != null) {
                getQualityFromString(qualitydata.text())
            } else {
                null
            }
            newTvSeriesSearchResponse(
                title,
                link
            ) {
                this.posterUrl = image
                this.quality = quality
            }
        }

        return newHomePageResponse(request.name, home)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val queryformatted = query.replace(" ", "+")
        val url = "$mainUrl/?s=$queryformatted"
        val doc = app.get(url).document
        return doc.select("article.post").map {
            val title = it.selectFirst(".elementor-post__title a")!!.text()
            val link = it.selectFirst("a")!!.attr("href")
            val image = it.selectFirst("img")!!.attr("src")

            MovieSearchResponse(
                title,
                link,
                this.name,
                posterUrl = image
            )
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val type = if (url.contains("serie-tv")) TvType.TvSeries else TvType.Movie
        val title = document.selectFirst("title")!!.text().substringBefore(" Streaming")
        val description = document.select(".short-synopsis, .rest-synopsis").text()

        val rating = document.selectFirst(".imdb-votes")?.text()

        val year = title.substringAfterLast("(")
                ?.filter { it.isDigit() }?.toIntOrNull()

    
        //val horizontalPosterData = document.selectFirst("body > main")?.attr("style")?:""
        val poster = document.html().substringAfter("background-image: url('").substringBefore("');");


        val trailerurl =
            document.selectFirst("div.youtube-player")?.attr("data-id")?.let { urldata ->
                "https://www.youtube.com/watch?v=$urldata"
            }

        if (type == TvType.TvSeries) {

            val episodeList = ArrayList<Episode>()

            document.select(".season-details > div").map{ seasonDiv ->
                val season =        seasonDiv.attr("id").substringAfter("_").toInt() +1 
                    seasonDiv.select("ul li").apmap{ episode -> 
                    val href = episode.selectFirst("a")!!.attr("href")
                    val epNum = episode.selectFirst(".episode-title").text().substringBefore(".").toIntOrNull()
                    val epTitle = episode.selectFirst(".episode-title").text().substringAfter(".")
                    episodeList.add(
                        Episode(
                            href,
                            epTitle,
                            season,
                            epNum,
                        )
                    )
                }
            }
            return newTvSeriesLoadResponse(
                title,
                url, type, episodeList
            ) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                addRating(rating)
                addTrailer(trailerurl)
            }
        } else {

            val urls = url+"?show_video=true"
            return newMovieLoadResponse(
                title,
                url,
                type,
                urls
            ) {
                posterUrl = fixUrlNull(poster)
                this.year = year
                this.plot = description
                addRating(rating)
                addTrailer(trailerurl)

            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val f = app.get(data).document
        val iframeContent = app.get(f.selectFirst("iframe")!!.attr("src")).document

        iframeContent.select(".container div[rel=nofollow]").map {
            loadExtractor(it.attr("meta-link"), data, subtitleCallback, callback)
        }
        //tryParseJson<List<String>>(data)?.apmap { id ->
        //    val link = ShortLink.unshorten(id).trim().replace("/v/", "/e/").replace("/f/", "/e/")
        //    loadExtractor(link, data, subtitleCallback, callback)
        //}
        return true
    }
}
