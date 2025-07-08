//NetPingViewModel
package com.example.netpingmonitor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import com.example.netpingmonitor.model.*
import com.example.netpingmonitor.NetPingRepository
import javax.inject.Inject
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext

@HiltViewModel class NetPingViewModel @Inject constructor( private val netPingRepository: NetPingRepository, @ApplicationContext private val context: Context ) : ViewModel() {

    companion object {
        private const val AUTO_UPDATE_INTERVAL_MS = 60_000L
        private const val KEY_SAVED_DEVICES = "saved_devices"
        private const val KEY_CURRENT_DEVICE_ID = "current_device_id"
    }

    private val _uiState = MutableStateFlow(NetPingUiState())
    val uiState: StateFlow<NetPingUiState> = _uiState.asStateFlow()

    private var autoUpdateJob: Job? = null
    private val sharedPreferences = context.getSharedPreferences("netping_devices", Context.MODE_PRIVATE)
    private val gson = Gson()

    init {
        loadSavedDevices()
    }

    fun addDevice(ipAddress: String, username: String, password: String) {
        val newDevice = SavedDevice(
            id = generateDeviceId(),
            name = "NetPing ($ipAddress)",
            ipAddress = ipAddress,
            username = username,
            password = password
        )

        val updatedDevices = _uiState.value.savedDevices + newDevice
        _uiState.value = _uiState.value.copy(
            savedDevices = updatedDevices,
            currentDeviceId = newDevice.id,
            isAddingDevice = false,
            newDeviceIpAddress = "",
            newDeviceUsername = "visor",
            newDevicePassword = "ping"
        )

        saveDevices()
    }

    fun deleteDevice(deviceId: String) {
        val updatedDevices = _uiState.value.savedDevices.filter { it.id != deviceId }
        val updatedDataMap = _uiState.value.deviceDataMap.toMutableMap().apply {
            remove(deviceId)
        }

        val newCurrentId = if (_uiState.value.currentDeviceId == deviceId) {
            updatedDevices.firstOrNull()?.id
        } else {
            _uiState.value.currentDeviceId
        }

        _uiState.value = _uiState.value.copy(
            savedDevices = updatedDevices,
            currentDeviceId = newCurrentId,
            deviceDataMap = updatedDataMap
        )

        saveDevices()
    }

    fun selectDevice(deviceId: String) {
        _uiState.value = _uiState.value.copy(currentDeviceId = deviceId)
        saveDevices()
    }

    fun updateNewDeviceIpAddress(address: String) {
        _uiState.value = _uiState.value.copy(newDeviceIpAddress = address)
    }

    fun updateNewDeviceUsername(username: String) {
        _uiState.value = _uiState.value.copy(newDeviceUsername = username)
    }

    fun updateNewDevicePassword(password: String) {
        _uiState.value = _uiState.value.copy(newDevicePassword = password)
    }

    fun startAddingDevice() {
        _uiState.value = _uiState.value.copy(isAddingDevice = true)
    }

    fun cancelAddingDevice() {
        _uiState.value = _uiState.value.copy(
            isAddingDevice = false,
            newDeviceIpAddress = "",
            newDeviceUsername = "visor",
            newDevicePassword = "ping"
        )
    }

