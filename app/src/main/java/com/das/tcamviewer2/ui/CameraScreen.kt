package com.das.tcamviewer2.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.das.tcamviewer2.ui.camera.CameraViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    // Injects your new ViewModel architecture controller layer instance automatically
    viewModel: CameraViewModel = viewModel()
) {
    val displayImageWidth = 280.dp
    val displayImageHeight = 350.dp
    val colorBarWidth = 32.dp
    val histogramWidth = 64.dp

    // --- COLLECT REACTIVE APP STATE FROM VIEWMODEL ---
    val spotmeterText by viewModel.spotmeterTemp.collectAsState()
    val maxTempText by viewModel.maxTemp.collectAsState()
    val minTempText by viewModel.minTemp.collectAsState()
    val fpsText by viewModel.fpsCounter.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (viewModel.isConnected) "Thermal Viewer (Connected)" else "Thermal Viewer (Connecting...)",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF80C0FF))
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(start = 16.dp, end = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {

                // 1. MAIN PREVIEW AREA
                Box(
                    modifier = Modifier
                        .size(width = displayImageWidth, height = displayImageHeight)
                        .padding(end = 10.dp)
                ) {
                    Image(
                        painter = painterResource(id = android.R.drawable.ic_menu_camera),
                        contentDescription = "Main Camera Feed",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )

                    // Dynamically binds observed spotmeter temperature value overlay updates
                    Text(
                        text = spotmeterText,
                        fontSize = 12.sp,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(5.dp)
                    )
                }

                // 2. DIAGNOSTICS & HARDWARE TEMPERATURE SIDEBARS
                Column(
                    modifier = Modifier.height(displayImageHeight),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = maxTempText,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 5.dp)
                    )

                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = android.R.drawable.button_onoff_indicator_on),
                            contentDescription = "Color Bar Scale",
                            modifier = Modifier
                                .width(colorBarWidth)
                                .fillMaxHeight()
                                .padding(end = 5.dp),
                            contentScale = ContentScale.FillBounds
                        )

                        Image(
                            painter = painterResource(id = android.R.drawable.ic_menu_report_image),
                            contentDescription = "Histogram Chart",
                            modifier = Modifier
                                .width(histogramWidth)
                                .fillMaxHeight()
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                            contentScale = ContentScale.FillBounds
                        )
                    }

                    Text(
                        text = minTempText,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(start = 5.dp)
                    )
                }
            }

            // 3. TOP-RIGHT LIVE FRAMERATE COUNTER BOUNDARY
            Text(
                text = fpsText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            )
        }
    }
}
