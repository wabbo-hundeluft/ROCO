package com.wabbo.healthconnectcoach

/** Explicit codepoints: the bundled subset has no ligature substitution table. */
internal fun materialSymbolGlyph(name: String): String = when (name) {
    "home" -> "\uE88A"
    "settings" -> "\uE8B8"
    "explore" -> "\uE87A"
    "smart_toy" -> "\uF06C"
    "bedtime" -> "\uE1F9"
    "battery_charging_full" -> "\uE1A3"
    "fitness_center" -> "\uEB43"
    "arrow_back" -> "\uE5C4"
    "chevron_right" -> "\uE5CC"
    "expand_less" -> "\uE5CE"
    "expand_more" -> "\uE5CF"
    "sync" -> "\uE627"
    "check_circle", "celebration" -> "\uE86C"
    else -> "\uE88E"
}
