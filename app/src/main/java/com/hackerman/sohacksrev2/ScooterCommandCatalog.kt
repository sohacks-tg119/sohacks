package com.hackerman.sohacksrev2

data class ScooterBleProfile(
    val serviceUuid: String,
    val writeUuid: String,
    val notifyUuid: String,
    val writeAesKey: String? = null,
    val readAesKey: String? = null,
    val aesKey: String? = null
) {
    val txAesKey: String? get() = writeAesKey ?: aesKey
    val rxAesKey: String? get() = readAesKey ?: aesKey
}

enum class ProtocolFamily {
    LEGACY_STATUS,
    SO4_PRO,
    D7_SO4_V1,
    D7_SO4_V51_PLUS,
    DYNAMIC_D7,
    TWO_BYTE
}

val NORDIC_UART_PROFILE = ScooterBleProfile(
    serviceUuid = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E",
    writeUuid = "6E400002-B5A3-F393-E0A9-E50E24DCCA9E",
    notifyUuid = "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"
)

data class CommandRuntimeState(
    val dynamicSecret: Int? = null,
    val sessionToken: String? = null
)

data class ScooterCommandSpec(
    val fixedHex: String? = null,
    val dynamicD7: DynamicD7Command? = null,
    val requiresSessionToken: Boolean = false
) {
    fun resolve(runtimeState: CommandRuntimeState = CommandRuntimeState()): String? {
        fixedHex?.let { hex ->
            return if (requiresSessionToken) {
                runtimeState.sessionToken?.let { token -> hex.replace(TOKEN_PLACEHOLDER, token) }
            } else {
                hex
            }
        }

        val secret = runtimeState.dynamicSecret ?: return null
        return dynamicD7?.resolve(secret)
    }

    val requiresDynamicSecret: Boolean get() = dynamicD7 != null

    companion object {
        private const val TOKEN_PLACEHOLDER = "<TOKEN>"

        fun fixed(hex: String): ScooterCommandSpec = ScooterCommandSpec(fixedHex = hex)

        fun tokenTemplate(hex: String): ScooterCommandSpec {
            return ScooterCommandSpec(fixedHex = hex, requiresSessionToken = true)
        }

        fun dynamic(command: Int, vararg payload: Int): ScooterCommandSpec {
            return ScooterCommandSpec(dynamicD7 = DynamicD7Command(command, payload.toList()))
        }
    }
}

data class DynamicD7Command(
    val command: Int,
    val payload: List<Int>
) {
    fun resolve(secret: Int): String {
        return D7CommandGenerator.build(
            command = command,
            prefix = secret,
            payload = payload
        )
    }
}

data class NamedScooterCommand(
    val id: String,
    val label: String,
    val command: ScooterCommandSpec
)

data class ScooterModel(
    val id: String,
    val displayName: String,
    val description: String,
    val commands: ScooterCommands,
    val bleProfile: ScooterBleProfile = NORDIC_UART_PROFILE,
    val protocolFamily: ProtocolFamily = ProtocolFamily.D7_SO4_V51_PLUS,
    val maxAdvancedMode: Int = 0,
    val extraCommands: List<NamedScooterCommand> = emptyList()
) {
    val supportedSpeeds: List<Int> = commands.speedCommands.keys.sorted()

    fun speedCommand(speed: Int, runtimeState: CommandRuntimeState = CommandRuntimeState()): String? {
        return commands.speedCommands[speed]?.resolve(runtimeState)
    }

    fun commandNeedsDynamicSecret(command: ScooterCommandSpec?): Boolean = command?.requiresDynamicSecret == true

    fun commandNeedsSessionToken(command: ScooterCommandSpec?): Boolean = command?.requiresSessionToken == true

    fun advancedModeCommand(mode: Int): String? {
        if (mode !in 1..maxAdvancedMode) return null
        return S04ModeCommandGenerator.build(mode)
    }

}

data class ScooterCommands(
    val eco: ScooterCommandSpec? = null,
    val normal: ScooterCommandSpec? = null,
    val sport: ScooterCommandSpec? = null,
    val dev: ScooterCommandSpec? = null,
    val lock: ScooterCommandSpec? = null,
    val unlock: ScooterCommandSpec? = null,
    val speedCommands: Map<Int, ScooterCommandSpec> = emptyMap(),
    val startupCommand: ScooterCommandSpec? = null,
    val sessionTokenCommand: ScooterCommandSpec? = null,
    val realtimeStartCommand: ScooterCommandSpec? = null,
    val realtimeStopCommand: ScooterCommandSpec? = null
)

