package com.bsdung.musicassistantcar.data.telegram

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * E96 P4 evidence: draws the Car Mode layout from [MeloCarLayoutPolicy] / [MeloDesignTokens] numbers as a labelled
 * wireframe SVG. It is a SIMULATION computed on the JVM - NOT a screenshot of an FYT, Sony, Shanling or Lenovo device.
 * Output: app/build/p4-evidence (copied to the handoff folder by the person running the task).
 */
class MeloCarLayoutSimulationTest {

    private class Spec(val file: String, val label: String, val w: Int, val h: Int, val ppi: Float)

    private val specs = listOf(
        Spec("sim-01-fyt-1920x1200.svg", "FYT 7870 1920x1200", 1920, 1200, 172.8f),
        Spec("sim-02-sony-landscape-2520x1080.svg", "Sony Xperia 5 II ngang 2520x1080", 2520, 1080, 449f),
    )

    @Test fun render_simulation_svg_files() {
        val out = File("build/p4-evidence").apply { mkdirs() }
        for (spec in specs) {
            val scale = MeloCarPolicy.carScale(spec.ppi, spec.w, spec.h)
            val eff = MeloCarPolicy.effectivePpi(spec.ppi, spec.w, spec.h)
            val s = MeloDesignTokens.sizes(MeloDesignTokens.Profile.CAR, spec.ppi / 160f, 1f, eff)
            val file = File(out, spec.file)
            file.writeText(draw(spec, s, scale), Charsets.UTF_8)
            assertTrue(file.length() > 2_000)
        }
    }

    private fun hex(c: Int) = "#%06X".format(c and 0xFFFFFF)

    private fun draw(spec: Spec, s: MeloDesignTokens.Sizes, scale: Float): String {
        val p = MeloDesignTokens.CAR_PALETTE
        val sb = StringBuilder()
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"${spec.w}\" height=\"${spec.h}\" viewBox=\"0 0 ${spec.w} ${spec.h}\" font-family=\"Arial, sans-serif\">\n")
        sb.append("<rect width=\"${spec.w}\" height=\"${spec.h}\" fill=\"${hex(p.background)}\"/>\n")
        fun rect(x: Int, y: Int, w: Int, h: Int, fill: Int, stroke: Int? = null) {
            sb.append("<rect x=\"$x\" y=\"$y\" width=\"$w\" height=\"$h\" rx=\"${s.corner}\" fill=\"${hex(fill)}\"")
            if (stroke != null) sb.append(" stroke=\"${hex(stroke)}\" stroke-width=\"3\"")
            sb.append("/>\n")
        }
        fun text(t: String, x: Int, y: Int, size: Float, color: Int, bold: Boolean = false) {
            sb.append("<text x=\"$x\" y=\"$y\" font-size=\"${size.toInt()}\" fill=\"${hex(color)}\"${if (bold) " font-weight=\"bold\"" else ""}>${t.replace("&", "&amp;").replace("<", "&lt;")}</text>\n")
        }
        val bars = MeloCarLayoutPolicy.barsBudgetPx(scale)
        sb.append("<rect x=\"0\" y=\"${spec.h - bars}\" width=\"${spec.w}\" height=\"$bars\" fill=\"#FFFFFF\" fill-opacity=\"0.15\"/>\n")
        text("vung danh cho thanh he thong ($bars px)", 20, spec.h - bars / 3, s.secondaryText * 0.7f, p.textSecondary)

        val top = s.gap
        val nav = s.navColumnWidth
        val gapX = s.screenMargin
        val player = (spec.w - nav - gapX * 3) * 760 / 1760
        val listX = nav + gapX + player + gapX
        val listW = spec.w - listX - s.screenMargin

        listOf("Nhac", "Telegram", "Playlist", "Da tai", "Tim", "Cai dat").forEachIndexed { i, label ->
            val h = (spec.h - bars - 2 * s.gap) / 6 - s.gap / 2
            val y = top + i * (h + s.gap / 2)
            rect(s.gap / 2, y, nav - s.gap, h, if (i == 2) p.accentSoft else p.surface, if (i == 2) p.accent else null)
            text(label, s.gap, y + h / 2, s.secondaryText * 0.8f, p.text)
        }

