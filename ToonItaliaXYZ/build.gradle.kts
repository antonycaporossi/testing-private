// use an integer for version numbers
version = 1

cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "Movies, TV Shows and Anime from ToonItalia.xyz"
    authors = listOf("Antony")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1

    tvTypes = listOf("Cartoon","TvSeries", "Movie", "Anime")

    language = "it"

    iconUrl = "https://toonitalia.xyz/wp-content/uploads/2023/08/cropped-Majintoon-32x32.jpg"
}