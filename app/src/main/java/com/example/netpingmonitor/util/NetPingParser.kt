//NetPingParser
package com.example.netpingmonitor.util


import com.example.netpingmonitor.model.*

/**
 * Утилитарный класс для парсинга ответов от NetPing устройств
 */
internal object NetPingParser {



    // Регулярные выражения
    private val DATA_REGEX = Regex("data=\\{([^}]+)\\};")
    private val UPTIME_REGEX = Regex("uptime_100ms=(\\d+)")
    private val FLAGS_REGEX = Regex("data_logic_flags\\s*=\\s*(\\d+)")
    private val TSTAT_STATUS_REGEX = Regex("tstat_status=\\[([^]]+)\\]")
    private val PINGER_STATUS_REGEX = Regex("pinger_status=\\[([^]]+)\\]")
    private val SETTER_STATUS_REGEX = Regex("setter_status=\\[([^]]+)\\]")
    private val TSTAT_OBJECT_REGEX = Regex("\\{t_status:(\\d+),t_val:([+-]?\\d+(?:\\.\\d+)?)\\}")
    private val JS_OBJECT_REGEX = Regex("\\{([^}]+)\\}")

    /**
     * Парсит ответ от setup_get.cgi
     */
    fun parseSetupResponse(responseBody: String): NetPingSetupData {
        try {
            val dataMatch = DATA_REGEX.find(responseBody)
            val uptimeMatch = UPTIME_REGEX.find(responseBody)

            if (dataMatch != null) {
                val dataString = dataMatch.groupValues[1]
                val data = parseJavaScriptObject(dataString)

                val uptimeMs = uptimeMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                val uptimeSeconds = uptimeMs / 10
                val uptimeFormatted = formatUptime(uptimeSeconds)

                return NetPingSetupData(
                    deviceInfo = createDeviceInfo(data, uptimeFormatted),
                    networkInterfaces = listOf(createNetworkInterface(data)),
                    systemStatus = createSystemStatus(),
                    rawData = data
                )
            } else {
                throw IllegalArgumentException("Не удалось распарсить ответ устройства")
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Ошибка парсинга данных: ${e.message}", e)
        }
    }

    /**
     * Парсит статус логики из logic_status.cgi
     */
    fun parseLogicStatusResponse(responseBody: String, tstatData: List<TstatData>): LogicStatusData {
        return try {
            val logicFlags = FLAGS_REGEX.find(responseBody)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val isLogicRunning = (logicFlags and 0x80) != 0

            val sensorValues = mutableMapOf<Int, String>()
            val sensorStatuses = mutableMapOf<Int, String>()

            parseTstatStatuses(responseBody, tstatData, sensorValues, sensorStatuses)
            val pingerStatuses = parsePingerStatuses(responseBody)
            val setterStatuses = parseSetterStatuses(responseBody)

            LogicStatusData(
                isLogicRunning = isLogicRunning,
                sensorValues = sensorValues,
                sensorStatuses = sensorStatuses,
                pingerStatuses = pingerStatuses,
                setterStatuses = setterStatuses,
                lastUpdate = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            LogicStatusData()
        }
    }

    /**
     * Парсит TSTAT данные
     */
    fun parseTstatResponse(responseBody: String): Triple<List<TstatData>, Int, Int> {
        return try {
            // Парсим termo_n_ch и rh_n_ch
            val termoNChMatch = Regex("termo_n_ch\\s*=\\s*(\\d+)").find(responseBody)
            val rhNChMatch = Regex("rh_n_ch\\s*=\\s*(\\d+)").find(responseBody)

            val termoNChannels = termoNChMatch?.groupValues?.get(1)?.toIntOrNull() ?: 8
            val rhNChannels = rhNChMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0



            // Парсим данные tstat
            val dataMatch = Regex("tstat_data=\\[([^]]+)\\]").find(responseBody)
            val tstatList = if (dataMatch != null) {
                val dataString = dataMatch.groupValues[1]
                val objects = JS_OBJECT_REGEX.findAll(dataString)

                objects.mapIndexed { index, match ->
                    val objectString = match.groupValues[1]
                    val objectData = parseJavaScriptObject(objectString)

                    TstatData(
                        id = index + 1,
                        sensorNo = objectData["sensor_no"]?.toIntOrNull() ?: 0,
                        setpoint = objectData["setpoint"]?.toIntOrNull() ?: 0,
                        hyst = objectData["hyst"]?.toIntOrNull() ?: 1
                    )
                }.toList()
            } else {
                emptyList()
            }

            Triple(tstatList, termoNChannels, rhNChannels)
        } catch (e: Exception) {
            Triple(emptyList(), 8, 0)
        }
    }

    /**
     * Парсит PINGER данные
     */
    fun parsePingerResponse(responseBody: String): List<PingerData> {
        return try {
            val dataMatch = Regex("pinger_data=\\[([^]]+)\\]").find(responseBody)
            if (dataMatch != null) {
                val dataString = dataMatch.groupValues[1]
                val objects = JS_OBJECT_REGEX.findAll(dataString)

                objects.mapIndexed { index, match ->
                    val objectString = match.groupValues[1]
                    val objectData = parseJavaScriptObject(objectString)

                    PingerData(
                        id = index + 1,
                        address = objectData["hostname"] ?: objectData["ip"] ?: "",
                        period = objectData["period"]?.toIntOrNull() ?: 30,
                        timeout = objectData["timeout"]?.toIntOrNull() ?: 1000
                    )
                }.toList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Парсит SETTER данные
     */
    fun parseSetterResponse(responseBody: String): List<SetterData> {
        return try {
            val dataMatch = Regex("setter_data=\\[([^]]+)\\]").find(responseBody)
            if (dataMatch != null) {
                val dataString = dataMatch.groupValues[1]
                val objects = JS_OBJECT_REGEX.findAll(dataString)

                objects.mapIndexed { index, match ->
                    val objectString = match.groupValues[1]
                    val objectData = parseJavaScriptObject(objectString)

                    SetterData(
                        id = index + 1,
                        name = objectData["name"] ?: "",
                        address = objectData["hostname"] ?: objectData["ip"] ?: "",
                        port = objectData["port"]?.toIntOrNull() ?: 161,
                        oid = objectData["oid"] ?: "",
                        community = objectData["community"] ?: "public",
                        valueOn = objectData["value_on"]?.toIntOrNull() ?: 1,
                        valueOff = objectData["value_off"]?.toIntOrNull() ?: 0
                    )
                }.toList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Парсит логические правила и извлекает data_logic_flags
     */
    fun parseLogicResponse(responseBody: String): List<LogicRule> {
        return try {
            // Извлекаем data_logic_flags


            // Парсим правила логики
            val dataMatch = Regex("data=\\[([^]]+)\\]").find(responseBody)
            if (dataMatch != null) {
                val dataString = dataMatch.groupValues[1]
                val objects = JS_OBJECT_REGEX.findAll(dataString)

                val rules = objects.mapIndexed { index, match ->
                    val objectString = match.groupValues[1]
                    val objectData = parseLogicObject(objectString)

                    val rule = LogicRule(
                        id = index + 1,
                        flags = objectData["flags"]?.toIntOrNull() ?: 0,
                        input = objectData["input"]?.toIntOrNull() ?: 0,
                        condition = objectData["condition"]?.toIntOrNull() ?: 0,
                        action = objectData["action"]?.toIntOrNull() ?: 0,
                        output = objectData["output"]?.toIntOrNull() ?: 0
                    )

                    rule
                }.toList()

                rules
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Приватные вспомогательные методы

    private fun parseJavaScriptObject(dataString: String): Map<String, String> {
        val result = mutableMapOf<String, String>()

        try {
            val pairs = extractKeyValuePairs(dataString)

            for (pair in pairs) {
                val colonIndex = pair.indexOf(':')
                if (colonIndex > 0) {
                    val key = pair.substring(0, colonIndex).trim()
                    val value = pair.substring(colonIndex + 1).trim()
                        .removeSurrounding("\"")
                        .removeSurrounding("'")
                    result[key] = value
                }
            }
        } catch (e: Exception) {
            // Fallback к простому парсингу
            result.putAll(parseSimpleKeyValue(dataString))
        }

        return result
    }

    private fun extractKeyValuePairs(dataString: String): List<String> {
        val pairs = mutableListOf<String>()
        var currentPair = ""
        var inQuotes = false
        var quoteChar = ' '

        for (char in dataString) {
            when {
                char == '"' || char == '\'' -> {
                    if (!inQuotes) {
                        inQuotes = true
                        quoteChar = char
                    } else if (char == quoteChar) {
                        inQuotes = false
                    }
                    currentPair += char
                }
                char == ',' && !inQuotes -> {
                    if (currentPair.isNotBlank()) {
                        pairs.add(currentPair.trim())
                        currentPair = ""
                    }
                }
                else -> {
                    currentPair += char
                }
            }
        }

        if (currentPair.isNotBlank()) {
            pairs.add(currentPair.trim())
        }

        return pairs
    }

    private fun parseSimpleKeyValue(dataString: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val pairs = dataString.split(",")

        for (pair in pairs) {
            val keyValue = pair.split(":")
            if (keyValue.size >= 2) {
                val key = keyValue[0].trim()
                val value = keyValue.drop(1).joinToString(":").trim()
                    .removeSurrounding("\"")
                    .removeSurrounding("'")
                result[key] = value
            }
        }

        return result
    }

    private fun parseLogicObject(objectString: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val pairs = objectString.split(",")

        for (pair in pairs) {
            val keyValue = pair.split(":")
            if (keyValue.size >= 2) {
                val key = keyValue[0].trim()
                val value = keyValue[1].trim()
                result[key] = value
            }
        }

        return result
    }

    private fun parseTstatStatuses(
        responseBody: String,
        tstatData: List<TstatData>,
        sensorValues: MutableMap<Int, String>,
        sensorStatuses: MutableMap<Int, String>
    ) {
        val tstatStatusMatch = TSTAT_STATUS_REGEX.find(responseBody)
        if (tstatStatusMatch != null) {
            val tstatArray = tstatStatusMatch.groupValues[1]

            val tstatObjects = TSTAT_OBJECT_REGEX.findAll(tstatArray)
            tstatObjects.forEachIndexed { index, match ->
                val status = match.groupValues[1].toIntOrNull() ?: 0
                val value = match.groupValues[2]

                val sensorKey = index
                sensorValues[sensorKey] = if (status == 0xfe) "-" else "$value °C"

                // Расчет статуса согласно оригинальной JavaScript логике
                sensorStatuses[sensorKey] = calculateTstatStatus(status, index, tstatData)
            }
        }
    }

    private fun calculateTstatStatus(status: Int, sensorIndex: Int, tstatData: List<TstatData>): String {
        return when (status) {
            0xfe -> "сбой датчика"
            0 -> {
                val tstat = tstatData.find { it.sensorNo == sensorIndex }
                if (tstat != null) {
                    "ниже (порог ${tstat.setpoint + tstat.hyst})"
                } else {
                    "ниже порога"
                }
            }
            1 -> {
                val tstat = tstatData.find { it.sensorNo == sensorIndex }
                if (tstat != null) {
                    "выше (порог ${tstat.setpoint - tstat.hyst})"
                } else {
                    "выше порога"
                }
            }
            else -> "-"
        }
    }

    private fun parsePingerStatuses(responseBody: String): Map<Int, String> {
        val pingerStatuses = mutableMapOf<Int, String>()
        val pingerStatusMatch = PINGER_STATUS_REGEX.find(responseBody)

        if (pingerStatusMatch != null) {
            val pingerArray = pingerStatusMatch.groupValues[1]

            val pingerValues = pingerArray.split(",")
            pingerValues.forEachIndexed { index, statusStr ->
                val status = statusStr.trim().toIntOrNull() ?: 255
                val pingerKey = 48 + index
                pingerStatuses[pingerKey] = PingerData.getStatusText(status)
            }
        }

        return pingerStatuses
    }

    private fun parseSetterStatuses(responseBody: String): Map<Int, String> {
        val setterStatuses = mutableMapOf<Int, String>()
        val setterStatusMatch = SETTER_STATUS_REGEX.find(responseBody)

        if (setterStatusMatch != null) {
            val setterArray = setterStatusMatch.groupValues[1]

            val setterValues = setterArray.split(",")
            setterValues.forEachIndexed { index, statusStr ->
                val status = statusStr.trim().toIntOrNull() ?: 255
                val setterKey = 192 + index
                setterStatuses[setterKey] = SetterData.getStatusText(status)
            }
        }

        return setterStatuses
    }

    private fun createDeviceInfo(data: Map<String, String>, uptime: String): DeviceInfo {
        return DeviceInfo(
            model = "NetPing 2/PWR-220v3/ETH",
            firmware = "v52.10.15.A-1",
            serialNumber = data["serial"] ?: "Unknown",
            uptime = uptime,
            hostname = data["hostname"] ?: "Unknown",
            location = data["location"] ?: "Unknown",
            contact = data["contact"] ?: "Unknown",
            hardwareVersion = "1.9"
        )
    }

    private fun createNetworkInterface(data: Map<String, String>): NetworkInterface {
        return NetworkInterface(
            name = "eth0",
            ipAddress = data["ip"] ?: "Unknown",
            macAddress = data["mac"] ?: "Unknown",
            isUp = true,
            speed = "100 Mbps",
            subnetMask = data["mask"] ?: "Unknown",
            gateway = data["gate"] ?: "Unknown"
        )
    }

    private fun createSystemStatus(): SystemStatus {
        return SystemStatus(
            cpuTemperature = 0.0,
            cpuLoad = 0.0,
            memoryUsage = 0.0,
            diskFree = 0L
        )
    }

    private fun formatUptime(seconds: Long): String {
        val days = seconds / 86400
        val hours = (seconds % 86400) / 3600
        val minutes = (seconds % 3600) / 60
        val remainingSeconds = seconds % 60

        return buildString {
            if (days > 0) append("${days}д ")
            if (hours > 0) append("${hours}ч ")
            if (minutes > 0) append("${minutes}м ")
            append("${remainingSeconds}с")
        }.trim()
    }
}
