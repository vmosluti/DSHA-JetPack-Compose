package com.luti.dshlauncher.ui.util

/**
 * 原临时诊断输出（每次切页 / 点底栏都在主线程同步写 /sdcard/Download 或查 MediaStore），
 * 是切页卡顿的来源之一。pager 问题已结束，这里保留空实现以兼容现有调用点。
 * 需要排查时用 android.util.Log + adb logcat。
 */
object DiagLog {
    @Suppress("UNUSED_PARAMETER")
    fun log(message: String) = Unit
}
