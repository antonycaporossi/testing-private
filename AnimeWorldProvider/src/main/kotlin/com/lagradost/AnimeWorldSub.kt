package it.dogior.hadEnough

import com.lagradost.cloudstream3.*

class AnimeWorldSub(isSplit: Boolean) : AnimeWorldCore(isSplit) {
    override var name = "AnimeWorld Sub"
    override var lang = "jp"
    override val currentExtension = CurrentExtension.SUB

    override val mainPage = super.mainPage + mainPageOf(
        "$mainUrl/filter?status=0&language=jp&sort=1" to "In Corso",
        "$mainUrl/filter?language=jp&sort=1" to "Ultimi aggiunti",
        "$mainUrl/filter?language=jp&sort=6" to "Più Visti",
        "$mainUrl/tops/all?sort=1" to "Top Anime",
    )
}
