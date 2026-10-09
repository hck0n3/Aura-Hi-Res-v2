package iad1tya.echo.music.ui.newui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import iad1tya.echo.music.constants.PlayerBackgroundStyle
import iad1tya.echo.music.ui.screens.settings.PaletteColors
import iad1tya.echo.music.ui.theme.contrastRatio
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The promises the redesign's Apariencia work makes, pinned.
 *
 * Three of them cannot be seen in a screenshot and none of them can be seen in a type signature:
 *
 *  1. **The accent reaches the new screens, and stays readable.** ~143 call sites read
 *     `AuraPalette.Teal` / `.Blue` / `.Violet`; those now follow the user's accent, and a swatch chosen
 *     for its colour must never produce text nobody can read on the ground.
 *  2. **All seven `PlayerBackgroundStyle` values do something, and something different.** Six of them
 *     used to be inert in the new portrait player — including the one `App.kt` seeds.
 *  3. **With nothing chosen, everything is the literal that shipped.** That is what makes the whole
 *     thing reversible.
 */
class AuraAppearanceTest {

    /** Every test that writes global palette state must hand it back, or it leaks into the next one. */
    @After
    fun restorePalette() {
        AuraPalette.reset()
    }

    // ------------------------------------------------------------------ 1. the accent

    @Test
    fun `every one of the 46 swatches produces a legible trio on the redesign's ground`() {
        val ground = AuraPalette.Ground
        PaletteColors.forEach { palette ->
            // The Dynamic entry is a sentinel (transparent), not a colour; the accent it stands for
            // arrives through colorScheme.primary like any other.
            if (palette.seedColor == Color.Transparent) return@forEach
            val accent = AuraAccent.from(palette.seedColor, ground)
            listOf(
                "primary" to accent.primary,
                "secondary" to accent.secondary,
                "tertiary" to accent.tertiary,
            ).forEach { (role, color) ->
                val ratio = contrastRatio(color, ground)
                assertTrue(
                    "swatch ${palette.seedColor} -> $role ${color} reads only $ratio:1 on the ground",
                    ratio >= TEXT_AA,
                )
            }
        }
    }

    @Test
    fun `the same holds on AMOLED's pure black, which is a different ground`() {
        PaletteColors.forEach { palette ->
            if (palette.seedColor == Color.Transparent) return@forEach
            val accent = AuraAccent.from(palette.seedColor, Color.Black)
            assertTrue(
                "swatch ${palette.seedColor} is unreadable on pure black",
                contrastRatio(accent.primary, Color.Black) >= TEXT_AA &&
                    contrastRatio(accent.secondary, Color.Black) >= TEXT_AA &&
                    contrastRatio(accent.tertiary, Color.Black) >= TEXT_AA,
            )
        }
    }

    @Test
    fun `the darkest and the lightest swatches are the ones that prove the clamp works`() {
        val ground = AuraPalette.Ground
        // Rojo oscuro: too dark to read on a near-black ground as picked, so it must be LIFTED…
        val darkRed = Color(0xFF7F0000)
        assertTrue(contrastRatio(darkRed, ground) < TEXT_AA)
        assertTrue(contrastRatio(AuraAccent.from(darkRed, ground).primary, ground) >= TEXT_AA)
        // …and Marfil already passes, so it must be handed back UNTOUCHED rather than "corrected".
        val ivory = Color(0xFFF2EDE3)
        assertEquals(ivory, AuraAccent.from(ivory, ground).primary)
    }

    @Test
    fun `the trio keeps the render's hue spacing, so a gradient is still a gradient`() {
        val ground = AuraPalette.Ground
        // A saturated pick: the three stops must be three different colours, or the play button's
        // gradient collapses to a flat fill.
        val accent = AuraAccent.from(Color(0xFF1E88E5), ground)
        assertNotEquals(accent.primary, accent.secondary)
        assertNotEquals(accent.secondary, accent.tertiary)
    }

