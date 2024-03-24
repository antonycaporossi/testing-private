rootProject.name = "CloudstreamPlugins"

// This file sets what projects are included. Every time you add a new project, you must add it
// to the includes below.

// Plugins are included like this
val disabled = listOf<String>(
"AkwamProvider",


"AnimeIndoProvider",
"AnimeSailProvider",

"AnimefenixProvider",
"AnimeflvIOProvider",
"AnimeflvnetProvider",
"Anizm",




"CinecalidadProvider",
"CuevanaProvider",
"DokumentalneProvider",
"DoramasYTProvider",
"DramaidProvider",
"DubokuProvider",
"EgyBestProvider",
"ElifilmsProvider",
"EntrepeliculasyseriesProvider",
"EstrenosDoramasProvider",

"FaselHDProvider",
"FilmanProvider",

"FreeTVProvider",
"FrenchStreamProvider",
"GomunimeProvider",
"Gomunimeis",

"HDMovie5",
"HDrezkaProvider",
"Hdfilmcehennemi",
"IdlixProvider",

"IptvorgProvider",
"JKAnimeProvider",
"KuramanimeProvider",
"KuronimeProvider",
"LayarKacaProvider",
"MesFilmsProvider",
"MonoschinosProvider",
"MultiplexProvider",
"MundoDonghuaProvider",
"MyCimaProvider",
"NekosamaProvider",
"NeonimeProvider",
"NineGoalProvider",
"NontonAnimeIDProvider",
"OploverzProvider",
"OtakudesuProvider",
"PeliSmartProvider",
"PelisflixProvider",
"PelisplusHDProvider",
"PelisplusProvider",
"PhimmoichillProvider",
"PinoyHDXyzProvider",
"PinoyMovies",
"PinoyMoviesHub",
"RebahinProvider",
"SeriesflixProvider",
"SkillShareProvider",

"StarLiveProvider",


"TocanimeProvider",

"TvpolanProvider",
"UakinoProvider",
"UseeTv",
"VfFilmProvider",
"VfSerieProvider",
"VizjerProvider",
"VostfreeProvider",
"WebFlix",
"WiflixProvider",
"XcineProvider",
"YomoviesProvider",
"ZaluknijProvider",
)

File(rootDir, ".").eachDir { dir ->
    if (!disabled.contains(dir.name) && File(dir, "build.gradle.kts").exists()) {
        include(dir.name)
    }
}

fun File.eachDir(block: (File) -> Unit) {
    listFiles()?.filter { it.isDirectory }?.forEach { block(it) }
}


// To only include a single project, comment out the previous lines (except the first one), and include your plugin like so:
// include("PluginName")
