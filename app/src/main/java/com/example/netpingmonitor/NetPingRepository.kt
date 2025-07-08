//NetPingRepository
package com.example.netpingmonitor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.example.netpingmonitor.util.NetPingParser
import com.example.netpingmonitor.model.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class NetPingRepository @Inject constructor( private val okHttpClient: OkHttpClient ) { companion object {
    private const val DEVNAME_ENDPOINT = "/devname_menu.cgi"
    private const val SETUP_ENDPOINT = "/setup_get.cgi"
    private const val LOGIC_ENDPOINT = "/logic_get.cgi"
    private const val LOGIC_STATUS_ENDPOINT = "/logic_status.cgi"
    private const val LOGIC_SET_ENDPOINT = "/logic_set.cgi"
    private const val LOGIC_RUN_ENDPOINT = "/logic_run.cgi"
    private const val TSTAT_ENDPOINT = "/tstat_get.cgi"
    private const val TSTAT_SET_ENDPOINT = "/tstat_set.cgi"
    private const val PINGER_ENDPOINT = "/pinger_get.cgi"
    private const val PINGER_SET_ENDPOINT = "/pinger_set.cgi"
    private const val SETTER_ENDPOINT = "/setter_get.cgi"
    private const val SETTER_SET_ENDPOINT = "/setter_set.cgi"
    private const val SETTER_TEST_ENDPOINT = "/setter_test.cgi"
}

    private var currentDeviceUrl: String? = null
    private var currentCredentials: String? = null
    private var currentTstatData: List<TstatData> = emptyList()
    private var cachedLogicRunning: Boolean = false

    suspend fun connect(address: String, username: String, password: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                currentDeviceUrl = "http://$address"
                currentCredentials = Credentials.basic(username, password)

                val isConnected = testConnection()
                if (isConnected) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("Устройство не отвечает"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getAllData(): NetPingDeviceData = withContext(Dispatchers.IO) {
        requireConnection()

        try {
            val deviceInfoData = fetchDeviceInfo()
            val setupData = fetchSetupData()
            val logicRules = fetchLogicRules()
            val (tstatData, termoNChannels, rhNChannels) = fetchTstatData()
            currentTstatData = tstatData

            val uptimeFromSetup = fetchUptimeFromSetup()
            val completeDeviceInfo = deviceInfoData.copy(
                serialNumber = setupData.rawData["serial"] ?: deviceInfoData.serialNumber,
                uptime = uptimeFromSetup,
                contact = setupData.rawData["contact"] ?: deviceInfoData.contact
            )

            val sensorStatus = fetchLogicStatus()
            val completeLogicStatus = sensorStatus.copy(isLogicRunning = cachedLogicRunning)

            NetPingDeviceData(
                deviceInfo = completeDeviceInfo,
                networkInterfaces = setupData.networkInterfaces,
                systemStatus = setupData.systemStatus,
                logicRules = logicRules,
                tstatData = tstatData,
                pingerData = fetchPingerData(),
                setterData = fetchSetterData(),
                logicStatus = completeLogicStatus,
                termoNChannels = termoNChannels,
                rhNChannels = rhNChannels
            )
        } catch (e: Exception) {
            throw Exception("Ошибка получения данных: ${e.message}")
        }
    }

    suspend fun getLogicStatus(): LogicStatusData = withContext(Dispatchers.IO) {
        try {
            val sensorStatus = fetchLogicStatus()
            sensorStatus.copy(isLogicRunning = cachedLogicRunning)
        } catch (@Suppress("UNUSED_PARAMETER") e: Exception) {
            LogicStatusData(isLogicRunning = cachedLogicRunning)
        }
    }

    suspend fun saveLogicRules(logicRules: List<LogicRule>): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                requireConnection()

                val validLogicRules = logicRules.take(8)
                val packedData = packLogicRules(validLogicRules)

                // Пробуем form-data отправку
                try {
                    val formData = FormBody.Builder().add("data", packedData).build()
                    val formResponse = okHttpClient.newCall(createPostRequest(LOGIC_SET_ENDPOINT, formData)).execute()

                    if (formResponse.isSuccessful) {
                        val responseBody = formResponse.body?.string() ?: ""
                        if (!responseBody.contains("error", ignoreCase = true)) {
                            delay(1500)
                            val currentLogicRules = fetchLogicRules()
                            val hasExpectedData = validLogicRules.any { expected ->
                                currentLogicRules.any { current ->
                                    expected.flags == current.flags &&
                                            expected.input == current.input &&
                                            expected.condition == current.condition &&
                                            expected.action == current.action &&
                                            expected.output == current.output
                                }
                            }
                            if (hasExpectedData) {
                                return@withContext Result.success(true)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Fallback к бинарной отправке
                }

                val binaryPayload = hexStringToByteArray(packedData)
                val mediaType = "application/octet-stream".toMediaType()
                val requestBody = binaryPayload.toRequestBody(mediaType)
                val request = createBinaryPostRequest(LOGIC_SET_ENDPOINT, requestBody)

                val response = try {
                    okHttpClient.newCall(request).execute()
                } catch (e: java.io.IOException) {
                    if (e.message?.contains("unexpected end of stream") == true ||
                        e is java.io.EOFException ||
                        e.message?.contains("EOFException") == true) {
                        delay(1500)
                        try {
                            val currentLogicRules = fetchLogicRules()
                            val hasExpectedData = validLogicRules.any { expected ->
                                currentLogicRules.any { current ->
                                    expected.flags == current.flags &&
                                            expected.input == current.input &&
                                            expected.condition == current.condition &&
                                            expected.action == current.action &&
                                            expected.output == current.output
                                }
                            }
                            if (hasExpectedData) {
                                return@withContext Result.success(true)
                            } else {
                                return@withContext Result.failure(Exception("Данные не сохранились"))
                            }
                        } catch (e: Exception) {
                            return@withContext Result.success(true)
                        }
                    } else {
                        throw e
                    }
                }

                val responseBody = try {
                    response.body?.string() ?: ""
                } catch (e: Exception) {
                    ""
                }

                if (response.isSuccessful) {
                    if (responseBody.contains("<html", ignoreCase = true) || responseBody.contains("<!DOCTYPE", ignoreCase = true)) {
                        delay(1500)
                        try {
                            val currentLogicRules = fetchLogicRules()
                            val hasExpectedData = validLogicRules.any { expected ->
                                currentLogicRules.any { current ->
                                    expected.flags == current.flags &&
                                            expected.input == current.input &&
                                            expected.condition == current.condition &&
                                            expected.action == current.action &&
                                            expected.output == current.output
                                }
                            }

                            if (hasExpectedData) {
                                Result.success(true)
                            } else {
                                Result.failure(Exception("LOGIC правила не сохранились"))
                            }
                        } catch (e: Exception) {
                            Result.success(true)
                        }
                    } else {
                        Result.success(true)
                    }
                } else {
                    Result.failure(Exception("HTTP ${response.code}: $responseBody"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun controlLogic(action: LogicAction): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                requireConnection()

                val actionValue = when (action) {
                    LogicAction.STOP -> 0
                    LogicAction.START -> 1
                    LogicAction.RESET -> 2
                }

                val url = "${currentDeviceUrl}${LOGIC_RUN_ENDPOINT}?${actionValue}"
                val request = createGetRequest(url)
                val response = okHttpClient.newCall(request).execute()

                val responseBody = try {
                    response.body?.string() ?: ""
                } catch (e: Exception) {
                    ""
                }

                if (response.isSuccessful) {
                    val currentLogicStatus = when (responseBody.trim()) {
                        "1" -> true
                        "0" -> false
                        else -> {
                            when (action) {
                                LogicAction.START -> true
                                LogicAction.STOP, LogicAction.RESET -> false
                            }
                        }
                    }

                    cachedLogicRunning = currentLogicStatus
                    Result.success(currentLogicStatus)
                } else {
                    Result.failure(Exception("HTTP ${response.code}: $responseBody"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun saveTstatData(tstatData: List<TstatData>): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                requireConnection()

                val validTstatData = tstatData.take(2)
                val binaryPayload = buildTstatPayload(validTstatData)
                val hexString = binaryPayload.joinToString("") { "%02x".format(it.toInt() and 0xFF) }

                // Пробуем form-data отправку
                try {
                    val formData = FormBody.Builder().add("data", hexString).build()
                    val formResponse = okHttpClient.newCall(createPostRequest(TSTAT_SET_ENDPOINT, formData)).execute()

                    if (formResponse.isSuccessful) {
                        val responseBody = formResponse.body?.string() ?: ""
                        if (!responseBody.contains("error", ignoreCase = true)) {
                            delay(1500)
                            val currentTstatData = fetchTstatData()
                            val hasExpectedData = validTstatData.any { expected ->
                                currentTstatData.first.any { current ->
                                    expected.sensorNo == current.sensorNo &&
                                            expected.setpoint == current.setpoint &&
                                            expected.hyst == current.hyst
                                }
                            }
                            if (hasExpectedData) {
                                return@withContext Result.success(true)
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Fallback к бинарной отправке
                }

                val mediaType = "application/octet-stream".toMediaType()
                val requestBody = binaryPayload.toRequestBody(mediaType)
                val request = createBinaryPostRequest(TSTAT_SET_ENDPOINT, requestBody)

                val response = try {
                    okHttpClient.newCall(request).execute()
                } catch (e: java.io.IOException) {
                    if (e.message?.contains("unexpected end of stream") == true) {
                        delay(1500)
                        try {
                            val currentTstatData = fetchTstatData()
                            val hasExpectedData = validTstatData.any { expected ->
                                currentTstatData.first.any { current ->
                                    expected.sensorNo == current.sensorNo &&
                                            expected.setpoint == current.setpoint &&
                                            expected.hyst == current.hyst
                                }
                            }
                            if (hasExpectedData) {
                                return@withContext Result.success(true)
                            } else {
                                return@withContext Result.failure(Exception("Данные не сохранились"))
                            }
                        } catch (_: Exception) {
                            return@withContext Result.success(true)
                        }
                    } else {
                        throw e
                    }
                }

                val responseBody = try {
                    response.body?.string() ?: ""
                } catch (e: Exception) {
                    ""
                }

                if (response.isSuccessful) {
                    if (responseBody.contains("<html", ignoreCase = true) || responseBody.contains("<!DOCTYPE", ignoreCase = true)) {
                        delay(1500)
                        try {
                            val currentTstatData = fetchTstatData()
                            val hasExpectedData = validTstatData.any { expected ->
                                currentTstatData.first.any { current ->
                                    expected.sensorNo == current.sensorNo &&
                                            expected.setpoint == current.setpoint &&
                                            expected.hyst == current.hyst
                                }
                            }

                            if (hasExpectedData) {
                                Result.success(true)
                            } else {
                                Result.failure(Exception("TSTAT данные не сохранились"))
                            }
                        } catch (_: Exception) {
                            Result.success(true)
                        }
                    } else {
                        Result.success(true)
                    }
                } else {
                    Result.failure(Exception("HTTP ${response.code}: $responseBody"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun savePingerData(pingerData: List<PingerData>): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                requireConnection()

                val validPingerData = pingerData.take(2)
                val binaryPayload = buildPingerPayload(validPingerData)
                val hexString = binaryPayload.joinToString("") { "%02x".format(it.toInt() and 0xFF) }

                // Пробуем form-data отправку
                try {
                    val formData = FormBody.Builder().add("data", hexString).build()
                    val formResponse = okHttpClient.newCall(createPostRequest(PINGER_SET_ENDPOINT, formData)).execute()

                    if (formResponse.isSuccessful) {
                        val responseBody = formResponse.body?.string() ?: ""
                        if (!responseBody.contains("error", ignoreCase = true)) {
                            delay(1500)
                            val currentPingerData = fetchPingerData()
                            val hasExpectedData = currentPingerData.any { pinger ->
                                validPingerData.any { expected ->
                                    expected.address.equals(pinger.address, ignoreCase = true) &&
                                            expected.period == pinger.period &&
                                            expected.timeout == pinger.timeout
                                }
                            }
                            if (hasExpectedData) {
                                return@withContext Result.success(true)
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Fallback к бинарной отправке
                }

                val mediaType = "application/octet-stream".toMediaType()
                val requestBody = binaryPayload.toRequestBody(mediaType)
                val request = createBinaryPostRequest(PINGER_SET_ENDPOINT, requestBody)

                val response = try {
                    okHttpClient.newCall(request).execute()
                } catch (e: java.io.IOException) {
                    if (e.message?.contains("unexpected end of stream") == true) {
                        delay(1500)
                        try {
                            val currentPingerData = fetchPingerData()
                            val hasExpectedData = currentPingerData.any { pinger ->
                                validPingerData.any { expected ->
                                    expected.address.equals(pinger.address, ignoreCase = true) &&
                                            expected.period == pinger.period &&
                                            expected.timeout == pinger.timeout
                                }
                            }
                            if (hasExpectedData) {
                                return@withContext Result.success(true)
                            } else {
                                return@withContext Result.failure(Exception("Данные не сохранились"))
                            }
                        } catch (_: Exception) {
                            return@withContext Result.success(true)
                        }
                    } else {
                        throw e
                    }
                }

                val responseBody = try {
                    response.body?.string() ?: ""
                } catch (e: Exception) {
                    ""
                }

                if (response.isSuccessful) {
                    if (responseBody.contains("<html", ignoreCase = true) || responseBody.contains("<!DOCTYPE", ignoreCase = true)) {
                        delay(1500)
                        try {
                            val currentPingerData = fetchPingerData()
                            val hasExpectedData = currentPingerData.any { pinger ->
                                validPingerData.any { expected ->
                                    expected.address.equals(pinger.address, ignoreCase = true) &&
                                            expected.period == pinger.period &&
                                            expected.timeout == pinger.timeout
                                }
                            }

                            if (hasExpectedData) {
                                Result.success(true)
                            } else {
                                Result.failure(Exception("Данные не сохранились"))
                            }
                        } catch (_: Exception) {
                            Result.success(true)
                        }
                    } else {
                        Result.success(true)
                    }
                } else {
                    Result.failure(Exception("HTTP ${response.code}: $responseBody"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun saveSetterData(setterData: List<SetterData>): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                requireConnection()

                val validSetterData = setterData.take(2)
                val binaryPayload = buildSetterPayload(validSetterData)
                val hexString = binaryPayload.joinToString("") { "%02x".format(it.toInt() and 0xFF) }

                // Пробуем form-data отправку
                try {
                    val formData = FormBody.Builder().add("data", hexString).build()
                    val formResponse = okHttpClient.newCall(createPostRequest(SETTER_SET_ENDPOINT, formData)).execute()

                    if (formResponse.isSuccessful) {
                        val responseBody = formResponse.body?.string() ?: ""
                        if (!responseBody.contains("error", ignoreCase = true)) {
                            delay(1500)
                            val currentSetterData = fetchSetterData()
                            val hasExpectedData = validSetterData.any { expected ->
                                currentSetterData.any { current ->
                                    expected.name.equals(current.name, ignoreCase = true) ||
                                            expected.address.equals(current.address, ignoreCase = true) ||
                                            expected.port == current.port
                                }
                            }
                            if (hasExpectedData) {
                                return@withContext Result.success(true)
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Fallback к бинарной отправке
                }

                val mediaType = "application/octet-stream".toMediaType()
                val requestBody = binaryPayload.toRequestBody(mediaType)
                val request = createBinaryPostRequest(SETTER_SET_ENDPOINT, requestBody)

                val response = try {
                    okHttpClient.newCall(request).execute()
                } catch (e: java.io.IOException) {
                    if (e.message?.contains("unexpected end of stream") == true) {
                        delay(1500)
                        try {
                            val currentSetterData = fetchSetterData()
                            val hasExpectedData = validSetterData.any { expected ->
                                currentSetterData.any { current ->
                                    expected.name.equals(current.name, ignoreCase = true) ||
                                            expected.address.equals(current.address, ignoreCase = true) ||
                                            expected.port == current.port
                                }
                            }
                            if (hasExpectedData) {
                                return@withContext Result.success(true)
                            } else {
                                return@withContext Result.failure(Exception("Данные не сохранились"))
                            }
                        } catch (_: Exception) {
                            return@withContext Result.success(true)
                        }
                    } else {
                        throw e
                    }
                }

                val responseBody = try {
                    response.body?.string() ?: ""
                } catch (e: Exception) {
                    ""
                }

                if (response.isSuccessful) {
                    if (responseBody.contains("<html", ignoreCase = true) || responseBody.contains("<!DOCTYPE", ignoreCase = true)) {
                        delay(1500)
                        try {
                            val currentSetterData = fetchSetterData()
                            val hasExpectedData = validSetterData.any { expected ->
                                currentSetterData.any { current ->
                                    expected.name.equals(current.name, ignoreCase = true) ||
                                            expected.address.equals(current.address, ignoreCase = true) ||
                                            expected.port == current.port
                                }
                            }

                            if (hasExpectedData) {
                                Result.success(true)
                            } else {
                                Result.failure(Exception("Setter данные не сохранились"))
                            }
                        } catch (_: Exception) {
                            Result.success(true)
                        }
                    } else {
                        Result.success(true)
                    }
                } else {
                    Result.failure(Exception("HTTP ${response.code}: $responseBody"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun testSetter(setterIndex: Int, turnOn: Boolean): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                requireConnection()

                val channelNumber = setterIndex + 1
                val value = if (turnOn) 1 else 0
                val url = "${currentDeviceUrl}${SETTER_TEST_ENDPOINT}?ch${channelNumber}=${value}"

                val request = createGetRequest(url)
                val response = okHttpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("HTTP ${response.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun testConnection(): Boolean {
        return try {
            val request = createGetRequest(DEVNAME_ENDPOINT)
            val response = okHttpClient.newCall(request).execute()
            response.isSuccessful
        } catch (@Suppress("UNUSED_PARAMETER") e: Exception) {
            false
        }
    }

    private fun fetchDeviceInfo(): DeviceInfo {
        val request = createGetRequest(DEVNAME_ENDPOINT)
        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}")
        }

        val responseBody = response.body?.string() ?: ""
        return parseDeviceInfoResponse(responseBody)
    }

    private fun fetchSetupData(): NetPingSetupData {
        val request = createGetRequest(SETUP_ENDPOINT)
        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}")
        }

        val responseBody = response.body?.string() ?: ""
        return NetPingParser.parseSetupResponse(responseBody)
    }

    private fun fetchLogicRules(): List<LogicRule> {
        return try {
            val request = createGetRequest(LOGIC_ENDPOINT)
            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""

                val logicFlagsMatch = Regex("data_logic_flags\\s*=\\s*(\\d+)").find(responseBody)
                val logicFlags = logicFlagsMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val isLogicRunning = (logicFlags and 0x80) != 0

                cachedLogicRunning = isLogicRunning
                parseLogicResponse(responseBody)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun fetchTstatData(): Triple<List<TstatData>, Int, Int> {
        return try {
            val request = createGetRequest(TSTAT_ENDPOINT)
            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                NetPingParser.parseTstatResponse(responseBody)
            } else {
                Triple(emptyList(), 8, 0)
            }
        } catch (e: Exception) {
            Triple(emptyList(), 8, 0)
        }
    }

    private fun fetchPingerData(): List<PingerData> {
        return try {
            val request = createGetRequest(PINGER_ENDPOINT)
            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                parsePingerResponse(responseBody)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun fetchSetterData(): List<SetterData> {
        return try {
            val request = createGetRequest(SETTER_ENDPOINT)
            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                parseSetterResponse(responseBody)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun fetchLogicStatus(): LogicStatusData {
        try {
            val request = createGetRequest(LOGIC_STATUS_ENDPOINT)
            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                val logicStatus = NetPingParser.parseLogicStatusResponse(responseBody, currentTstatData)
                return logicStatus
            }
        } catch (@Suppress("UNUSED_PARAMETER") e: Exception) {
            // Молча обрабатываем ошибки
        }

        return LogicStatusData()
    }

    private fun requireConnection() {
        currentDeviceUrl ?: throw IllegalStateException("Не подключен к устройству")
        currentCredentials ?: throw IllegalStateException("Не установлены учетные данные")
    }

    private fun createGetRequest(endpoint: String): Request {
        val url = if (endpoint.startsWith("http")) endpoint else "${currentDeviceUrl}${endpoint}"
        return Request.Builder()
            .url(url)
            .header("Authorization", currentCredentials!!)
            .header("Accept-Encoding", "identity")
            .header("Accept", "text/html,text/plain,*/*")
            .header("User-Agent", "NetPingMonitor/1.0")
            .build()
    }

    private fun createPostRequest(endpoint: String, body: RequestBody): Request {
        return Request.Builder()
            .url("${currentDeviceUrl}${endpoint}")
            .header("Authorization", currentCredentials!!)
            .header("Accept-Encoding", "identity")
            .header("Accept", "*/*")
            .header("User-Agent", "Mozilla/5.0 (compatible; NetPingMonitor/1.0)")
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .header("Connection", "close")
            .header("Expect", "")
            .post(body)
            .build()
    }

    private fun createBinaryPostRequest(endpoint: String, body: RequestBody): Request {
        return Request.Builder()
            .url("${currentDeviceUrl}${endpoint}")
            .header("Authorization", currentCredentials!!)
            .header("Accept-Encoding", "identity")
            .header("Accept", "*/*")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .header("Connection", "close")
            .header("Expect", "")
            .removeHeader("Accept-Encoding")
            .post(body)
            .build()
    }

    private fun hexStringToByteArray(hexString: String): ByteArray {
        val len = hexString.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hexString[i], 16) shl 4) + Character.digit(hexString[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun parseDeviceInfoResponse(responseBody: String): DeviceInfo {
        try {
            val devnameRegex = Regex("var devname='([^']+)';")
            val fwverRegex = Regex("var fwver='([^']+)';")
            val hwverRegex = Regex("var hwver=(\\d+);")
            val sysNameRegex = Regex("var sys_name=\"([^\"]*)\";")
            val sysLocationRegex = Regex("var sys_location=\"([^\"]*)\";")

            val devnameMatch = devnameRegex.find(responseBody)
            val fwverMatch = fwverRegex.find(responseBody)
            val hwverMatch = hwverRegex.find(responseBody)
            val sysNameMatch = sysNameRegex.find(responseBody)
            val sysLocationMatch = sysLocationRegex.find(responseBody)

            val model = devnameMatch?.groupValues?.get(1) ?: "Unknown"
            val firmware = fwverMatch?.groupValues?.get(1) ?: "Unknown"
            val hwver = hwverMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val hardwareVersion = "1.$hwver"
            val hostname = sysNameMatch?.groupValues?.get(1) ?: ""
            val location = sysLocationMatch?.groupValues?.get(1) ?: ""

            return DeviceInfo(
                model = model,
                firmware = firmware,
                serialNumber = "Unknown",
                uptime = "Unknown",
                hostname = hostname,
                location = location,
                contact = "Unknown",
                hardwareVersion = hardwareVersion
            )

        } catch (e: Exception) {
            return DeviceInfo(
                model = "Unknown",
                firmware = "Unknown",
                serialNumber = "Unknown",
                uptime = "Unknown",
                hostname = "Unknown",
                location = "Unknown",
                contact = "Unknown",
                hardwareVersion = "Unknown"
            )
        }
    }

    private fun fetchUptimeFromSetup(): String {
        return try {
            val request = createGetRequest(SETUP_ENDPOINT)
            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                val uptimeMatch = Regex("uptime_100ms=(\\d+)").find(responseBody)
                val uptimeMs = uptimeMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                val uptimeSeconds = uptimeMs / 10
                formatUptime(uptimeSeconds)
            } else {
                "Unknown"
            }
        } catch (e: Exception) {
            "Unknown"
        }
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

    private fun parsePingerResponse(responseBody: String): List<PingerData> {
        return NetPingParser.parsePingerResponse(responseBody)
    }

    private fun parseSetterResponse(responseBody: String): List<SetterData> {
        return NetPingParser.parseSetterResponse(responseBody)
    }

    private fun parseLogicResponse(responseBody: String): List<LogicRule> {
        val rules = NetPingParser.parseLogicResponse(responseBody)

        val logicFlagsMatch = Regex("data_logic_flags\\s*=\\s*(\\d+)").find(responseBody)
        if (logicFlagsMatch != null) {
            val logicFlags = logicFlagsMatch.groupValues[1].toIntOrNull() ?: 0
            val isLogicRunning = (logicFlags and 0x80) != 0
            cachedLogicRunning = isLogicRunning
        }

        return rules
    }

    private fun packLogicRules(rules: List<LogicRule>): String {
        val generator = LogicRulePayloadGenerator()
        return generator.generatePayload(rules)
    }

    private fun buildTstatPayload(tstatData: List<TstatData>): ByteArray {
        val generator = ThermostatPayloadGenerator()
        val thermostatConfigs = tstatData.map { tstat ->
            ThermostatConfig(
                sensorNo = tstat.sensorNo,
                setpoint = tstat.setpoint,
                hyst = tstat.hyst,
                isRH = false
            )
        }
        val configsToUse = thermostatConfigs.take(2)

        val hexString = generator.generatePayload(configsToUse)
        return hexStringToByteArray(hexString)
    }

    private fun buildSetterPayload(setterData: List<SetterData>): ByteArray {
        val generator = SetterPayloadGenerator()
        val setterConfigs = setterData.map { setter ->
            SetterConfig(
                name = setter.name.ifBlank { "" },
                address = setter.address.ifBlank { "" },
                port = setter.port.takeIf { it > 0 } ?: 161,
                oid = setter.oid.ifBlank { "1.3.6.1.4.1.25728.8200.1.1.2.1.1" },
                community = setter.community.ifBlank { "public" },
                valueOn = setter.valueOn.takeIf { it != 0 } ?: 1,
                valueOff = setter.valueOff.takeIf { it != 0 } ?: 0
            )
        }
        val configsToUse = setterConfigs.take(2)

        val hexString = generator.generatePayload(configsToUse)
        return hexStringToByteArray(hexString)
    }

    private fun buildPingerPayload(pingerData: List<PingerData>): ByteArray {
        val generator = PingerPayloadGenerator()
        val pingerConfigs = pingerData.map { pinger ->
            PingerConfig(pinger.address, pinger.period, pinger.timeout)
        }.filter { generator.validatePingerConfig(it) }

        val configsToUse = if (pingerConfigs.isEmpty()) {
            listOf(PingerConfig("", 15, 1000), PingerConfig("", 15, 1000))
        } else {
            pingerConfigs.take(2)
        }

        val hexString = generator.generatePayload(configsToUse)
        return hexStringToByteArray(hexString)
    }

    data class PingerConfig(
        val address: String,
        val period: Int,
        val timeout: Int
    )

    data class ThermostatConfig(
        val sensorNo: Int,
        val setpoint: Int,
        val hyst: Int,
        val isRH: Boolean = false
    )

    data class SetterConfig(
        val name: String,
        val address: String,
        val port: Int,
        val oid: String,
        val community: String,
        val valueOn: Int,
        val valueOff: Int
    )

    class ThermostatPayloadGenerator {
        companion object {
            private const val THERMOSTAT_BLOCK_SIZE = 8
            private const val TOTAL_PAYLOAD_SIZE = 16
            private const val SENSOR_NO_OFFSET = 0
            private const val SETPOINT_OFFSET = 1
            private const val PARAM1_OFFSET = 2
            private const val PARAM2_OFFSET = 3
        }

        fun generatePayload(thermostats: List<ThermostatConfig>): String {
            if (thermostats.isEmpty()) {
                return "".padEnd(TOTAL_PAYLOAD_SIZE * 2, '0')
            }

            val payloadBuffer = ByteArray(TOTAL_PAYLOAD_SIZE)

            thermostats.forEachIndexed { idx, cfg ->
                if (idx * THERMOSTAT_BLOCK_SIZE >= TOTAL_PAYLOAD_SIZE) {
                    return@forEachIndexed
                }

                val blockStartOffset = idx * THERMOSTAT_BLOCK_SIZE

                payloadBuffer[blockStartOffset + SENSOR_NO_OFFSET] = cfg.setpoint.toByte()
                payloadBuffer[blockStartOffset + SETPOINT_OFFSET] = cfg.hyst.toByte()
                payloadBuffer[blockStartOffset + PARAM1_OFFSET] = cfg.sensorNo.toByte()
                payloadBuffer[blockStartOffset + PARAM2_OFFSET] = 0
            }

            return payloadBuffer.joinToString("") { "%02x".format(it) }
        }
    }

    class PingerPayloadGenerator {
        companion object {
            private const val PINGER_BLOCK_SIZE = 124
            private const val FIRST_PINGER_PERIOD_OFFSET = 8
            private const val FIRST_PINGER_TIMEOUT_OFFSET = 10
            private const val FIRST_PINGER_HOSTNAME_OFFSET = 60
            private const val OTHER_PINGER_PERIOD_OFFSET = 8
            private const val OTHER_PINGER_TIMEOUT_OFFSET = 10
            private const val OTHER_PINGER_HOSTNAME_OFFSET = 60
            private const val MIN_PERIOD = 5
            private const val MAX_PERIOD = 900
            private const val MIN_TIMEOUT = 100
            private const val MAX_TIMEOUT = 10000
        }

        fun generatePayload(pingers: List<PingerConfig>): String {
            if (pingers.isEmpty()) return ""

            val totalSize = pingers.size * PINGER_BLOCK_SIZE
            val buffer = ByteArray(totalSize)

            pingers.forEachIndexed { idx, cfg ->
                val blk = ByteArray(PINGER_BLOCK_SIZE)
                if (idx == 0) {
                    val pb = cfg.period.toLE()
                    blk[FIRST_PINGER_PERIOD_OFFSET] = pb[0]
                    blk[FIRST_PINGER_PERIOD_OFFSET + 1] = pb[1]

                    val tb = cfg.timeout.toLE()
                    blk[FIRST_PINGER_TIMEOUT_OFFSET] = tb[0]
                    blk[FIRST_PINGER_TIMEOUT_OFFSET + 1] = tb[1]

                    val ab = cfg.address.toByteArray()
                    val len = ab.size.coerceAtMost(PINGER_BLOCK_SIZE - (FIRST_PINGER_HOSTNAME_OFFSET + 1))
                    blk[FIRST_PINGER_HOSTNAME_OFFSET] = len.toByte()
                    System.arraycopy(ab, 0, blk, FIRST_PINGER_HOSTNAME_OFFSET + 1, len)
                } else {
                    val pb = cfg.period.toLE()
                    blk[OTHER_PINGER_PERIOD_OFFSET] = pb[0]
                    blk[OTHER_PINGER_PERIOD_OFFSET + 1] = pb[1]

                    val tb = cfg.timeout.toLE()
                    blk[OTHER_PINGER_TIMEOUT_OFFSET] = tb[0]
                    blk[OTHER_PINGER_TIMEOUT_OFFSET + 1] = tb[1]

                    val ab = cfg.address.toByteArray()
                    val len = ab.size.coerceAtMost(PINGER_BLOCK_SIZE - (OTHER_PINGER_HOSTNAME_OFFSET + 1))
                    blk[OTHER_PINGER_HOSTNAME_OFFSET] = len.toByte()
                    System.arraycopy(ab, 0, blk, OTHER_PINGER_HOSTNAME_OFFSET + 1, len)
                }

                System.arraycopy(blk, 0, buffer, idx * PINGER_BLOCK_SIZE, PINGER_BLOCK_SIZE)
            }

            return buffer.joinToString("") { "%02x".format(it) }
        }

        fun validatePingerConfig(c: PingerConfig): Boolean {
            return c.period in MIN_PERIOD..MAX_PERIOD &&
                    c.timeout in MIN_TIMEOUT..MAX_TIMEOUT &&
                    c.address.isNotBlank()
        }

        private fun Int.toLE(): ByteArray = byteArrayOf((this and 0xFF).toByte(), ((this shr 8) and 0xFF).toByte())
    }

    class LogicRulePayloadGenerator {
        companion object {
            private const val RULE_BLOCK_SIZE = 16
            private const val MAX_RULES = 8
            private const val TOTAL_PAYLOAD_SIZE = RULE_BLOCK_SIZE * MAX_RULES
            private const val FLAGS_OFFSET = 0
            private const val INPUT_OFFSET = 1
            private const val CONDITION_OFFSET = 2
            private const val ACTION_OFFSET = 3
            private const val OUTPUT_OFFSET = 4
        }

        fun generatePayload(rules: List<LogicRule>): String {
            val payloadBuffer = ByteArray(TOTAL_PAYLOAD_SIZE)

            rules.forEachIndexed { idx, rule ->
                if (idx >= MAX_RULES) {
                    return@forEachIndexed
                }

                if (!validateRule(rule, idx)) {
                    return@forEachIndexed
                }

                val blockStartOffset = idx * RULE_BLOCK_SIZE

                payloadBuffer[blockStartOffset + FLAGS_OFFSET] = rule.flags.toByte()
                payloadBuffer[blockStartOffset + INPUT_OFFSET] = rule.input.toByte()
                payloadBuffer[blockStartOffset + CONDITION_OFFSET] = rule.condition.toByte()
                payloadBuffer[blockStartOffset + ACTION_OFFSET] = rule.action.toByte()
                payloadBuffer[blockStartOffset + OUTPUT_OFFSET] = rule.output.toByte()
            }

            return payloadBuffer.joinToString("") { "%02x".format(it) }
        }

        private fun validateRule(rule: LogicRule, ruleIndex: Int): Boolean {
            if (!rule.isEnabled) return true

            if (rule.input == 1 && rule.action == 2) {
                return false
            }

            if ((rule.output and 0xf0) == 0xd0) {
                val validAction = (rule.isTrigger && rule.action == 1) ||
                        (!rule.isTrigger && rule.action == 0)
                if (!validAction) {
                    return false
                }
            }

            if ((rule.output and 0xf0) == 0xc0) {
                if (!rule.isTrigger) {
                    return false
                }
            }

            if (rule.output == 240) {
                if (!(rule.isTrigger && rule.action == 1)) {
                    return false
                }
            }

            return true
        }
    }

    class SetterPayloadGenerator {
        companion object {
            private const val SETTER_BLOCK_SIZE = 220
            private const val TOTAL_PAYLOAD_SIZE = 440
            private const val NAME_OFFSET = 0
            private const val OID_OFFSET = 32
            private const val HOSTNAME_OFFSET = 100
            private const val COMMUNITY_OFFSET = 164
            private const val PORT_OFFSET = 194
            private const val VALUE_ON_OFFSET = 196
            private const val VALUE_OFF_OFFSET = 200
            private const val NAME_SIZE = 32
            private const val OID_SIZE = 60
            private const val HOSTNAME_SIZE = 64
            private const val COMMUNITY_SIZE = 16
        }

        fun generatePayload(setters: List<SetterConfig>): String {
            if (setters.isEmpty()) {
                return "0".repeat(TOTAL_PAYLOAD_SIZE * 2)
            }

            val payloadBuffer = ByteArray(TOTAL_PAYLOAD_SIZE)

            setters.forEachIndexed { idx, cfg ->
                if (idx * SETTER_BLOCK_SIZE >= TOTAL_PAYLOAD_SIZE) return@forEachIndexed

                val blockStartOffset = idx * SETTER_BLOCK_SIZE

                writeLengthPrefixedString(
                    buffer = payloadBuffer,
                    baseOffset = blockStartOffset + NAME_OFFSET,
                    maxSize = NAME_SIZE,
                    value = cfg.name
                )

                writeLengthPrefixedString(
                    buffer = payloadBuffer,
                    baseOffset = blockStartOffset + OID_OFFSET,
                    maxSize = OID_SIZE,
                    value = cfg.oid
                )

                writeLengthPrefixedString(
                    buffer = payloadBuffer,
                    baseOffset = blockStartOffset + HOSTNAME_OFFSET,
                    maxSize = HOSTNAME_SIZE,
                    value = cfg.address
                )

                writeLengthPrefixedString(
                    buffer = payloadBuffer,
                    baseOffset = blockStartOffset + COMMUNITY_OFFSET,
                    maxSize = COMMUNITY_SIZE,
                    value = cfg.community
                )

                writeShortLE(
                    buffer = payloadBuffer,
                    offset = blockStartOffset + PORT_OFFSET,
                    value = cfg.port
                )

                writeIntLE(
                    buffer = payloadBuffer,
                    offset = blockStartOffset + VALUE_ON_OFFSET,
                    value = cfg.valueOn
                )

                writeIntLE(
                    buffer = payloadBuffer,
                    offset = blockStartOffset + VALUE_OFF_OFFSET,
                    value = cfg.valueOff
                )
            }

            return payloadBuffer.joinToString("") { "%02x".format(it) }
        }

        private fun writeLengthPrefixedString(buffer: ByteArray, baseOffset: Int, maxSize: Int, value: String) {
            val stringBytes = value.toByteArray(Charsets.UTF_8)
            val len = stringBytes.size.coerceAtMost(maxSize - 1)
            buffer[baseOffset] = len.toByte()
            System.arraycopy(stringBytes, 0, buffer, baseOffset + 1, len)
        }

        private fun writeShortLE(buffer: ByteArray, offset: Int, value: Int) {
            val bytes = java.nio.ByteBuffer.allocate(2).order(java.nio.ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array()
            System.arraycopy(bytes, 0, buffer, offset, 2)
        }

        private fun writeIntLE(buffer: ByteArray, offset: Int, value: Int) {
            val bytes = java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(value).array()
            System.arraycopy(bytes, 0, buffer, offset, 4)
        }
    }
}
