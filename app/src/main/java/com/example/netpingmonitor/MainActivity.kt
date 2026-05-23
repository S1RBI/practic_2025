//MainActivity
package com.example.netpingmonitor

import android.R
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.*
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Job
import javax.inject.Inject
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.example.netpingmonitor.ui.theme.NetPingMonitorTheme
import com.example.netpingmonitor.model.*
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.netpingmonitor.ui.theme.StatusConnected
import dagger.hilt.android.lifecycle.HiltViewModel
import com.example.netpingmonitor.viewmodel.NetPingViewModel
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.alpha
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NetPingMonitorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NetPingApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DeviceContentPager(
    uiState: NetPingUiState,
    viewModel: NetPingViewModel
) {
    val connectedDevices = uiState.savedDevices.filter { it.isConnected }
    val currentDeviceIndex = connectedDevices.indexOfFirst { it.id == uiState.currentDeviceId }.takeIf { it >= 0 } ?: 0

    val pagerState = rememberPagerState(
        initialPage = currentDeviceIndex,
        pageCount = { connectedDevices.size }
    )

    val coroutineScope = rememberCoroutineScope()

    // Синхронизация выбора устройства с pager
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage < connectedDevices.size) {
            val selectedDevice = connectedDevices[pagerState.currentPage]
            if (selectedDevice.id != uiState.currentDeviceId) {
                viewModel.selectDevice(selectedDevice.id)
            }
        }
    }

    // Синхронизация pager с выбором устройства
    LaunchedEffect(uiState.currentDeviceId) {
        val newIndex = connectedDevices.indexOfFirst { it.id == uiState.currentDeviceId }
        if (newIndex >= 0 && newIndex != pagerState.currentPage) {
            coroutineScope.launch {
                pagerState.animateScrollToPage(newIndex)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Device tabs (swipeable)
        if (connectedDevices.size > 1) {
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                indicator = { tabPositions ->
                    if (tabPositions.isNotEmpty() && pagerState.currentPage < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                            height = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                edgePadding = 16.dp
            ) {
                connectedDevices.forEachIndexed { index, device ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        modifier = Modifier.height(56.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(8.dp)
                                ) {}
                                Text(
                                    text = device.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = device.ipAddress,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Device content pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            if (pageIndex < connectedDevices.size) {
                val device = connectedDevices[pageIndex]
                val deviceData = uiState.deviceDataMap[device.id]

                if (deviceData != null) {
                    DeviceTabContent(
                        deviceData = deviceData,
                        selectedTab = uiState.selectedTab,
                        onTabSelected = viewModel::selectTab,
                        viewModel = viewModel
                    )
                } else {
                    // Loading state for this device
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 4.dp
                            )
                            Text(
                                text = "Загрузка данных с ${device.name}...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceTabContent(
    deviceData: NetPingDeviceData,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    viewModel: NetPingViewModel
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Content tabs
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth(),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            edgePadding = 0.dp,
            indicator = { tabPositions ->
                if (tabPositions.isNotEmpty() && selectedTab < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        height = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { onTabSelected(0) },
                modifier = Modifier.height(32.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Информация",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Tab(
                selected = selectedTab == 1,
                onClick = { onTabSelected(1) },
                modifier = Modifier.height(32.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Логика",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Tab(
                selected = selectedTab == 2,
                onClick = { onTabSelected(2) },
                modifier = Modifier.height(32.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Power,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Реле",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Tab(
                selected = selectedTab == 3,
                onClick = { onTabSelected(3) },
                modifier = Modifier.height(32.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Thermostat,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Термо",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Tab(
                selected = selectedTab == 4,
                onClick = { onTabSelected(4) },
                modifier = Modifier.height(32.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WaterDrop,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Влажность",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Tab(
                selected = selectedTab == 5,
                onClick = { onTabSelected(5) },
                modifier = Modifier.height(32.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ListAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Журнал",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Tab content
        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                0 -> InfoTab(deviceData = deviceData)
                1 -> LogicTab(deviceData = deviceData, viewModel = viewModel)
                2 -> RelayTab(deviceData = deviceData, viewModel = viewModel)
                3 -> TermoSensorsTab(deviceData = deviceData, viewModel = viewModel)
                4 -> RhTab(deviceData = deviceData, viewModel = viewModel)
                5 -> LogTab(deviceData = deviceData)
            }
        }
    }
}

@Composable
fun DeviceManagerHeader(
    uiState: NetPingUiState,
    viewModel: NetPingViewModel,
    onDeleteDevice: (SavedDevice) -> Unit = {}
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            // Header with device management controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "NetPing Monitor",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    // Current device info
                    uiState.currentDevice?.let { device ->
                        Text(
                            text = device.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            maxLines = 1
                        )
                        Text(
                            text = "IP: ${device.ipAddress}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }

                // Device management buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Add device button
                    IconButton(
                        onClick = viewModel::startAddingDevice,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Добавить устройство",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    // Devices list toggle
                    IconButton(
                        onClick = viewModel::toggleDeviceManager,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.isDeviceManagerExpanded)
                                Icons.Default.ExpandLess else Icons.Default.DevicesOther,
                            contentDescription = if (uiState.isDeviceManagerExpanded)
                                "Свернуть список" else "Показать устройства",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Connection status indicator
                Surface(
                    shape = CircleShape,
                    color = if (uiState.isConnected)
                        StatusConnected
                    else
                        MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(12.dp)
                ) {}
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Connection status chip
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (uiState.isConnected)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                else
                    MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (uiState.isConnected)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.error,
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.isConnected) {
                            "${uiState.savedDevices.count { it.isConnected }} из ${uiState.savedDevices.size} подключено"
                        } else if (uiState.savedDevices.isNotEmpty()) {
                            "${uiState.savedDevices.size} устройств сохранено"
                        } else {
                            "Нет сохраненных устройств"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (uiState.isConnected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )

                    if (uiState.isConnected && uiState.currentDevice != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = viewModel::refreshData,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Обновить",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Device manager content
            AnimatedVisibility(
                visible = uiState.isDeviceManagerExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                DeviceManagerContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    onDeleteDevice = onDeleteDevice
                )
            }

            // Add device form
            AnimatedVisibility(
                visible = uiState.isAddingDevice,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                AddDeviceForm(
                    uiState = uiState,
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
fun DeviceManagerContent(
    uiState: NetPingUiState,
    viewModel: NetPingViewModel,
    onDeleteDevice: (SavedDevice) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .padding(top = 16.dp)
            .heightIn(max = 250.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Сохраненные устройства",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (uiState.savedDevices.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Нет сохраненных устройств",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 200.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.savedDevices) { device ->
                    DeviceCard(
                        device = device,
                        isSelected = device.id == uiState.currentDeviceId,
                        isConnected = device.isConnected,
                        onSelect = { viewModel.selectDevice(device.id) },
                        onConnect = { viewModel.connectToDevice(device.id) },
                        onDelete = { onDeleteDevice(device) }
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceCard(
    device: SavedDevice,
    isSelected: Boolean,
    isConnected: Boolean,
    onSelect: () -> Unit,
    onConnect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            else
                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
        ),
        border = if (isSelected)
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    Surface(
                        shape = CircleShape,
                        color = if (isConnected)
                            StatusConnected
                        else
                            MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(8.dp)
                    ) {}
                }

                Text(
                    text = device.ipAddress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Text(
                    text = "Статус: ${device.connectionStatus}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isConnected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                if (device.connectionStatus.startsWith("Подключение")) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth(0.65f)
                            .height(4.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }

                if (device.lastConnected > 0) {
                    Text(
                        text = "Последнее подключение: ${formatLastUpdate(device.lastConnected)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Connect/Disconnect button
                IconButton(
                    onClick = onConnect,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isConnected) Icons.Default.Refresh else Icons.Default.PlayArrow,
                        contentDescription = if (isConnected) "Отключить" else "Подключить",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Delete button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Удалить",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun AddDeviceForm(
    uiState: NetPingUiState,
    viewModel: NetPingViewModel
) {
    Column(
        modifier = Modifier.padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Добавить новое устройство",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Название устройства будет определено автоматически при подключении",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        OutlinedTextField(
            value = uiState.newDeviceIpAddress,
            onValueChange = viewModel::updateNewDeviceIpAddress,
            label = { Text("IP адрес") },
            placeholder = { Text("192.168.1.100") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Computer,
                    contentDescription = null
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = uiState.newDeviceUsername,
                onValueChange = viewModel::updateNewDeviceUsername,
                label = { Text("Логин") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null
                    )
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = uiState.newDevicePassword,
                onValueChange = viewModel::updateNewDevicePassword,
                label = { Text("Пароль") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null
                    )
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                visualTransformation = PasswordVisualTransformation()
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = viewModel::cancelAddingDevice,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Отмена")
            }

            Button(
                onClick = {
                    viewModel.addDevice(
                        ipAddress = uiState.newDeviceIpAddress,
                        username = uiState.newDeviceUsername,
                        password = uiState.newDevicePassword
                    )
                },
                enabled = uiState.newDeviceIpAddress.isNotBlank(),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text("Сохранить")
                }
            }
        }
    }
}

// Вспомогательная функция для форматирования времени последнего обновления
private fun formatLastUpdate(timestamp: Long): String {
    if (timestamp == 0L) return ""

    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "только что"
        diff < 3600_000 -> "${diff / 60_000} мин назад"
        else -> {
            val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
            formatter.format(Date(timestamp))
        }
    }
}

@Composable
fun LogicRulesTab(deviceData: NetPingDeviceData, viewModel: NetPingViewModel) {
    Column {
        // Компактная единая строка: статус + кнопки управления
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Индикатор состояния логики (компактный)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (deviceData.logicStatus.isLogicRunning)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    else
                        MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    color = if (deviceData.logicStatus.isLogicRunning)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.error,
                                    shape = CircleShape
                                )
                        )
                        Text(
                            text = if (deviceData.logicStatus.isLogicRunning)
                                "ЗАПУЩЕНА"
                            else
                                "остановлена",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            fontWeight = FontWeight.Medium,
                            color = if (deviceData.logicStatus.isLogicRunning)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Компактные кнопки управления
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Reset - всегда активна
                    Button(
                        onClick = {
                            viewModel.controlLogic(LogicAction.RESET)
                        },
                        modifier = Modifier
                            .width(60.dp)
                            .height(28.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Reset",
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = "Reset",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp)
                            )
                        }
                    }

                    // Start/Stop (одна кнопка как на сайте)
                    Button(
                        onClick = {
                            if (deviceData.logicStatus.isLogicRunning) {
                                viewModel.controlLogic(LogicAction.STOP)
                            } else {
                                viewModel.controlLogic(LogicAction.START)
                            }
                        },
                        modifier = Modifier
                            .width(60.dp)
                            .height(28.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (deviceData.logicStatus.isLogicRunning)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = if (deviceData.logicStatus.isLogicRunning)
                                    Icons.Default.Stop
                                else
                                    Icons.Default.PlayArrow,
                                contentDescription = if (deviceData.logicStatus.isLogicRunning) "Стоп" else "Пуск",
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = if (deviceData.logicStatus.isLogicRunning) "Стоп" else "Пуск",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp)
                            )
                        }
                    }

                    // Apply
                    Button(
                        onClick = {
                            viewModel.saveLogicRules()
                        },
                        enabled = deviceData.logicRules.isNotEmpty(),
                        modifier = Modifier
                            .width(80.dp)
                            .height(28.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Применить",
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = "Применить",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Компактный список правил
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (deviceData.logicRules.isNotEmpty()) {
                itemsIndexed(
                    items = deviceData.logicRules,
                    key = { index, rule -> "rule_$index" }
                ) { index, rule ->
                    CompactLogicRuleCard(
                        rule = rule,
                        ruleNumber = index + 1,
                        onRuleUpdate = { updatedRule ->
                            viewModel.updateLogicRule(index, updatedRule)
                        },
                        onMoveUp = {
                            if (index > 0) {
                                viewModel.moveLogicRule(index, index - 1)
                            }
                        },
                        onMoveDown = {
                            if (index < deviceData.logicRules.size - 1) {
                                viewModel.moveLogicRule(index, index + 1)
                            }
                        },
                        canMoveUp = index > 0,
                        canMoveDown = index < deviceData.logicRules.size - 1,
                        deviceData = deviceData
                    )
                }
            } else {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Rule,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Нет правил логики",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}




@Composable
fun TstatTab(deviceData: NetPingDeviceData, viewModel: NetPingViewModel) {
    Column {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Термо(гигро)стат",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (deviceData.logicStatus.lastUpdate > 0) {
                    Text(
                        text = "Последнее обновление: ${formatLastUpdate(deviceData.logicStatus.lastUpdate)}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = {
                    viewModel.saveTstatData()
                },
                enabled = deviceData.tstatData.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Применить",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        if (deviceData.tstatData.isNotEmpty()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(deviceData.tstatData.size) { index ->
                    TstatCard(
                        tstat = deviceData.tstatData[index],
                        index = index,
                        logicStatus = deviceData.logicStatus,
                        termoNChannels = deviceData.termoNChannels,
                        rhNChannels = deviceData.rhNChannels,
                        deviceData = deviceData,
                        viewModel = viewModel
                    )
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Нет данных термостата")
                }
            }
        }
    }
}

@Composable
fun PingerTab(deviceData: NetPingDeviceData, viewModel: NetPingViewModel) {
    Column {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Пингер",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (deviceData.logicStatus.lastUpdate > 0) {
                    Text(
                        text = "Последнее обновление: ${formatLastUpdate(deviceData.logicStatus.lastUpdate)}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = { viewModel.savePingerData() },
                enabled = deviceData.pingerData.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Применить",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        if (deviceData.pingerData.isNotEmpty()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(deviceData.pingerData.size) { index ->
                    PingerCard(
                        pinger = deviceData.pingerData[index],
                        index = index,
                        logicStatus = deviceData.logicStatus,
                        viewModel = viewModel
                    )
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Нет данных пингера")
                }
            }
        }
    }
}

@Composable
fun SnmpSetterTab(deviceData: NetPingDeviceData, viewModel: NetPingViewModel) {
    Column {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "SNMP SETTER",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (deviceData.logicStatus.lastUpdate > 0) {
                    Text(
                        text = "Последнее обновление: ${formatLastUpdate(deviceData.logicStatus.lastUpdate)}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = { viewModel.saveSetterData() },
                enabled = deviceData.setterData.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Применить",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        if (deviceData.setterData.isNotEmpty()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(deviceData.setterData.size) { index ->
                    SetterCard(
                        setter = deviceData.setterData[index],
                        index = index,
                        logicStatus = deviceData.logicStatus,
                        viewModel = viewModel
                    )
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Нет данных SNMP setter")
                }
            }
        }
    }
}

@Composable
fun RelayTab(deviceData: NetPingDeviceData, viewModel: NetPingViewModel) {
    Column {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Управление реле",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (deviceData.relayStatus.lastUpdate > 0) {
                    Text(
                        text = "Последнее обновление: ${formatLastUpdate(deviceData.relayStatus.lastUpdate)}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = { viewModel.saveRelayData() },
                enabled = deviceData.relayData.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Применить",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        if (deviceData.relayData.isNotEmpty()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(deviceData.relayData.size) { index ->
                    RelayCard(
                        relay = deviceData.relayData[index],
                        index = index,
                        relayStatus = deviceData.relayStatus,
                        viewModel = viewModel
                    )
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Power,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Нет данных реле",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TstatCard(
    tstat: TstatData,
    index: Int,
    logicStatus: LogicStatusData = LogicStatusData(),
    termoNChannels: Int = 8,
    rhNChannels: Int = 0,
    deviceData: NetPingDeviceData,
    viewModel: NetPingViewModel
) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "TSTAT ${index + 1}",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Sensor selection
            var sensorExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = sensorExpanded,
                onExpandedChange = { sensorExpanded = !sensorExpanded }
            ) {
                OutlinedTextField(
                    value = TstatData.getSensorOptions(termoNChannels, rhNChannels, deviceData.deviceInfo?.model ?: "").find { it.first == tstat.sensorNo }?.second ?: "Датчик 1",
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Датчик") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = sensorExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = sensorExpanded,
                    onDismissRequest = { sensorExpanded = false }
                ) {
                    TstatData.getSensorOptions(termoNChannels, rhNChannels, deviceData.deviceInfo?.model ?: "").forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                viewModel.updateTstatData(index, tstat.copy(sensorNo = value))
                                sensorExpanded = false
                            },
                            leadingIcon = if (value == tstat.sensorNo) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else null
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Current value (read-only, auto-updated)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Текущее значение",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (logicStatus.sensorValues.isEmpty()) {
                            "Нет данных"
                        } else {
                            tstat.getCurrentValue(logicStatus)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Status (read-only, auto-updated)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Статус",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (logicStatus.sensorStatuses.isEmpty()) {
                            "Нет статуса"
                        } else {
                            tstat.getCurrentStatus(logicStatus)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = tstat.setpoint.toString(),
                    onValueChange = { newValue ->
                        newValue.toIntOrNull()?.let { setpoint ->
                            viewModel.updateTstatData(index, tstat.copy(setpoint = setpoint))
                        }
                    },
                    label = { Text("Порог") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                OutlinedTextField(
                    value = tstat.hyst.toString(),
                    onValueChange = { newValue ->
                        newValue.toIntOrNull()?.let { hyst ->
                            viewModel.updateTstatData(index, tstat.copy(hyst = hyst))
                        }
                    },
                    label = { Text("Гистерезис (1-8)") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }
    }
}

@Composable
fun PingerCard(
    pinger: PingerData,
    index: Int,
    logicStatus: LogicStatusData = LogicStatusData(),
    viewModel: NetPingViewModel
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PINGER ${index + 1}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = pinger.getCurrentStatus(logicStatus),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = pinger.address,
                onValueChange = { newAddress ->
                    viewModel.updatePingerData(index, pinger.copy(address = newAddress.trim()))
                },
                label = { Text("Адрес (опционально)") },
                placeholder = { Text("example.com или 192.168.1.1") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = pinger.period.toString(),
                    onValueChange = { newValue ->
                        newValue.toIntOrNull()?.let { period ->
                            if (period in 5..900) {
                                viewModel.updatePingerData(index, pinger.copy(period = period))
                            }
                        }
                    },
                    label = { Text("Период, с (5-900)") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = pinger.period !in 5..900
                )

                OutlinedTextField(
                    value = pinger.timeout.toString(),
                    onValueChange = { newValue ->
                        newValue.toIntOrNull()?.let { timeout ->
                            if (timeout in 100..10000) {
                                viewModel.updatePingerData(index, pinger.copy(timeout = timeout))
                            }
                        }
                    },
                    label = { Text("Таймаут, мс (100-10000)") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = pinger.timeout !in 100..10000
                )
            }
        }
    }
}

@Composable
fun SetterCard(
    setter: SetterData,
    index: Int,
    logicStatus: LogicStatusData = LogicStatusData(),
    viewModel: NetPingViewModel
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SNMP ${index + 1}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = setter.getCurrentStatus(logicStatus),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = setter.name,
                onValueChange = { newName ->
                    viewModel.updateSetterData(index, setter.copy(name = newName))
                },
                label = { Text("Памятка (опционально)") },
                placeholder = { Text("Описание назначения") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = setter.address,
                onValueChange = { newAddress ->
                    viewModel.updateSetterData(index, setter.copy(address = newAddress))
                },
                label = { Text("Адрес (опционально)") },
                placeholder = { Text("192.168.1.100 или hostname") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = setter.port.toString(),
                    onValueChange = { newValue ->
                        newValue.toIntOrNull()?.let { port ->
                            viewModel.updateSetterData(index, setter.copy(port = port))
                        }
                    },
                    label = { Text("Порт") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                OutlinedTextField(
                    value = setter.community,
                    onValueChange = { newCommunity ->
                        viewModel.updateSetterData(index, setter.copy(community = newCommunity))
                    },
                    label = { Text("Community") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = setter.oid,
                onValueChange = { newOid ->
                    viewModel.updateSetterData(index, setter.copy(oid = newOid))
                },
                label = { Text("OID (опционально)") },
                placeholder = { Text(".1.3.6.1.4.1.25728.8200.1.1.1.1.0") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = setter.valueOn.toString(),
                    onValueChange = { newValue ->
                        newValue.toIntOrNull()?.let { valueOn ->
                            viewModel.updateSetterData(index, setter.copy(valueOn = valueOn))
                        }
                    },
                    label = { Text("Значение \"Вкл\"") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                OutlinedTextField(
                    value = setter.valueOff.toString(),
                    onValueChange = { newValue ->
                        newValue.toIntOrNull()?.let { valueOff ->
                            viewModel.updateSetterData(index, setter.copy(valueOff = valueOff))
                        }
                    },
                    label = { Text("Значение \"Выкл\"") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.testSetter(index, true)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Тест ВКЛ")
                }

                OutlinedButton(
                    onClick = {
                        viewModel.testSetter(index, false)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Тест ВЫКЛ")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelayCard(
    relay: RelayData,
    index: Int,
    relayStatus: RelayStatusData = RelayStatusData(),
    viewModel: NetPingViewModel
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Реле ${index + 1}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Состояние реле
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val isOn = relayStatus.relayStates[index] ?: false
                    Surface(
                        shape = CircleShape,
                        color = if (isOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(12.dp)
                    ) {}
                    Text(
                        text = if (isOn) "Вкл" else "Выкл",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = relay.name,
                onValueChange = { newName ->
                    viewModel.updateRelayData(index, relay.copy(name = newName))
                },
                label = { Text("Памятка") },
                placeholder = { Text("Описание назначения реле") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Управление реле
            var modeExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = modeExpanded,
                onExpandedChange = { modeExpanded = !modeExpanded }
            ) {
                OutlinedTextField(
                    value = relay.getModeDescription(),
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Управление реле") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = modeExpanded,
                    onDismissRequest = { modeExpanded = false }
                ) {
                    RelayData.getModeOptions().forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                viewModel.updateRelayData(index, relay.copy(mode = value))
                                modeExpanded = false
                            },
                            leadingIcon = if (value == relay.mode) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else null
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Кратковременное управление
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.controlRelay(index, RelayAction.FORCE_ON)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Power,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Кратковр. вкл",
                        fontSize = 12.sp,
                    )
                }

                OutlinedButton(
                    onClick = {
                        viewModel.controlRelay(index, RelayAction.FORCE_OFF)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerOff,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Кратковр. выкл",
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

// ViewModel для управления состоянием приложения
@HiltViewModel
class NetPingViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(NetPingUiState())


    private var autoUpdateJob: Job? = null
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("netping_devices", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_SAVED_DEVICES = "saved_devices"
        private const val KEY_CURRENT_DEVICE_ID = "current_device_id"
    }

    init {
        loadSavedDevices()
    }





    // Вспомогательные методы
    private fun loadSavedDevices() {
        try {
            val devicesJson = sharedPreferences.getString(KEY_SAVED_DEVICES, null)
            val currentDeviceId = sharedPreferences.getString(KEY_CURRENT_DEVICE_ID, null)

            val savedDevices = if (devicesJson != null) {
                try {
                    val type = object : TypeToken<List<SavedDevice>>() {}.type
                    gson.fromJson<List<SavedDevice>>(devicesJson, type) ?: emptyList()
                } catch (e: Exception) {
                    // Если ошибка парсинга, начинаем с пустого списка
                    emptyList()
                }
            } else {
                // Начинаем с пустого списка, пользователь сам добавит устройства
                emptyList()
            }

            _uiState.value = _uiState.value.copy(
                savedDevices = savedDevices,
                currentDeviceId = currentDeviceId ?: savedDevices.firstOrNull()?.id
            )
        } catch (e: Exception) {
            // В случае любой ошибки начинаем с чистого состояния
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

            // Используем commit() для немедленного сохранения
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

    private fun stopAutoUpdate() {
        autoUpdateJob?.cancel()
        autoUpdateJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopAutoUpdate()
        // Принудительно сохраняем все данные при закрытии
        saveDevices()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetPingApp(viewModel: NetPingViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deviceToDelete by remember { mutableStateOf<SavedDevice?>(null) }

    // Диалог подтверждения удаления
    if (showDeleteDialog && deviceToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                deviceToDelete = null
            },
            title = {
                Text(
                    text = "Удалить устройство?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Вы действительно хотите удалить устройство:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = deviceToDelete!!.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = deviceToDelete!!.ipAddress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Все сохраненные данные и настройки будут потеряны.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDevice(deviceToDelete!!.id)
                        showDeleteDialog = false
                        deviceToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Удалить")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showDeleteDialog = false
                        deviceToDelete = null
                    }
                ) {
                    Text("Отмена")
                }
            }
        )
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            // Для успешных операций показываем Toast дольше
            val duration = if (error.startsWith("✅")) {
                Toast.LENGTH_LONG
            } else {
                Toast.LENGTH_LONG
            }
            Toast.makeText(context, error, duration).show()

            // Для успешных сообщений ждем дольше перед очисткой
            if (error.startsWith("✅")) {
                delay(3000) // 3 секунды для успешных операций
            } else {
                delay(2000) // 2 секунды для ошибок
            }
            viewModel.clearError()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .padding(bottom = if (uiState.isConnected) 0.dp else 80.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Modern Header with device management
            DeviceManagerHeader(
                uiState = uiState,
                viewModel = viewModel,
                onDeleteDevice = { device ->
                    deviceToDelete = device
                    showDeleteDialog = true
                }
            )

            // Content area с поддержкой нескольких устройств
            if (uiState.savedDevices.any { it.isConnected }) {
                DeviceContentPager(
                    uiState = uiState,
                    viewModel = viewModel
                )
            } else if (uiState.isConnected) {
                // Modern loading state
                ElevatedCard(
                    modifier = Modifier.fillMaxSize(),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 4.dp
                            )
                            Text(
                                text = "Загрузка данных...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoTab(deviceData: NetPingDeviceData) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Device info
        item {
            SimpleDataCard(
                title = "Информация об устройстве",
                icon = Icons.Default.DeviceHub,
                data = listOf(
                    "Имя устройства" to (deviceData.deviceInfo?.hostname ?: "N/A"),
                    "Местонахождение" to (deviceData.deviceInfo?.location ?: "N/A"),
                    "Контакт" to (deviceData.deviceInfo?.contact ?: "N/A"),
                    "Серийный номер" to (deviceData.deviceInfo?.serialNumber ?: "N/A"),
                    "MAC адрес" to (deviceData.networkInterfaces.firstOrNull()?.macAddress ?: "N/A"),
                    "Модель" to (deviceData.deviceInfo?.model ?: "N/A"),
                    "Версия ПО" to (deviceData.deviceInfo?.firmware ?: "N/A"),
                    "Версия железа" to (deviceData.deviceInfo?.hardwareVersion ?: "N/A"),
                    "Время работы" to (deviceData.deviceInfo?.uptime ?: "N/A")
                )
            )
        }

        // Network settings
        if (deviceData.networkInterfaces.isNotEmpty()) {
            item {
                SimpleDataCard(
                    title = "Настройки сети",
                    icon = Icons.Default.NetworkCheck,
                    data = deviceData.networkInterfaces.flatMap { networkInterface ->
                        listOf(
                            "IP адрес" to networkInterface.ipAddress,
                            "Маска подсети" to networkInterface.subnetMask,
                            "Шлюз" to networkInterface.gateway
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun TermoSensorsTab(deviceData: NetPingDeviceData, viewModel: NetPingViewModel) {
    val termoData = deviceData.termoData

    val scope = rememberCoroutineScope()
    var notifySensorIndex by remember { mutableStateOf<Int?>(null) }
    var notifySettings by remember { mutableStateOf<NotifySettings?>(null) }
    var editNotifySettings by remember { mutableStateOf<NotifySettings?>(null) }
    var notifyIsLoading by remember { mutableStateOf(false) }

    fun toggleBit(mask: Int, bit: Int, enabled: Boolean): Int {
        return if (enabled) mask or bit else mask and bit.inv()
    }

    if (notifySensorIndex != null) {
        val sensorCh = notifySensorIndex!! // 0-based channel index in веб

        AlertDialog(
            onDismissRequest = { notifySensorIndex = null },
            title = {
                Text("Уведомления для термодатчика ${sensorCh + 1}")
            },
            text = {
                // Перехватываем в val для Smart Cast (без !!)
                val currentSettings = editNotifySettings

                if (notifyIsLoading || currentSettings == null) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()), // Скролл обязателен!
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Стандартный набор уведомлений
                        val standardOptions = listOf(
                            NotifyOption(1, "Журнал"),
                            NotifyOption(2, "Syslog"),
                            NotifyOption(4, "E-mail"),
                            NotifyOption(8, "SMS"),
                            NotifyOption(16, "SNMP Trap")
                        )

                        // Набор для отчета с ОТКЛЮЧЕННЫМИ (серенькими) чекбоксами
                        val reportOptions = listOf(
                            NotifyOption(1, "Журнал", isEnabled = false),
                            NotifyOption(2, "Syslog", isEnabled = false),
                            NotifyOption(4, "E-mail"),
                            NotifyOption(8, "SMS"),
                            NotifyOption(16, "SNMP Trap", isEnabled = false)
                        )

                        NotifySection(
                            title = "Температура выше нормы",
                            mask = currentSettings.highMask,
                            options = standardOptions,
                            onMaskChange = { editNotifySettings = currentSettings.copy(highMask = it) }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        NotifySection(
                            title = "Температура в норме",
                            mask = currentSettings.normMask,
                            options = standardOptions,
                            onMaskChange = { editNotifySettings = currentSettings.copy(normMask = it) }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        NotifySection(
                            title = "Температура ниже нормы",
                            mask = currentSettings.lowMask,
                            options = standardOptions,
                            onMaskChange = { editNotifySettings = currentSettings.copy(lowMask = it) }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        NotifySection(
                            title = "Отказ датчика",
                            mask = currentSettings.failMask,
                            options = standardOptions,
                            onMaskChange = { editNotifySettings = currentSettings.copy(failMask = it) }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        NotifySection(
                            title = "Периодический отчёт",
                            mask = currentSettings.reportMask,
                            options = reportOptions, // Передаем список с выключенными элементами
                            onMaskChange = { editNotifySettings = currentSettings.copy(reportMask = it) }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        editNotifySettings?.let { viewModel.saveTermoNotifySettings(sensorCh, it) }
                        notifySensorIndex = null
                    },
                    enabled = !notifyIsLoading && editNotifySettings != null
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { notifySensorIndex = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Термодатчики",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (deviceData.relayStatus.lastUpdate > 0) {
                        Text(
                            text = "Последнее обновление: ${formatLastUpdate(deviceData.relayStatus.lastUpdate)}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = { viewModel.saveTermoSensors() },
                    enabled = termoData.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Применить",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        if (termoData.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Нет данных термодатчиков")
                    }
                }
            }
        } else {
            itemsIndexed(termoData, key = { _, s -> s.id.toString() }) { index, sensor ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Датчик ${sensor.id}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Text(
                                text = sensor.getStatusText(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = sensor.name,
                                onValueChange = { newName ->
                                    viewModel.updateTermoSensor(
                                        index,
                                        sensor.copy(name = newName)
                                    )
                                },
                                label = { Text("Имя (до 16)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Текущее значение",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${sensor.tVal} °C",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Статус",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = sensor.getStatusText(),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Нижняя граница нормы",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = sensor.bottom.toString(),
                                    onValueChange = { newValue ->
                                        newValue.toIntOrNull()?.let { v ->
                                            viewModel.updateTermoSensor(index, sensor.copy(bottom = v))
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Верхняя граница нормы",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = sensor.top.toString(),
                                    onValueChange = { newValue ->
                                        newValue.toIntOrNull()?.let { v ->
                                            viewModel.updateTermoSensor(index, sensor.copy(top = v))
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedButton(
                            onClick = {
                                val ch = sensor.id - 1
                                notifyIsLoading = true
                                notifySettings = null
                                editNotifySettings = null
                                notifySensorIndex = ch
                                scope.launch {
                                    notifySettings = viewModel.fetchTermoNotifySettings(ch)
                                    editNotifySettings = notifySettings
                                    notifyIsLoading = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Уведомления при смене статуса датчика")
                        }
                    }
                }
            }
        }
    }
}

data class NotifyOption(val bit: Int, val label: String, val isEnabled: Boolean = true)

@Composable
fun NotifySection(
    title: String,
    mask: Int,
    options: List<NotifyOption>,
    onMaskChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        options.chunked(2).forEach { rowOptions ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowOptions.forEach { option ->
                    LabeledCheckbox(
                        label = option.label,
                        checked = (mask and option.bit) != 0,
                        isEnabled = option.isEnabled, // Передаем состояние
                        onCheckedChange = { checked ->
                            onMaskChange(toggleBit(mask, option.bit, checked))
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowOptions.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun LabeledCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true // Новое свойство
) {
    // Если чекбокс выключен, делаем текст полупрозрачным (серым)
    val textAlpha = if (isEnabled) 1f else 0.5f

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                enabled = isEnabled, // Текст нельзя нажать, если чекбокс выключен
                onClick = { onCheckedChange(!checked) }
            )
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            enabled = isEnabled // Выключаем сам чекбокс
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.alpha(textAlpha) // Применяем прозрачность к тексту
        )
    }
}
// Если у вас еще не вынесена эта функция
private fun toggleBit(mask: Int, bit: Int, enabled: Boolean): Int {
    return if (enabled) mask or bit else mask and bit.inv()
}



@Composable
fun RhTab(deviceData: NetPingDeviceData, viewModel: NetPingViewModel) {
    val rh = deviceData.rhStatData
    val rhHigh = deviceData.rhHigh
    val rhLow = deviceData.rhLow

    val scope = rememberCoroutineScope()
    var notifOpen by remember { mutableStateOf(false) }
    var notifySettings by remember { mutableStateOf<NotifySettings?>(null) }
    var editNotifySettings by remember { mutableStateOf<NotifySettings?>(null) }
    var notifyIsLoading by remember { mutableStateOf(false) }

    fun toggleBit(mask: Int, bit: Int, enabled: Boolean): Int {
        return if (enabled) mask or bit else mask and bit.inv()
    }

    if (notifOpen) {
        AlertDialog(
            onDismissRequest = { notifOpen = false },
            title = { Text("Уведомления для датчика влажности") },
            text = {
                // Перехватываем в локальную переменную, чтобы избавиться от !!
                val currentSettings = editNotifySettings

                if (notifyIsLoading || currentSettings == null) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 3.dp
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()), // Обязательно для длинных списков в Alert!
                        verticalArrangement = Arrangement.spacedBy(16.dp) // Отступы между секциями
                    ) {
                        // Стандартный набор уведомлений
                        val standardOptions = listOf(
                            NotifyOption(1, "Журнал"),
                            NotifyOption(2, "Syslog"),
                            NotifyOption(4, "E-mail"),
                            NotifyOption(8, "SMS"),
                            NotifyOption(16, "SNMP Trap")
                        )

                        // Набор для отчетов (только почта и смс)
                        val reportOptions = listOf(
                            NotifyOption(4, "E-mail"),
                            NotifyOption(8, "SMS")
                        )

                        NotifySection(
                            title = "Влажность выше нормы",
                            mask = currentSettings.highMask,
                            options = standardOptions,
                            onMaskChange = {
                                editNotifySettings = currentSettings.copy(highMask = it)
                            }
                        )

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(
                                alpha = 0.5f
                            )
                        )

                        NotifySection(
                            title = "Влажность в норме",
                            mask = currentSettings.normMask,
                            options = standardOptions,
                            onMaskChange = {
                                editNotifySettings = currentSettings.copy(normMask = it)
                            }
                        )

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(
                                alpha = 0.5f
                            )
                        )

                        NotifySection(
                            title = "Влажность ниже нормы",
                            mask = currentSettings.lowMask,
                            options = standardOptions,
                            onMaskChange = {
                                editNotifySettings = currentSettings.copy(lowMask = it)
                            }
                        )

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(
                                alpha = 0.5f
                            )
                        )

                        NotifySection(
                            title = "Отказ датчика",
                            mask = currentSettings.failMask,
                            options = standardOptions,
                            onMaskChange = {
                                editNotifySettings = currentSettings.copy(failMask = it)
                            }
                        )

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(
                                alpha = 0.5f
                            )
                        )

                        NotifySection(
                            title = "Периодический отчёт",
                            mask = currentSettings.reportMask,
                            options = reportOptions,
                            onMaskChange = {
                                editNotifySettings = currentSettings.copy(reportMask = it)
                            }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        editNotifySettings?.let { viewModel.saveRhNotifySettings(it) }
                        notifOpen = false
                    }
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { notifOpen = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Влажность",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (deviceData.relayStatus.lastUpdate > 0) {
                        Text(
                            text = "Последнее обновление: ${formatLastUpdate(deviceData.relayStatus.lastUpdate)}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = { viewModel.saveRhSensorConfig() },
                    enabled = rh != null && rhHigh != null && rhLow != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Применить",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        if (rh == null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Нет данных датчика влажности")
                    }
                }
            }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SimpleDataCard(
                        title = "Датчик относительной влажности",
                        icon = Icons.Default.WaterDrop,
                        data = listOf(
                            "Статус датчика" to rh.getStatusText(),
                            "Относительная влажность" to "${rh.rhValue} %",
                            "Температура" to String.format(Locale.getDefault(), "%.1f °C", rh.tValueC),
                            "Точка росы" to (rh.dewPointC?.let { "$it °C" } ?: "-")
                        )
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = (rhLow ?: 0).toString(),
                            onValueChange = { newValue ->
                                newValue.toIntOrNull()?.let { v -> viewModel.updateRhConfig(rhHigh, v) }
                            },
                            label = { Text("Нижняя граница") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = (rhHigh ?: 0).toString(),
                            onValueChange = { newValue ->
                                newValue.toIntOrNull()?.let { v -> viewModel.updateRhConfig(v, rhLow) }
                            },
                            label = { Text("Верхняя граница") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            notifOpen = true
                            notifyIsLoading = true
                            scope.launch {
                                notifySettings = viewModel.fetchRhNotifySettings()
                                editNotifySettings = notifySettings
                                notifyIsLoading = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Уведомления при смене статуса датчика")
                    }
                }
            }
        }
    }
}

@Composable
fun LogTab(deviceData: NetPingDeviceData) {
    val logText = deviceData.logText
    val lines = remember(logText) {
        val all = logText.lines()
        (if (all.size > 300) all.takeLast(300) else all).reversed()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier.fillMaxSize(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            if (lines.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Нет данных журнала")
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(lines) { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LogicTab(deviceData: NetPingDeviceData, viewModel: NetPingViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Column {
        // Modern sub-tabs with icons
        ScrollableTabRow(
            selectedTabIndex = uiState.selectedLogicTab,
            modifier = Modifier.fillMaxWidth(),
            edgePadding = 16.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[uiState.selectedLogicTab]),
                    height = 3.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            divider = {}
        ) {
            Tab(
                selected = uiState.selectedLogicTab == 0,
                onClick = { viewModel.selectLogicTab(0) },
                modifier = Modifier.height(48.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Rule,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Правила",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
            Tab(
                selected = uiState.selectedLogicTab == 1,
                onClick = { viewModel.selectLogicTab(1) },
                modifier = Modifier.height(48.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Thermostat,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Термостат",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
            Tab(
                selected = uiState.selectedLogicTab == 2,
                onClick = { viewModel.selectLogicTab(2) },
                modifier = Modifier.height(48.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NetworkPing,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Пингер",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
            Tab(
                selected = uiState.selectedLogicTab == 3,
                onClick = { viewModel.selectLogicTab(3) },
                modifier = Modifier.height(48.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Hub,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "SNMP",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Sub-tab content
        when (uiState.selectedLogicTab) {
            0 -> LogicRulesTab(deviceData = deviceData, viewModel = viewModel)
            1 -> TstatTab(deviceData = deviceData, viewModel = viewModel)
            2 -> PingerTab(deviceData = deviceData, viewModel = viewModel)
            3 -> SnmpSetterTab(deviceData = deviceData, viewModel = viewModel)
        }
    }
}

@Composable
fun CompactLogicRuleCard(
    rule: LogicRule,
    ruleNumber: Int,
    onRuleUpdate: (LogicRule) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    deviceData: NetPingDeviceData
) {
    var isExpanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            // Компактная строка правила
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Левая часть: кнопки перемещения + номер + статус
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Увеличенные кнопки перемещения
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onMoveUp()
                            },
                            enabled = canMoveUp,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = "Переместить вверх",
                                modifier = Modifier.size(24.dp),
                                tint = if (canMoveUp)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        }

                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onMoveDown()
                            },
                            enabled = canMoveDown,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Переместить вниз",
                                modifier = Modifier.size(24.dp),
                                tint = if (canMoveDown)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        }
                    }

                    // Номер правила
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                text = "$ruleNumber",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    // Компактный переключатель статуса
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (rule.isEnabled)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .clickable {
                                val newFlags = if (rule.isEnabled) {
                                    rule.flags and 1.inv()
                                } else {
                                    rule.flags or 1
                                }
                                onRuleUpdate(rule.copy(flags = newFlags))
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = if (rule.isEnabled) Icons.Default.Check else Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(10.dp),
                                tint = if (rule.isEnabled)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (rule.isEnabled) "ВКЛ" else "ВЫКЛ",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                fontWeight = FontWeight.Medium,
                                color = if (rule.isEnabled)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Правая часть: описание + кнопка расширения
                Row(
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Компактное описание правила
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (rule.isTrigger) Icons.Default.FlashOn else Icons.Default.Loop,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${if (rule.isTrigger) "Если" else "Пока"} ${rule.getInputDescription(deviceData.deviceInfo?.model ?: "")} ${rule.getConditionDescription(deviceData.deviceInfo?.model ?: "")} → ${rule.getActionDescription()} → ${rule.getOutputDescription(deviceData.deviceInfo?.model ?: "")}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Кнопка расширения
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded)
                                Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Свернуть" else "Развернуть",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Раскрывающаяся секция настроек
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Компактные поля настроек в сетке 2x3
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            CompactDropdownField(
                                label = "Тип",
                                value = if (rule.isTrigger) 1 else 0,
                                options = LogicRule.getRuleTypeOptions(),
                                onValueChange = { newType ->
                                    val newFlags = if (newType == 1) {
                                        rule.flags or 2
                                    } else {
                                        rule.flags and 2.inv()
                                    }
                                    onRuleUpdate(rule.copy(flags = newFlags))
                                },
                                modifier = Modifier.weight(1f)
                            )

                            CompactDropdownField(
                                label = "Вход",
                                value = rule.input,
                                options = LogicRule.getInputOptions(deviceData.deviceInfo?.model ?: ""),
                                onValueChange = { newInput ->
                                    onRuleUpdate(rule.copy(input = newInput))
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            CompactDropdownField(
                                label = "Условие",
                                value = rule.condition,
                                options = LogicRule.getConditionOptions(rule.input, deviceData.deviceInfo?.model ?: ""),
                                onValueChange = { newCondition ->
                                    onRuleUpdate(rule.copy(condition = newCondition))
                                },
                                modifier = Modifier.weight(1f)
                            )

                            CompactDropdownField(
                                label = "Действие",
                                value = rule.action,
                                options = LogicRule.getActionOptions(rule.isTrigger),
                                onValueChange = { newAction ->
                                    onRuleUpdate(rule.copy(action = newAction))
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        CompactDropdownField(
                            label = "Выход",
                            value = rule.output,
                            options = LogicRule.getOutputOptions(deviceData.deviceInfo?.model ?: ""),
                            onValueChange = { newOutput ->
                                onRuleUpdate(rule.copy(output = newOutput))
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactDropdownField(
    label: String,
    value: Int,
    options: List<Pair<Int, String>>,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedOption = options.find { it.first == value }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedOption?.second ?: "Неизвестно",
            onValueChange = { },
            readOnly = true,
            label = {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium
                )
            },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            shape = RoundedCornerShape(8.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
                .height(56.dp)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(
                MaterialTheme.colorScheme.surface,
                RoundedCornerShape(8.dp)
            )
        ) {
            options.forEach { (optionValue, optionLabel) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = optionLabel,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    onClick = {
                        onValueChange(optionValue)
                        expanded = false
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    leadingIcon = if (optionValue == value) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else null
                )
            }
        }
    }
}

@Composable
fun SimpleDataCard(
    title: String,
    data: List<Pair<String, String>>,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Заголовок секции
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Данные в простом формате
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            data.forEach { (key, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = key,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (data.indexOf(Pair(key, value)) < data.size - 1) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }
}
