import android.util.Log
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addRating
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.extractors.MaxStreamExtractor
import com.lagradost.extractors.StreamTapeExtractor
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class ToonItalia :
    MainAPI() { // all providers must be an intstance of MainAPI
    override var mainUrl = "https://toonitalia.green/"
    override var name = "ToonItalia"
    override var lang = "it"
    override val supportedTypes =
        setOf(TvType.TvSeries, TvType.Movie, TvType.Anime, TvType.AnimeMovie, TvType.Cartoon)
    override val hasMainPage = true


    override val mainPage = mainPageOf(
        mainUrl to "Ultimi Aggiunti",
        "${mainUrl}category/kids/" to "Serie Tv",
        "${mainUrl}category/anime/" to "Anime",
        "${mainUrl}film-anime/" to "Film",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) {
            request.data
        } else {
            request.data + "page/$page"
        }
        val response = app.get(url)
        val document = response.document

        val mainSection = document.select("#main").first()?.children()
        val list: List<SearchResponse> = mainSection?.mapNotNull {
            if (it.tagName() == "article") {
                it.toSearchResponse(false)
            } else {
                null
            }
        } ?: emptyList()

        val pageNumbersIndex = if (page == 1) 1 else 3

        val pageNumbers = try {
            document.select("div.nav-links > a.page-numbers")[pageNumbersIndex].text().toInt()
        } catch (e: IndexOutOfBoundsException) {
            0
        }

        val hasNext = page < pageNumbers

        return newHomePageResponse(
            HomePageList(request.name, list, false),
            hasNext
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
//        val response = app.get("$mainUrl?s=$query", interceptor = WebViewResolver("""\?s=lupin""".toRegex()))
//        val document = response.document
//        Log.d("ToonItalia:search", document.toString())
//        val list = document.select("article").mapNotNull {
//            if (it.tagName() == "article") {
//                it.toSearchResponse(true)
//            } else {
//                null
//            }
//        }
        return braveSearch(query)
    }

    private suspend fun braveSearch(query: String): List<SearchResponse> {
        val response = app.get("https://search.brave.com/search?q=${query}+site%3A${mainUrl.toHttpUrl().host}")
        val document = response.document
        val resultList = document.select("#results")
        val results = resultList.select(".snippet[data-type=\"web\"]").map { it.selectFirst("a")?.attr("href") }

        val excludedUrls = listOf(
            mainUrl + "category/anime/",
            mainUrl + "category/kids/",
            mainUrl + "film-anime/",
            mainUrl + "lista-anime-e-cartoni/",
            mainUrl + "lista-film-anime/",
            mainUrl + "lista-serietv-ragazzi/")

        val searchResponseList = results.amap{ url ->
            if (url != null && excludedUrls.all { !it.contains(url) }){
                val r = app.get(url).document
                val title = r.select(".entry-title").text().trim()
                val poster = r.select(".attachment-post-thumbnail").attr("src")
                val typeFooter = r.select(".cat-links > a:nth-child(1)").text()

                val type = if (typeFooter == "") {
                    TvType.Movie
                } else {
                    TvType.TvSeries
                }

                if (type == TvType.TvSeries) {
                    newTvSeriesSearchResponse(title, url, type) {
                        this.posterUrl = poster
                    }
                } else {
                    newMovieSearchResponse(title, url, type) {
                        this.posterUrl = poster
                    }
                }
            }else{
                null
            }
        }.filterNotNull()
        return searchResponseList
    }

    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url).document
        var title = response.select(".entry-title").text().trim()
        var year: Int? = null
        if (title.takeLast(4).all { it.isDigit() }) {
            year = title.takeLast(4).toInt()
            title = title.replace(Regex(""" ?[-–]? ?\d{4}$"""), "")
        }
        val plot = response.select(".entry-content > p:nth-child(2)").text().trim()
        val poster = response.select(".attachment-post-thumbnail").attr("src")
        val typeFooter = response.select(".cat-links > a:nth-child(1)").text()
        val type = if (typeFooter == "") {
            TvType.Movie
        } else {
            TvType.TvSeries
        }
        return if (type == TvType.TvSeries) {
            val episodes: List<Episode> = getEpisodes(url)
            year =
                response.select(".no-border > tbody:nth-child(2) > tr:nth-child(3) > td:nth-child(1)")
                    .text().takeLast(4).toIntOrNull()
            val genres =
                response.select(".no-border > tbody:nth-child(2) > tr:nth-child(1) > td:nth-child(2)")
                    .text().substringAfterLast("Genere: ").split(',')
            val rating =
                response.select(".no-border > tbody:nth-child(2) > tr:nth-child(4) > td:nth-child(1)")
                    .text().substringAfterLast("Voto: ")
            newTvSeriesLoadResponse(
                title,
                url,
                type,
                episodes
            ) {
                this.plot = plot
                this.year = year
                this.posterUrl = poster
                this.tags = genres
                addRating(rating)
            }
        } else {
            newMovieLoadResponse(
                title,
                url,
                type,
                dataUrl = "$url€${response.select(".entry-content > table:nth-child(5) > tbody:nth-child(2) > tr:nth-child(1) > td > a")}"
            ) {
                this.plot = plot
                this.year = year
                this.posterUrl = poster
            }
        }
    }

    private suspend fun getEpisodes(url: String): List<Episode> {
        val response = app.get(url)
        val table = response.document.select(".table_link > thead:nth-child(2)")
        var season: Int? = 1
        val rows = table.select("tr")
        val episodes: List<Episode> = rows.mapNotNull {
            if (it.childrenSize() == 0) {
                null
            } else if (it.childrenSize() == 1) {
                val seasonText = it.select("td:nth-child(1)").text()
                season = Regex("""\d+""").find(seasonText)?.value?.toInt()
                null
            } else {
                val title = it.select("td:nth-child(1)").text()
                newEpisode("$url€${it.select("a")}") {
                    name = title
                    this.season = season
                }
            }
        }

        return episodes
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val linkData = data.split("€")
//        val pageUrl = linkData[0]
        // The a tags with the links from the different servers
        val episodeLinks = linkData[1]
        val soup = Jsoup.parse(episodeLinks)
        soup.select("a").forEach {
            val link = it.attr("href")
//            val url = if (link.contains("uprot")) bypassUprot(link) else link
            if (link.contains("uprot")) {
                val url = bypassUprot(link)
                if (url != null) {
                    if (url.contains("streamtape")) {
                        StreamTapeExtractor().getUrl(url, null, subtitleCallback, callback)
                    } else {
                        MaxStreamExtractor().getUrl(url, null, subtitleCallback, callback)
//                            loadExtractor(url, subtitleCallback, callback)
                    }
                }
            }
        }
        return true
    }

    private suspend fun bypassUprot(link: String): String? {
        val updatedLink = if ("msf" in link) link.replace("msf", "mse") else link


        // Generate headers (replace with your own method to generate fake headers)
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
        )