    @Test
    fun `the nav bar's unselected tint is not a term of the accent, so no swatch can dim it`() {
        // It is a step of OnGround, and the guarantee is that it clears AA on both grounds.
        assertTrue(contrastRatio(AuraPalette.NavInactive, AuraPalette.Ground) >= TEXT_AA)
        AuraPalette.apply(
            AuraAccent.from(Color(0xFFF2EDE3), Color.Black),
            pureBlack = true,
            coverCorners = AuraCoverCorners.Render,
        )
        assertEquals(Color.Black, AuraPalette.Ground)
        assertTrue(contrastRatio(AuraPalette.NavInactive, AuraPalette.Ground) >= TEXT_AA)
    }

    // ------------------------------------------------------------------ 2. AMOLED and the corners

    @Test
    fun `AMOLED moves the redesign's own ground, not only the app ColorScheme`() {
        assertEquals(Color(0xFF060A12), AuraPalette.Ground)
        AuraPalette.apply(AuraAccent.Brand, pureBlack = true, coverCorners = AuraCoverCorners.Render)
        assertEquals(Color.Black, AuraPalette.Ground)
        // The raised ground must NOT collapse into it, or the mini pill and the player menu sheet
        // disappear into the page.
        assertNotEquals(Color.Black, AuraPalette.GroundRaised)
        assertTrue(AuraPalette.GroundRaised.luminance() < 0.01f)
    }

    @Test
    fun `the cover corner control is the render's radius scaled, identity at the shipped default`() {
        // The shipped default of ThumbnailCornerRadiusKey is 3, which AuraPaletteSync turns into a
        // multiplier of 1 — so a user who never touched the control gets the render's own radii.
        assertEquals(1f, AuraCoverCorners.Render.scale, 0f)
        assertEquals(RoundedCornerShape(11.dp), AuraCoverCorners.Render.row)
        assertEquals(RoundedCornerShape(20.dp), AuraCoverCorners.Render.player)
        // 0 is square and anything above 1 is rounder: the control is live and monotonic, not a switch
        // between two looks.
        assertEquals(RoundedCornerShape(0.dp), AuraCoverCorners(0f).row)
        assertNotEquals(AuraCoverCorners(4f).row, AuraCoverCorners.Render.row)
    }

    // ------------------------------------------------------------------ 3. the seven grounds

    @Test
    fun `no PlayerBackgroundStyle draws nothing`() {
        PlayerBackgroundStyle.entries.forEach { style ->
            val recipe = auraGroundRecipe(style, hasCover = true, motion = true)
            assertTrue(
                "$style draws no ground at all",
                recipe.bloom > 0f || recipe.cover > 0f || recipe.wash > 0f ||
                    recipe.lobes > 0f || recipe.film > 0f,
            )
        }
    }

    @Test
    fun `no two styles draw the same ground`() {
        val recipes = PlayerBackgroundStyle.entries.associateWith {
            auraGroundRecipe(it, hasCover = true, motion = true)
        }
        val distinct = recipes.values.distinct()
        assertEquals(
            "two styles resolve to the same recipe: $recipes",
            PlayerBackgroundStyle.entries.size,
            distinct.size,
        )
    }

    @Test
    fun `losing the cover never leaves a style empty`() {
        // No artwork (a local track), or API below 31 where Modifier-blur is a no-op.
        PlayerBackgroundStyle.entries.forEach { style ->
            val recipe = auraGroundRecipe(style, hasCover = false, motion = true)
            assertEquals("$style still asks for a cover it cannot have", 0f, recipe.cover, 0f)
            assertTrue(
                "$style degrades to an empty ground",
                recipe.bloom > 0f || recipe.wash > 0f || recipe.lobes > 0f || recipe.film > 0f,
            )
        }
    }