object D7CommandGenerator {
    fun build(
        command: Int,
        prefix: Int,
        payload: List<Int>
    ): String {
        require(command in 0x00..0xFF) { "Command muss ein Byte sein" }
        require(prefix in 0x00..0xFF) { "Prefix muss ein Byte sein" }
        require(payload.all { it in 0x00..0xFF }) { "Payload darf nur Bytes enthalten" }

        val length = 5 + payload.size
        val checksum = (length + command + prefix + payload.sum()) and 0xFF
        return buildString {
            append("D7")
            append("%02X%02X%02X".format(length, command, prefix))
            payload.forEach { append("%02X".format(it)) }
            append("%02X".format(checksum))
        }
    }
}

object S04ModeCommandGenerator {
    fun build(mode: Int): String {
        require(mode in 0..254) { "Mode muss zwischen 0 und 254 liegen" }
        return D7CommandGenerator.build(
            command = 0xA3,
            prefix = 0x00,
            payload = listOf(mode)
        )
    }
}

object ScooterCommandCatalog {
    private const val MAX_ADVANCED_MODE = 254

    private val staticSpeedCommands = (1..65).associateWith { speed ->
        val tenths = speed * 10
        ScooterCommandSpec.fixed(
            D7CommandGenerator.build(
                command = 0xA9,
                prefix = 0x00,
                payload = listOf((tenths shr 8) and 0xFF, tenths and 0xFF)
            )
        )
    }

    private val dynamicSpeedCommands = (1..65).associateWith { speed ->
        val tenths = speed * 10
        ScooterCommandSpec.dynamic(0xA9, (tenths shr 8) and 0xFF, tenths and 0xFF)
    }

    private val s04ProSpeedCommands = staticSpeedCommands

    private val s04ProCommands = ScooterCommands(
        eco = ScooterCommandSpec.fixed("D707A45A000005"),
        normal = ScooterCommandSpec.fixed("D706A30001AA"),
        sport = ScooterCommandSpec.fixed("D706A30002AB"),
        dev = ScooterCommandSpec.fixed("D706A30003AC"),
        lock = ScooterCommandSpec.fixed("D707A0000101A9"),
        unlock = ScooterCommandSpec.fixed("D707A0000301AB"),
        speedCommands = s04ProSpeedCommands
    )

    private val s04Gen2Commands = ScooterCommands(
        eco = ScooterCommandSpec.fixed("D706A30000A9"),
        normal = ScooterCommandSpec.fixed("D706A30001AA"),
        sport = ScooterCommandSpec.fixed("D706A30002AB"),
        dev = ScooterCommandSpec.fixed("D706A30003AC"),
        lock = ScooterCommandSpec.fixed("D706A00001A7"),
        unlock = ScooterCommandSpec.fixed("D706A00000A6"),
        speedCommands = staticSpeedCommands,
        startupCommand = ScooterCommandSpec.fixed("D706A60001AD")
    )

    private val s04Commands = ScooterCommands(
        eco = ScooterCommandSpec.fixed("D707A0000100A8"),
        normal = ScooterCommandSpec.fixed("D707A0000300AA"),
        sport = ScooterCommandSpec.fixed("D707A0000500AC"),
        dev = ScooterCommandSpec.fixed("D706A30003AC"),
        lock = ScooterCommandSpec.fixed("D707A0000301AB"),
        unlock = ScooterCommandSpec.fixed("D707A0000300AA"),
        speedCommands = staticSpeedCommands
    )

    private val s04T51Commands = ScooterCommands(
        eco = ScooterCommandSpec.fixed("D707A0000000A7"),
        normal = ScooterCommandSpec.fixed("D707A0000200A9"),
        sport = ScooterCommandSpec.fixed("D707A0000400AB"),
        dev = ScooterCommandSpec.fixed("D706A30003AC"),
        lock = ScooterCommandSpec.fixed("D707A0000201AA"),
        unlock = ScooterCommandSpec.fixed("D707A0000200A9"),
        speedCommands = staticSpeedCommands,
        startupCommand = ScooterCommandSpec.fixed("D707A60001AE")
    )

    private val s04T52Commands = ScooterCommands(
        eco = ScooterCommandSpec.fixed("D706A30000A9"),
        normal = ScooterCommandSpec.fixed("D706A30001AA"),
        sport = ScooterCommandSpec.fixed("D706A30002AB"),
        dev = ScooterCommandSpec.fixed("D706A30003AC"),
        lock = ScooterCommandSpec.fixed("D706A00001A7"),
        unlock = ScooterCommandSpec.fixed("D706A00000A6"),
        speedCommands = staticSpeedCommands,
        startupCommand = ScooterCommandSpec.fixed("D706A60001AD")
    )

