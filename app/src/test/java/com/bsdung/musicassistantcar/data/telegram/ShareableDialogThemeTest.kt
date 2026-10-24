package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * E96 P5B3 REWORK-01 (e): every AlertDialog of the Shareable screens is readable. The live failure was white text on a
 * white dialog surface (the app theme set white text but no dialog theme, and the system picked a light dialog).
 * These tests read the real resource files, so a theme that loses its dialog theme, or a colour that drops below
 * 4.5:1 on the dialog surface, fails the build. Shareable only: the Personal flavor's files are checked to be unchanged
 * in kind (no MeloVault style leaks into main).
 */
class ShareableDialogThemeTest {
    private val srcRoot = listOf(File("src"), File("app/src")).first { it.isDirectory }
    private val mainRes = File(srcRoot, "main/res/values")
    private val shareableRes = File(srcRoot, "shareable/res/values")

    private class Style(val name: String, val parent: String?, val items: Map<String, String>)

    private fun parse(file: File) = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement

    private fun styles(vararg files: File): Map<String, Style> {
        val out = HashMap<String, Style>()
        for (file in files) {
            val nodes = parse(file).getElementsByTagName("style")
            for (i in 0 until nodes.length) {
                val e = nodes.item(i) as Element
                val items = HashMap<String, String>()
                val children = e.getElementsByTagName("item")
                for (j in 0 until children.length) {
                    val item = children.item(j) as Element
                    items[item.getAttribute("name")] = item.textContent.trim()
                }
                val name = e.getAttribute("name")
                val parent = e.getAttribute("parent").ifEmpty { name.substringBeforeLast('.', "").ifEmpty { null } }
                out[name] = Style(name, parent, items)
            }
        }
        return out
    }

    private val all by lazy { styles(File(mainRes, "themes.xml"), File(shareableRes, "themes.xml")) }

    /** The value of [attr] for [style], looking through its parents (dotted names count as parents too). */
    private fun resolve(style: String, attr: String): String? {
        var current: String? = style
        while (current != null) {
            val s = all[current] ?: return null
            s.items[attr]?.let { return it }
            current = s.parent
        }
        return null
    }

    private val colors by lazy {
        val out = HashMap<String, String>()
        for (file in listOf(File(mainRes, "colors.xml"), File(shareableRes, "themes.xml"))) {
            val nodes = parse(file).getElementsByTagName("color")
            for (i in 0 until nodes.length) {
                val e = nodes.item(i) as Element
                out[e.getAttribute("name")] = e.textContent.trim()
            }
        }
        out
    }

    private fun color(ref: String?): Int {
        assertNotNull("unresolved attribute", ref)
        val hex = if (ref!!.startsWith("@color/")) colors.getValue(ref.removePrefix("@color/")) else ref
        return java.lang.Long.parseLong(hex.removePrefix("#").let { if (it.length == 6) "FF$it" else it }, 16).toInt()
    }

