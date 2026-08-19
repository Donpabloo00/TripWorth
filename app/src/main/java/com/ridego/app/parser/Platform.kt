package com.ridego.app.parser

enum class Platform {
    UBER,
    BOLT,
    UNKNOWN;

    val label: String
        get() = when (this) {
            UBER -> "UBER"
            BOLT -> "BOLT"
            UNKNOWN -> "NECUNOSCUT"
        }
}

/** Which platforms the driver wants analyzed. */
enum class PlatformMode {
    AUTO,
    UBER_ONLY,
    BOLT_ONLY;

    fun accepts(platform: Platform): Boolean = when (this) {
        AUTO -> true
        UBER_ONLY -> platform == Platform.UBER
        BOLT_ONLY -> platform == Platform.BOLT
    }

    val label: String
        get() = when (this) {
            AUTO -> "AUTO"
            UBER_ONLY -> "UBER"
            BOLT_ONLY -> "BOLT"
        }
}
