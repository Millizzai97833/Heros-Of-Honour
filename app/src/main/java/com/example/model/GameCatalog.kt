package com.example.model

data class CampaignMission(
    val id: String,
    val title: String,
    val subtitle: String,
    val theatre: WarTheatre,
    val weather: WeatherType,
    val dynamicWeatherShift: Boolean,
    val difficulty: String,
    val enemyCount: Int,
    val destructibleTargets: Int,
    val coinReward: Long,
    val xpReward: Long,
    val briefing: String
)

enum class GearSlot(val label: String) {
    WEAPON("Primary Weapon"),
    MUZZLE("Muzzle / Barrel"),
    OPTIC("Optic / Targeting"),
    ARMOR("Armor / Hull"),
    AMMO("Ammo / Payload")
}

data class GearItem(
    val id: String,
    val name: String,
    val slot: GearSlot,
    val theatre: WarTheatre?, // null = universal attachment
    val costCoins: Long,
    val damageBonus: Int,
    val fireRateBonus: Int,
    val armorBonus: Int,
    val speedBonus: Int,
    val precisionBonus: Int,
    val description: String
)

data class CombatSkill(
    val id: String,
    val name: String,
    val costSkillPoints: Long,
    val summary: String,
    val statEffect: String
)

data class SkinItem(
    val id: String,
    val name: String,
    val rarity: SkinRarity,
    val costCoins: Long,
    val primaryColorHex: Long,
    val accentColorHex: Long,
    val description: String
)

data class ShopBundle(
    val id: String,
    val title: String,
    val discountLabel: String,
    val originalCostCoins: Long,
    val bundleCostCoins: Long,
    val bonusCoins: Long,
    val includedSkinId: String,
    val includedWeaponId: String,
    val description: String,
    val drawableTag: String
)

data class DailyRewardDay(
    val day: Int,
    val label: String,
    val coins: Long,
    val skillPoints: Long,
    val bonusSkinId: String? = null
)

data class ComputedLoadoutStats(
    val damage: Float,
    val fireCooldownSec: Float,
    val maxHp: Int,
    val moveSpeed: Float,
    val spreadRad: Float,
    val range: Float
)

object GameCatalog {

    val missions: List<CampaignMission> = listOf(
        CampaignMission(
            id = "mis_tank_iron",
            title = "Operation Iron Thunder",
            subtitle = "Armored Ridge Assault",
            theatre = WarTheatre.TANK,
            weather = WeatherType.SANDSTORM,
            dynamicWeatherShift = true,
            difficulty = "Standard",
            enemyCount = 5,
            destructibleTargets = 6,
            coinReward = 450,
            xpReward = 350,
            briefing = "Break through the desert valley fortification. Demolish concrete bunkers, fuel silos, and enemy heavy armor amidst shifting sandstorms."
        ),
        CampaignMission(
            id = "mis_air_skyfury",
            title = "Operation Sky Fury",
            subtitle = "Supersonic Air Superiority",
            theatre = WarTheatre.AIRCRAFT,
            weather = WeatherType.RAIN,
            dynamicWeatherShift = true,
            difficulty = "Veteran",
            enemyCount = 6,
            destructibleTargets = 5,
            coinReward = 550,
            xpReward = 450,
            briefing = "Intercept hostile strike fighters through monsoon lightning and destroy mountain AA radar installations."
        ),
        CampaignMission(
            id = "mis_navy_abyssal",
            title = "Operation Abyssal Fleet",
            subtitle = "Blue-Water Blockade Duel",
            theatre = WarTheatre.NAVY,
            weather = WeatherType.RAIN,
            dynamicWeatherShift = false,
            difficulty = "Veteran",
            enemyCount = 5,
            destructibleTargets = 6,
            coinReward = 600,
            xpReward = 500,
            briefing = "Command the guided missile destroyer across heavy swells. Sink enemy corvettes and shatter coastal gun batteries."
        ),
        CampaignMission(
            id = "mis_inf_shadow",
            title = "Operation Shadow Strike",
            subtitle = "Spec-Ops Urban Infiltration",
            theatre = WarTheatre.INFANTRY,
            weather = WeatherType.NIGHT,
            dynamicWeatherShift = false,
            difficulty = "Elite",
            enemyCount = 7,
            destructibleTargets = 6,
            coinReward = 650,
            xpReward = 550,
            briefing = "Lead your elite army squad under cover of darkness. Breach destructible barriers and eliminate hostile patrols."
        ),
        CampaignMission(
            id = "mis_tank_citadel",
            title = "Citadel Steel Storm",
            subtitle = "Night Armored Blitz",
            theatre = WarTheatre.TANK,
            weather = WeatherType.NIGHT,
            dynamicWeatherShift = true,
            difficulty = "Commander",
            enemyCount = 7,
            destructibleTargets = 8,
            coinReward = 850,
            xpReward = 700,
            briefing = "Night-ops heavy armor clash. Use thermal optics to ambush enemy tank platoons and level the command fortress."
        ),
        CampaignMission(
            id = "mis_air_valkyrie",
            title = "Valkyrie Apex Strike",
            subtitle = "High-Altitude Fleet Escort",
            theatre = WarTheatre.AIRCRAFT,
            weather = WeatherType.CLEAR,
            dynamicWeatherShift = true,
            difficulty = "Commander",
            enemyCount = 8,
            destructibleTargets = 7,
            coinReward = 950,
            xpReward = 800,
            briefing = "Ultimate air dominance sortie. Outmaneuver ace squadrons and level fortified depot towers."
        )
    )

