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
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.addPoster
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.extractors.NewVoeExtractor
import com.lagradost.extractors.PeytonepreExtractor
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.loadExtractor
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.network.CloudflareKiller


class ToonItaliaXYZ :
    MainAPI() { // all providers must be an intstance of MainAPI
    override var mainUrl = "https://toonitalia.xyz/"
    override var name = "ToonItalia XYZ"
    override var lang = "it"
    override val supportedTypes =
        setOf(TvType.TvSeries, TvType.Movie, TvType.Anime, TvType.AnimeMovie, TvType.Cartoon)
    override val hasMainPage = true
    private val interceptor = CloudflareKiller()


    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(mainUrl, interceptor = interceptor).document

        val sections = document.select(".widget.recent-posts-widget-with-thumbnails")

        return newHomePageResponse(sections.map { it ->
            val categoryName = it.selectFirst("h2")!!.text()
            val shows = it.select("ul li").map {
                val href = it.selectFirst("a")!!.attr("href")
                val name = it.selectFirst("span.rpwwt-post-title")!!.text()
                val posterUrl = fixUrl(it.selectFirst("img")!!.attr("src"))
                newTvSeriesSearchResponse(name, href) {
                    this.posterUrl = posterUrl
                    this.posterHeaders = interceptor.getCookieHeaders(mainUrl).toMap()
                }
            }
            HomePageList(
                categoryName,
                shows
            )
        }, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.get("$mainUrl?s=$query", interceptor = interceptor)
        val document = response.document
        Log.d("ToonItalia:search", document.toString())
        val list = document.select("article").mapNotNull {
            if (it.tagName() == "article") {
                it.toSearchResponse(true)
            } else {
                null
            }
        }
        return list
    }

    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url, interceptor = interceptor).document
        var title = response.select("h2 span").text().trim()
        val poster = response.select(".entry-content h2 img").attr("src")

        val genres: List<String> = response.select(".entry-categories-inner a").map { it.text() }
        val type = if (genres.contains("Movie") || genres.contains("Film Animazione") || genres.contains("Oav")) {
            TvType.Movie
        } else {
            TvType.TvSeries
        }
        val entryContent: Element? = response.select(".entry-content").first()
        return if (type == TvType.TvSeries) {
            val episodes: List<Episode> = entryContent?.let {
                getEpisodes(it)
            } ?: emptyList()
            val headers = interceptor.getCookieHeaders(url).toMap().toMutableMap()
            headers["User-Agent"] = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36"
            newTvSeriesLoadResponse(
                title,
                url,
                type,
                episodes
            ) {
                this.posterUrl = poster
                this.posterHeaders = headers
                this.tags = genres
            }
        } else {
            val movieUrl: String = entryContent?.let {
                getMovie(it)
            } ?: throw ErrorLoadingException("No Link found")
            newMovieLoadResponse(
                title,
                url,
                type,
                dataUrl = movieUrl
            ) {
                this.posterUrl = poster
                this.posterHeaders = interceptor.getCookieHeaders(url).toMap()
            }
        }
    }

    private fun getMovie(entryContent: Element): String {
        val streamingLink = entryContent.select("p")
        val movieElements = entryContent.select("p")

        movieElements.forEach { movieElement ->
            if (Regex("^Link Streaming").find(movieElement.text()) != null) {
                val links = Jsoup.parse(movieElement.html()).select("a").map() { it.attr("href") }.toJson()
                return links
            }
        }
        throw ErrorLoadingException("No Link found")
    }

    private fun getEpisodes(entryContent: Element): List<Episode> {
        val episodeList = mutableListOf<Episode>()
        val episodeElements = entryContent.select("p")

        episodeElements.forEach { episodeElement ->
            val _episodeText = episodeElement.html()
            if (Regex("^[0-9]+\\s–\\s").find(_episodeText) != null || Regex("^[0-9]+×").find(_episodeText) != null) {
                _episodeText.split("<br>").forEach {
                    val parts = it.split(" – ")
                    val episodeNumber = parts[0].trim()
                    val episodeTitle = parts[1].trim()
                    val links = Jsoup.parse(it).select("a").map() { it.attr("href") }.toJson()
                    episodeList.add(
                        newEpisode(links){
                            this.name = episodeTitle
                            this.episode = episodeNumber?.toDoubleOrNull()?.toInt()
                        }
                    )
                }
            }
        }
        

        return episodeList
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val links = parseJson<List<String>>(data)
        links.forEach {
            loadExtractor(it, subtitleCallback, callback)
            if (it.contains("voe") || it.contains("richardsignfish")) {
                NewVoeExtractor().getUrl(it, null, subtitleCallback, callback)
            }
            else if (it.contains("peytonepre") || it.contains("niikaplayerr") || it.contains("vidhideplus") || it.contains("smoothpre")) {
                PeytonepreExtractor().getUrl(it, null, subtitleCallback, callback)
            }
             else {
                loadExtractor(it, subtitleCallback, callback)
            }
            //
        }
        return true
    }
    

    private fun Element.toSearchResponse(fromSearch: Boolean): SearchResponse {
        val title = this.select("h2 > a").text().trim().replace(Regex(""" ?[-–]? ?\d{4}$"""), "")

        val url = this.select("h2 > a").attr("href")

        val genres: List<String> = this.select(".entry-categories-inner a").map { it.text() }
        val type = if (genres.contains("Movie") || genres.contains("Film Animazione") || genres.contains("Oav")) {
            TvType.Movie
        } else {
            TvType.TvSeries
        }

        val posterUrl = null

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