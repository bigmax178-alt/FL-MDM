package com.example.firebaselabelapp.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BatteryUnknown
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.BottomEnd
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.firebaselabelapp.R
import com.example.firebaselabelapp.model.ItemButton
import com.example.firebaselabelapp.model.MenuButton
import com.example.firebaselabelapp.repository.FirestoreRepository
import com.example.firebaselabelapp.subscription.SubscriptionManager
import com.example.firebaselabelapp.ui.components.AddItemDialog
import com.example.firebaselabelapp.ui.components.AddMenuDialog
import com.example.firebaselabelapp.ui.components.EditItemDialog
import com.example.firebaselabelapp.ui.components.EditMenuDialog
import com.example.firebaselabelapp.ui.components.PrimaryButton
import com.example.firebaselabelapp.ui.components.YellowWarningBanner
import com.example.firebaselabelapp.ui.theme.FirebaseLabelAppTheme
import com.example.firebaselabelapp.ui.viewmodel.ItemViewModel
import com.example.firebaselabelapp.ui.viewmodel.ItemViewModelFactory
import com.example.firebaselabelapp.ui.viewmodel.MenuViewModel
import com.example.firebaselabelapp.ui.viewmodel.SessionState
import com.example.firebaselabelapp.ui.viewmodel.SharedViewModel
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import com.google.firebase.crashlytics.FirebaseCrashlytics

@Composable
private fun getBatteryIcon(level: Int, isCharging: Boolean): ImageVector {
    return when {
        isCharging -> Icons.Default.BatteryChargingFull
        level > 90 -> Icons.Default.BatteryFull
        level > 20 -> Icons.Default.BatteryStd
        level >= 0 -> Icons.Default.BatteryAlert
        else -> Icons.AutoMirrored.Filled.BatteryUnknown
    }
}

