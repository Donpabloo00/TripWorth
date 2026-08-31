package com.ridego.app.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val RO = Locale("ro", "RO")

/** Romanian decimal comma everywhere — drivers read these at a glance. */
fun Double?.ron(decimals: Int = 2): String =
    if (this == null) "—" else String.format(RO, "%,.${decimals}f", this)

fun Double?.km(): String = if (this == null) "—" else String.format(RO, "%.1f km", this)

fun Int?.min(): String = if (this == null) "—" else "$this min"

fun Double?.kmh(): String = if (this == null) "—" else String.format(RO, "%.0f km/h", this)

fun formatTime(timestamp: Long): String =
    SimpleDateFormat("dd MMM, HH:mm", RO).format(Date(timestamp))
