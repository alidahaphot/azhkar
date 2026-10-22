package com.adhkar.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var statusView: TextView
    private lateinit var toggleBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        if (Prefs.isEnabled(this)) startSvc(null)
        refresh()
    }

    private fun buildUi() {
        val pad = dp(20)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.layoutDirection = View.LAYOUT_DIRECTION_RTL
        col.setPadding(pad, pad, pad, pad)

        val title = TextView(this)
        title.text = "أذكار الصباح والمساء"
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
        title.typeface = Typeface.DEFAULT_BOLD
        title.gravity = Gravity.CENTER
        col.addView(title, lp(0, 0, 0, dp(8)))

        val sub = TextView(this)
        sub.text = "عند كل فتح للهاتف يظهر ذكر: أذكار الصباح من الساعة " +
            Adhkar.MORNING_START + " صباحًا حتى " + (Adhkar.EVENING_START - 12) +
            " عصرًا، وأذكار المساء في بقية اليوم."
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        sub.gravity = Gravity.CENTER
        col.addView(sub, lp(0, 0, 0, dp(16)))

        statusView = TextView(this)
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        statusView.setLineSpacing(0f, 1.3f)
        col.addView(statusView, lp(0, 0, 0, dp(16)))

        addButton(col, "1) منح إذن العرض فوق التطبيقات") {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
        addButton(col, "2) السماح بالإشعارات") {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
        addButton(col, "3) استثناء التطبيق من توفير البطارية") {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        }
        toggleBtn = addButton(col, "") {
            val enable = !Prefs.isEnabled(this)
            Prefs.setEnabled(this, enable)
            if (enable) {
                startSvc(null)
            } else {
                stopService(Intent(this, DhikrService::class.java))
            }
            refresh()
        }
        addButton(col, "تجربة الآن") { startSvc(DhikrService.ACTION_TEST) }

        val scroll = ScrollView(this)
        scroll.addView(col)
        setContentView(scroll)
    }

    private fun refresh() {
        val enabled = Prefs.isEnabled(this)
        val overlayOk = Settings.canDrawOverlays(this)
        val notifOk = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryOk = pm.isIgnoringBatteryOptimizations(packageName)

        statusView.text =
            mark(enabled) + " الخدمة: " + (if (enabled) "تعمل" else "متوقفة") + "\n" +
            mark(overlayOk) + " العرض فوق التطبيقات\n" +
            mark(notifOk) + " الإشعارات\n" +
            mark(batteryOk) + " الاستثناء من توفير البطارية"
        toggleBtn.text = if (enabled) "إيقاف الخدمة" else "تشغيل الخدمة"
    }

    private fun mark(ok: Boolean): String = if (ok) "✅" else "❌"

    private fun startSvc(action: String?) {
        try {
            val i = Intent(this, DhikrService::class.java)
            if (action != null) i.action = action
            startForegroundService(i)
        } catch (e: Exception) {
            // ignored
        }
    }

    private fun addButton(parent: LinearLayout, label: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        b.setOnClickListener { onClick() }
        parent.addView(b, lp(0, 0, 0, dp(8)))
        return b
    }

    private fun lp(l: Int, t: Int, r: Int, b: Int): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        p.setMargins(l, t, r, b)
        return p
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
        ).toInt()
}