    @Test
    fun `denying motion stops every animation and leaves the ground standing`() {
        PlayerBackgroundStyle.entries.forEach { style ->
            val still = auraGroundRecipe(style, hasCover = true, motion = false)
            assertTrue("$style still drifts under the thermal gate", !still.drift)
            assertTrue("$style still spins under the thermal gate", !still.spin)
            assertTrue(
                "$style has nothing left once its motion is denied",
                still.bloom > 0f || still.cover > 0f || still.wash > 0f ||
                    still.lobes > 0f || still.film > 0f,
            )
        }
    }

    @Test
    fun `DEFAULT is exactly the ambient bloom the redesign shipped`() {
        val recipe = auraGroundRecipe(PlayerBackgroundStyle.DEFAULT, hasCover = true, motion = true)
        assertEquals(1f, recipe.bloom, 0f)
        assertEquals(0f, recipe.cover, 0f)
        assertEquals(0f, recipe.wash, 0f)
        assertEquals(0f, recipe.lobes, 0f)
        assertEquals(0f, recipe.film, 0f)
    }

    @Test
    fun `the flat artwork ceiling is the redesign's own hairline, not an invented number`() {
        // The brightest imaginable cover, at the ceiling, over the ground.
        val worstCase = Color.White.copy(alpha = AURA_COVER_ALPHA).compositeOver(AuraPalette.Ground)
        // …is exactly the surface the app theme already calls surfaceContainerHigh and prints text on.
        val shippedCeiling = AuraPalette.SurfaceLine.compositeOver(AuraPalette.Ground)
        assertEquals(shippedCeiling.luminance(), worstCase.luminance(), 1e-6f)
        // And the redesign's ink still clears AA on it, at the two steps the player uses.
        listOf(AuraPalette.OnGroundMuted, AuraPalette.OnGroundFaint).forEach { ink ->
            val composited = ink.compositeOver(worstCase)
            assertTrue(
                "ink $ink reads only ${contrastRatio(composited, worstCase)}:1 on the worst-case ground",
                contrastRatio(composited, worstCase) >= TEXT_AA,
            )
        }
    }

    // --------------------------------------------- 3b. the mini player's OWN background control

    /**
     * The five values `AppearanceSettings` offers for "Estilo de fondo del minirreproductor"
     * (`availableMiniPlayerBackgroundStyles` = every style except GRADIENT and APPLE_MUSIC).
     *
     * The row was hidden under the new UI because the key had no renderer; `AuraMiniPlayer` now reads
     * it. These tests are what stops "restored" from meaning "visible again but still inert".
     */
    private val miniPlayerOfferedStyles = PlayerBackgroundStyle.entries.filter {
        it != PlayerBackgroundStyle.GRADIENT && it != PlayerBackgroundStyle.APPLE_MUSIC
    }

    @Test
    fun `Predeterminado is the theme's own ground on the pill, and Desenfoque is the cover`() {
        // The blocker this replaces: both values resolved to a full-strength blurred cover 4 dp of blur
        // apart on a 128x128 decode inside a 64 dp pill, i.e. "Desenfoque" did nothing. Each name now
        // means what it says, and THIS is the assertion that would have caught it.
        val default = auraPillRecipe(
            auraGroundRecipe(PlayerBackgroundStyle.DEFAULT, hasCover = true, motion = true),
        )
        // "Seguir el tema": no artwork layer at all. The pill is [AuraPalette.GroundRaised] (which the
        // AMOLED switch moves) plus the content Row's own SurfaceFill — the flat `.mi` the render draws.
        assertEquals(AuraGroundRecipe(), default)

        val blur = auraPillRecipe(
            auraGroundRecipe(PlayerBackgroundStyle.BLUR, hasCover = true, motion = true),
        )
        // "Desenfoque": the blurred cover, at the full strength the pill's own scrim is sized for.
        assertEquals(1f, blur.cover, 0f)
        assertNotEquals(default, blur)
    }

