package com.tonbanna.smoothengine

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var details: TextView
    private lateinit var status: TextView
    private var selectedUri: Uri? = null

    private val bg = Color.rgb(8, 12, 20)
    private val panel = Color.rgb(18, 27, 40)
    private val cyan = Color.rgb(65, 231, 216)
    private val white = Color.rgb(239, 245, 250)
    private val muted = Color.rgb(155, 174, 192)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(18))
            setBackgroundColor(bg)
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(18))
        }
        val brand = label("TON", 16f, cyan, true)
        brand.letterSpacing = 0.22f
        content.addView(brand)
        content.addView(label("SMOOTH ENGINE", 29f, white, true).apply { setPadding(0, dp(4), 0, dp(5)) })
        content.addView(label("LOCAL VIDEO ANALYZER  •  VERSION 0.1.0", 11f, muted, true))
        content.addView(space(22))

        val hero = card()
        hero.addView(label("SMART FRAME ENGINE", 17f, cyan, true))
        hero.addView(label("วิเคราะห์วิดีโอและเตรียมไฟล์ให้ลื่นไหล โดยประมวลผลภายในมือถือ", 14f, white, false).apply {
            setPadding(0, dp(8), 0, dp(14))
        })
        val choose = button("เลือกวิดีโอจากมือถือ", cyan)
        choose.setOnClickListener { openVideoPicker() }
        hero.addView(choose)
        content.addView(hero)
        content.addView(space(14))

        val info = card()
        info.addView(label("VIDEO ANALYSIS", 14f, cyan, true))
        status = label("ยังไม่ได้เลือกวิดีโอ", 14f, white, true).apply { setPadding(0, dp(10), 0, dp(8)) }
        info.addView(status)
        details = label("เลือกวิดีโอเพื่ออ่าน Resolution, FPS, Codec, Bitrate และข้อมูลสี", 13f, muted, false)
        details.setLineSpacing(dp(5).toFloat(), 1f)
        info.addView(details)
        content.addView(info)
        content.addView(space(14))

        val policy = card()
        policy.addView(label("EXPORT DEFAULTS", 14f, cyan, true))
        policy.addView(label("FPS  •  Smart target 60+ (เมื่อทำได้จริง)\nResolution  •  Original\nQuality  •  Maximum practical\nHDR  •  Preserve when supported\nProcessing  •  Local only", 13f, white, false).apply {
            setPadding(0, dp(10), 0, 0)
            setLineSpacing(dp(5).toFloat(), 1f)
        })
        content.addView(policy)
        content.addView(space(14))

        val note = card()
        note.addView(label("ENGINE STATUS", 14f, cyan, true))
        note.addView(label(
            "ระยะที่ 1: เลือกไฟล์และวิเคราะห์ข้อมูล\nระยะที่ 2: เพิ่มระบบสร้างเฟรมแทรกจริง (Frame Interpolation)\n\nขณะนี้ยังไม่ส่งออก 60FPS และจะไม่อ้างว่าเป็น Interpolation จนกว่าจะสร้างเฟรมใหม่และตรวจสอบไฟล์ผลลัพธ์ได้",
            12f, muted, false
        ).apply { setPadding(0, dp(9), 0, 0); setLineSpacing(dp(5).toFloat(), 1f) })
        content.addView(note)

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(label("NO CLOUD UPLOAD  •  SOURCE FILE IS NEVER OVERWRITTEN", 10f, muted, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
        })
        setContentView(root)
    }

    private fun openVideoPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_VIDEO)
    }

    @Deprecated("Deprecated by platform; retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_VIDEO || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        selectedUri = uri
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) { }
        analyze(uri)
    }

    private fun analyze(uri: Uri) {
        status.text = "กำลังวิเคราะห์ไฟล์..."
        details.text = "กำลังอ่านข้อมูลจากวิดีโอในเครื่อง"
        Thread {
            val report = runCatching { readMediaInfo(uri) }
            runOnUiThread {
                if (report.isSuccess) {
                    status.text = "MEDIA LOADED  •  ANALYSIS COMPLETE"
                    details.text = report.getOrThrow()
                } else {
                    status.text = "อ่านข้อมูลไม่สำเร็จ"
                    details.text = "ไม่สามารถอ่านไฟล์นี้ได้: ${report.exceptionOrNull()?.message ?: "Unknown error"}"
                }
            }
        }.start()
    }

    private fun readMediaInfo(uri: Uri): String {
        val retriever = MediaMetadataRetriever()
        var duration = "ไม่ทราบ"
        var rotation = "0°"
        var containerWidth = "ไม่ทราบ"
        var containerHeight = "ไม่ทราบ"
        var overallBitrate = "ไม่ทราบ"
        try {
            retriever.setDataSource(this, uri)
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.let {
                val ms = it.toLongOrNull() ?: 0L
                String.format(Locale.US, "%d:%02d", ms / 60000, (ms / 1000) % 60)
            } ?: duration
            rotation = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) ?: "0") + "°"
            containerWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) ?: containerWidth
            containerHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT) ?: containerHeight
            overallBitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
                ?.let { String.format(Locale.US, "%.2f Mbps", it / 1_000_000.0) } ?: overallBitrate
        } finally {
            runCatching { retriever.release() }
        }

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(this, uri, null)
            var videoInfo = "ไม่พบ video track"
            var audioInfo = "ไม่พบ audio track"
            var fps = "ไม่ระบุใน metadata"
            var colorInfo = "ไม่ระบุใน metadata"
            var videoMime = "ไม่ทราบ"
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: "unknown"
                if (mime.startsWith("video/")) {
                    videoMime = mime
                    val w = format.intOrNull(MediaFormat.KEY_WIDTH)?.toString() ?: containerWidth
                    val h = format.intOrNull(MediaFormat.KEY_HEIGHT)?.toString() ?: containerHeight
                    val frameRate = format.intOrNull(MediaFormat.KEY_FRAME_RATE)
                    if (frameRate != null && frameRate > 0) fps = "$frameRate FPS (metadata hint)"
                    val bitrate = format.intOrNull(MediaFormat.KEY_BIT_RATE)?.let {
                        String.format(Locale.US, "%.2f Mbps", it / 1_000_000.0)
                    } ?: overallBitrate
                    val colorStandard = format.intOrNull(MediaFormat.KEY_COLOR_STANDARD)?.toString() ?: "?"
                    val colorTransfer = format.intOrNull(MediaFormat.KEY_COLOR_TRANSFER)?.toString() ?: "?"
                    val colorRange = format.intOrNull(MediaFormat.KEY_COLOR_RANGE)?.toString() ?: "?"
                    colorInfo = "standard=$colorStandard, transfer=$colorTransfer, range=$colorRange"
                    videoInfo = "$w × $h\nCodec: $mime\nTrack bitrate: $bitrate"
                } else if (mime.startsWith("audio/")) {
                    val channels = format.intOrNull(MediaFormat.KEY_CHANNEL_COUNT)?.toString() ?: "?"
                    val sampleRate = format.intOrNull(MediaFormat.KEY_SAMPLE_RATE)?.toString() ?: "?"
                    audioInfo = "$mime • $channels ch • $sampleRate Hz"
                }
            }
            return "VIDEO\n$videoInfo\nContainer size: $containerWidth × $containerHeight\nFPS: $fps\nDuration: $duration\nRotation: $rotation\nOverall bitrate: $overallBitrate\nColor metadata: $colorInfo\n\nAUDIO\n$audioInfo\n\nNEXT: Smart FPS plan will be selected after frame-rate and device capability validation."
        } finally {
            extractor.release()
        }
    }

    private fun MediaFormat.intOrNull(key: String): Int? =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        setBackgroundColor(panel)
        elevation = dp(2).toFloat()
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }

    private fun button(text: String, color: Int) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setTextColor(Color.rgb(5, 18, 23))
        setBackgroundColor(color)
        textSize = 15f
    }

    private fun space(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object { private const val REQUEST_VIDEO = 1201 }
}
