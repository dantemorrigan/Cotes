package io.github.dantemorrigan.cotes.ui

import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import io.github.dantemorrigan.cotes.AppSettings

/** Меняется ли у настроек то, что перекрашивает весь интерфейс. */
fun AppSettings.restylesUi(other: AppSettings): Boolean =
    theme != other.theme || pureBlack != other.pureBlack || accent != other.accent || dynamicColor != other.dynamicColor

/**
 * Плавная смена темы без перерисовки интерфейса на каждом кадре: снимок окна ложится поверх,
 * тема применяется сразу, а снимок растворяется на render-потоке (view-анимация alpha).
 * Если снимок сделать не удалось, тема просто применяется.
 */
fun Activity.fadeThrough(apply: () -> Unit) {
    val root = window?.decorView as? ViewGroup
    if (root == null || root.width == 0 || root.height == 0) {
        apply()
        return
    }
    val shot = runCatching { Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888) }.getOrNull()
    if (shot == null) {
        apply()
        return
    }
    val done = runCatching {
        PixelCopy.request(window, shot, { result ->
            if (result == PixelCopy.SUCCESS) {
                val overlay = ImageView(this).apply {
                    setImageBitmap(shot)
                    scaleType = ImageView.ScaleType.FIT_XY
                    isClickable = false
                    isFocusable = false
                    importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                root.addView(overlay, FrameLayout.LayoutParams(root.width, root.height))
                apply()
                overlay.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction { root.removeView(overlay) }
                    .start()
            } else {
                apply()
            }
        }, Handler(Looper.getMainLooper()))
    }.isSuccess
    if (!done) apply()
}
