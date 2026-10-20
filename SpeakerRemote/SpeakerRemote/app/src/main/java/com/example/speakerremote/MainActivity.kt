package com.example.speakerremote

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.ConsumerIrManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** One button on the remote. cmd = default NEC command byte. */
class Key(
    val id: String,
    val label: String,
    val cmd: Int,
    val bg: Int,
    val fg: Int = Color.WHITE,
    val repeat: Boolean = false
)

class MainActivity : Activity() {

    private var ir: ConsumerIrManager? = null
    private lateinit var prefs: SharedPreferences
    private lateinit var worker: HandlerThread
    private lateinit var irHandler: Handler
    private val ui = Handler(Looper.getMainLooper())
    private var editMode = false
    private var holdRunnable: Runnable? = null

    private val RED = Color.parseColor("#D32F2F")
    private val GREEN = Color.parseColor("#2E7D32")
    private val BLUE = Color.parseColor("#1E88E5")
    private val NAVY = Color.parseColor("#2A2F6B")
    private val WHITE = Color.parseColor("#F2F2F2")
    private val DARK = Color.parseColor("#111111")

    // Layout matches the physical remote (held upright). cmd = NEC command byte, address 0x00.
    private val rows: List<List<Key>> by lazy {
        listOf(
            listOf(Key("power", "PWR", 0x45, RED), Key("mode", "MODE", 0x46, NAVY), Key("mute", "MUTE", 0x47, GREEN)),
            listOf(Key("play", "▶❚❚", 0x44, BLUE), Key("prev", "◀◀", 0x40, BLUE), Key("next", "▶▶", 0x43, BLUE)),
            listOf(Key("eq", "EQ", 0x07, NAVY), Key("voldn", "VOL−", 0x15, GREEN, repeat = true), Key("volup", "VOL+", 0x09, GREEN, repeat = true)),
            listOf(Key("n0", "0", 0x16, WHITE, DARK), Key("rpt", "RPT", 0x19, RED), Key("usd", "U/SD", 0x0D, RED)),
            listOf(Key("n1", "1", 0x0C, WHITE, DARK), Key("n2", "2", 0x18, WHITE, DARK), Key("n3", "3", 0x5E, WHITE, DARK)),
            listOf(Key("n4", "4", 0x08, WHITE, DARK), Key("n5", "5", 0x1C, WHITE, DARK), Key("n6", "6", 0x5A, WHITE, DARK)),
            listOf(Key("n7", "7", 0x42, WHITE, DARK), Key("n8", "8", 0x52, WHITE, DARK), Key("n9", "9", 0x4A, WHITE, DARK))
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("codes", Context.MODE_PRIVATE)
        worker = HandlerThread("ir-sender").also { it.start() }
        irHandler = Handler(worker.looper)
        ir = getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        buildUi()
    }

    override fun onDestroy() {
        super.onDestroy()
        worker.quit()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#1B1B1B"))
            setPadding(dp(12), dp(24), dp(12), dp(24))
        }

        root.addView(TextView(this).apply {
            text = "Speaker Remote"
            setTextColor(Color.WHITE)
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })

        val hasIr = ir?.hasIrEmitter() == true
        root.addView(TextView(this).apply {
            text = if (hasIr) "IR blaster ready — point the top of your phone at the speaker"
            else "This phone has no IR blaster, so buttons can't transmit."
            setTextColor(if (hasIr) Color.parseColor("#81C784") else Color.parseColor("#EF9A9A"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(8))
        })

        val editBtn = Button(this).apply { text = "Edit codes: OFF" }
        editBtn.setOnClickListener {
            editMode = !editMode
            editBtn.text = if (editMode) "Edit codes: ON (tap a key)" else "Edit codes: OFF"
        }
        root.addView(editBtn)

        val size = minOf(resources.displayMetrics.widthPixels / 4, dp(88))
        for (row in rows) {
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            for (k in row) r.addView(makeButton(k, size))
            root.addView(r)
        }

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#1B1B1B"))
            addView(root)
        })
    }

    private fun makeButton(k: Key, size: Int): Button {
        val b = Button(this)
        b.text = k.label
        b.setTextColor(k.fg)
        b.textSize = 15f
        b.typeface = Typeface.DEFAULT_BOLD
        b.isAllCaps = false
        b.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(k.bg)
        }
        val lp = LinearLayout.LayoutParams(size, size)
        lp.setMargins(dp(8), dp(8), dp(8), dp(8))
        b.layoutParams = lp
        b.setPadding(0, 0, 0, 0)
        b.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.alpha = 0.6f
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    if (editMode) editKey(k) else press(k)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.alpha = 1f
                    release()
                }
            }
            true
        }
        return b
    }

    // ---- stored codes -------------------------------------------------------

    private fun address(): Int = prefs.getInt("addr", 0x00)
    private fun command(k: Key): Int = prefs.getInt("cmd_" + k.id, k.cmd)

    private fun editKey(k: Key) {
        val cmdBox = EditText(this).apply {
            setText("%02X".format(command(k)))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        }
        val addrBox = EditText(this).apply {
            setText("%02X".format(address()))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(TextView(context).apply { text = "Command byte (hex 00–FF)" })
            addView(cmdBox)
            addView(TextView(context).apply { text = "Address byte (hex 00–FF, shared by all keys)" })
            addView(addrBox)
        }
        AlertDialog.Builder(this)
            .setTitle("Edit " + k.label)
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val c = cmdBox.text.toString().trim().toIntOrNull(16)
                val a = addrBox.text.toString().trim().toIntOrNull(16)
                if (c == null || a == null || c !in 0..255 || a !in 0..255) {
                    Toast.makeText(this, "Enter hex values from 00 to FF", Toast.LENGTH_SHORT).show()
                } else {
                    prefs.edit().putInt("cmd_" + k.id, c).putInt("addr", a).apply()
                }
            }
            .setNeutralButton("Reset key") { _, _ ->
                prefs.edit().remove("cmd_" + k.id).apply()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---- sending ------------------------------------------------------------

    private fun press(k: Key) {
        val frame = necFrame(address(), command(k))
        irHandler.post { transmit(frame) }
        if (k.repeat) {
            val r = object : Runnable {
                override fun run() {
                    irHandler.post { transmit(NEC_REPEAT) }
                    ui.postDelayed(this, 108)
                }
            }
            holdRunnable = r
            ui.postDelayed(r, 108)
        }
    }

    private fun release() {
        holdRunnable?.let { ui.removeCallbacks(it) }
        holdRunnable = null
    }

    private fun transmit(pattern: IntArray) {
        try {
            ir?.transmit(38000, pattern)
        } catch (e: Exception) {
            ui.post { Toast.makeText(this, "IR send failed: " + e.message, Toast.LENGTH_SHORT).show() }
        }
    }

    /** Standard NEC frame: 9ms/4.5ms header, 32 bits LSB first (addr, ~addr, cmd, ~cmd), stop bit. */
    private fun necFrame(addr: Int, cmd: Int): IntArray {
        val bytes = intArrayOf(addr and 0xFF, addr.inv() and 0xFF, cmd and 0xFF, cmd.inv() and 0xFF)
        val p = ArrayList<Int>()
        p.add(9000); p.add(4500)
        for (b in bytes) {
            for (i in 0..7) {
                p.add(560)
                p.add(if ((b shr i) and 1 == 1) 1690 else 560)
            }
        }
        p.add(560)
        p.add(40000) // trailing gap so the next frame starts ~108 ms after this one
        return p.toIntArray()
    }

    private val NEC_REPEAT = intArrayOf(9000, 2250, 560, 96000)
}
