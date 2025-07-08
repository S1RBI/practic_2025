//DataModels
package com.example.netpingmonitor.model

// Сохраненное устройство
data class SavedDevice(
    val id: String,
    val name: String,
    val ipAddress: String,
    val username: String,
    val password: String,
    val lastConnected: Long = 0L,
    val isConnected: Boolean = false
)

// UI состояние приложения
data class NetPingUiState(
    // Состояние подключения
    val isLoading: Boolean = false,
    val currentDeviceId: String? = null,
    val errorMessage: String? = null,

    // Управление устройствами
    val savedDevices: List<SavedDevice> = emptyList(),
    val deviceDataMap: Map<String, NetPingDeviceData> = emptyMap(),
    val isDeviceManagerExpanded: Boolean = false,

    // Навигация
    val selectedTab: Int = 0,
    val selectedLogicTab: Int = 0,

    // Форма добавления нового устройства (без поля названия)
    val newDeviceIpAddress: String = "",
    val newDeviceUsername: String = "",
    val newDevicePassword: String = "",
    val isAddingDevice: Boolean = false
) {
    val currentDevice: SavedDevice?
        get() = savedDevices.find { it.id == currentDeviceId }

    val currentDeviceData: NetPingDeviceData?
        get() = currentDeviceId?.let { deviceDataMap[it] }

    val isConnected: Boolean
        get() = currentDevice?.isConnected == true
}

// Основная модель данных устройства NetPing
data class NetPingDeviceData(
    val deviceInfo: DeviceInfo? = null,
    val networkInterfaces: List<NetworkInterface> = emptyList(),
    val systemStatus: SystemStatus? = null,
    val logicRules: List<LogicRule> = emptyList(),
    val tstatData: List<TstatData> = emptyList(),
    val pingerData: List<PingerData> = emptyList(),
    val setterData: List<SetterData> = emptyList(),
    val logicStatus: LogicStatusData = LogicStatusData(),
    val termoNChannels: Int = 8, // Количество температурных каналов из termo_n_ch
    val rhNChannels: Int = 0, // Количество датчиков влажности из rh_n_ch
    val relayData: List<RelayData> = emptyList(), // Данные реле
    val relayStatus: RelayStatusData = RelayStatusData() // Статус реле
)

// Информация об устройстве
data class DeviceInfo(
    val model: String,
    val firmware: String,
    val serialNumber: String,
    val uptime: String,
    val hostname: String,
    val location: String,
    val contact: String,
    val hardwareVersion: String
)

// Сетевой интерфейс
data class NetworkInterface(
    val name: String,
    val ipAddress: String,
    val macAddress: String = "",
    val isUp: Boolean,
    val speed: String = "Unknown",
    val subnetMask: String = "Unknown",
    val gateway: String = "Unknown"
)

// Системный статус
data class SystemStatus(
    val cpuTemperature: Double,
    val cpuLoad: Double,
    val memoryUsage: Double,
    val diskFree: Long
)