    @Test
    fun `the pill's ink clears AA on the ground Predeterminado draws, on both themes`() {
        // The ground under the pill's text when no artwork layer is drawn: the opaque raised ground with
        // the content Row's SurfaceFill over it (AuraShell.kt). No cover means `pillHasArtwork` is false,
        // so the 45 % scrim is not drawn either — this really is the whole stack.
        fun assertLegible(where: String) {
            val ground = AuraPalette.SurfaceFill.compositeOver(AuraPalette.GroundRaised)
            val title = contrastRatio(AuraPalette.OnGround, ground)
            assertTrue("the pill's title reads only $title:1 on $where", title >= TEXT_AA)
            // The artist line is the 55 % step, so it is the one that decides this.
            val artist = contrastRatio(AuraPalette.OnGroundMuted.compositeOver(ground), ground)
            assertTrue("the pill's artist line reads only $artist:1 on $where", artist >= TEXT_AA)
        }
        assertLegible("the brand ground")
        AuraPalette.apply(AuraAccent.Brand, pureBlack = true, coverCorners = AuraCoverCorners.Render)
        assertLegible("AMOLED")
    }

    @Test
    fun `no mini-player style draws the same layers as Predeterminado on the pill`() {
        val default = auraPillRecipe(
            auraGroundRecipe(PlayerBackgroundStyle.DEFAULT, hasCover = true, motion = true),
        )
        // A style counts as restored only if it changes the LAYERS. `coverBlur` is deliberately NOT in
        // this disjunction: a different blur radius on the same layer is exactly the 4 dp placebo.
        miniPlayerOfferedStyles.filter { it != PlayerBackgroundStyle.DEFAULT }.forEach { style ->
            val pill = auraPillRecipe(auraGroundRecipe(style, hasCover = true, motion = true))
            // `spin` is deliberately NOT in this disjunction any more: HALLAZGO-059 strips ALL motion
            // from the pill (it is always on screen), so no style can differ through it.
            assertTrue(
                "$style draws the same layers as Predeterminado on the pill — it would be a placebo",
                pill.cover != default.cover ||
                    pill.lobes != default.lobes ||
                    pill.film != default.film ||
                    pill.wash != default.wash ||
                    pill.coverSaturation != default.coverSaturation ||
                    pill.coverScale != default.coverScale,
            )
        }
    }

    @Test
    fun `LIQUID_GLASS on the pill is a frosted cover, never a live backdrop sample`() {
        // The claim the restored settings row makes out loud, in both languages. A backdrop sample
        // would have to be a per-frame effect; this recipe is a still cover plus a cached film, and it
        // still resolves to something when there is no cover to blur (API < 31, or a local track).
        val withCover = auraPillRecipe(
            auraGroundRecipe(PlayerBackgroundStyle.LIQUID_GLASS, hasCover = true, motion = true),
        )
        assertTrue("the frosted film is missing", withCover.film > 0f)
        assertTrue("nothing is being frosted", withCover.cover > 0f)
        assertTrue("a still ground must not drift", !withCover.drift && !withCover.spin)

        val withoutCover = auraPillRecipe(
            auraGroundRecipe(PlayerBackgroundStyle.LIQUID_GLASS, hasCover = false, motion = true),
        )
        assertEquals("asks for a cover it cannot have", 0f, withoutCover.cover, 0f)
        assertTrue("degrades to an empty pill", withoutCover.film > 0f || withoutCover.wash > 0f)
        // Motion is denied on a throttled device; this style never had any to lose.
        val throttled = auraPillRecipe(
            auraGroundRecipe(PlayerBackgroundStyle.LIQUID_GLASS, hasCover = true, motion = false),
        )
        assertEquals(withCover, throttled)
    }

    @Test
    fun `no pill ever owns a per-frame writer, whatever the style, the cover or the motion gate`() {
        // HALLAZGO-059: the pill is the one surface that is ALWAYS on screen — every tab, every
        // scroll, behind the expanded player. A `drift` or `spin` inherited from the sheet's recipe
        // would pin a per-frame InfiniteTransition to it forever, so the pill's recipe drops both,
        // for every style, with or without a cover, on both sides of the motion gate. The sheet
        // keeps its motion; the pill draws the same layers still.
        PlayerBackgroundStyle.entries.forEach { style ->
            listOf(true, false).forEach { hasCover ->
                listOf(true, false).forEach { motion ->
                    val pill = auraPillRecipe(
                        auraGroundRecipe(style, hasCover = hasCover, motion = motion),
                    )
                    assertTrue("$style still drifts on the pill", !pill.drift)
                    assertTrue("$style still spins on the pill", !pill.spin)
                }
            }
        }
    }

