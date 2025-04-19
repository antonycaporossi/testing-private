package com.lagradost.extractors

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import java.io.File
import java.io.FileOutputStream

class PeytonepreExtractor : ExtractorApi() {
    override var name = "PeytonepreExtractor"
    override var mainUrl = "https://peytonepre.com/"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val fixedUrl = if (url.contains("vidhideplus.com")) {
            url.replace("vidhideplus.com", "niikaplayerr.com")
        } else {
            url
        }
        val headers = mapOf(
            "Accept" to "*/*",
            "Connection" to "keep-alive",
            "User-Agent" to "Mozilla/5.0 (Windows NT 6.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/70.0.3538.110 Safari/537.36",
            "Accept-Language" to "en-US;q=0.5,en;q=0.3",
            "Cache-Control" to "max-age=0",
            "Upgrade-Insecure-Requests" to "1"
        )
        val response = app.get(url, headers = headers, timeout = 10_000)
        val responseBody = response.body.string()

        val unpackedScript = getAndUnpack(getAndUnpack(responseBody))
        val src = unpackedScript.substringAfter("hls2\":\"").substringBefore("\"}")
        Log.d("teest", "Script: $src")
        callback.invoke(
            newExtractorLink(
                source = name,
                name = name,
                url = src,
                ExtractorLinkType.M3U8
            ){
                this.referer = referer ?: ""
                this.quality = Qualities.Unknown.value
            }
        )
    }
}