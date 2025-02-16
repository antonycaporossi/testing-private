package it.dogior.hadEnough

import com.fasterxml.jackson.annotation.JsonProperty

data class SearchJson(
    @JsonProperty("animes") val animes: List<AnimeJson>
)

data class AnimeJson(
    @JsonProperty("name") val name: String,
    @JsonProperty("image") val image: String,
    @JsonProperty("link") val link: String,
    @JsonProperty("animeTypeName") val type: String,
    @JsonProperty("language") val language: String,
    @JsonProperty("jtitle") val otherTitle: String,
    @JsonProperty("identifier") val id: String
)

data class Json(
    @JsonProperty("grabber") val grabber: String,
    @JsonProperty("name") val name: String,
    @JsonProperty("target") val target: String,
)