    // ------------------------------------------------------------------ 4. reversibility

    @Test
    fun `with nothing resolved the palette is the literal that shipped`() {
        AuraPalette.reset()
        assertEquals(Color(0xFF3FE7CE), AuraPalette.Teal)
        assertEquals(Color(0xFF2FA6F0), AuraPalette.Blue)
        assertEquals(Color(0xFF6A5BFF), AuraPalette.Violet)
        assertEquals(Color(0xFF060A12), AuraPalette.Ground)
        assertEquals(Color(0xFF080D18), AuraPalette.GroundRaised)
        assertEquals(1f, AuraPalette.coverCorners.scale, 0f)
        // The bloom's fallback is built from those, so it comes back with them.
        assertEquals(AuraPalette.Teal.copy(alpha = 0.32f), AuraBloomColors.Brand.topLeft)
    }

    // ------------------------------------------------------------------ 5. cover-tinted ground

    /** Owner 2026-10-06: "la veo muy oscura… que los colores de las portadas sean más notables". */
    @Test
    fun `the cover-tinted ground is lighter than the shipped one and no less legible`() {
        val shipped = Color(0xFF060A12)
        for (hue in 0 until 360 step 15) {
            val seed = iad1tya.echo.music.ui.component.ColorPickerConversions.hsvToColor(hue.toFloat(), 0.8f, 0.8f)
            val ground = auraArtworkGround(seed)
            assertTrue("hue $hue: ground must be lighter than #060A12", ground.luminance() > shipped.luminance())
            // Same cover-tinted ink AuraPaletteSync paints on it (hue, s 0.10, v 0.96), at every step that
            // carries text (row 341: the steps were raised with the lighter 2026-10-07 ground): all AA.
            val ink = iad1tya.echo.music.ui.component.ColorPickerConversions.hsvToColor(hue.toFloat(), 0.10f, 0.96f)
            AuraPalette.apply(AuraAccent.Brand, pureBlack = false, coverCorners = AuraCoverCorners.Render, artworkInk = ink, artworkGround = ground)
            listOf(
                "ghost" to AuraPalette.OnGroundGhost,
                "faint" to AuraPalette.OnGroundFaint,
                "muted" to AuraPalette.OnGroundMuted,
                "nav" to AuraPalette.NavInactive,
            ).forEach { (step, color) ->
                val ratio = contrastRatio(color.compositeOver(ground), ground)
                assertTrue("hue $hue: $step text at $ratio", ratio >= TEXT_AA)
            }
            assertTrue("hue $hue: body text", contrastRatio(ink, ground) >= 12f)
        }
    }

    /** Row 341: the accent shows the cover's own colours, each still AA on the ground. */
    @Test
    fun `the cover accent uses the cover's real second and third colours`() {
        val seed = Color(0xFF1E5BD8)
        val ground = auraArtworkGround(seed)
        val red = Color(0xFFD32F2F)
        val yellow = Color(0xFFF2C21B)
        val accent = AuraAccent.fromCover(seed, red, yellow, ground)
        assertEquals(AuraAccent.from(seed, ground).primary, accent.primary)
        listOf(accent.primary, accent.secondary, accent.tertiary).forEach {
            assertTrue(contrastRatio(it, ground) >= TEXT_AA)
        }
        // Same hue as the cover's colour (only lightness may move to reach AA).
        val (redHue, _, _) = iad1tya.echo.music.ui.component.ColorPickerConversions.colorToHsv(red)
        val (secondHue, _, _) = iad1tya.echo.music.ui.component.ColorPickerConversions.colorToHsv(accent.secondary)
        assertTrue(CoverColors.hueDistance(redHue, secondHue) < 3f)
        // A single-hue cover keeps the rotated stops it always had.
        assertEquals(AuraAccent.from(seed, ground), AuraAccent.fromCover(seed, null, null, ground))
    }