    private val dynamicD7Commands = ScooterCommands(
        eco = ScooterCommandSpec.dynamic(0xA4, 0x00, 0x00),
        normal = ScooterCommandSpec.dynamic(0xA4, 0x00, 0x01),
        sport = ScooterCommandSpec.dynamic(0xA4, 0x00, 0x02),
        dev = ScooterCommandSpec.dynamic(0xA4, 0x00, 0x02),
        lock = ScooterCommandSpec.dynamic(0xA2, 0x00, 0x01),
        unlock = ScooterCommandSpec.dynamic(0xA2, 0x00, 0x00),
        speedCommands = dynamicSpeedCommands,
        startupCommand = ScooterCommandSpec.fixed(
            D7CommandGenerator.build(
                command = 0xA0,
                prefix = 0x00,
                payload = listOf(0x00, 0x01)
            )
        )
    )

    private val so1A0Commands = ScooterCommands(
        eco = ScooterCommandSpec.dynamic(0xA0, 0x01, 0x00),
        normal = ScooterCommandSpec.dynamic(0xA0, 0x03, 0x00),
        sport = ScooterCommandSpec.dynamic(0xA0, 0x05, 0x00),
        dev = ScooterCommandSpec.dynamic(0xA0, 0x05, 0x00),
        lock = ScooterCommandSpec.dynamic(0xA0, 0x03, 0x01),
        unlock = ScooterCommandSpec.dynamic(0xA0, 0x03, 0x00),
        speedCommands = dynamicSpeedCommands,
        startupCommand = ScooterCommandSpec.fixed(
            D7CommandGenerator.build(
                command = 0xA0,
                prefix = 0x00,
                payload = listOf(0x00, 0x01)
            )
        )
    )

    private val so2AirCommands = ScooterCommands(
        eco = ScooterCommandSpec.dynamic(0xA0, 0x01, 0x00),
        normal = ScooterCommandSpec.dynamic(0xA0, 0x03, 0x00),
        sport = ScooterCommandSpec.dynamic(0xA0, 0x05, 0x00),
        dev = ScooterCommandSpec.dynamic(0xA0, 0x05, 0x00),
        lock = ScooterCommandSpec.dynamic(0xA0, 0x03, 0x01),
        unlock = ScooterCommandSpec.dynamic(0xA0, 0x03, 0x00),
        speedCommands = dynamicSpeedCommands,
        startupCommand = ScooterCommandSpec.fixed(
            D7CommandGenerator.build(
                command = 0xA6,
                prefix = 0x00,
                payload = listOf(0x00, 0x01)
            )
        )
    )

    private val tokenCommands = ScooterCommands(
        lock = ScooterCommandSpec.tokenTemplate("050C0101<TOKEN>"),
        unlock = ScooterCommandSpec.tokenTemplate("050106303030303030<TOKEN>"),
        sessionTokenCommand = ScooterCommandSpec.fixed("060101"),
        realtimeStartCommand = ScooterCommandSpec.tokenTemplate("0546020101<TOKEN>"),
        realtimeStopCommand = ScooterCommandSpec.tokenTemplate("0546020001<TOKEN>")
    )

    private val aesV52Profile = NORDIC_UART_PROFILE.copy(
        writeAesKey = "30572F52364B3F473050415811632D2B"
    )

    private val twoByteNordicProfile = NORDIC_UART_PROFILE.copy(
        aesKey = "20572F52364B3F473050415811632D2B"
    )

    private val so6Profile = ScooterBleProfile(
        serviceUuid = "60000001-0000-1000-8000-00805F9B34FB",
        writeUuid = "60000003-0000-1000-8000-00805F9B34FB",
        notifyUuid = "60000002-0000-1000-8000-00805F9B34FB",
        aesKey = "20572F52364B3F473050415811632D2B"
    )

    private val twoByteExtraCommands = listOf(
        NamedScooterCommand("lock_state", "Lock-State lesen", ScooterCommandSpec.tokenTemplate("050E0101<TOKEN>")),
        NamedScooterCommand("cycling_data", "Cycling-Data lesen", ScooterCommandSpec.tokenTemplate("05480101<TOKEN>")),
        NamedScooterCommand("light_state", "Lichtstatus lesen", ScooterCommandSpec.tokenTemplate("05450101<TOKEN>")),
        NamedScooterCommand("light_on", "Licht an", ScooterCommandSpec.tokenTemplate("05470101<TOKEN>")),
        NamedScooterCommand("light_off", "Licht aus", ScooterCommandSpec.tokenTemplate("05470100<TOKEN>")),
        NamedScooterCommand("speed_limit_read", "Speed-Limit lesen", ScooterCommandSpec.tokenTemplate("05490101<TOKEN>"))
    )