// Правило логики
data class LogicRule(
    val id: Int,
    val flags: Int,
    val input: Int,
    val condition: Int,
    val action: Int,
    val output: Int
) {
    // Вычисляемые свойства на основе flags
    val isEnabled: Boolean get() = (flags and 1) != 0
    val isTrigger: Boolean get() = (flags and 2) != 0

    fun getInputDescription(deviceModel: String = ""): String {
        val isUniPing = deviceModel.contains("UniPing", ignoreCase = true)

        return when (input) {
            1 -> "RESET"
            16 -> "IO 1"
            17 -> "IO 2"
            18 -> "IO 3"
            19 -> "IO 4"
            32 -> "TSTAT 1"
            33 -> "TSTAT 2"
            48 -> "PINGER 1"
            49 -> "PINGER 2"
            64 -> if (isUniPing) "CS ALARM" else "Неизвестный вход ($input)"
            80 -> if (isUniPing) "CS FAIL" else "Неизвестный вход ($input)"
            96 -> if (isUniPing) "CS NORM" else "Неизвестный вход ($input)"
            112 -> if (isUniPing) "SMOKE 1" else "Неизвестный вход ($input)"
            113 -> if (isUniPing) "SMOKE 2" else "Неизвестный вход ($input)"
            114 -> if (isUniPing) "SMOKE 3" else "Неизвестный вход ($input)"
            115 -> if (isUniPing) "SMOKE 4" else "Неизвестный вход ($input)"
            128 -> if (isUniPing) "Неизвестный вход ($input)" else "AC PWR"
            else -> "Неизвестный вход ($input)"
        }
    }

    fun getConditionDescription(deviceModel: String = ""): String {
        val isUniPing = deviceModel.contains("UniPing", ignoreCase = true)

        return when {
            input in TSTAT_INPUTS -> when (condition) {
                0 -> if (isUniPing) "ниже порога" else "ниже заданной T"
                1 -> if (isUniPing) "выше порога" else "выше заданной T"
                else -> "Неизвестное условие ($condition)"
            }
            input in PINGER_INPUTS -> when (condition) {
                0 -> "молчит"
                1 -> "отвечает"
                else -> "Неизвестное условие ($condition)"
            }
            input in IO_INPUTS -> when (condition) {
                0 -> "= лог. 0"
                1 -> "= лог. 1"
                else -> "Неизвестное условие ($condition)"
            }
            input == 128 && !isUniPing -> when (condition) { // AC PWR для NetPing
                0 -> "отсутствует"
                1 -> "присутствует"
                else -> "Неизвестное условие ($condition)"
            }
            input in SMOKE_INPUTS && isUniPing -> when (condition) {
                0 -> "= норма"
                1 -> "= тревога"
                4 -> "= выкл"
                5 -> "= отказ"
                else -> "Неизвестное условие ($condition)"
            }
            input in CS_INPUTS && isUniPing -> when (condition) {
                0 -> "= лог. 0"
                1 -> "= лог. 1"
                else -> "Неизвестное условие ($condition)"
            }
            else -> "Неизвестное условие ($condition)"
        }
    }

    fun getActionDescription(): String = if (isTrigger) {
        when (action) {
            0 -> "выключить"
            1 -> "включить"
            2 -> "переключить"
            else -> "Неизвестное действие ($action)"
        }
    } else {
        when (action) {
            0 -> "держать выкл"
            1 -> "держать вкл"
            else -> "Неизвестное действие ($action)"
        }
    }

    fun getOutputDescription(deviceModel: String = ""): String {
        val isUniPing = deviceModel.contains("UniPing", ignoreCase = true)

        return when (output) {
            160 -> "IO 1"
            161 -> "IO 2"
            162 -> "IO 3"
            163 -> "IO 4"
            176 -> "RELAY 1"
            177 -> if (isUniPing) "Неизвестный выход ($output)" else "RELAY 2"
            192 -> "SNMP 1"
            193 -> "SNMP 2"
            208 -> "IR 1"
            209 -> "IR 2"
            210 -> "IR 3"
            211 -> "IR 4"
            224 -> if (isUniPing) "CS PWR" else "Неизвестный выход ($output)"
            240 -> if (isUniPing) "SMOKE RST" else "Неизвестный выход ($output)"
            else -> "Неизвестный выход ($output)"
        }
    }

    // Backward compatibility - используйте функции напрямую

    companion object {
        private val TSTAT_INPUTS = listOf(32, 33)
        private val PINGER_INPUTS = listOf(48, 49)
        private val IO_INPUTS = listOf(1, 16, 17, 18, 19)
        private val SMOKE_INPUTS = listOf(112, 113, 114, 115)
        private val CS_INPUTS = listOf(64, 80, 96)

        fun getInputOptions(deviceModel: String = ""): List<Pair<Int, String>> {
            val isUniPing = deviceModel.contains("UniPing", ignoreCase = true)

            val commonInputs = listOf(
                1 to "RESET",
                16 to "IO 1",
                17 to "IO 2",
                18 to "IO 3",
                19 to "IO 4",
                32 to "TSTAT 1",
                33 to "TSTAT 2",
                48 to "PINGER 1",
                49 to "PINGER 2"
            )

            return if (isUniPing) {
                commonInputs + listOf(
                    64 to "CS ALARM",
                    80 to "CS FAIL",
                    96 to "CS NORM",
                    112 to "SMOKE 1",
                    113 to "SMOKE 2",
                    114 to "SMOKE 3",
                    115 to "SMOKE 4"
                )
            } else {
                commonInputs + listOf(
                    128 to "AC PWR"
                )
            }
        }

        fun getRuleTypeOptions(): List<Pair<Int, String>> = listOf(
            0 to "Пока",
            1 to "Если"
        )

        fun getConditionOptions(inputValue: Int, deviceModel: String = ""): List<Pair<Int, String>> {
            val isUniPing = deviceModel.contains("UniPing", ignoreCase = true)

            return when {
                inputValue in TSTAT_INPUTS -> if (isUniPing) {
                    listOf(
                        0 to "ниже порога",
                        1 to "выше порога"
                    )
                } else {
                    listOf(
                        0 to "ниже заданной T",
                        1 to "выше заданной T"
                    )
                }
                inputValue in PINGER_INPUTS -> listOf(
                    0 to "молчит",
                    1 to "отвечает"
                )
                inputValue in IO_INPUTS || inputValue in CS_INPUTS -> listOf(
                    0 to "= лог. 0",
                    1 to "= лог. 1"
                )
                inputValue == 128 && !isUniPing -> listOf( // AC PWR для NetPing
                    0 to "отсутствует",
                    1 to "присутствует"
                )
                inputValue in SMOKE_INPUTS && isUniPing -> listOf(
                    0 to "= норма",
                    1 to "= тревога",
                    4 to "= выкл",
                    5 to "= отказ"
                )
                else -> listOf(
                    0 to "Условие 0",
                    1 to "Условие 1"
                )
            }
        }

        fun getActionOptions(isTrigger: Boolean): List<Pair<Int, String>> = if (isTrigger) {
            listOf(
                0 to "выключить",
                1 to "включить",
                2 to "переключить"
            )
        } else {
            listOf(
                0 to "держать выкл",
                1 to "держать вкл"
            )
        }

        fun getOutputOptions(deviceModel: String = ""): List<Pair<Int, String>> {
            val isUniPing = deviceModel.contains("UniPing", ignoreCase = true)

            val commonOutputs = listOf(
                160 to "IO 1",
                161 to "IO 2",
                162 to "IO 3",
                163 to "IO 4",
                176 to "RELAY 1",
                192 to "SNMP 1",
                193 to "SNMP 2",
                208 to "IR 1",
                209 to "IR 2",
                210 to "IR 3",
                211 to "IR 4"
            )

            return if (isUniPing) {
                commonOutputs + listOf(
                    224 to "CS PWR",
                    240 to "SMOKE RST"
                )
            } else {
                commonOutputs + listOf(
                    177 to "RELAY 2"
                )
            }
        }
    }
}