@Composable
fun BatteryStatus() {
    val context = LocalContext.current
    var batteryLevel by remember { mutableStateOf<Int?>(null) }
    var isCharging by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                batteryLevel =
                    if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else null

                val status: Int = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                isCharging =
                    status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        // Register the receiver for the first time to get the initial sticky intent
        val initialIntent = context.registerReceiver(receiver, filter)
        if (initialIntent != null) {
            // Manually trigger onReceive for the sticky intent
            receiver.onReceive(context, initialIntent)
        }


        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    if (batteryLevel != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(end = 4.dp)
        ) {
            val icon = getBatteryIcon(batteryLevel!!, isCharging)
            Icon(
                imageVector = icon,
                contentDescription = "Battery Status",
                tint = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$batteryLevel%",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CombinedMenuItemScreen(
    sharedViewModel: SharedViewModel,
    menuViewModel: MenuViewModel,
    repository: FirestoreRepository,
    onItemClick: (ItemButton) -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    val selectedMenuId by sharedViewModel.selectedMenuId.collectAsState()
    val selectedMenuName by sharedViewModel.selectedMenuName.collectAsState()

    var showAddMenuDialog by remember { mutableStateOf(false) }
    var showAddItemDialog by remember { mutableStateOf(false) }
    var isEditMode by remember { mutableStateOf(false) }

    val itemViewModel: ItemViewModel? = selectedMenuId?.let { menuId ->
        viewModel(
            key = menuId,
            factory = ItemViewModelFactory(menuId, repository)
        )
    }

    val isOnline by menuViewModel.isOnline.collectAsState()
    val menuSyncFailed by menuViewModel.lastSyncFailed.collectAsState()
    val isCompleteSyncRequired by menuViewModel.isCompleteSyncRequired.collectAsState()
    val sessionState by sharedViewModel.sessionState.collectAsState()

    val (showYellowBanner, timeUntilDeactivation) = when (val state = sessionState) {
        is SessionState.LoggedIn -> {
            val shouldShow =
                state.showRedBanner && state.timeUntilDeactivation > SubscriptionManager.RED_BANNER_DURATION
            Pair(shouldShow, state.timeUntilDeactivation)
        }

        else -> Pair(false, 0L)
    }

    val showSyncButton = !isOnline || isCompleteSyncRequired
    val appBarColor =
        if (showYellowBanner) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary



    FirebaseLabelAppTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        if (showYellowBanner) {
                            YellowWarningBanner(
                                timeUntilDeactivation = timeUntilDeactivation
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
//                                    Text(
//                                        "Категории и Продукты",
//                                        style = MaterialTheme.typography.titleLarge,
//                                        color = MaterialTheme.colorScheme.onPrimary
//                                    )
/*
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_time_tag_logo),
                                        contentScale = ContentScale.Fit,
                                        contentDescription = "TimeTag logo",
                                        modifier = Modifier
                                            .fillMaxHeight()
//                                            .padding(4.dp)
                                    )
*/
                                    IconButton(onClick = {
                                        FirebaseCrashlytics.getInstance()
                                            .log("CombinedScreen: Edit mode toggled to ${!isEditMode}")
                                        isEditMode = !isEditMode
                                    }) {
                                        Icon(
                                            imageVector = if (isEditMode) Icons.Filled.Done else Icons.Filled.Edit,
                                            contentDescription = if (isEditMode) "Выйти из режима редактирования" else "Войти в режим редактирования",
                                            tint = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                    if (!isOnline) {
                                        Text(
                                            "Офлайн режим",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                        )
                                    } else if (isCompleteSyncRequired) {
                                        Text(
                                            "Требуется синхронизация",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Yellow.copy(alpha = 0.9f)
                                        )
                                    }
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            FirebaseCrashlytics.getInstance()
                                .log("CombinedScreen: Back (Exit) clicked")
                            onBackClick()
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Home",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = appBarColor, // Use the calculated color here
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    actions = {
                        BatteryStatus()
                        if (showSyncButton) {
                            IconButton(
                                onClick = {
                                    menuViewModel.performDeltaSync()
                                    itemViewModel?.reload()
                                },
                                enabled = isOnline
                            ) {
                                Icon(
                                    painterResource(R.drawable.sync_24px),
                                    contentDescription = "Sync All Data",
                                    tint = if (isOnline) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
                                )
                            }
                        }
                        if (!isOnline) {
                            IconButton(onClick = { }) {
                                Icon(
                                    painterResource(R.drawable.cloud_off_24px),
                                    contentDescription = "Offline",
                                    tint = Color.Red
                                )
                            }
                        } else if (isCompleteSyncRequired) {
                            IconButton(onClick = { }) {
                                Icon(
                                    painterResource(R.drawable.cloud_off_24px),
                                    contentDescription = "Sync Needed",
                                    tint = Color.Yellow
                                )
                            }
                        }
                        IconButton(onClick = {
                            FirebaseCrashlytics.getInstance().log("CombinedScreen: Search clicked")
                            onSearchClick()
                        }) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        IconButton(onClick = {
                            FirebaseCrashlytics.getInstance()
                                .log("CombinedScreen: Settings clicked")
                            onSettingsClick()
                        }) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }

//                        IconButton( onClick = {
//                            FirebaseCrashlytics.getInstance().log("My test breadcrumb for the crash")
//                            FirebaseCrashlytics.getInstance().recordException(Exception("Test Non-Fatal Event"))
//                            throw RuntimeException("Test Crash")
//                        }) {
//                            Icon(
//                                Icons.Filled.BugReport,
//                                contentDescription = "Test Crash",
//                                tint = Color.Red
//                            )
//                        }
                    }
                )
            },
            floatingActionButton = {
                if (isEditMode && isOnline) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier.weight(4f),
                            contentAlignment = BottomEnd
                        ) {
                            if (selectedMenuId != null) {
                                FloatingActionButton(
                                    onClick = {
                                        FirebaseCrashlytics.getInstance()
                                            .log("CombinedScreen: FAB Add Item clicked")
                                        showAddItemDialog = true
                                    },
                                    containerColor = MaterialTheme.colorScheme.tertiary,
                                    contentColor = MaterialTheme.colorScheme.onTertiary,
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    Icon(Icons.Default.Add, "Add Item")
                                }
                            }
                        }
                        Box(
                            modifier = Modifier.weight(2f),
                            contentAlignment = BottomEnd
                        ) {
                            FloatingActionButton(
                                onClick = {
                                    FirebaseCrashlytics.getInstance()
                                        .log("CombinedScreen: FAB Add Menu clicked")
                                    showAddMenuDialog = true
                                },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(end = 16.dp)
                            ) {
                                Icon(Icons.Default.Add, "Add Menu")
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Row(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Box(
                    modifier = Modifier
                        .weight(2f)
                        .fillMaxHeight()
                        .padding(end = 8.dp)
                ) {
                    ItemsSection(
                        sharedViewModel = sharedViewModel,
                        itemViewModel = itemViewModel,
                        selectedMenuName = selectedMenuName,
                        onItemClick = onItemClick,
                        isEditMode = isEditMode,
                        isOnline = isOnline,
                        syncFailed = isCompleteSyncRequired
                    )
                }

                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 8.dp)
                ) {
                    MenusSection(
                        menuViewModel = menuViewModel,
                        selectedMenuId = selectedMenuId,
                        onMenuClick = { menuId, menuName ->
                            sharedViewModel.selectMenu(menuId, menuName)
                        },
                        isEditMode = isEditMode,
                        isOnline = isOnline,
                        syncFailed = menuSyncFailed
                    )
                }
            }
        }

        if (showAddMenuDialog) {
            AddMenuDialog(
                onAdd = { name ->
                    menuViewModel.addMenu(name)
                    showAddMenuDialog = false
                },
                onDismiss = { showAddMenuDialog = false }
            )
        }

        if (showAddItemDialog && selectedMenuId != null) {
            AddItemDialog(
                sharedViewModel,
                onAdd = { name, years, months, days, hours, description ->
                    itemViewModel?.addItem(name, years, months, days, hours, description)
                    showAddItemDialog = false
                },
                onDismiss = { showAddItemDialog = false }
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ItemsSection(
    sharedViewModel: SharedViewModel,
    itemViewModel: ItemViewModel?,
    selectedMenuName: String?,
    onItemClick: (ItemButton) -> Unit,
    isEditMode: Boolean,
    isOnline: Boolean,
    syncFailed: Boolean
) {
    var showEditItemDialog by remember { mutableStateOf(false) }
    var itemButtonToEdit by remember { mutableStateOf<ItemButton?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedMenuName != null) "Продукты из категории: $selectedMenuName"
                    else "Выберите категорию справа",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                if (!isOnline) {
                    Icon(
                        painterResource(R.drawable.cloud_off_24px),
                        contentDescription = "Items Offline",
                        tint = Color.Red,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                } else if (syncFailed) {
                    Icon(
                        painterResource(R.drawable.cloud_off_24px),
                        contentDescription = "Items Need Sync",
                        tint = Color.Yellow,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            when {
                itemViewModel == null -> {
                    Text(
                        "Выберите категорию из списка справа",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }

                itemViewModel.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                itemViewModel.itemButtons.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "В этой категории пока нет продуктов",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )

                        if (!isOnline || syncFailed) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                if (!isOnline) "(Данные могут быть недоступны в офлайн режиме)"
                                else "(Некоторые продукты могут быть недоступны - требуется полная синхронизация)",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 130.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(itemViewModel.itemButtons) { item ->
                            val tooltipState = rememberTooltipState()

                            TooltipBox(
                                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                tooltip = {
                                    PlainTooltip {
                                        Text(item.name)
                                    }
                                },
                                state = tooltipState
                            ) {
                                Box(
                                    modifier = Modifier
                                        .height(120.dp)
                                        .animateItem(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    PrimaryButton(
                                        text = item.name,
                                        onClick = {
                                            if (!isEditMode) {
                                                FirebaseCrashlytics.getInstance()
                                                    .log("CombinedScreen: Item clicked - ${item.name}")
                                                onItemClick(item)
                                            } else {
                                                FirebaseCrashlytics.getInstance()
                                                    .log("CombinedScreen: Edit Item clicked - ${item.name}")
                                                showEditItemDialog = true
                                                itemButtonToEdit = item
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(100.dp),
                                        textStyle = MaterialTheme.typography.bodyMedium,
                                        contentPadding = PaddingValues(4.dp),
                                        maxLines = 3
                                    )

                                    if (isEditMode && isOnline) {
                                        IconButton(
                                            onClick = {
                                                FirebaseCrashlytics.getInstance()
                                                    .log("CombinedScreen: Delete Item clicked - ${item.name}")
                                                itemViewModel.deleteItem(item)
                                            },
                                            modifier = Modifier.align(Alignment.TopEnd)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete Item",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditItemDialog) {
        if (itemButtonToEdit == null) {
            showEditItemDialog = false
            Log.w("CombinedMenuItemScreen", "Tried to open item editing for null item")
        } else {
            EditItemDialog(
                sharedViewModel = sharedViewModel,
                itemButton = itemButtonToEdit!!,
                onEdit = { updatedItem ->
                    itemViewModel?.updateItem(updatedItem)
                    showEditItemDialog = false
                },
                onDismiss = { showEditItemDialog = false }
            )
        }
    }
}

@Composable
private fun MenusSection(
    menuViewModel: MenuViewModel,
    selectedMenuId: String?,
    onMenuClick: (String, String) -> Unit,
    isEditMode: Boolean,
    isOnline: Boolean,
    syncFailed: Boolean
) {
    val menus by menuViewModel.menus.collectAsState()
    var showEditMenuDialog by remember { mutableStateOf(false) }
    var menuButtonToEdit by remember { mutableStateOf<MenuButton?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Категории",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )

                if (!isOnline) {
                    Icon(
                        painterResource(R.drawable.cloud_off_24px),
                        contentDescription = "Menus Offline",
                        tint = Color.Red,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                } else if (syncFailed) {
                    Icon(
                        painterResource(R.drawable.cloud_off_24px),
                        contentDescription = "Complete Sync Needed",
                        tint = Color.Yellow,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            when {
                menuViewModel.isLoading.collectAsState().value -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                menus.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Нет доступных категорий",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )

                        if (!isOnline || syncFailed) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                if (!isOnline) "(Данные могут быть недоступны в офлайн режиме)"
                                else "(Данные могут быть устаревшими - требуется синхронизация)",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(menus) { menu ->
                            MenuButtonItem(
                                menu = menu,
                                isSelected = menu.id == selectedMenuId,
                                onClick = {
                                    FirebaseCrashlytics.getInstance()
                                        .log("CombinedScreen: Menu clicked - ${menu.name}")
                                    onMenuClick(menu.id ?: "", menu.name)
                                },
                                onLongClick = {
                                    if (isEditMode) {
                                        FirebaseCrashlytics.getInstance()
                                            .log("CombinedScreen: Edit Menu long-clicked - ${menu.name}")
                                        showEditMenuDialog = true
                                        menuButtonToEdit = menu
                                    }
                                },
                                onDelete = if (isEditMode && isOnline) {
                                    {
                                        FirebaseCrashlytics.getInstance()
                                            .log("CombinedScreen: Delete Menu clicked - ${menu.name}")
                                        menuViewModel.deleteMenu(menu)
                                    }
                                } else null
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditMenuDialog) {
        if (menuButtonToEdit == null) {
            Log.e("CombinedMenuItemScreen", "Trying to edit null menu button")
        } else {
            EditMenuDialog(
                menuButtonToEdit!!,
                onEdit = { updatedMenuButton ->
                    menuViewModel.updateMenu(updatedMenuButton)
                    showEditMenuDialog = false
                },
                onDismiss = { showEditMenuDialog = false }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MenuButtonItem(
    menu: MenuButton,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 1.dp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .height(60.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = menu.name.uppercase(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            )

            if (onDelete != null) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Menu",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}