    /** Row 341: the glass carries the cover's colour and the nav bar stays legible on it. */
    @Test
    fun `the cover glass tint keeps the nav labels legible on every hue`() {
        assertEquals(null, AuraPalette.CoverGlassTint)
        for (hue in 0 until 360 step 10) {
            val seed = iad1tya.echo.music.ui.component.ColorPickerConversions.hsvToColor(hue.toFloat(), 0.95f, 0.95f)
            val ground = auraArtworkGround(seed)
            val ink = iad1tya.echo.music.ui.component.ColorPickerConversions.hsvToColor(hue.toFloat(), 0.10f, 0.96f)
            AuraPalette.apply(
                AuraAccent.from(seed, ground),
                pureBlack = false,
                coverCorners = AuraCoverCorners.Render,
                artworkInk = ink,
                artworkGround = ground,
            )
            val glass = AuraPalette.CoverGlassTint!!
            val ratio = contrastRatio(AuraPalette.NavInactive.compositeOver(glass), glass)
            assertTrue("hue $hue: nav label on the glass at $ratio", ratio >= TEXT_AA)
        }
        AuraPalette.reset()
        assertEquals(null, AuraPalette.CoverGlassTint)
    }

    @Test
    fun `a black and white cover gets a neutral ground, never an invented hue`() {
        val ground = auraArtworkGround(Color(0xFF808080))
        assertEquals(ground.red, ground.green, 0.002f)
        assertEquals(ground.green, ground.blue, 0.002f)
    }

    @Test
    fun `AMOLED and the shipped fallback still win over the cover ground`() {
        val tinted = auraArtworkGround(Color(0xFFE53935))
        AuraPalette.apply(AuraAccent.Brand, pureBlack = false, coverCorners = AuraCoverCorners.Render, artworkGround = tinted)
        assertEquals(tinted, AuraPalette.Ground)
        AuraPalette.apply(AuraAccent.Brand, pureBlack = true, coverCorners = AuraCoverCorners.Render, artworkGround = tinted)
        assertEquals(Color.Black, AuraPalette.Ground)
        AuraPalette.reset()
        assertEquals(Color(0xFF060A12), AuraPalette.Ground)
    }

    // ------------------------------------------------------------------ 6. the whole cover (2026-10-09)

    /** The ink [AuraPaletteSync] paints: the seed's hue at s 0.10 (0 for a grey seed), v 0.96, ≥ 4.5:1. */
    private fun syncInk(seed: Color, ground: Color): Color {
        val (hue, saturation, _) = iad1tya.echo.music.ui.component.ColorPickerConversions.colorToHsv(seed)
        val tint = if (saturation <= 0.05f) 0f else 0.10f
        return iad1tya.echo.music.ui.theme.ensureLegibleOn(
            iad1tya.echo.music.ui.component.ColorPickerConversions.hsvToColor(hue, tint, 0.96f),
            ground,
            4.5f,
        )
    }