    val gearItems: List<GearItem> = listOf(
        // Primary Weapons
        GearItem("wpn_tank_120", "120mm L/55 Smoothbore", GearSlot.WEAPON, WarTheatre.TANK, 0, 42, 0, 0, 0, 10, "Standard NATO main battle tank cannon."),
        GearItem("wpn_tank_rail", "140mm Electromagnetic Railgun", GearSlot.WEAPON, WarTheatre.TANK, 900, 68, 12, 0, 0, 24, "Hyper-velocity tungsten penetrator cannon."),
        GearItem("wpn_air_vulcan", "M61A2 20mm Vulcan + AGM", GearSlot.WEAPON, WarTheatre.AIRCRAFT, 0, 26, 35, 0, 10, 12, "High-RPM rotary autocannon with air-to-ground pods."),
        GearItem("wpn_air_phoenix", "Twin Plasma Pod + AIM-260", GearSlot.WEAPON, WarTheatre.AIRCRAFT, 950, 45, 42, 0, 16, 22, "Next-gen interceptor payload with lethal burst."),
        GearItem("wpn_navy_406", "Twin 155mm Naval Battery", GearSlot.WEAPON, WarTheatre.NAVY, 0, 52, 8, 10, 0, 12, "Long-range stabilized naval deck guns."),
        GearItem("wpn_navy_tomahawk", "Mk-41 Rail Battery + Salvo", GearSlot.WEAPON, WarTheatre.NAVY, 1000, 78, 16, 15, 0, 20, "Devastating heavy cruiser broadside launcher."),
        GearItem("wpn_inf_m416", "M416 Tactical Carbine", GearSlot.WEAPON, WarTheatre.INFANTRY, 0, 24, 30, 0, 14, 15, "Balanced special-forces assault rifle."),
        GearItem("wpn_inf_awm", "AWM-X Anti-Materiel Rifle", GearSlot.WEAPON, WarTheatre.INFANTRY, 850, 58, 10, 0, 10, 32, "Armor-piercing precision rifle capable of breaching light cover."),

        // Muzzles
        GearItem("muz_std", "Standard Flash Suppressor", GearSlot.MUZZLE, null, 0, 0, 0, 0, 0, 5, "Factory balanced muzzle brake."),
        GearItem("muz_comp", "Heavy Tactical Compensator", GearSlot.MUZZLE, null, 350, 8, 8, 0, 0, 14, "Reduces recoil dispersion and increases muzzle velocity."),
        GearItem("muz_stealth", "Titanium Stealth Suppressor", GearSlot.MUZZLE, null, 550, 12, 10, 0, 6, 18, "Dampens signature and boosts precision."),

        // Optics
        GearItem("opt_holo", "1x Holographic Sight", GearSlot.OPTIC, null, 0, 0, 0, 0, 0, 8, "Crisp close-quarters reticle."),
        GearItem("opt_acog", "4x Ballistic ACOG", GearSlot.OPTIC, null, 400, 6, 0, 0, 0, 20, "Medium-range ballistic drop compensation."),
        GearItem("opt_thermal", "FLIR Thermal Ballistic Computer", GearSlot.OPTIC, null, 700, 14, 5, 0, 0, 30, "Cuts through sandstorms, rain, and night fog."),

        // Armor
        GearItem("arm_composite", "Chobham Composite Armor", GearSlot.ARMOR, null, 0, 0, 0, 25, 0, 0, "Multi-layered ceramic-steel protection."),
        GearItem("arm_era", "Explosive Reactive Armor (ERA)", GearSlot.ARMOR, null, 500, 0, 0, 65, -2, 0, "Neutralizes incoming kinetic & shaped-charge hits."),
        GearItem("arm_titanium", "Nano-Titanium Stealth Plating", GearSlot.ARMOR, null, 850, 5, 0, 85, 8, 0, "Ultralight aerospace armor with high durability."),

        // Ammo
        GearItem("amm_apfsds", "APFSDS Tungsten Core", GearSlot.AMMO, null, 0, 10, 0, 0, 0, 8, "High penetration armor-piercing rounds."),
        GearItem("amm_he", "HE-FRAG Demolition Shells", GearSlot.AMMO, null, 450, 22, 0, 0, 0, 4, "Expanded blast radius against bunkers & squads."),
        GearItem("amm_emp", "EMP Plasma-Tipped Rounds", GearSlot.AMMO, null, 750, 28, 10, 0, 0, 14, "Overloads enemy armor and shreds structures.")
    )