// Термостат
data class TstatData(
    val id: Int,
    val sensorNo: Int,
    val setpoint: Int,
    val hyst: Int
) {
    fun getCurrentValue(logicStatus: LogicStatusData): String {
        return logicStatus.sensorValues[sensorNo] ?: "-"
    }

    fun getCurrentStatus(logicStatus: LogicStatusData): String {
        return logicStatus.sensorStatuses[sensorNo] ?: "-"
    }
    companion object {
        fun getSensorOptions(termoNChannels: Int = 8, rhNChannels: Int = 0, deviceModel: String = ""): List<Pair<Int, String>> {
            val options = mutableListOf<Pair<Int, String>>()

            if (deviceModel.contains("UniPing", ignoreCase = true)) {
                // Логика для UniPing: сложная система с датчиками влажности
                // Добавляем датчики температуры (от 0 до termo_n_ch-1)
                for (j in 0 until termoNChannels) {
                    options.add(j to "Д.температуры ${j + 1}")
                }

                // Добавляем датчики влажности (влажность)
                for (j in 0 until rhNChannels) {
                    val sensorIndex = termoNChannels + j * 2
                    options.add(sensorIndex to "Д.влажности ${j + 1} - влажн.")
                }

                // Добавляем датчики влажности (температура)
                for (j in 0 until rhNChannels) {
                    val sensorIndex = termoNChannels + j * 2 + 1
                    options.add(sensorIndex to "Д.влажности ${j + 1} - темп.")
                }
            } else {
                // Логика для NetPing: простая система согласно коду
                // Добавляем датчики температуры (от 1 до termo_n_ch)
                for (j in 0 until termoNChannels) {
                    options.add(j to "${j + 1}")
                }

                // Добавляем датчик влажности последним
                options.add(termoNChannels to "Отн.влажность")
            }

            return options
        }
    }
}

