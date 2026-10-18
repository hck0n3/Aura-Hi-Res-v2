package iad1tya.echo.music.ui.screens

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry row 432 (owner 2026-10-18): «el apartado de Inicio… que el usuario se sienta identificado, que sienta que esa
 * parte lo conoce muy bien». What is built from the user comes before what is the same for everybody.
 */
class HomeSectionOrderTest {
    private fun order(): List<HomeSection> = computeHomeSections(
        randomizeHomeOrder = false,
        randomSeed = 0L,
        tasteOnlyHome = false,
        showSpeedDial = true,
        perfOn = false,
        speedDialCount = 1,
        quickPickCount = 5,
        dailyMixItemCounts = listOf(5, 5, 5),
        timeOfDayMixSongCount = 5,
        aiRecommendedSongCount = 5,
        keepListeningCount = 5,
        accountPlaylistCount = 3,
        forgottenFavoritesCount = 5,
        communityPlaylistCount = 3,
        newFromArtistsCount = 3,
        genreMixSongCount = 5,
        similarRecommendationCount = 3,
        homePageSectionCount = 3,
        hasMoodAndGenres = true,
        newReleaseAlbumCount = 5,
    )

    @Test
    fun `every personal shelf comes before every generic one`() {
        val sections = order()
        fun at(s: HomeSection) = sections.indexOf(s).also { assertTrue("$s missing", it >= 0) }
        val personal = listOf(
            HomeSection.QuickPicks, HomeSection.KeepListening, HomeSection.AiRecommended, HomeSection.TimeOfDayMix,
            HomeSection.DailyMix(0), HomeSection.DailyMix(2), HomeSection.GenreMix, HomeSection.SimilarRecommendation(0),
            HomeSection.SimilarRecommendation(2), HomeSection.ForgottenFavorites, HomeSection.NewFromArtists,
            HomeSection.AccountPlaylists,
        )
        val generic = listOf(
            HomeSection.NewReleases, HomeSection.FromTheCommunity, HomeSection.HomePageSection(0), HomeSection.MoodAndGenres,
        )
        val lastPersonal = personal.maxOf { at(it) }
        val firstGeneric = generic.minOf { at(it) }
        assertTrue("a generic shelf ($firstGeneric) sits above a personal one ($lastPersonal)", lastPersonal < firstGeneric)
    }

    @Test
    fun `quick access still leads`() {
        val sections = order()
        assertTrue(sections.first() == HomeSection.SpeedDial)
        assertTrue(sections.indexOf(HomeSection.QuickPicks) < sections.indexOf(HomeSection.KeepListening))
    }
}
