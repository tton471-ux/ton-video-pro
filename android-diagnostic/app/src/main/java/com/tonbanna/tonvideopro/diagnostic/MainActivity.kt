package com.tonbanna.tonvideopro.diagnostic

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var reportView: TextView
    private var latestReport: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
        }
        val title = TextView(this).apply {
            text = "TON VIDEO PRO MOBILE\nMediaCodec Diagnostic"
            textSize = 23f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 18)
        }
        val subtitle = TextView(this).apply {
            text = "ตรวจความสามารถ Encoder ของเครื่องนี้โดยตรง • ไม่อ่านหรือแก้ไขวิดีโอ"
            textSize = 14f
            setPadding(0, 0, 0, 16)
        }
        reportView = TextView(this).apply {
            textSize = 13f
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        val scroll = ScrollView(this).apply { addView(reportView) }
        val copy = Button(this).apply {
            text = "คัดลอกรายงาน"
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("TON VIDEO PRO report", latestReport))
                text = "คัดลอกแล้ว"
            }
        }
        val refresh = Button(this).apply {
            text = "ตรวจสอบอีกครั้ง"
            setOnClickListener { runDiagnostic() }
        }
        root.addView(title)
        root.addView(subtitle)
        root.addView(refresh)
        root.addView(copy)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        runDiagnostic()
    }

    private fun runDiagnostic() {
        latestReport = buildReport()
        reportView.text = latestReport
    }

    private fun buildReport(): String {
        val out = StringBuilder()
        out.appendLine("TON VIDEO PRO MOBILE — DEVICE REPORT")
        out.appendLine("เวลา: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(java.util.Date())}")
        out.appendLine("ผู้ผลิต: ${Build.MANUFACTURER}")
        out.appendLine("รุ่น: ${Build.MODEL} (${Build.DEVICE})")
        out.appendLine("Android: ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
        out.appendLine("ฮาร์ดแวร์: ${Build.HARDWARE}")
        out.appendLine("--------------------------------------------------")

        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        val hevcEncoders = codecList.codecInfos.filter { info ->
            info.isEncoder && info.supportedTypes.any { it.equals("video/hevc", true) }
        }
        out.appendLine("พบ HEVC Encoder: ${hevcEncoders.size}")
        if (hevcEncoders.isEmpty()) out.appendLine("RESULT: ไม่พบ HEVC encoder ที่ Android เปิดเผย")

        var main10 = false
        var hdrEditing = false
        var p010 = false
        var supports4k60 = false
        var supports4k = false

        hevcEncoders.forEachIndexed { index, info ->
            out.appendLine()
            out.appendLine("[HEVC ENCODER ${index + 1}]")
            out.appendLine("ชื่อ: ${info.name}")
            out.appendLine("ชนิด: ${if (info.isHardwareAccelerated) "Hardware" else if (info.isSoftwareOnly) "Software" else "Unknown/Hybrid"}")
            out.appendLine("Vendor: ${info.isVendor}")
            val caps = info.getCapabilitiesForType("video/hevc")
            val main10Profile = MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
            val profiles = caps.profileLevels
            val hasMain10 = profiles.any { it.profile == main10Profile }
            main10 = main10 || hasMain10
            out.appendLine("Profiles: " + profiles.joinToString { "profile=${it.profile},level=${it.level}" })
            out.appendLine("HEVC Main10: ${if (hasMain10) "YES (ประกาศรองรับ)" else "NO / ไม่ประกาศรองรับ"}")
            val hasHdrEdit = if (Build.VERSION.SDK_INT >= 29) {
                caps.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_HdrEditing)
            } else false
            hdrEditing = hdrEditing || hasHdrEdit
            out.appendLine("FEATURE_HdrEditing: ${if (hasHdrEdit) "YES" else "NO"}")
            val p010Code = 54 // COLOR_FormatYUVP010
            val hasP010 = caps.colorFormats.contains(p010Code)
            p010 = p010 || hasP010
            out.appendLine("P010 (color format 54): ${if (hasP010) "YES" else "ไม่ประกาศใน colorFormats"}")
            out.appendLine("Color formats: ${caps.colorFormats.joinToString()}")
            val video = caps.videoCapabilities
            if (video != null) {
                val size4k = video.areSizeAndRateSupported(3840, 2160, 60.0)
                val size4k30 = video.areSizeAndRateSupported(3840, 2160, 30.0)
                supports4k60 = supports4k60 || size4k
                supports4k = supports4k || size4k30
                out.appendLine("3840x2160 @ 60: ${if (size4k) "YES" else "NO"}")
                out.appendLine("3840x2160 @ 30: ${if (size4k30) "YES" else "NO"}")
                out.appendLine("ช่วงความกว้าง: ${video.supportedWidths}")
                out.appendLine("ช่วงความสูง: ${video.supportedHeights}")
                out.appendLine("FPS ตามขนาด 4K: ${runCatching { video.getSupportedFrameRatesFor(3840, 2160) }.getOrNull() ?: "อ่านไม่ได้"}")
                if (Build.VERSION.SDK_INT >= 29) {
                    val points = video.supportedPerformancePoints
                    out.appendLine("Performance points: ${points?.joinToString() ?: "ไม่มีข้อมูล"}")
                }
            } else {
                out.appendLine("Video capabilities: ไม่มีข้อมูล")
            }
        }

        out.appendLine()
        out.appendLine("--------------------------------------------------")
        out.appendLine("สรุปจากข้อมูลที่ Android เปิดเผย")
        out.appendLine("HEVC Main10: ${yesNo(main10)}")
        out.appendLine("HDR Editing feature: ${yesNo(hdrEditing)}")
        out.appendLine("P010: ${yesNo(p010)}")
        out.appendLine("4K 30: ${yesNo(supports4k)}")
        out.appendLine("4K 60: ${yesNo(supports4k60)}")
        out.appendLine()
        out.appendLine("ข้อควรทราบ:")
        out.appendLine("- HLG/PQ และ BT.2020 ต้องทดสอบด้วยการ encode จริงและตรวจ metadata ของไฟล์ผลลัพธ์")
        out.appendLine("- Main10 หรือ P010 ที่รายงานได้ ไม่ได้รับประกันว่าเส้นทาง decode→encode จะรักษา HDR ได้")
        out.appendLine("- รายงานนี้ยังไม่ทดสอบคุณภาพภาพ, metadata mastering, MaxCLL หรือ Dolby Vision")
        out.appendLine("- ส่งรายงานนี้กลับมาเพื่อวางขั้นตอนทดสอบคลิปตัวอย่าง")
        return out.toString()
    }

    private fun yesNo(value: Boolean) = if (value) "YES" else "NO / ไม่พบการประกาศรองรับ"
}