    private val coverCases: List<List<CoverColors.Swatch>> = listOf(
        // black + red + gold
        listOf(CoverColors.Swatch(0xFF0A0A0A.toInt(), 6000), CoverColors.Swatch(0xFFD32F2F.toInt(), 2500), CoverColors.Swatch(0xFFD4A537.toInt(), 1000)),
        // black and white
        listOf(CoverColors.Swatch(0xFFF5F5F5.toInt(), 2000), CoverColors.Swatch(0xFF7A7A7A.toInt(), 5000), CoverColors.Swatch(0xFF0A0A0A.toInt(), 3000)),
        // white + blue
        listOf(CoverColors.Swatch(0xFFF5F5F5.toInt(), 7000), CoverColors.Swatch(0xFF1E5BD8.toInt(), 3000)),
        // colourful (two-tone ground: blue over yellow)
        listOf(
            CoverColors.Swatch(0xFF1E5BD8.toInt(), 3000), CoverColors.Swatch(0xFFF2C21B.toInt(), 2500),
            CoverColors.Swatch(0xFFD32F2F.toInt(), 2000), CoverColors.Swatch(0xFF2FA84F.toInt(), 1500),
        ),
        // dark navy + brown + beige
        listOf(CoverColors.Swatch(0xFF0A1A40.toInt(), 7000), CoverColors.Swatch(0xFF3A2618.toInt(), 2000), CoverColors.Swatch(0xFFC9B48A.toInt(), 1000)),
        // yellow, the old lightest ground
        listOf(CoverColors.Swatch(0xFFF2C21B.toInt(), 9000), CoverColors.Swatch(0xFF8E44AD.toInt(), 1000)),
    )

    /** Owner 2026-10-09: every cover — black, white, colourful — keeps AA text on BOTH ground tones. */
    @Test
    fun `text stays AA on both tones of every cover's ground`() {
        coverCases.forEach { swatches ->
            val mix = AuraCoverMix.resolve(CoverColors.pick(swatches)!!)
            val top = Color(mix.ground)
            val bottom = Color(mix.groundBottom)
            // AuraPaletteSync measures against the lighter tone.
            val measured = if (bottom.luminance() > top.luminance()) bottom else top
            val ink = syncInk(Color(mix.seed), measured)
            AuraPalette.apply(
                AuraAccent.fromCover(Color(mix.seed), mix.second?.let { Color(it) }, mix.third?.let { Color(it) }, measured),
                pureBlack = false,
                coverCorners = AuraCoverCorners.Render,
                artworkInk = ink,
                artworkGround = top,
                artworkGroundBottom = bottom,
            )
            for (tone in listOf(top, bottom)) {
                assertTrue("body on $tone", contrastRatio(ink, tone) >= 12f)
                listOf(
                    "ghost" to AuraPalette.OnGroundGhost,
                    "faint" to AuraPalette.OnGroundFaint,
                    "muted" to AuraPalette.OnGroundMuted,
                    "nav" to AuraPalette.NavInactive,
                ).forEach { (step, color) ->
                    val ratio = contrastRatio(color.compositeOver(tone), tone)
                    assertTrue("$swatches: $step on $tone at $ratio", ratio >= TEXT_AA)
                }
                listOf(AuraPalette.Teal, AuraPalette.Blue, AuraPalette.Violet).forEach {
                    assertTrue("accent $it on $tone", contrastRatio(it, tone) >= TEXT_AA - 0.15f)
                }
            }
            assertEquals(top, AuraPalette.Ground)
            assertEquals(bottom, AuraPalette.GroundBottom)
        }
    }

    @Test
    fun `a black and white cover gets neutral ink, never pink text`() {
        val ink = syncInk(Color(0xFFB7B7B7), Color(0xFF1B1B1B))
        assertEquals(ink.red, ink.green, 0.002f)
        assertEquals(ink.green, ink.blue, 0.002f)
    }

