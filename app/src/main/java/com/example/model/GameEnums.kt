package com.example.model

enum class WarTheatre(val displayName: String, val unitTitle: String, val shortTag: String) {
    TANK("Tank War", "M1A2 Titan MBT", "ARMOR"),
    AIRCRAFT("Aircraft War", "F-22 Raptor Strike", "AIR"),
    NAVY("Navy War", "USS Vanguard Destroyer", "NAVAL"),
    INFANTRY("Army Soldiers War", "Ghost Spec-Ops Squad", "INFANTRY");

    companion object {
        fun safeValueOf(raw: String?): WarTheatre =
            entries.firstOrNull { it.name == raw } ?: TANK
    }
}

enum class WeatherType(
    val displayName: String,
    val tacticalEffect: String,
    val fogDensity: Float
) {
    CLEAR("Clear Sky", "Max visibility & precision", 0.004f),
    RAIN("Monsoon Storm", "Heavy rain & lightning, -15% radar", 0.011f),
    SANDSTORM("Desert Sandstorm", "Amber dust haze, -30% range", 0.018f),
    NIGHT("Night Ops", "Thermal tracers & dark stealth", 0.013f);

    companion object {
        fun safeValueOf(raw: String?): WeatherType =
            entries.firstOrNull { it.name == raw } ?: CLEAR
    }
}

enum class GraphicsQuality(
    val label: String,
    val detailHint: String,
    val particleMultiplier: Float,
    val shadowDetail: Boolean,
    val renderScale: Float,
    val targetFps: Int
) {
    LOW("Smooth", "30 FPS Battery Saver", 0.35f, false, 0.75f, 30),
    MEDIUM("Balanced", "45 FPS Tactical Clarity", 0.65f, true, 0.85f, 45),
    HIGH("HD", "60 FPS Crisp Lighting", 1.0f, true, 1.0f, 60),
    ULTRA("Ultra HD", "60 FPS PUBG-Grade Shaders & FX", 1.5f, true, 1.0f, 60);

    companion object {
        fun safeValueOf(raw: String?): GraphicsQuality =
            entries.firstOrNull { it.name == raw } ?: HIGH
    }
}

enum class SkinRarity(val label: String) {
    STANDARD("Standard"),
    RARE("Rare"),
    EPIC("Epic"),
    LEGENDARY("Legendary")
}
