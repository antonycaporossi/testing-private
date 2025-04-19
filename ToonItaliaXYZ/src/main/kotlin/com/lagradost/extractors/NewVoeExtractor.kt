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
import org.jsoup.nodes.Element
import java.util.Base64
import java.io.FileOutputStream
import org.json.JSONObject

class NewVoeExtractor : ExtractorApi() {
    override var name = "Voe / Richardsignfish"
    override var mainUrl = "https://richardsignfish.com/"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ) {
        val fixedUrl = if (url.contains("voe.sx")) {
            url.replace("voe.sx", "richardsignfish.com")
        } else {
            url
        }
        val response = app.get(fixedUrl).document


        val src = ObfuscationDecoder.findScriptElement(response)!!
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

     object ObfuscationDecoder {

        fun findScriptElement(rawHtml: Element): String? {
            val scripts = rawHtml.getElementsByTag("script")

            val obfuscatedScript = scripts.firstOrNull {
                it.toString().startsWith("<script>(function () {var KGMAaM=")
            } ?: return null

            val splitIndex = obfuscatedScript.toString().indexOf("MKGMa=\"")
            if (splitIndex == -1) return null

            val extracted = obfuscatedScript.toString()
                .substring(splitIndex + 7)
                .substringBefore("\"")
            
            
            val j = debFunc(extracted) as JSONObject
            val source = j.getString("source")
            
            return source
        }

        private fun debFunc1(input: String): String = buildString {
            for (char in input) {
                val code = char.code
                append(
                    when {
                        code in 'A'.code..'Z'.code -> ((code - 'A'.code + 13) % 26 + 'A'.code).toChar()
                        code in 'a'.code..'z'.code -> ((code - 'a'.code + 13) % 26 + 'a'.code).toChar()
                        else -> char
                    }
                )
            }
        }

        private fun regexFunc(input: String): String {
            val patterns = listOf("@$", "^^", "~@", "%?", "*~", "!!", "#&")
            var result = input
            for (pattern in patterns) {
                result = result.replace(Regex(Regex.escape(pattern)), "_")
            }
            return result
        }

        private fun debFunc3(input: String, shift: Int): String {
            return input.map { (it.code - shift).toChar() }.joinToString("")
        }

        private fun debFunc(input: String): JSONObject? {
            return try {
                val rot13 = debFunc1(input)
                val cleaned = regexFunc(rot13).replace("_", "")
                val base64Decoded1 = String(Base64.getDecoder().decode(cleaned))
                val shifted = debFunc3(base64Decoded1, 3)
                val reversed = shifted.reversed()
                val base64Decoded2 = String(Base64.getDecoder().decode(reversed))
                
                JSONObject(base64Decoded2)
            } catch (e: Exception) {
                println("Parsing error: ${e.message}")
                null
            }
        }
    }
}