    val combatSkills: List<CombatSkill> = listOf(
        CombatSkill("skl_reload", "Reactive Autoloader", 1, "Accelerates weapon cycling speed in intense firefights.", "-18% Fire Cooldown"),
        CombatSkill("skl_ballistics", "Ballistic Trajectory AI", 1, "Tightens projectile spread across all ranges.", "+25% Accuracy"),
        CombatSkill("skl_repair", "Field Repair Drone", 2, "Boosts in-combat emergency hull/medkit restoration.", "+35% Repair Heal"),
        CombatSkill("skl_storm", "Storm Adaptation", 2, "Compensates targeting during Rain, Sandstorm & Night Ops.", "+40% Weather Clarity"),
        CombatSkill("skl_adrenaline", "Adrenaline Surge", 3, "Increases critical hit damage against armored targets.", "+20% Weapon Damage"),
        CombatSkill("skl_fortress", "Bastion Protocol", 3, "Reinforces structural hull integrity at mission start.", "+40 Max Armor HP")
    )

    val skins: List<SkinItem> = listOf(
        SkinItem("skin_olive", "Standard Olive Tactical", SkinRarity.STANDARD, 0, 0xFF4A5D3F, 0xFFD4AF37, "Classic NATO woodland olive drab finish."),
        SkinItem("skin_desert", "Desert Viper", SkinRarity.RARE, 600, 0xFFC29B61, 0xFF3E2723, "Arid sandstone camo with viper trim."),
        SkinItem("skin_arctic", "Arctic Ghost", SkinRarity.RARE, 800, 0xFFCFD8DC, 0xFF38BDF8, "Sub-zero frost digital camouflage with ice-cyan trim."),
        SkinItem("skin_obsidian", "Obsidian Spec-Ops", SkinRarity.EPIC, 1200, 0xFF1E242E, 0xFFEF4444, "Stealth matte charcoal chassis with crimson visor accents."),
        SkinItem("skin_valkyrie", "Valkyrie Carbon Gold", SkinRarity.EPIC, 1500, 0xFF141821, 0xFFF5A623, "Carbon weave tactical finish with amber gold trim."),
        SkinItem("skin_sovereign", "Golden Sovereign", SkinRarity.LEGENDARY, 2500, 0xFFD4AF37, 0xFF111827, "Legendary championship gold-plated war chassis.")
    )

    val bundles: List<ShopBundle> = listOf(
        ShopBundle(
            id = "bnd_valkyrie",
            title = "Valkyrie Strike Bundle",
            discountLabel = "45% OFF",
            originalCostCoins = 2900,
            bundleCostCoins = 1600,
            bonusCoins = 400,
            includedSkinId = "skin_valkyrie",
            includedWeaponId = "wpn_air_phoenix",
            description = "Includes Valkyrie Carbon Gold Skin, Twin Plasma Pod, + 400 Bonus Coins!",
            drawableTag = "valkyrie"
        ),
        ShopBundle(
            id = "bnd_sovereign",
            title = "Iron Sovereign Pack",
            discountLabel = "50% OFF",
            originalCostCoins = 4200,
            bundleCostCoins = 2100,
            bonusCoins = 750,
            includedSkinId = "skin_sovereign",
            includedWeaponId = "wpn_tank_rail",
            description = "Includes Legendary Golden Sovereign Skin, 140mm Railgun, + 750 Bonus Coins!",
            drawableTag = "sovereign"
        ),
        ShopBundle(
            id = "bnd_recon",
            title = "Ghost Recon Starter Kit",
            discountLabel = "35% OFF",
            originalCostCoins = 1550,
            bundleCostCoins = 990,
            bonusCoins = 250,
            includedSkinId = "skin_obsidian",
            includedWeaponId = "muz_stealth",
            description = "Includes Obsidian Spec-Ops Skin, Titanium Suppressor, + 250 Bonus Coins!",
            drawableTag = "valkyrie"
        )
    )

