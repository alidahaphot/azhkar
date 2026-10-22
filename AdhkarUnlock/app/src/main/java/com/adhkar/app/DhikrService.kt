package com.adhkar.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class DhikrService : Service() {

    companion object {
        const val ACTION_TEST = "com.adhkar.app.TEST"

        private const val CH_SERVICE = "service"
        private const val CH_ALERT = "dhikr"
        private const val ID_SERVICE = 1
        private const val ID_ALERT = 2

        // How long the card stays on screen before closing by itself.
        private const val AUTO_DISMISS_MS = 30_000L

        // Minimum time between two cards. 0 = show on every unlock.
        private const val MIN_INTERVAL_MS = 0L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var overlay: View? = null
    private var lastShown = 0L

    private val dismissRunnable = Runnable { removeOverlay() }

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_USER_PRESENT) {
                showDhikr(false)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannels()
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(unlockReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(unlockReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CH_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_dhikr)
            .setContentTitle("أذكار الصباح والمساء")
            .setContentText("يظهر ذكر عند فتح الهاتف")
            .setContentIntent(open)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID_SERVICE, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ID_SERVICE, notification)
        }

        if (intent?.action == ACTION_TEST) {
            showDhikr(true)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try {
            unregisterReceiver(unlockReceiver)
        } catch (e: Exception) {
            // already unregistered
        }
        removeOverlay()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- display

    private fun showDhikr(force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!force && lastShown != 0L && now - lastShown < MIN_INTERVAL_MS) return
        lastShown = now

        val (dhikr, morning) = Adhkar.next(this)
        if (Settings.canDrawOverlays(this)) {
            try {
                showOverlay(dhikr, morning)
                return
            } catch (e: Exception) {
                // fall through to a notification
            }
        }
        showNotification(dhikr, morning)
    }

    private fun showOverlay(d: Dhikr, morning: Boolean) {
        removeOverlay()
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val card = buildCard(d, morning)

        val lp = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.92f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.CENTER
        lp.windowAnimations = android.R.style.Animation_Toast

        wm.addView(card, lp)
        overlay = card
        handler.postDelayed(dismissRunnable, AUTO_DISMISS_MS)
    }

    private fun removeOverlay() {
        handler.removeCallbacks(dismissRunnable)
        val v = overlay ?: return
        overlay = null
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(v)
        } catch (e: Exception) {
            // view already gone
        }
    }

    private fun buildCard(d: Dhikr, morning: Boolean): View {
        val bg = if (morning) 0xFFFFF8E7.toInt() else 0xFF172036.toInt()
        val fg = if (morning) 0xFF2B2113.toInt() else 0xFFF2F4FA.toInt()
        val accent = if (morning) 0xFFB7791F.toInt() else 0xFF9DB4FF.toInt()
        val buttonColor = if (morning) 0xFFB7791F.toInt() else 0xFF3B5BDB.toInt()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.layoutDirection = View.LAYOUT_DIRECTION_RTL
        root.setPadding(dp(20), dp(16), dp(20), dp(14))
        val bgShape = GradientDrawable()
        bgShape.setColor(bg)
        bgShape.cornerRadius = dp(22).toFloat()
        bgShape.setStroke(dp(1), accent)
        root.background = bgShape
        root.elevation = dp(8).toFloat()

        val title = TextView(this)
        title.text = if (morning) "☀️  أذكار الصباح" else "🌙  أذكار المساء"
        title.setTextColor(accent)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        title.typeface = Typeface.DEFAULT_BOLD
        title.gravity = Gravity.CENTER
        root.addView(title, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val maxHeight = (resources.displayMetrics.heightPixels * 0.5f).toInt()
        val scroll = MaxHeightScrollView(this, maxHeight)
        scroll.isVerticalScrollBarEnabled = false

        val body = TextView(this)
        body.text = d.text
        body.setTextColor(fg)
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        body.gravity = Gravity.CENTER
        body.textDirection = View.TEXT_DIRECTION_RTL
        body.setLineSpacing(0f, 1.4f)
        body.setPadding(0, dp(12), 0, dp(12))
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val meta = TextView(this)
        meta.text = d.count + "  •  " + d.source
        meta.setTextColor(accent)
        meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        meta.gravity = Gravity.CENTER
        meta.textDirection = View.TEXT_DIRECTION_RTL
        root.addView(meta, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val close = TextView(this)
        close.text = "إغلاق"
        close.setTextColor(Color.WHITE)
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        close.gravity = Gravity.CENTER
        close.setPadding(dp(24), dp(10), dp(24), dp(10))
        val btnShape = GradientDrawable()
        btnShape.setColor(buttonColor)
        btnShape.cornerRadius = dp(14).toFloat()
        close.background = btnShape
        close.setOnClickListener { removeOverlay() }
        val closeLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        closeLp.gravity = Gravity.CENTER_HORIZONTAL
        closeLp.topMargin = dp(12)
        root.addView(close, closeLp)

        return root
    }

    private fun showNotification(d: Dhikr, morning: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat_dhikr)
            .setContentTitle(if (morning) "أذكار الصباح" else "أذكار المساء")
            .setContentText(d.text)
            .setStyle(Notification.BigTextStyle().bigText(d.text + "\n\n" + d.count + " • " + d.source))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter(AUTO_DISMISS_MS * 2)
            .build()
        nm.notify(ID_ALERT, notification)
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        val service = NotificationChannel(CH_SERVICE, "حالة الخدمة", NotificationManager.IMPORTANCE_MIN)
        service.setShowBadge(false)
        nm.createNotificationChannel(service)
        val alert = NotificationChannel(CH_ALERT, "الأذكار", NotificationManager.IMPORTANCE_HIGH)
        nm.createNotificationChannel(alert)
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
        ).toInt()

    // A ScrollView that never grows taller than maxHeight, so long adhkar scroll inside the card.
    private class MaxHeightScrollView(context: Context, private val maxHeight: Int) :
        ScrollView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(
                widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST)
            )
        }
    }
}
