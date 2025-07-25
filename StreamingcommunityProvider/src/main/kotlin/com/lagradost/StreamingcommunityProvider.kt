@file:Suppress("NAME_SHADOWING")

package com.lagradost

//import android.util.Log
import android.widget.Toast
//import android.content.Context
//import android.app.AlertDialog
//import android.content.DialogInterface
import com.fasterxml.jackson.annotation.*
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addRating
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.CommonActivity.showToast
import java.net.URLDecoder
import org.json.JSONObject
import com.lagradost.cloudstream3.network.CloudflareKiller
import android.util.Log

class StreamingcommunityProvider : MainAPI() {
    override var mainUrl = "https://streamingcommunityz.cash"
    private var cdnUrl = "https://cdn.streamingcommunityz.cash" // Images
    private var xInertiaVersion = "759de90f13813ff6f03369b34b51a141"
    override var name = "StreamingCommunity"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val hasChromecastSupport = true
    override var lang = "it"
    private val interceptor = CloudflareKiller()
    override val hasMainPage = true
    override val mainPage = mainPageOf(
        "{\"name\":\"trending\",\"genre\":null}" to "I titoli del momento",
        "{\"name\":\"latest\",\"genre\":null}" to "Aggiunti di recente",
        "{\"name\":\"top10\",\"genre\":null}" to "Top 10 titoli di oggi",
        "{\"name\":\"genre\",\"genre\":\"Famiglia\"}" to "Famiglia",
        "{\"name\":\"genre\",\"genre\":\"Kids\"}" to "Kids",
        "{\"name\":\"genre\",\"genre\":\"Crime\"}" to "Crime",
        "{\"name\":\"genre\",\"genre\":\"Musica\"}" to "Musica",
        "{\"name\":\"genre\",\"genre\":\"Thriller\"}" to "Thriller",
        "{\"name\":\"genre\",\"genre\":\"Korean Drama\"}" to "Korean Drama",
        "{\"name\":\"genre\",\"genre\":\"Soap\"}" to "Soap",
        "{\"name\":\"genre\",\"genre\":\"Guerra\"}" to "Guerra",
        "{\"name\":\"genre\",\"genre\":\"Commedia\"}" to "Commedia",
        "{\"name\":\"genre\",\"genre\":\"Action & Adventure\"}" to "Action & Adventure",
        "{\"name\":\"genre\",\"genre\":\"Avventura\"}" to "Avventura",
        "{\"name\":\"genre\",\"genre\":\"Animazione\"}" to "Animazione",
        "{\"name\":\"genre\",\"genre\":\"Mistero\"}" to "Mistero",
        "{\"name\":\"genre\",\"genre\":\"Storia\"}" to "Storia",
        "{\"name\":\"genre\",\"genre\":\"Televisione Film\"}" to "Televisione Film",
        "{\"name\":\"genre\",\"genre\":\"Azione\"}" to "Azione",
        "{\"name\":\"genre\",\"genre\":\"War & Politics\"}" to "War & Politics",
        "{\"name\":\"genre\",\"genre\":\"Romance\"}" to "Romance",
        "{\"name\":\"genre\",\"genre\":\"Sci-Fi & Fantasy\"}" to "Sci-Fi & Fantasy",
        "{\"name\":\"genre\",\"genre\":\"Reality\"}" to "Reality",
        "{\"name\":\"genre\",\"genre\":\"Horror\"}" to "Horror",
        "{\"name\":\"genre\",\"genre\":\"Fantascienza\"}" to "Fantascienza",
        "{\"name\":\"genre\",\"genre\":\"Western\"}" to "Western",
        "{\"name\":\"genre\",\"genre\":\"Fantasy\"}" to "Fantasy",
        "{\"name\":\"genre\",\"genre\":\"Documentario\"}" to "Documentario",
        "{\"name\":\"genre\",\"genre\":\"Dramma\"}" to "Dramma",
    )

    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36"


    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (page == 1 && request.data.contains("trending")) {
            val url = "${this.mainUrl}/api/sliders/fetch"
            val postDatas = listOf(
                
            "{\"sliders\":[{\"name\":\"trending\",\"genre\":null},{\"name\":\"top10\",\"genre\":null},{\"name\":\"latest\",\"genre\":null},{\"name\":\"genre\",\"genre\":\"Famiglia\"},{\"name\":\"genre\",\"genre\":\"Kids\"}]}",
            "{\"sliders\":[{\"name\":\"genre\",\"genre\":\"Crime\"},{\"name\":\"genre\",\"genre\":\"Musica\"},{\"name\":\"genre\",\"genre\":\"Thriller\"},{\"name\":\"genre\",\"genre\":\"Korean drama\"},{\"name\":\"genre\",\"genre\":\"Soap\"},{\"name\":\"genre\",\"genre\":\"Guerra\"}]}",
            "{\"sliders\":[{\"name\":\"genre\",\"genre\":\"Commedia\"},{\"name\":\"genre\",\"genre\":\"Action & Adventure\"},{\"name\":\"genre\",\"genre\":\"Avventura\"},{\"name\":\"genre\",\"genre\":\"Animazione\"},{\"name\":\"genre\",\"genre\":\"Mistero\"},{\"name\":\"genre\",\"genre\":\"Storia\"}]}",
            "{\"sliders\":[{\"name\":\"genre\",\"genre\":\"televisione film\"},{\"name\":\"genre\",\"genre\":\"Azione\"},{\"name\":\"genre\",\"genre\":\"War & Politics\"},{\"name\":\"genre\",\"genre\":\"Romance\"},{\"name\":\"genre\",\"genre\":\"Sci-Fi & Fantasy\"},{\"name\":\"genre\",\"genre\":\"Reality\"}]}",
            "{\"sliders\":[{\"name\":\"genre\",\"genre\":\"Horror\"},{\"name\":\"genre\",\"genre\":\"Fantascienza\"},{\"name\":\"genre\",\"genre\":\"Western\"},{\"name\":\"genre\",\"genre\":\"Fantasy\"},{\"name\":\"genre\",\"genre\":\"Documentario\"},{\"name\":\"genre\",\"genre\":\"Dramma\"}]}"
            )
            val items: List<HomePageList> = postDatas.map { postData ->
                val soup = app.post(
                    url,
                    interceptor = interceptor,
                    json = JSONObject(postData)
                ).text
                val jsonResponse = parseJson<List<MainPageResponse>>(soup)
                jsonResponse.map { genre ->
                    val searchResponses = genre.titles.map { show -> //array of title
                        show.toSearchResult()
                    }
                    HomePageList(genre.label, searchResponses)


                }

            }.flatten()
            if (items.isEmpty()) throw ErrorLoadingException()
            return HomePageResponse(items, hasNext = true)

        } else if (page != 1 && !request.data.contains("top10")) { //to load other pages
            val offset = ((page - 1) * 30).toString()
            val requestJson = parseJson<RequestJson>(request.data)
            val url = when (requestJson.name) {
                "trending", "latest" -> "${this.mainUrl}/api/browse/${requestJson.name}"
                else -> "${this.mainUrl}/api/browse/genre"
            }
            val params = when (requestJson.name) {
                "trending", "latest" -> mapOf("offset" to offset)
                else -> mapOf("offset" to offset, "g" to requestJson.genre)
            }
            val soup = app.get(url, params = params, interceptor = interceptor).text
            val jsonResponse = parseJson<MainPageResponse>(soup)
            val items: List<HomePageList> = arrayListOf(
                HomePageList(jsonResponse.label, jsonResponse.titles.map { show -> //array of title
                    show.toSearchResult()
                })
            )

            return HomePageResponse(items, hasNext = true)
        }
        return HomePageResponse(arrayListOf(), hasNext = true)
    }


    private fun MainPageTitles.toSearchResult(): SearchResponse {
        val title = this.name ?: "No title found!"
        val isMovie = this.type?.contains("movie") ?: true
        val link = LinkData(
            id = this.id, slug = this.slug
        ).toJson()

        val posterUrl =
            "$cdnUrl/images/" + this.images.filter { it.type == "poster" }.map { it.filename }
                .firstOrNull()

        return if (isMovie) {
            newMovieSearchResponse(title, link, TvType.Movie) {
                addPoster(url = posterUrl, headers = interceptor.getCookieHeaders(mainUrl).toMap())
            }
        } else {
            newTvSeriesSearchResponse(title, link, TvType.TvSeries) {
                addPoster(url = posterUrl, headers = interceptor.getCookieHeaders(mainUrl).toMap())
            }
        }
    }

    private suspend fun getDecodedJson(url: String = mainUrl): LoadResponseJson {

        // Otherwise, make a request to get the version
        val document = app.get(url, interceptor = interceptor).document // Adjust URL if necessary
        val encodedJson = document.select("div#app").attr("data-page")
        
        // Process JSON as needed to get the version
        val cleanedJson = encodedJson.replace("%(?![0-9A-Fa-f]{2})".toRegex(), "")
        val decodedJson = URLDecoder.decode(cleanedJson, "UTF-8")
        val parsedJson = parseJson<LoadResponseJson>(decodedJson)

        return parsedJson
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/it/search"
        val parsedJson = getDecodedJson(url)
        val xInertiaVersion_2 = parsedJson.version
        val soup = app.get(
            url, params = mapOf("q" to query), headers = mapOf(
                "X-Inertia" to "true", "X-Inertia-Version" to xInertiaVersion_2
            ),
            interceptor = interceptor
        ).text
        val responseJson = parseJson<SearchResponseJson>(soup)
        return responseJson.props.titles.map { it.toSearchResult() }
    }

    private fun transformUrl(url: String): String? {
        val regex = """https://vixcloud\.co/embed/(\d+)\?""".toRegex()
        val matchResult = regex.find(url)

        return matchResult?.let {
            val videoId = it.groupValues[1]
            "https://vixcloud.co/playlist/$videoId"
        }
    }

    private fun Episodes.toEpisode(titleId: String, season: Int): Episode {
        val data = LoadLinkData(
            titleId = titleId, episodeId = this.id.toString(), scwsId = this.scwsId.toString()
        ).toJson()

        val epNum = this.number
        val epTitle = this.name
        val posterUrl =
            "$cdnUrl/images/" + this.images.filter { it.type == "cover" }.map { it.filename }
                .firstOrNull()
        return newEpisode(data){
            this.name = epTitle
            this.season = season
            this.episode = epNum
            this.posterUrl = posterUrl
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val linkData = parseJson<LinkData>(url)
        val realUrl = "${this.mainUrl}/it/titles/${linkData.id}-${linkData.slug}"

        val parsedJson = getDecodedJson(realUrl)
        val xInertiaVersion_2 = parsedJson.version

        val type = if (parsedJson.props.title.type == "tv") TvType.TvSeries else TvType.Movie

        val title = parsedJson.props.title.name ?: "No title"

        val description = parsedJson.props.title.plot
        val year = parsedJson.props.title.releaseDate?.substringAfter("-")
        val poster =
            "$cdnUrl/images/" + parsedJson.props.title.images.filter { it.type == "background" }
                .map { it.filename }.firstOrNull()
        val rating = parsedJson.props.title.score?.trim()?.toRatingInt()
        val recomm =
            parsedJson.props.sliders.firstOrNull { it.name == "related" }?.titles?.map { it.toSearchResult() }
        val actors: List<ActorData> =
            parsedJson.props.title.mainActors.map { ActorData(actor = Actor(it.name)) }

        val trailer =
            parsedJson.props.title.trailers.map { "https://www.youtube.com/watch?v=${it.youtubeId}" }
                .randomOrNull()

        val titleId = parsedJson.props.title.id.toString()

        if (type == TvType.TvSeries) {
            
            val seasonsCountInt = parsedJson.props.title.seasonsCount!!.toInt()
            val episodeList = (1..seasonsCountInt).map { season ->
                Log.d("teest", "documentSeason: $realUrl/season-$season")
                val documentSeason = app.get(
                    "$realUrl/season-$season",
                    referer = mainUrl,
                    headers = mapOf(
                        "X-Inertia" to "true",
                        "X-Inertia-Version" to xInertiaVersion_2
                    ),
                    interceptor = interceptor
                ).text
                val parsedJsonSeason = parseJson<LoadResponseJson>(documentSeason)
                parsedJsonSeason.props.loadedSeason!!.episodes.map { it.toEpisode(titleId, season) }
            }.flatten()

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodeList) {
                this.year = year?.toIntOrNull()
                this.plot = description
                this.actors = actors
                this.recommendations = recomm
                this.tags = parsedJson.props.title.genres.mapNotNull { it.name }
                addPoster(url = poster, headers = interceptor.getCookieHeaders(mainUrl).toMap())
                addRating(rating)
                addTrailer(trailer)
            }
        } else {
            val data = LoadLinkData(
                titleId = titleId,
                episodeId = "0",
                scwsId = parsedJson.props.title.scwsId.toString()
            ).toJson()
            Log.d("teest", "titleId: $titleId, episodeId: 0, scwsId: ${parsedJson.props.title.scwsId.toString()}")
            return newMovieLoadResponse(title, data, TvType.Movie, data) {
                this.year = year?.toIntOrNull()
                this.plot = titleId + " | " + parsedJson.props.title.scwsId.toString() + " - " + description
                this.actors = actors
                this.recommendations = recomm
                this.tags = parsedJson.props.title.genres.mapNotNull { it.name }
                addPoster(url = poster, headers = interceptor.getCookieHeaders(mainUrl).toMap())
                addRating(rating)
                addTrailer(trailer)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val dataJson = parseJson<LoadLinkData>(data)
        val document = app.get(
            "${this.mainUrl}/it/iframe/${dataJson.titleId}?episode_id=${dataJson.episodeId}",
            referer = mainUrl,
            headers = mapOf(
                "User-Agent" to userAgent
            ),
            interceptor = interceptor
        ).document
        val firstStageUrl = document.select("iframe").attr("src")



        //val embedContent = app.get(firstStageUrl, referer = mainUrl, headers = mapOf("User-Agent" to userAgent))


        //showToast(embedContent.toString(), Toast.LENGTH_LONG)
        //val realUrl = firstStageUrl
        //val realUrl = transformUrl(firstStageUrl).toString()

        val documentVixcloud = app.get(
            firstStageUrl,
            referer = mainUrl,
            headers = mapOf("User-Agent" to userAgent),
            interceptor = interceptor
        ).document.toString()
        val test =
            Regex("""window\.masterPlaylist = (\{[^}]+\})""").find(documentVixcloud)!!.groupValues[1].trim()

        val urld = documentVixcloud.substringAfter("url: '").substringBefore("',")
        val token = test.substringAfter("'token': '").substringBefore("',")
        val expires = test.substringAfter("'expires': '").substringBefore("',")
        val asn = test.substringAfter("'asn': '").substringBefore("',")
        
        //val tokens: Tokens = parseJson(test)
        //val realUrl = "${(firstStageUrl.substringBefore("?").replace("embed", "playlist"))}?token=1"

        val separator = if (urld.contains('?')) "&" else "?"
        val asnPart = if (asn.isNotEmpty()) "&asn=$asn" else ""
        val h = if(firstStageUrl.contains("canPlayFHD")) "&h=1" else ""
        val realUrl = "$urld$separator" +
            "token=$token&expires=$expires$asnPart$h"
        callback.invoke(
            newExtractorLink(
                name,
                name,
                realUrl,
                ExtractorLinkType.M3U8
            ){
                this.referer = mainUrl
                this.quality = Qualities.Unknown.value
            }
        )
        return true
    }

}


//Mainly for SearchResponse
data class MainPageResponse(

    @JsonProperty("name") var name: String? = null,
    @JsonProperty("label") var label: String,
    @JsonProperty("titles") var titles: ArrayList<MainPageTitles> = arrayListOf()

)

data class MainPageTitles(

    @JsonProperty("id") var id: Int? = null,
    @JsonProperty("slug") var slug: String? = null,
    @JsonProperty("name") var name: String? = null,
    @JsonProperty("type") var type: String? = null,
    @JsonProperty("score") var score: String? = null,
    @JsonProperty("sub_ita") var subIta: Int? = null,
    @JsonProperty("last_air_date") var lastAirDate: String? = null,
    @JsonProperty("seasons_count") var seasonsCount: Int? = null,
    @JsonProperty("images") var images: ArrayList<Images> = arrayListOf()

)

data class Images(
    @JsonProperty("filename") var filename: String? = null,
    @JsonProperty("type") var type: String? = null,
)

data class RequestJson(
    @JsonProperty("name") var name: String, @JsonProperty("genre") var genre: String


)

//For search

data class SearchResponseJson(
    @JsonProperty("props") var props: Props = Props(),
)


data class Props(
    @JsonProperty("titles") var titles: ArrayList<MainPageTitles> = arrayListOf(),
)


//for load

private data class LinkData(
    val id: Int? = null, val slug: String? = null
)

data class LoadResponseJson(
    @JsonProperty("props") var props: LoadProps = LoadProps(),
    @JsonProperty("version") var version: String,

    )


data class LoadProps(
    @JsonProperty("title") var title: LoadTitle = LoadTitle(),
    @JsonProperty("loadedSeason") var loadedSeason: LoadedSeason? = LoadedSeason(),
    @JsonProperty("sliders") var sliders: ArrayList<Sliders> = arrayListOf(),
)


data class LoadedSeason(

    @JsonProperty("id") var id: Int? = null,
    @JsonProperty("number") var number: Int? = null,
    @JsonProperty("name") var name: String? = null,
    @JsonProperty("plot") var plot: String? = null,
    @JsonProperty("release_date") var releaseDate: String? = null,
    @JsonProperty("title_id") var titleId: Int? = null,
    @JsonProperty("created_at") var createdAt: String? = null,
    @JsonProperty("updated_at") var updatedAt: String? = null,
    @JsonProperty("episodes") var episodes: ArrayList<Episodes> = arrayListOf()

)

data class Episodes(

    @JsonProperty("id") var id: Int? = null,
    @JsonProperty("number") var number: Int? = null,
    @JsonProperty("name") var name: String? = null,
    @JsonProperty("plot") var plot: String? = null,
    @JsonProperty("duration") var duration: Int? = null,
    @JsonProperty("scws_id") var scwsId: Int? = null,
    @JsonProperty("season_id") var seasonId: Int? = null,
    @JsonProperty("created_by") var createdBy: String? = null,
    @JsonProperty("created_at") var createdAt: String? = null,
    @JsonProperty("updated_at") var updatedAt: String? = null,
    @JsonProperty("images") var images: ArrayList<Images> = arrayListOf()

)

data class Sliders( //To get reccomended

    @JsonProperty("name") var name: String? = null,
    @JsonProperty("label") var label: String? = null,
    @JsonProperty("titles") var titles: ArrayList<MainPageTitles> = arrayListOf()

)


data class LoadTitle(

    @JsonProperty("id") var id: Int = 0,
    @JsonProperty("name") var name: String? = null,
    @JsonProperty("slug") var slug: String? = null,
    @JsonProperty("plot") var plot: String? = null,
    @JsonProperty("quality") var quality: String? = null,
    @JsonProperty("type") var type: String? = null,
    @JsonProperty("original_name") var originalName: String? = null,
    @JsonProperty("score") var score: String? = null,
    @JsonProperty("tmdb_id") var tmdbId: Int? = null,
    @JsonProperty("imdb_id") var imdbId: String? = null, //tt11122333
    @JsonProperty("scws_id") var scwsId: Int? = null,
    @JsonProperty("release_date") var releaseDate: String? = null,
    @JsonProperty("last_air_date") var lastAirDate: String? = null,
    @JsonProperty("seasons_count") var seasonsCount: Int? = null,
    @JsonProperty("seasons") var seasons: ArrayList<Episodes> = arrayListOf(),
    @JsonProperty("trailers") var trailers: ArrayList<Trailers> = arrayListOf(),
    @JsonProperty("images") var images: ArrayList<Images> = arrayListOf(),
    @JsonProperty("genres") var genres: ArrayList<Genres> = arrayListOf(),
    @JsonProperty("main_actors") var mainActors: ArrayList<MainActors> = arrayListOf(),
)

data class Trailers(
    @JsonProperty("youtube_id") var youtubeId: String? = null,
)


data class Genres(
    @JsonProperty("name") var name: String? = null,
)

data class MainActors(
    @JsonProperty("name") var name: String
)

//for loadlink

private data class LoadLinkData(
    val titleId: String? = null, val episodeId: String? = null, val scwsId: String? = null
)

private data class Tokens(
    @JsonProperty("token") var token: String? = null,
    @JsonProperty("params") var params: Params? = null,
    @JsonProperty("token360p") var token360p: String? = null,
    @JsonProperty("token480p") var token480p: String? = null,
    @JsonProperty("token720p") var token720p: String? = null,
    @JsonProperty("token1080p") var token1080p: String? = null,
    @JsonProperty("expires") var expires: String? = null
)


private data class Params(
    @JsonProperty("token") var token: String? = null,
    @JsonProperty("expires") var expires: String? = null
)