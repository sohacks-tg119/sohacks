package com.hackerman.sohacksrev2

data class ScooterModel(
    val id: String,
    val displayName: String,
    val description: String,
    val commands: ScooterCommands,
    val maxAdvancedMode: Int = 254
) {
    val supportedSpeeds: List<Int> = commands.speedCommands.keys.sorted()

    fun speedCommand(speed: Int): String? = commands.speedCommands[speed]

    fun advancedModeCommand(mode: Int): String? {
        if (mode !in 1..maxAdvancedMode) return null
        return S04ModeCommandGenerator.build(mode)
    }
}

data class ScooterCommands(
    val eco: String,
    val normal: String,
    val sport: String,
    val dev: String,
    val lock: String,
    val unlock: String,
    val speedCommands: Map<Int, String>
)

object S04ModeCommandGenerator {
    fun build(mode: Int): String {
        require(mode in 0..254) { "Mode muss zwischen 0 und 254 liegen" }
        val rawChecksum = 0xA9 + mode
        // Preserves the legacy SO4 mode table where values from 0xFF onward wrap by 0xF0.
        val checksum = if (rawChecksum >= 0xFF) rawChecksum - 0xF0 else rawChecksum
        return "D706A300%02X%02X0D0A".format(mode, checksum)
    }
}

object ScooterCommandCatalog {
    private val s04ProCommands = ScooterCommands(
        eco = "D707A45A00005",
        normal = "D706A30001AA",
        sport = "D706A30002AB",
        dev = "D706A30003AC",
        lock = "D707A0000101A9",
        unlock = "D707A0000301AB",
        speedCommands = mapOf(
            8 to "D707A900005000",
            9 to "D707A900005A0A",
            10 to "D707A900006414",
            11 to "D707A900006E1E",
            12 to "D707A900007828",
            13 to "D707A900008232",
            14 to "D707A900008C3C",
            15 to "D707A900009646",
            16 to "D707A90000A050",
            17 to "D707A90000AA5A",
            18 to "D707A90000B464",
            19 to "D707A90000BE6E",
            20 to "D707A90000C878",
            21 to "D707A90000D282",
            22 to "D707A90000DC8C",
            23 to "D707A90000E696",
            24 to "D707A90000F0A0",
            25 to "D707A90000FAAA",
            26 to "D707A9000104B5",
            27 to "D707A900010EBF",
            28 to "D707A9000118C9",
            29 to "D707A9000122D3",
            30 to "D707A900012CDD"
        )
    )

    val models: List<ScooterModel> = listOf(
        ScooterModel(
            id = "s04_pro_gen2",
            displayName = "SO4 Pro Gen 2",
            description = "Bekanntes SO4-Pro-Profil mit den vorhandenen Fahr-, Lock- und Speed-Kommandos.",
            commands = s04ProCommands
        ),
        ScooterModel(
            id = "s04_pro_gen3",
            displayName = "SO4 Pro Gen 3",
            description = "Gen-3-Profil, aktuell kompatibel mit dem bestehenden SO4-Pro-Kommandosatz.",
            commands = s04ProCommands
        )
    )

    val defaultModel: ScooterModel = models.first()

    fun findModel(id: String?): ScooterModel = models.firstOrNull { it.id == id } ?: defaultModel
}
