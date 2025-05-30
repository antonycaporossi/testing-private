// use an integer for version numbers
version = 8


cloudstream {
    language = "it"
    // All of these properties are optional, you can safely remove them

    // description = "Lorem Ipsum"
    authors = listOf("Adippe")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 0 // 2025-05-11: Taken down
    tvTypes = listOf(
        "TvSeries",
        "Movie",
    )


    iconUrl = "https://www.google.com/s2/favicons?domain=tantifilm.mobi&sz=%size%"
}