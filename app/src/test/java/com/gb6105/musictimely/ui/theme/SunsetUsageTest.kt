package com.gb6105.musictimely.ui.theme

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/** The sunset gradient is only for CTA, selection and progress emphasis (docs/design/README.md). */
class SunsetUsageTest {
    private val root = File("src/main/java/com/gb6105/musictimely")

    @Test
    fun `sunset is used only by the primary button, the selected chip, the record label and progress emphasis`() {
        val users = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "Tokens.kt" }
            .filter { Regex("""MtColors\.Sunset""").containsMatchIn(it.readText()) }
            .map { it.relativeTo(root).path }
            .toSet()

        assertThat(users).isNotEmpty()
        // Progress emphasis (README "진행 강조"): passed progress cells and the song segments of the translation dial.
        val allowed = setOf("ui/component/Buttons.kt", "ui/component/Turntable.kt", "ui/component/ProgressPanel.kt", "ui/component/TranslationCard.kt")
        assertThat(allowed).containsAtLeastElementsIn(users)
    }

    @Test
    fun `every sunset brush uses the design stops`() {
        val evenStops = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { Regex("""horizontalGradient\(\s*MtColors\.Sunset\s*\)""").containsMatchIn(it.readText()) }
            .map { it.name }
            .toList()

        assertThat(evenStops).isEmpty()
    }
}