    val dailyRewards: List<DailyRewardDay> = listOf(
        DailyRewardDay(1, "Day 1", coins = 300, skillPoints = 0),
        DailyRewardDay(2, "Day 2", coins = 450, skillPoints = 1),
        DailyRewardDay(3, "Day 3", coins = 600, skillPoints = 0),
        DailyRewardDay(4, "Day 4", coins = 750, skillPoints = 1),
        DailyRewardDay(5, "Day 5", coins = 900, skillPoints = 1),
        DailyRewardDay(6, "Day 6", coins = 1100, skillPoints = 1, bonusSkinId = "skin_arctic"),
        DailyRewardDay(7, "Day 7", coins = 2000, skillPoints = 2, bonusSkinId = "skin_sovereign")
    )

    fun defaultWeaponForTheatre(theatre: WarTheatre): String = when (theatre) {
        WarTheatre.TANK -> "wpn_tank_120"
        WarTheatre.AIRCRAFT -> "wpn_air_vulcan"
        WarTheatre.NAVY -> "wpn_navy_406"
        WarTheatre.INFANTRY -> "wpn_inf_m416"
    }

    fun computeLoadoutStats(profile: PlayerProfile, theatre: WarTheatre): ComputedLoadoutStats {
        val weapon = gearItems.find { it.id == profile.equippedWeapon && it.theatre == theatre }
            ?: gearItems.first { it.theatre == theatre }
        val muzzle = gearItems.find { it.id == profile.equippedMuzzle } ?: gearItems.first { it.slot == GearSlot.MUZZLE }
        val optic = gearItems.find { it.id == profile.equippedOptic } ?: gearItems.first { it.slot == GearSlot.OPTIC }
        val armor = gearItems.find { it.id == profile.equippedArmor } ?: gearItems.first { it.slot == GearSlot.ARMOR }
        val ammo = gearItems.find { it.id == profile.equippedAmmo } ?: gearItems.first { it.slot == GearSlot.AMMO }

        val parts = listOf(weapon, muzzle, optic, armor, ammo)
        val dmgBonus = parts.sumOf { it.damageBonus } + if ("skl_adrenaline" in profile.unlockedSkills) 18 else 0
        val rateBonus = parts.sumOf { it.fireRateBonus }
        val armBonus = parts.sumOf { it.armorBonus } + if ("skl_fortress" in profile.unlockedSkills) 40 else 0
        val spdBonus = parts.sumOf { it.speedBonus }
        val precBonus = parts.sumOf { it.precisionBonus } + if ("skl_ballistics" in profile.unlockedSkills) 18 else 0

        val baseHp = when (theatre) {
            WarTheatre.TANK -> 220
            WarTheatre.AIRCRAFT -> 150
            WarTheatre.NAVY -> 260
            WarTheatre.INFANTRY -> 130
        }
        val baseSpeed = when (theatre) {
            WarTheatre.TANK -> 11f
            WarTheatre.AIRCRAFT -> 24f
            WarTheatre.NAVY -> 9.5f
            WarTheatre.INFANTRY -> 13.5f
        }
        val baseCooldown = when (theatre) {
            WarTheatre.TANK -> 0.85f
            WarTheatre.AIRCRAFT -> 0.16f
            WarTheatre.NAVY -> 0.95f
            WarTheatre.INFANTRY -> 0.20f
        }
        val reloadMult = if ("skl_reload" in profile.unlockedSkills) 0.82f else 1.0f

        return ComputedLoadoutStats(
            damage = (dmgBonus * 0.9f).coerceAtLeast(15f),
            fireCooldownSec = (baseCooldown * reloadMult * (1f - rateBonus * 0.004f)).coerceAtLeast(0.11f),
            maxHp = baseHp + armBonus,
            moveSpeed = (baseSpeed + spdBonus * 0.12f).coerceAtLeast(6f),
            spreadRad = (0.07f - precBonus * 0.0006f).coerceIn(0.008f, 0.09f),
            range = when (theatre) {
                WarTheatre.TANK -> 95f
                WarTheatre.AIRCRAFT -> 120f
                WarTheatre.NAVY -> 115f
                WarTheatre.INFANTRY -> 75f
            }
        )
    }
}
