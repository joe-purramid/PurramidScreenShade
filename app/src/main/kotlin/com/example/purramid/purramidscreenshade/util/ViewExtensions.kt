// ViewExtensions.kt
package com.example.purramid.purramidscreenshade.util

import android.app.Activity
import android.content.Context
import android.graphics.Point
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup

fun Context.dpToPx(dp: Int): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp.toFloat(),
        this.resources.displayMetrics
    ).toInt()
}

fun Context.pxToDp(px: Int): Int {
    return (px / resources.displayMetrics.density).toInt()
}

fun View.dpToPx(dp: Int): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp.toFloat(),
        resources.displayMetrics
    ).toInt()
}

fun Activity.dpToPx(dp: Int): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp.toFloat(),
        resources.displayMetrics
    ).toInt()
}

fun isPointInView(view: View, point: Point): Boolean {
    val location = IntArray(2)
    view.getLocationInWindow(location)
    return point.x >= location[0] &&
            point.x <= location[0] + view.width &&
            point.y >= location[1] &&
            point.y <= location[1] + view.height
}

fun View.cleanup() {
    // Clear click listeners
    setOnClickListener(null)
    setOnLongClickListener(null)
    setOnTouchListener(null)

    // Clear animations
    clearAnimation()
    animate().cancel()

    // Clear background
    background = null

    // Recursively cleanup child views
    if (this is ViewGroup) {
        for (i in 0 until childCount) {
            getChildAt(i)?.cleanup()
        }
        removeAllViews()
    }
}