package com.ridego.app.calculator

/**
 * Where the banner sits on screen.
 *
 * Nine presets plus [CUSTOM]. Dragging the banner switches to CUSTOM, so a
 * preset is never silently overwritten by a nudge: the driver either picks a
 * corner and it stays there across reboots and rotations, or places it by hand
 * and RideGo remembers those exact pixels.
 */
enum class OverlayAnchor(val label: String) {
    TOP_LEFT("Sus stânga"),
    TOP_CENTER("Sus centru"),
    TOP_RIGHT("Sus dreapta"),
    CENTER_LEFT("Mijloc stânga"),
    CENTER("Centru"),
    CENTER_RIGHT("Mijloc dreapta"),
    BOTTOM_LEFT("Jos stânga"),
    BOTTOM_CENTER("Jos centru"),
    BOTTOM_RIGHT("Jos dreapta"),

    /** Wherever the driver last dragged it. */
    CUSTOM("Poziție proprie");

    val isPreset: Boolean get() = this != CUSTOM
}