        var y = top
        val px = nav + gapX
        rect(px, y, s.playerArt, s.playerArt, p.surface, p.border)
        text("anh bia", px + s.gap * 3, y + s.playerArt / 2, s.titleText, p.textSecondary)
        text("Ten bai dang phat", px + s.playerArt + s.gap * 3, y + s.playerTitleText.toInt(), s.playerTitleText, p.text, true)
        text("Kenh Telegram - FLAC - Da tai", px + s.playerArt + s.gap * 3, y + (s.playerTitleText * 2).toInt(), s.secondaryText, p.textSecondary)
        y += s.playerArt + s.gap * 3
        rect(px, y, player, s.progressHeight, p.border); rect(px, y, player / 3, s.progressHeight, p.accent)
        y += s.progressThumb
        text("1:20 / 4:05", px + player - (s.secondaryText * 5).toInt(), y + s.secondaryText.toInt(), s.secondaryText, p.textSecondary)
        y += (s.secondaryText * 1.3f).toInt() + s.gap * 2
        val side = (player - 4 * s.gap - s.playButtonWidth) / 2
        rect(px, y, side, s.transportHeight, p.surfaceRaised); text("Truoc", px + s.gap * 2, y + s.transportHeight / 2, s.titleText, p.text)
        rect(px + side + s.gap * 2, y, s.playButtonWidth, s.playButtonHeight, p.accent); text("Phat", px + side + s.gap * 4, y + s.playButtonHeight / 2, s.titleText, p.onAccent, true)
        rect(px + side + s.gap * 4 + s.playButtonWidth, y, side, s.transportHeight, p.surfaceRaised); text("Ke", px + side + s.gap * 6 + s.playButtonWidth, y + s.transportHeight / 2, s.titleText, p.text)
        y += maxOf(s.transportHeight, s.playButtonHeight) + s.gap * 2
        val half = (player - s.gap * 2) / 2
        rect(px, y, half, s.transportHeight, p.surfaceRaised); text("-30s", px + s.gap * 2, y + s.transportHeight / 2, s.titleText, p.text)
        rect(px + half + s.gap * 2, y, half, s.transportHeight, p.surfaceRaised); text("+60s", px + half + s.gap * 4, y + s.transportHeight / 2, s.titleText, p.text)
        y += s.transportHeight + s.gap * 2
        val saveH = MeloSavePolicy.carButtonHeightPx(s)
        rect(px, y, player, saveH, p.accentSoft, p.accent)
        text("Bo luu  (nut Luu / Bo luu 1 cham, cao ${saveH}px)", px + s.gap * 3, y + saveH / 2 + (s.titleText / 3).toInt(), s.titleText, p.accent, true)

        var ly = top
        text("Playlist - 12 bai", listX, ly + s.titleText.toInt(), s.titleText, p.text, true)
        text("Da luu tren may - Chua bat dong bo", listX, ly + s.titleText.toInt() + (s.secondaryText * 1.3f).toInt(), s.secondaryText, p.textSecondary)
        ly += MeloCarLayoutPolicy.listHeaderHeightPx(s, true)
        repeat(MeloCarPolicy.ROWS_PER_PAGE) { i ->
            rect(listX, ly, listW, s.rowHeight, if (i == 1) p.accentSoft else p.surface, p.border)
            text("${i + 1}. Bai hat mau", listX + s.rowArt + s.gap * 3, ly + s.rowHeight / 2, s.titleText, p.text)
            ly += s.rowHeight + s.gap
        }
        rect(listX, ly, listW / 2 - s.gap, s.pageButtonHeight, p.surfaceRaised); text("Cuon trang len", listX + s.gap * 2, ly + s.pageButtonHeight / 2, s.titleText, p.text)
        rect(listX + listW / 2 + s.gap, ly, listW / 2 - s.gap, s.pageButtonHeight, p.surfaceRaised); text("Cuon trang xuong", listX + listW / 2 + s.gap * 3, ly + s.pageButtonHeight / 2, s.titleText, p.text)

        val note = 0xFFFFD166.toInt()
        text("MO PHONG BO CUC tu MeloCarLayoutPolicy - KHONG phai anh chup thiet bi that. ${spec.label}; scale=${"%.3f".format(scale)}", 20, 22, 18f, note, true)
        text("Player pane ${MeloCarLayoutPolicy.playerPaneHeightPx(s)}px / List pane ${MeloCarLayoutPolicy.listPaneHeightPx(s, true)}px / kha dung ${MeloCarLayoutPolicy.availableHeightPx(spec.h, s, bars)}px", 20, 44, 18f, note, true)
        sb.append("</svg>\n")
        return sb.toString()
    }
}