    /** Owner 2026-10-09: the ground and the ink ease (~1 s) on a track change instead of jumping. */
    @Test
    fun `the ground eases to the next cover and stays legible on the way`() {
        val redCover = AuraCoverMix.resolve(
            CoverColors.pick(listOf(CoverColors.Swatch(0xFFD32F2F.toInt(), 9000), CoverColors.Swatch(0xFF0A0A0A.toInt(), 1000)))!!,
        )
        val blackCover = AuraCoverMix.resolve(
            CoverColors.pick(listOf(CoverColors.Swatch(0xFF0A0A0A.toInt(), 8000), CoverColors.Swatch(0xFFF2C21B.toInt(), 2000)))!!,
        )
        val groundA = Color(redCover.ground)
        val groundB = Color(blackCover.ground)
        val inkA = syncInk(Color(redCover.seed), groundA)
        val inkB = syncInk(Color(blackCover.seed), groundB)
        AuraPalette.apply(AuraAccent.Brand, false, AuraCoverCorners.Render, artworkInk = inkA, artworkGround = groundA)
        AuraPalette.apply(AuraAccent.Brand, false, AuraCoverCorners.Render, artworkInk = inkB, artworkGround = groundB, animate = true)
        // The instant of the change: still the previous cover — no cut.
        assertEquals(groundA, AuraPalette.Ground)
        assertEquals(inkA, AuraPalette.OnGround)
        val t0 = 10_000L
        assertTrue(!AuraPalette.stepGroundTransition(t0))
        var previous = AuraPalette.Ground
        var sawInBetween = false
        for (ms in 62L until GROUND_TRANSITION_MS step 62L) {
            AuraPalette.stepGroundTransition(t0 + ms)
            val ground = AuraPalette.Ground
            if (ground != groundA && ground != groundB) sawInBetween = true
            // Mid-way the text steps still clear AA.
            val ratio = contrastRatio(AuraPalette.OnGroundGhost.compositeOver(ground), ground)
            assertTrue("at $ms ms ghost text $ratio", ratio >= TEXT_AA)
            assertTrue(contrastRatio(AuraPalette.OnGround, ground) >= 12f)
            previous = ground
        }
        assertTrue(sawInBetween)
        assertTrue(AuraPalette.stepGroundTransition(t0 + GROUND_TRANSITION_MS))
        assertEquals(groundB, AuraPalette.Ground)
        assertEquals(inkB, AuraPalette.OnGround)
        assertTrue(previous != groundA)
    }

    @Test
    fun `the ease is quantised, monotonic and ends on time`() {
        assertEquals(0f, groundTransitionProgress(0L), 0f)
        assertEquals(1f, groundTransitionProgress(GROUND_TRANSITION_MS), 0f)
        assertEquals(1f, groundTransitionProgress(GROUND_TRANSITION_MS * 5), 0f)
        var last = 0f
        val distinct = HashSet<Float>()
        for (ms in 0L..GROUND_TRANSITION_MS) {
            val p = groundTransitionProgress(ms)
            assertTrue(p >= last)
            assertEquals(0f, (p * GROUND_TRANSITION_STEPS) % 1f, 1e-4f)
            distinct += p
            last = p
        }
        // At most one write per step: the screens reading the ground update ≤ 17 times per track change.
        assertTrue(distinct.size <= GROUND_TRANSITION_STEPS + 1)
        // Ease-out: most of the change lands in the first half, like the lobes' spring.
        assertTrue(groundTransitionProgress(GROUND_TRANSITION_MS / 2) >= 0.8f)
    }

    @Test
    fun `the timeline spectrum falls back to the accent trio and never empties`() {
        AuraPalette.reset()
        assertEquals(listOf(AuraPalette.Teal, AuraPalette.Blue, AuraPalette.Violet), AuraPalette.ProgressSpectrum)
        val cover = listOf(Color(0xFFE74540), Color(0xFFCD9900), Color(0xFFD4D4D4))
        AuraPalette.apply(AuraAccent.Brand, false, AuraCoverCorners.Render, spectrum = cover)
        assertEquals(cover, AuraPalette.ProgressSpectrum)
        AuraPalette.apply(AuraAccent.Brand, false, AuraCoverCorners.Render, spectrum = listOf(Color(0xFFE74540)))
        assertEquals(3, AuraPalette.ProgressSpectrum.size)
    }

    companion object {
        /**
         * WCAG 2.1 body text. The accent carries 11–12 sp technical type in this UI ("◆ HI-RES", the
         * codec chips, "SONANDO", the queue's radio caption), so 4.5 is the bar — not the 3.0 the
         * theme's own `MIN_ACCENT_CONTRAST` uses for large text and graphical objects.
         */
        private const val TEXT_AA = 4.5f
    }
}