//        Log.d("CB01:Uprot", updatedLink)

        // Make the HTTP request
        val response = app.get(updatedLink, headers = headers, timeout = 10_000)

        val responseBody = response.body.string()

        // Parse the HTML using Jsoup
        val document = Jsoup.parse(responseBody)
        Log.d("CB01:Uprot", document.select("a").toString())
        val maxstreamUrl = document.selectFirst("a")?.attr("href")

        return maxstreamUrl
    }

    private fun Element.toSearchResponse(fromSearch: Boolean): SearchResponse {
        val title = this.select("h2 > a").text().trim().replace(Regex(""" ?[-–]? ?\d{4}$"""), "")

        val url = this.select("h2 > a").attr("href")
        val footer = this.select("footer > span > a").text()

        val type = if (fromSearch) {
            try {
                this.select(".cat-links > a:nth-child(1)").text()
                TvType.TvSeries
            } catch (e: Exception) {
                TvType.Movie
            }
        } else {
            if (footer != "") {
                TvType.TvSeries
            } else {
                TvType.Movie
            }
        }

        val posterUrl = if (fromSearch) {
            this.select("header:nth-child(1) > div:nth-child(2) > p:nth-child(1) > a:nth-child(1) > img:nth-child(1)")
                .attr("src")
        } else {
            this.select("header:nth-child(1) > p:nth-child(2) > a:nth-child(1) > img:nth-child(1)")
                .attr("src")
        }

        return if (type == TvType.TvSeries) {
            newTvSeriesSearchResponse(title, url, type) {
                this.posterUrl = posterUrl
            }
        } else {
            newMovieSearchResponse(title, url, type) {
                this.posterUrl = posterUrl
            }
        }
    }


}