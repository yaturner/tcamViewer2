package com.das.tcamviewer2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.das.tcamviewer2.factory.PaletteFactory
import com.das.tcamviewer2.services.CameraService
import com.das.tcamviewer2.ui.CameraScreen
import com.das.tcamviewer2.ui.LibraryScreen
import com.das.tcamviewer2.ui.SettingsScreen
import com.das.tcamviewer2.ui.theme.TcamViewer2Theme
import com.das.tcamviewer2.utils.CameraUtils
import com.das.tcamviewer2.utils.Utils
import timber.log.Timber

lateinit var cameraService: CameraService
lateinit var settingsDataManager: SettingsDataManager
lateinit var cameraUtils: CameraUtils
lateinit var paletteFactory: PaletteFactory
lateinit var utils: Utils

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Timber.plant(Timber.DebugTree())
        if (!::cameraService.isInitialized) cameraService = CameraService()
        if (!::settingsDataManager.isInitialized) settingsDataManager = SettingsDataManager(this)
        if (!::cameraUtils.isInitialized) cameraUtils = CameraUtils(this)
        if (!::paletteFactory.isInitialized) paletteFactory = PaletteFactory()
        setContent {
            TcamViewer2Theme {
                MainScreen()
            }
        }
        utils = Utils(this)
    }

//    public fun getImage() : Bitmap {
//        // Example inside a ViewModel or CoroutineScope enabled Activity
//        lifecycleScope.launch {
//            // 1. Call your bound service reference
//            val resultJson = cameraService.sendCmd(
//                cmd = "{\"cmd\": \"get_device_info\"}",
//                expectedKey = "get_device_info"
//            )
//
//            // 2. Direct use of returned data on Main Thread context!
//            val serialNumber = resultJson.optString("serial_number", "Unknown")
//            return Bitmap.createBitmap()
//        }
//    }
}

enum class ScreenTab(val title: String, val icon: ImageVector) {
    Camera("Camera", Icons.Filled.CameraAlt),
    Settings("Settings", Icons.Filled.Settings),
    Library("Library", Icons.Filled.PhotoLibrary)
}

@Composable
fun MainScreen() {
    var selectedTabItem by remember { mutableIntStateOf(0) }
    var previousTabItem by remember { mutableIntStateOf(0) }
    val tabs = ScreenTab.entries.toTypedArray()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(modifier = Modifier.height(110.dp)) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTabItem == index,
                        onClick = {
                            previousTabItem = selectedTabItem
                            selectedTabItem = index
                        },
                        label = { Text(tab.title) },
                        icon = { Icon(imageVector = tab.icon, contentDescription = tab.title) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (tabs[selectedTabItem]) {
                ScreenTab.Camera -> CameraScreen()
                ScreenTab.Settings -> SettingsScreen(
                    onNavigateBack = { selectedTabItem = previousTabItem }
                )
                ScreenTab.Library -> LibraryScreen()
            }
        }
    }
}


@Composable
fun GenericScreen(name: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "Hello $name Screen!", fontSize = 24.sp)
    }
}