// Пингер
data class PingerData(
    val id: Int,
    val address: String,
    val period: Int,
    val timeout: Int
) {
    fun getCurrentStatus(logicStatus: LogicStatusData): String {
        val pingerKey = 48 + (id - 1)
        return logicStatus.pingerStatuses[pingerKey] ?: "-"
    }

    companion object {
        fun getStatusText(status: Int): String = when (status) {
            0 -> "молчит"
            1 -> "отвечает"
            2 -> "отвечает со сбоями"
            254, 255 -> "-"
            else -> "неизвестно ($status)"
        }
    }
}

// SNMP Setter
data class SetterData(
    val id: Int,
    val name: String,
    val address: String,
    val port: Int,
    val oid: String,
    val community: String,
    val valueOn: Int,
    val valueOff: Int
) {
    fun getCurrentStatus(logicStatus: LogicStatusData): String {
        val setterKey = 192 + (id - 1)
        return logicStatus.setterStatuses[setterKey] ?: "-"
    }

    companion object {
        fun getStatusText(status: Int): String = when (status) {
            0 -> "OK"
            1 -> "слишком большое"
            2 -> "нет такого OID"
            3 -> "неправильное значение"
            4 -> "запись запрещена"
            5 -> "ошибка"
            6 -> "ожидание ответа"
            7 -> "таймаут"
            8 -> "ошибка DNS"
            9 -> "отсутствует адрес"
            255 -> "-"
            else -> "неизвестно ($status)"
        }
    }
}

// Статус логики
data class LogicStatusData(
    val isLogicRunning: Boolean = false,
    val sensorValues: Map<Int, String> = emptyMap(),
    val sensorStatuses: Map<Int, String> = emptyMap(),
    val pingerStatuses: Map<Int, String> = emptyMap(),
    val setterStatuses: Map<Int, String> = emptyMap(),
    val lastUpdate: Long = 0L
)

// Действия управления логикой
enum class LogicAction {
    STOP,   // Остановить логику
    START,  // Запустить логику
    RESET   // Сброс логики
}

// Реле
data class RelayData(
    val id: Int,
    val name: String,
    val mode: Int,
    val resetTime: Int = 15,
    val relayState: Boolean = false
) {
    fun getModeDescription(): String = when (mode) {
        0 -> "Ручное Выкл"
        1 -> "Ручное Вкл"
        2 -> "Сторож"
        3 -> "Расписание"
        4 -> "Расп+Сторож"
        5 -> "Логика"
        else -> "Неизвестный режим ($mode)"
    }

    companion object {
        fun getModeOptions(): List<Pair<Int, String>> = listOf(
            0 to "Ручное Выкл",
            1 to "Ручное Вкл",
            2 to "Сторож",
            3 to "Расписание",
            4 to "Расп+Сторож",
            5 to "Логика"
        )
    }
}

// Статус реле
data class RelayStatusData(
    val relayStates: Map<Int, Boolean> = emptyMap(), // Состояния реле по индексам
    val lastUpdate: Long = 0L
)

// Действия управления реле
enum class RelayAction {
    FORCE_ON,   // Кратковременное включение (15с)
    FORCE_OFF   // Кратковременное отключение (15с)
}

// Вспомогательный класс для setup_get.cgi
internal data class NetPingSetupData(
    val deviceInfo: DeviceInfo,
    val networkInterfaces: List<NetworkInterface>,
    val systemStatus: SystemStatus,
    val rawData: Map<String, String>
)