    fun connectToDevice(deviceId: String) {
        val device = _uiState.value.savedDevices.find { it.id == deviceId } ?: return

        viewModelScope.launch {
            setLoading(true)

            try {
                val result = netPingRepository.connect(
                    address = device.ipAddress,
                    username = device.username,
                    password = device.password
                )

                result.fold(
                    onSuccess = {
                        val updatedDevices = _uiState.value.savedDevices.map {
                            if (it.id == deviceId) it.copy(isConnected = true, lastConnected = System.currentTimeMillis())
                            else it.copy(isConnected = false)
                        }

                        _uiState.value = _uiState.value.copy(
                            savedDevices = updatedDevices,
                            currentDeviceId = deviceId
                        )

                        saveDevices()
                        refreshData()
                        updateDeviceName(deviceId)
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            errorMessage = error.message ?: "Ошибка подключения"
                        )
                    }
                )
            } finally {
                setLoading(false)
            }
        }
    }


    fun refreshData() {
        val currentDeviceId = _uiState.value.currentDeviceId
        val currentDevice = _uiState.value.currentDevice

        if (currentDeviceId == null || currentDevice?.isConnected != true) {
            return
        }

        viewModelScope.launch {
            setLoading(true)

            try {
                val deviceData = netPingRepository.getAllData()

                val updatedDataMap = _uiState.value.deviceDataMap.toMutableMap()
                updatedDataMap[currentDeviceId] = deviceData

                _uiState.value = _uiState.value.copy(
                    deviceDataMap = updatedDataMap
                )

                updateDeviceName(currentDeviceId)
                startAutoUpdate()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "Ошибка получения данных"
                )
            } finally {
                setLoading(false)
            }
        }
    }

    private fun startAutoUpdate() {
        stopAutoUpdate()

        autoUpdateJob = viewModelScope.launch {
            while (true) {
                delay(AUTO_UPDATE_INTERVAL_MS)

                val currentDevice = _uiState.value.currentDevice
                if (currentDevice?.isConnected == true && _uiState.value.savedDevices.any { it.isConnected }) {
                    try {
                        updateLogicStatusInternal()
                    } catch (_: Exception) {
                        // Игнорируем ошибки автообновления
                    }
                }
            }
        }
    }

    private suspend fun updateLogicStatusInternal() {
        val currentDeviceId = _uiState.value.currentDeviceId ?: return
        val logicStatus = netPingRepository.getLogicStatus()
        val currentData = _uiState.value.deviceDataMap[currentDeviceId]

        if (currentData != null) {
            val updatedDataMap = _uiState.value.deviceDataMap.toMutableMap()
            updatedDataMap[currentDeviceId] = currentData.copy(logicStatus = logicStatus)

            _uiState.value = _uiState.value.copy(
                deviceDataMap = updatedDataMap
            )
        }
    }

    private fun stopAutoUpdate() {
        autoUpdateJob?.cancel()
        autoUpdateJob = null
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun toggleDeviceManager() {
        _uiState.value = _uiState.value.copy(
            isDeviceManagerExpanded = !_uiState.value.isDeviceManagerExpanded
        )
    }

    fun selectTab(tabIndex: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = tabIndex)
    }

    fun selectLogicTab(tabIndex: Int) {
        _uiState.value = _uiState.value.copy(selectedLogicTab = tabIndex)
    }

    fun updateLogicRule(ruleIndex: Int, updatedRule: LogicRule) {
        val currentDeviceId = _uiState.value.currentDeviceId ?: return
        val currentData = _uiState.value.deviceDataMap[currentDeviceId] ?: return
        val updatedRules = currentData.logicRules.toMutableList()

        if (ruleIndex < updatedRules.size) {
            updatedRules[ruleIndex] = updatedRule
            val updatedDataMap = _uiState.value.deviceDataMap.toMutableMap()
            updatedDataMap[currentDeviceId] = currentData.copy(logicRules = updatedRules)

            _uiState.value = _uiState.value.copy(
                deviceDataMap = updatedDataMap
            )
        }
    }



    fun controlLogic(action: LogicAction) {
        viewModelScope.launch {
            try {
                val currentDevice = _uiState.value.currentDevice
                if (currentDevice?.isConnected != true) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Устройство не подключено"
                    )
                    return@launch
                }

                val result = netPingRepository.controlLogic(action)

                if (result.isFailure) {
                    val errorMsg = "Ошибка управления логикой: ${result.exceptionOrNull()?.message}"
                    _uiState.value = _uiState.value.copy(
                        errorMessage = errorMsg
                    )
                } else {
                    val actionName = when (action) {
                        LogicAction.START -> "запущена"
                        LogicAction.STOP -> "остановлена"
                        LogicAction.RESET -> "сброшена"
                    }

                    val currentLogicStatus = result.getOrNull() ?: false

                    val currentDeviceId = _uiState.value.currentDeviceId
                    val currentData = _uiState.value.deviceDataMap[currentDeviceId]
                    if (currentDeviceId != null && currentData != null) {
                        val updatedLogicStatus = currentData.logicStatus.copy(
                            isLogicRunning = currentLogicStatus,
                            lastUpdate = System.currentTimeMillis()
                        )
                        val updatedDataMap = _uiState.value.deviceDataMap.toMutableMap()
                        updatedDataMap[currentDeviceId] = currentData.copy(logicStatus = updatedLogicStatus)

                        _uiState.value = _uiState.value.copy(
                            deviceDataMap = updatedDataMap,
                            errorMessage = "Логика $actionName"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "Ошибка управления логикой"
                )
            }
        }
    }

    fun saveLogicRules() {
        viewModelScope.launch {
            setLoading(true)

            try {
                val currentData = _uiState.value.currentDeviceData
                if (currentData != null) {
                    val result = netPingRepository.saveLogicRules(currentData.logicRules)
                    result.fold(
                        onSuccess = {
                            _uiState.value = _uiState.value.copy(
                                errorMessage = "Правила логики успешно сохранены"
                            )
                        },
                        onFailure = { error ->
                            _uiState.value = _uiState.value.copy(
                                errorMessage = "Ошибка сохранения правил логики: ${error.message}"
                            )
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "Ошибка сохранения правил логики"
                )
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun saveTstatData() {
        viewModelScope.launch {
            setLoading(true)

            try {
                val currentData = _uiState.value.currentDeviceData
                if (currentData != null) {
                    val result = netPingRepository.saveTstatData(currentData.tstatData)
                    result.fold(
                        onSuccess = {
                            _uiState.value = _uiState.value.copy(
                                errorMessage = "Термостат успешно сохранён"
                            )
                        },
                        onFailure = { error ->
                            _uiState.value = _uiState.value.copy(
                                errorMessage = "Ошибка сохранения термостата: ${error.message}"
                            )
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "Ошибка сохранения термостата"
                )
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun savePingerData() {
        viewModelScope.launch {
            setLoading(true)

            try {
                val currentData = _uiState.value.currentDeviceData
                if (currentData != null) {
                    val result = netPingRepository.savePingerData(currentData.pingerData)
                    result.fold(
                        onSuccess = {
                            _uiState.value = _uiState.value.copy(
                                errorMessage = "Пингер успешно сохранён"
                            )
                        },
                        onFailure = { error ->
                            _uiState.value = _uiState.value.copy(
                                errorMessage = "Ошибка сохранения пингера: ${error.message}"
                            )
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "Ошибка сохранения пингера"
                )
            } finally {
                setLoading(false)
            }
        }
    }

    fun saveSetterData() {
        viewModelScope.launch {
            setLoading(true)

            try {
                val currentData = _uiState.value.currentDeviceData
                if (currentData != null) {
                    val result = netPingRepository.saveSetterData(currentData.setterData)
                    result.fold(
                        onSuccess = {
                            _uiState.value = _uiState.value.copy(
                                errorMessage = "SNMP setter успешно сохранён"
                            )
                        },
                        onFailure = { error ->
                            _uiState.value = _uiState.value.copy(
                                errorMessage = "Ошибка сохранения SNMP setter: ${error.message}"
                            )
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "Ошибка сохранения SNMP setter"
                )
            } finally {
                setLoading(false)
            }
        }
    }

    fun testSetter(setterIndex: Int, turnOn: Boolean) {
        viewModelScope.launch {
            try {
                val currentDevice = _uiState.value.currentDevice
                if (currentDevice?.isConnected != true) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Устройство не подключено"
                    )
                    return@launch
                }

                val channelNumber = setterIndex + 1
                val result = netPingRepository.testSetter(setterIndex, turnOn)

                if (result.isFailure) {
                    val errorMsg = "Ошибка тестирования SNMP setter $channelNumber: ${result.exceptionOrNull()?.message}"
                    _uiState.value = _uiState.value.copy(
                        errorMessage = errorMsg
                    )
                } else {
                    delay(2000)
                    updateLogicStatusInternal()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "Ошибка тестирования SNMP setter"
                )
            }
        }
    }

    fun updateTstatData(index: Int, updatedTstat: TstatData) {
        val currentDeviceId = _uiState.value.currentDeviceId ?: return
        val currentData = _uiState.value.deviceDataMap[currentDeviceId] ?: return
        val updatedList = currentData.tstatData.toMutableList()

        if (index < updatedList.size) {
            updatedList[index] = updatedTstat
            val updatedDataMap = _uiState.value.deviceDataMap.toMutableMap()
            updatedDataMap[currentDeviceId] = currentData.copy(tstatData = updatedList)

            _uiState.value = _uiState.value.copy(deviceDataMap = updatedDataMap)
        }
    }

    fun updatePingerData(index: Int, updatedPinger: PingerData) {
        val currentDeviceId = _uiState.value.currentDeviceId ?: return
        val currentData = _uiState.value.deviceDataMap[currentDeviceId] ?: return
        val updatedList = currentData.pingerData.toMutableList()

        if (index < updatedList.size) {
            updatedList[index] = updatedPinger
            val updatedDataMap = _uiState.value.deviceDataMap.toMutableMap()
            updatedDataMap[currentDeviceId] = currentData.copy(pingerData = updatedList)

            _uiState.value = _uiState.value.copy(deviceDataMap = updatedDataMap)
        }
    }

    fun updateSetterData(index: Int, updatedSetter: SetterData) {
        val currentDeviceId = _uiState.value.currentDeviceId ?: return
        val currentData = _uiState.value.deviceDataMap[currentDeviceId] ?: return
        val updatedList = currentData.setterData.toMutableList()

        if (index < updatedList.size) {
            updatedList[index] = updatedSetter
            val updatedDataMap = _uiState.value.deviceDataMap.toMutableMap()
            updatedDataMap[currentDeviceId] = currentData.copy(setterData = updatedList)

            _uiState.value = _uiState.value.copy(deviceDataMap = updatedDataMap)
        }
    }

    fun moveLogicRule(fromIndex: Int, toIndex: Int) {
        val currentDeviceId = _uiState.value.currentDeviceId ?: return
        val currentData = _uiState.value.deviceDataMap[currentDeviceId] ?: return
        val rules = currentData.logicRules.toMutableList()

        if (fromIndex in rules.indices && toIndex in rules.indices && fromIndex != toIndex) {
            val movedRule = rules.removeAt(fromIndex)
            rules.add(toIndex, movedRule)

            val updatedDataMap = _uiState.value.deviceDataMap.toMutableMap()
            updatedDataMap[currentDeviceId] = currentData.copy(logicRules = rules)

            _uiState.value = _uiState.value.copy(
                deviceDataMap = updatedDataMap
            )
        }
    }

    private fun generateDeviceId(): String {
        return "device_${System.currentTimeMillis()}"
    }

    private fun loadSavedDevices() {
        try {
            val devicesJson = sharedPreferences.getString(KEY_SAVED_DEVICES, null)
            val currentDeviceId = sharedPreferences.getString(KEY_CURRENT_DEVICE_ID, null)

            val savedDevices = if (devicesJson != null) {
                try {
                    val type = object : TypeToken<List<SavedDevice>>() {}.type
                    gson.fromJson<List<SavedDevice>>(devicesJson, type) ?: emptyList()
                } catch (_: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }

            val devicesWithResetConnection = savedDevices.map { device ->
                device.copy(isConnected = false)
            }

            _uiState.value = _uiState.value.copy(
                savedDevices = devicesWithResetConnection,
                currentDeviceId = currentDeviceId ?: devicesWithResetConnection.firstOrNull()?.id
            )

            if (devicesWithResetConnection != savedDevices) {
                saveDevices()
            }
        } catch (_: Exception) {
            _uiState.value = _uiState.value.copy(
                savedDevices = emptyList(),
                currentDeviceId = null
            )
        }
    }

    private fun saveDevices() {
        try {
            val devicesJson = gson.toJson(_uiState.value.savedDevices)
            val editor = sharedPreferences.edit()
            editor.putString(KEY_SAVED_DEVICES, devicesJson)
            editor.putString(KEY_CURRENT_DEVICE_ID, _uiState.value.currentDeviceId)

            val success = editor.commit()

            if (!success) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Ошибка сохранения настроек устройств"
                )
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Ошибка сохранения настроек: ${e.message}"
            )
        }
    }

    private fun setLoading(isLoading: Boolean) {
        _uiState.value = _uiState.value.copy(isLoading = isLoading, errorMessage = null)
    }

    private fun updateDeviceName(deviceId: String) {
        val deviceData = _uiState.value.deviceDataMap[deviceId]
        if (deviceData?.deviceInfo != null) {
            val deviceName = deviceData.deviceInfo.model
            val updatedDevices = _uiState.value.savedDevices.map { device ->
                if (device.id == deviceId) {
                    device.copy(name = deviceName)
                } else {
                    device
                }
            }

            _uiState.value = _uiState.value.copy(savedDevices = updatedDevices)
            saveDevices()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAutoUpdate()
        saveDevices()
    }


}