    val models: List<ScooterModel> = listOf(
        ScooterModel(
            id = "s04_pro_gen2",
            displayName = "SO4 Pro Gen 2",
            description = "SO4 Pro Gen 2 unverschluesseltes Nordic-UART-Profil.",
            commands = s04Gen2Commands.copy(startupCommand = null),
            bleProfile = NORDIC_UART_PROFILE,
            protocolFamily = ProtocolFamily.SO4_PRO,
            maxAdvancedMode = MAX_ADVANCED_MODE
        ),
        ScooterModel(
            id = "s04_pro_gen3",
            displayName = "SO4 Pro Gen 3",
            description = "SO4 Gen3 / SO4T-v5.2 Profil.",
            commands = s04Gen2Commands,
            bleProfile = aesV52Profile,
            protocolFamily = ProtocolFamily.D7_SO4_V51_PLUS,
            maxAdvancedMode = MAX_ADVANCED_MODE
        ),
        ScooterModel(
            id = "s04_pro_legacy_test",
            displayName = "SO4 Pro Legacy Test",
            description = "Altes gemischtes SO4-Pro-Profil zum Vergleichen, falls dein Scooter nur darauf reagiert.",
            commands = s04ProCommands,
            protocolFamily = ProtocolFamily.LEGACY_STATUS,
            maxAdvancedMode = MAX_ADVANCED_MODE
        ),
        ScooterModel(
            id = "so4",
            displayName = "SO4",
            description = "SO4Tv1.0 D7-Profil mit Legacy-Telemetrie-Indizes.",
            commands = s04Commands,
            protocolFamily = ProtocolFamily.D7_SO4_V1,
            maxAdvancedMode = MAX_ADVANCED_MODE
        ),
        ScooterModel(
            id = "so4_5_1",
            displayName = "SO4 5.1",
            description = "SO4T-v5.1-Profil mit Connection-Status-, Lock- und Speed-Kommandos.",
            commands = s04T51Commands,
            protocolFamily = ProtocolFamily.D7_SO4_V51_PLUS,
            maxAdvancedMode = MAX_ADVANCED_MODE
        ),
        ScooterModel(
            id = "so4_5_2",
            displayName = "SO4 5.2",
            description = "SO4T-v5.2-Profil aus dem Command-Maker.",
            commands = s04T52Commands,
            bleProfile = aesV52Profile,
            protocolFamily = ProtocolFamily.D7_SO4_V51_PLUS,
            maxAdvancedMode = MAX_ADVANCED_MODE
        ),
        ScooterModel(
            id = "somytier",
            displayName = "SO myTier",
            description = "myTier-Profil mit SO4T-v5.2-Kommandostruktur und Transport-AES-Hinweis.",
            commands = s04T52Commands,
            bleProfile = aesV52Profile,
            protocolFamily = ProtocolFamily.D7_SO4_V51_PLUS
        ),
        ScooterModel(
            id = "so1",
            displayName = "SO1",
            description = "D7-Profil mit Live-Secret aus Realtime-Daten.",
            commands = dynamicD7Commands,
            protocolFamily = ProtocolFamily.DYNAMIC_D7
        ),
        ScooterModel(
            id = "so1_a0_test",
            displayName = "SO1 A0 Test",
            description = "Alternatives SO1-Testprofil mit A0-basierten Fahr- und Lock-Kommandos.",
            commands = so1A0Commands,
            protocolFamily = ProtocolFamily.DYNAMIC_D7
        ),
        ScooterModel(
            id = "so2_air",
            displayName = "SO2 Air",
            description = "D7 Profil",
            commands = dynamicD7Commands,
            protocolFamily = ProtocolFamily.DYNAMIC_D7
        ),
        ScooterModel(
            id = "so2_air_a0_test",
            displayName = "SO2 Air A0 Test",
            description = "Alternatives SO2-Air-Testprofil mit A0/A6-basierten Kommandos.",
            commands = so2AirCommands,
            protocolFamily = ProtocolFamily.DYNAMIC_D7
        ),
        ScooterModel(
            id = "so3",
            displayName = "SO3",
            description = "D7 Profil",
            commands = dynamicD7Commands,
            protocolFamily = ProtocolFamily.DYNAMIC_D7,
            maxAdvancedMode = MAX_ADVANCED_MODE
        ),
        ScooterModel(
            id = "so4ul",
            displayName = "SO4 UL",
            description = "Tokenbasiertes Zwei-Byte-Protokoll über Nordic UART.",
            commands = tokenCommands,
            bleProfile = twoByteNordicProfile,
            protocolFamily = ProtocolFamily.TWO_BYTE,
            extraCommands = twoByteExtraCommands
        ),
        ScooterModel(
            id = "so6",
            displayName = "SO6",
            description = "Tokenbasiertes Zwei-Byte-Protokoll mit SO6-Service-UUIDs.",
            commands = tokenCommands,
            bleProfile = so6Profile,
            protocolFamily = ProtocolFamily.TWO_BYTE,
            extraCommands = twoByteExtraCommands
        )
    )

    val defaultModel: ScooterModel = models.first { it.id == "s04_pro_gen2" }

    fun findModel(id: String?): ScooterModel = models.firstOrNull { it.id == id } ?: defaultModel
}