    private fun luminance(c: Int): Double {
        fun channel(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel((c shr 16) and 0xFF) + 0.7152 * channel((c shr 8) and 0xFF) + 0.0722 * channel(c and 0xFF)
    }

    private fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    @Test fun t20_theShareableAppThemeNamesItsDialogTheme() {
        assertEquals("@style/Theme.MeloVault.Dialog", resolve("Theme.MeloVault.Shareable", "alertDialogTheme"))
        assertEquals("@style/Theme.MeloVault.Dialog", resolve("Theme.MeloVault.Shareable", "materialAlertDialogTheme"))
        assertEquals("Theme.MusicAssistantCar", all.getValue("Theme.MeloVault.Shareable").parent)
        // everything the screens had stays: it only adds the dialog theme
        assertEquals("@color/bg_base", resolve("Theme.MeloVault.Shareable", "android:windowBackground"))
    }

    @Test fun t20_everyDialogTextAndButtonColourHasAtLeastFourAndAHalfToOneOnTheDialogSurface() {
        val surface = color(resolve("Theme.MeloVault.Dialog", "colorSurface"))
        assertEquals("the dialog surface is the dark elevated one", color("@color/bg_surface_elevated"), surface)
        assertEquals(surface, color(resolve("Theme.MeloVault.Dialog", "android:background")))
        val checked = mapOf(
            "title/body text" to resolve("Theme.MeloVault.Dialog", "android:textColorPrimary"),
            "secondary text" to resolve("Theme.MeloVault.Dialog", "android:textColorSecondary"),
            "on-surface" to resolve("Theme.MeloVault.Dialog", "colorOnSurface"),
            "list item" to resolve("Theme.MeloVault.Dialog", "textColorAlertDialogListItem"),
            "button (colorPrimary)" to resolve("Theme.MeloVault.Dialog", "colorPrimary"),
            "button (colorAccent)" to resolve("Theme.MeloVault.Dialog", "colorAccent"),
            "button (colorSecondary)" to resolve("Theme.MeloVault.Dialog", "colorSecondary"),
        )
        for ((what, ref) in checked) {
            val ratio = contrast(color(ref), surface)
            assertTrue("$what $ref is only %.2f:1".format(ratio), ratio >= 4.5)
        }
        // the old accent was the reason for a dedicated button colour
        assertTrue(contrast(color("@color/accent_primary"), surface) < 4.5)
    }

    @Test fun theDialogThemeIsDarkAndNotADayNightOneThatCouldTurnWhite() {
        var current: String? = "Theme.MeloVault.Dialog"
        val chain = ArrayList<String>()
        while (current != null) {
            chain += current
            current = all[current]?.parent
        }
        assertTrue(chain.toString(), "Theme.MaterialComponents.Dialog.Alert" in chain)
        assertFalse(chain.toString(), chain.any { it.contains("DayNight") || it.contains("Light") })
    }

    @Test fun everyShareableActivityUsesTheShareableTheme() {
        val manifest = parse(File(srcRoot, "shareable/AndroidManifest.xml"))
        val activities = manifest.getElementsByTagName("activity")
        val themes = (0 until activities.length).associate {
            val e = activities.item(it) as Element
            e.getAttribute("android:name") to e.getAttribute("android:theme")
        }
        for (name in listOf(".data.telegram.MeloUiLabActivity", ".data.telegram.MeloSyncSettingsActivity", ".data.telegram.MeloUiPreviewActivity", ".data.telegram.TelegramPreviewActivity")) {
            assertEquals(name, "@style/Theme.MeloVault.Shareable", themes[name])
        }
        // the demo MainActivity of the Personal flavor is only replaced (exported=false), not themed here
        assertFalse(themes.containsKey(".MainActivity") && themes[".MainActivity"]!!.isNotEmpty())
    }

    @Test fun noShareableSourceFallsBackToThePlainAppThemeOrThePersonalDialogTheme() {
        val kotlin = File(srcRoot, "shareable/java").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(kotlin.isNotEmpty())
        for (file in kotlin) {
            val text = file.readText()
            assertFalse("${file.name} uses the plain theme", Regex("R\\.style\\.Theme_MusicAssistantCar\\b(?!_)").containsMatchIn(text))
            assertFalse("${file.name} uses the Personal dialog theme", text.contains("Theme_MusicAssistantCar_Dialog"))
            // a dialog colours itself through the theme only
            if (text.contains("AlertDialog.Builder(")) {
                assertFalse("${file.name} paints a dialog by hand", Regex("getButton\\([^)]*\\)\\??\\.setTextColor").containsMatchIn(text))
            }
        }
        val settings = File(srcRoot, "shareable/java/com/bsdung/musicassistantcar/data/telegram/MeloSyncSettingsActivity.kt").readText()
        assertTrue(settings.contains("setTheme(R.style.Theme_MeloVault_Shareable)"))
        assertTrue("its dialogs take the theme from the activity", settings.contains("AlertDialog.Builder(this)"))
    }

    @Test fun thePersonalFlavorIsUntouched() {
        val mainThemes = File(mainRes, "themes.xml").readText()
        assertFalse("no MeloVault style in the shared main themes", mainThemes.contains("MeloVault"))
        assertFalse(File(srcRoot, "main/res/values/colors.xml").readText().contains("melo_dialog_button"))
        assertTrue("the new file sits in the Shareable source set only", File(shareableRes, "themes.xml").path.replace('\\', '/').contains("/shareable/"))
        assertFalse(File(srcRoot, "personal/res/values/themes.xml").exists())
        assertTrue(File(srcRoot, "main/java/com/bsdung/musicassistantcar/MainActivity.kt").readText().contains("R.style.Theme_MusicAssistantCar_Dialog"))
    }
}
