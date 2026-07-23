package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.db.ConnectionSession
import com.example.model.VpnServer
import com.example.viewmodel.VpnViewModel
import java.text.SimpleDateFormat
import java.util.*

// Colors palette - Professional Polish Theme (iOS-like Dark Premium)
val Slate900 = Color(0xFF0C0C12) // Ultra-dark canvas
val Slate800 = Color(0xFF161622) // iOS-like Premium Translucent Card Surface
val Slate700 = Color(0xFF282837) // iOS-like Thin Border
val CyanNeon = Color(0xFF9D7BFF) // Glowing Indigo/Violet Accent
val GreenGlow = Color(0xFF30D158) // iOS Apple Green
val OrangeGlow = Color(0xFFFF9F0A) // iOS Apple Orange
val RedAlert = Color(0xFFFF453A) // iOS Apple Red
val Slate400 = Color(0xFF8E8E93) // iOS system Gray (Sub text)
val Slate200 = Color(0xFFF2F2F7) // iOS system Off-White (Main text)

@Composable
fun SiamVpnApp(viewModel: VpnViewModel) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(0) }

    val isConnected by viewModel.isConnected.collectAsStateWithLifecycle()
    val isConnecting by viewModel.isConnecting.collectAsStateWithLifecycle()
    val selectedServer by viewModel.selectedServer.collectAsStateWithLifecycle()
    val vpnPrepareIntent by viewModel.vpnPrepareIntent.collectAsStateWithLifecycle()
    val vpnAlert by viewModel.vpnAlert.collectAsStateWithLifecycle()

    // Trigger Toast Notification on alert
    LaunchedEffect(vpnAlert) {
        vpnAlert?.let { alert ->
            Toast.makeText(context, "${alert.title}\n${alert.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Activity launcher for system VPN permission prompt
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.clearPrepareIntent()
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.startVpnService(context)
            Toast.makeText(context, "สิทธิ์ VPN ได้รับอนุมัติ เริ่มการเชื่อมต่อ...", Toast.LENGTH_SHORT).show()
        } else {
            viewModel.cancelConnection()
            Toast.makeText(context, "ต้องการสิทธิ์การใช้งาน VPN เพื่อเปิดอุโมงค์เชื่อมต่อ", Toast.LENGTH_LONG).show()
        }
    }

    // Trigger Permission Prompt when requested by ViewModel
    LaunchedEffect(vpnPrepareIntent) {
        vpnPrepareIntent?.let { intent ->
            vpnPermissionLauncher.launch(intent)
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Slate900,
            surface = Slate800,
            primary = CyanNeon,
            secondary = GreenGlow,
            tertiary = OrangeGlow,
            onBackground = Slate200,
            onSurface = Color.White
        )
    ) {
        Scaffold(
            bottomBar = {
                VpnBottomNavigation(
                    currentTab = currentTab,
                    onTabSelected = { currentTab = it }
                )
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Slate900)
                    .padding(innerPadding)
            ) {
                // Background futuristic vector accent
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(CyanNeon.copy(alpha = 0.05f), Color.Transparent),
                            center = Offset(size.width * 0.5f, size.height * 0.2f),
                            radius = size.width * 0.7f
                        )
                    )
                }

                when (currentTab) {
                    0 -> HomeScreen(viewModel = viewModel, onNavigateToServers = { currentTab = 1 })
                    1 -> ServersScreen(viewModel = viewModel)
                    2 -> SplitTunnelingScreen(viewModel = viewModel)
                    3 -> HistoryLogsScreen(viewModel = viewModel)
                }

                // Top Notification / Toast Banner for VPN Connection Drop & Timeout
                AnimatedVisibility(
                    visible = vpnAlert != null,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    vpnAlert?.let { alert ->
                        VpnAlertNotificationBanner(
                            alert = alert,
                            onReconnect = {
                                viewModel.dismissAlert()
                                viewModel.toggleVpnConnection(context)
                            },
                            onDismiss = {
                                viewModel.dismissAlert()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VpnBottomNavigation(currentTab: Int, onTabSelected: (Int) -> Unit) {
    NavigationBar(
        containerColor = Slate800,
        tonalElevation = 8.dp,
        modifier = Modifier.border(
            width = 0.5.dp,
            color = Slate700,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        )
    ) {
        val tabItems = listOf(
            Triple(0, Icons.Default.Shield, "เชื่อมต่อ"),
            Triple(1, Icons.Default.Public, "เซิร์ฟเวอร์"),
            Triple(2, Icons.Default.AppSettingsAlt, "สลับแอพ"),
            Triple(3, Icons.Default.History, "ประวัติ/ล็อก")
        )

        tabItems.forEach { (index, icon, label) ->
            NavigationBarItem(
                selected = currentTab == index,
                onClick = { onTabSelected(index) },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        modifier = Modifier.size(22.dp)
                    )
                },
                label = {
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = if (currentTab == index) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = CyanNeon,
                    selectedTextColor = CyanNeon,
                    indicatorColor = Color.Transparent, // Clean iOS tab-bar look
                    unselectedIconColor = Slate400,
                    unselectedTextColor = Slate400
                ),
                modifier = Modifier.testTag(
                    when (index) {
                        0 -> "nav_home_tab"
                        1 -> "nav_servers_tab"
                        2 -> "nav_split_tab"
                        else -> "nav_history_tab"
                    }
                )
            )
        }
    }
}

@Composable
fun HomeScreen(viewModel: VpnViewModel, onNavigateToServers: () -> Unit) {
    val isConnected by viewModel.isConnected.collectAsStateWithLifecycle()
    val isConnecting by viewModel.isConnecting.collectAsStateWithLifecycle()
    val selectedServer by viewModel.selectedServer.collectAsStateWithLifecycle()
    val connectionDuration by viewModel.connectionDuration.collectAsStateWithLifecycle()
    val bytesUploaded by viewModel.bytesUploaded.collectAsStateWithLifecycle()
    val bytesDownloaded by viewModel.bytesDownloaded.collectAsStateWithLifecycle()

    val uploadSpeedHistory by viewModel.uploadSpeedHistory.collectAsStateWithLifecycle()
    val downloadSpeedHistory by viewModel.downloadSpeedHistory.collectAsStateWithLifecycle()

    val selectedProtocol by viewModel.selectedProtocol.collectAsStateWithLifecycle()
    val isCustomConfigEnabled by viewModel.isCustomConfigEnabled.collectAsStateWithLifecycle()
    val customHost by viewModel.customHost.collectAsStateWithLifecycle()
    val customPort by viewModel.customPort.collectAsStateWithLifecycle()
    val customUsername by viewModel.customUsername.collectAsStateWithLifecycle()
    val customPassword by viewModel.customPassword.collectAsStateWithLifecycle()
    val customSni by viewModel.customSni.collectAsStateWithLifecycle()
    val customPayload by viewModel.customPayload.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var showSessionLogsSheet by remember { mutableStateOf(false) }

    if (showSessionLogsSheet) {
        ConnectionSessionLogsBottomSheet(
            viewModel = viewModel,
            onDismiss = { showSessionLogsSheet = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Title Header (Professional Polish Design)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Shield container (rounded-xl background, tint text-primary)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(CyanNeon, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Shield logo",
                        tint = Color(0xFF381E72), // ThemeOnPrimary
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = "SiamVPN",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = Slate200, // ThemeTextMain
                    letterSpacing = (-0.5).sp
                )
            }
            
            IconButton(
                onClick = { showSessionLogsSheet = true },
                modifier = Modifier
                    .size(40.dp)
                    .testTag("open_session_logs_sheet_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.ReceiptLong,
                    contentDescription = "Session Logs",
                    tint = CyanNeon,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Session Data Usage Summary Header Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Slate800),
            modifier = Modifier
                .fillMaxWidth()
                .border(0.5.dp, Slate700, RoundedCornerShape(16.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Sent / Uploaded
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(OrangeGlow.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = "Sent",
                            tint = OrangeGlow,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "ส่งออก (Sent)",
                            fontSize = 10.sp,
                            color = Slate400,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = formatBytes(bytesUploaded),
                            fontSize = 12.sp,
                            color = Slate200,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Vertical Divider
                Box(
                    modifier = Modifier
                        .height(22.dp)
                        .width(1.dp)
                        .background(Slate700)
                )

                // Received / Downloaded
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(CyanNeon.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "Received",
                            tint = CyanNeon,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "รับเข้า (Received)",
                            fontSize = 10.sp,
                            color = Slate400,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = formatBytes(bytesDownloaded),
                            fontSize = 12.sp,
                            color = Slate200,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Vertical Divider
                Box(
                    modifier = Modifier
                        .height(22.dp)
                        .width(1.dp)
                        .background(Slate700)
                )

                // Total Session Data
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "รวมทั้งหมด",
                        fontSize = 10.sp,
                        color = Slate400,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = formatBytes(bytesUploaded + bytesDownloaded),
                        fontSize = 12.sp,
                        color = GreenGlow,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Connection State Display (Professional Polish Design)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 12.dp)
        ) {
            val statusText = when {
                isConnected -> "CONNECTED"
                isConnecting -> "CONNECTING"
                else -> "DISCONNECTED"
            }
            val statusThai = when {
                isConnected -> "เชื่อมต่อแล้ว"
                isConnecting -> "กำลังเชื่อมต่อ..."
                else -> "ไม่ได้เชื่อมต่อ"
            }
            val statusColor = when {
                isConnected -> GreenGlow
                isConnecting -> OrangeGlow
                else -> RedAlert
            }

            val statusDotTransition = rememberInfiniteTransition(label = "statusDotPulse")
            val statusDotPulseScale by statusDotTransition.animateFloat(
                initialValue = 1.0f,
                targetValue = if (isConnecting) 2.2f else if (isConnected) 1.5f else 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = if (isConnecting) 700 else 1800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "statusDotScale"
            )
            val statusDotPulseAlpha by statusDotTransition.animateFloat(
                initialValue = 0.8f,
                targetValue = if (isConnecting) 0.1f else if (isConnected) 0.25f else 0.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = if (isConnecting) 700 else 1800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "statusDotAlpha"
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                // Glow status indicator dot with dynamic pulse animation
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(16.dp)
                ) {
                    if (isConnecting || isConnected) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .scale(statusDotPulseScale)
                                .background(statusColor.copy(alpha = statusDotPulseAlpha), CircleShape)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(statusColor, CircleShape)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$statusText • $statusThai",
                    color = statusColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
            }

            Text(
                text = if (isConnected) {
                    "IP เซิร์ฟเวอร์ระบบ: ${selectedServer?.fakeIp ?: "10.8.0.1"}"
                } else {
                    "เครือข่ายจำลอง: ป้องกันข้อมูลแล้วเมื่อต่อ"
                },
                color = Slate400,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal
            )

            if (isConnected) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = formatDuration(connectionDuration),
                    color = Slate200,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Central Power Button with beautiful pulse and state transition animations
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(240.dp)
        ) {
            val transition = rememberInfiniteTransition(label = "pulse")
            val pulseScale by transition.animateFloat(
                initialValue = 1.0f,
                targetValue = if (isConnected) 1.25f else if (isConnecting) 1.15f else 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1500, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scale"
            )
            val pulseAlpha by transition.animateFloat(
                initialValue = 0.4f,
                targetValue = 0.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1500, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "alpha"
            )

            // Animated Colors & Scale for smooth transitions between states
            val targetGlowColor = when {
                isConnected -> GreenGlow
                isConnecting -> OrangeGlow
                else -> CyanNeon
            }
            val animatedGlowColor by animateColorAsState(
                targetValue = targetGlowColor,
                animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                label = "glowColor"
            )

            val targetBorderColor = when {
                isConnected -> GreenGlow.copy(alpha = 0.8f)
                isConnecting -> OrangeGlow.copy(alpha = 0.8f)
                else -> Slate700
            }
            val animatedBorderColor by animateColorAsState(
                targetValue = targetBorderColor,
                animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                label = "borderColor"
            )

            val targetInnerBgStart = when {
                isConnected -> GreenGlow
                isConnecting -> OrangeGlow
                else -> Slate800
            }
            val animatedInnerBgStart by animateColorAsState(
                targetValue = targetInnerBgStart,
                animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                label = "innerBgStart"
            )

            val targetInnerBgEnd = when {
                isConnected -> GreenGlow.copy(alpha = 0.7f)
                isConnecting -> OrangeGlow.copy(alpha = 0.7f)
                else -> Slate700
            }
            val animatedInnerBgEnd by animateColorAsState(
                targetValue = targetInnerBgEnd,
                animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                label = "innerBgEnd"
            )

            val targetIconColor = when {
                isConnected -> Color.White
                isConnecting -> Color.White
                else -> CyanNeon
            }
            val animatedIconColor by animateColorAsState(
                targetValue = targetIconColor,
                animationSpec = tween(durationMillis = 500),
                label = "iconColor"
            )

            val targetLabelColor = when {
                isConnected -> GreenGlow
                isConnecting -> OrangeGlow
                else -> CyanNeon
            }
            val animatedLabelColor by animateColorAsState(
                targetValue = targetLabelColor,
                animationSpec = tween(durationMillis = 500),
                label = "labelColor"
            )

            val targetInnerBorderColor = when {
                isConnected -> Color.White.copy(alpha = 0.3f)
                isConnecting -> Color.White.copy(alpha = 0.3f)
                else -> Slate700
            }
            val animatedInnerBorderColor by animateColorAsState(
                targetValue = targetInnerBorderColor,
                animationSpec = tween(durationMillis = 500),
                label = "innerBorderColor"
            )

            // Button scale transition when state changes
            val targetButtonScale = when {
                isConnected -> 1.05f
                isConnecting -> 0.98f
                else -> 1.0f
            }
            val animatedButtonScale by animateFloatAsState(
                targetValue = targetButtonScale,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "buttonScale"
            )

            val targetIconRotation = when {
                isConnected -> 360f
                isConnecting -> 180f
                else -> 0f
            }
            val animatedIconRotation by animateFloatAsState(
                targetValue = targetIconRotation,
                animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                label = "iconRotation"
            )

            val targetIconScale = when {
                isConnected -> 1.12f
                isConnecting -> 0.92f
                else -> 1.0f
            }
            val animatedIconScale by animateFloatAsState(
                targetValue = targetIconScale,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "iconScale"
            )

            // Pulsing Background Glow Ring
            if (isConnected || isConnecting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(pulseScale)
                        .background(animatedGlowColor.copy(alpha = pulseAlpha), CircleShape)
                        .border(1.dp, animatedGlowColor.copy(alpha = pulseAlpha * 1.5f), CircleShape)
                )
            }

            // Power button outer border (Professional Polish Design)
            Box(
                modifier = Modifier
                    .size(208.dp)
                    .scale(animatedButtonScale)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(Slate800, Slate900),
                            radius = 300f
                        ),
                        shape = CircleShape
                    )
                    .border(
                        width = 1.5.dp,
                        color = animatedBorderColor,
                        shape = CircleShape
                    )
                    .clickable { viewModel.toggleVpnConnection(context) }
                    .testTag("power_connect_button"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Inner Circle holding Power Icon with beautiful subtle glow
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(animatedInnerBgStart, animatedInnerBgEnd)
                                ),
                                shape = CircleShape
                            )
                            .border(
                                width = 1.dp,
                                color = animatedInnerBorderColor,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Power",
                            tint = animatedIconColor,
                            modifier = Modifier
                                .size(44.dp)
                                .rotate(animatedIconRotation)
                                .scale(animatedIconScale)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    // Smooth text transition
                    AnimatedContent(
                        targetState = Pair(isConnected, isConnecting),
                        transitionSpec = {
                            fadeIn(animationSpec = tween(400)) + slideInVertically { height -> height / 2 } togetherWith
                            fadeOut(animationSpec = tween(300)) + slideOutVertically { height -> -height / 2 }
                        },
                        label = "buttonTextTransition"
                    ) { (connected, connecting) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (connected) "TAP TO DISCONNECT" else if (connecting) "CONNECTING..." else "TAP TO CONNECT",
                                fontSize = 11.sp,
                                color = animatedLabelColor,
                                letterSpacing = 1.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (connected) "แตะเพื่อตัดการเชื่อมต่อ" else if (connecting) "กำลังเชื่อมโยงข้อมูล" else "แตะเพื่อความปลอดภัย",
                                fontSize = 11.sp,
                                color = Slate400,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Server Selector Card (Clean and Centered on Server selection only)
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Slate800),
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 0.5.dp, color = Slate700, shape = RoundedCornerShape(22.dp))
                .clickable(enabled = !isConnected) { onNavigateToServers() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Slate700, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = selectedServer?.flagEmoji ?: "🌐",
                            fontSize = 24.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "FASTEST SERVER",
                                color = CyanNeon,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            selectedServer?.let { server ->
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .background(CyanNeon.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                        .border(0.5.dp, CyanNeon.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = server.protocol,
                                        fontSize = 8.sp,
                                        color = CyanNeon,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = selectedServer?.name ?: "ยังไม่ได้เลือกเซิร์ฟเวอร์",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate200,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isConnected) "IP ปัจจุบัน: ${selectedServer?.fakeIp}" else "พร้อมสถาปนาการเชื่อมต่อ",
                            fontSize = 12.sp,
                            color = Slate400
                        )
                    }
                }

                if (isConnected && selectedServer?.currentPing != null && selectedServer!!.currentPing >= 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.NetworkCheck,
                            contentDescription = "Ping",
                            tint = getPingColor(selectedServer!!.currentPing),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${selectedServer!!.currentPing} ms",
                            color = getPingColor(selectedServer!!.currentPing),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (!isConnected) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "เปลี่ยน",
                            color = CyanNeon,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = "Select Server",
                            tint = CyanNeon,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Performance / Telemetry Section
        AnimatedVisibility(
            visible = isConnected,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Download telemetry card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .border(width = 0.5.dp, color = Slate700, shape = RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = Slate800)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = "Download",
                                tint = CyanNeon,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("DOWNLOAD", fontSize = 11.sp, color = Slate400, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = formatBytes(bytesDownloaded),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        // Mini graph
                        RealtimeSpeedChart(
                            speedHistory = downloadSpeedHistory,
                            lineColor = CyanNeon,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                        )
                    }
                }

                // Upload telemetry card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .border(width = 0.5.dp, color = Slate700, shape = RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = Slate800)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Upload",
                                tint = OrangeGlow,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("UPLOAD", fontSize = 11.sp, color = Slate400, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = formatBytes(bytesUploaded),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        // Mini graph
                        RealtimeSpeedChart(
                            speedHistory = uploadSpeedHistory,
                            lineColor = OrangeGlow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RealtimeSpeedChart(
    speedHistory: List<Float>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        if (speedHistory.isEmpty()) return@Canvas

        val maxSpeed = (speedHistory.maxOrNull() ?: 0f).coerceAtLeast(10f)
        val path = Path()
        val stepX = width / (speedHistory.size - 1)

        speedHistory.forEachIndexed { index, speed ->
            val x = index * stepX
            val y = height - (speed / maxSpeed) * height * 0.8f // padding on top
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                val prevX = (index - 1) * stepX
                val prevSpeed = speedHistory[index - 1]
                val prevY = height - (prevSpeed / maxSpeed) * height * 0.8f
                path.cubicTo(
                    (prevX + x) / 2, prevY,
                    (prevX + x) / 2, y,
                    x, y
                )
            }
        }

        // Draw Line Stroke
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )

        // Fill below line with smooth gradient
        val fillPath = Path().apply {
            addPath(path)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.20f), Color.Transparent),
                startY = 0f,
                endY = height
            )
        )
    }
}

@Composable
fun ServersScreen(viewModel: VpnViewModel) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val selectedServer by viewModel.selectedServer.collectAsStateWithLifecycle()
    val isTestingPing by viewModel.isTestingPing.collectAsStateWithLifecycle()
    val isConnected by viewModel.isConnected.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "รายการเซิร์ฟเวอร์",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "เลือกเซิร์ฟเวอร์ความเร็วสูงทั่วโลก",
                    fontSize = 12.sp,
                    color = Slate400
                )
            }

            // Real telemetry ping test trigger
            Button(
                onClick = { viewModel.runPingTests() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isTestingPing) Slate700 else CyanNeon,
                    contentColor = Slate900
                ),
                shape = RoundedCornerShape(12.dp),
                enabled = !isTestingPing,
                modifier = Modifier.testTag("ping_test_button")
            ) {
                if (isTestingPing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Slate400
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("กำลังทดสอบ", fontSize = 12.sp, color = Slate400, fontWeight = FontWeight.Bold)
                } else {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = "Test Ping",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ทดสอบปิง", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        if (isConnected) {
            Card(
                colors = CardDefaults.cardColors(containerColor = RedAlert.copy(alpha = 0.1f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, RedAlert.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = RedAlert,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "กรุณาตัดการเชื่อมต่อ VPN ก่อนเพื่อเลือกเซิร์ฟเวอร์อื่น",
                        fontSize = 12.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(servers) { server ->
                val isSelected = selectedServer?.id == server.id
                val cardBorderColor = when {
                    isSelected -> CyanNeon
                    else -> Slate700
                }

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) Slate700 else Slate800
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (isSelected) 1.5.dp else 0.5.dp,
                            color = cardBorderColor,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable(enabled = !isConnected) {
                            viewModel.selectServer(server)
                        }
                        .testTag("server_item_${server.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(Slate700, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = server.flagEmoji,
                                    fontSize = 18.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Text(
                                        text = server.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (server.isPremium) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .background(OrangeGlow.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                .border(0.5.dp, OrangeGlow, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text("PRO", fontSize = 8.sp, color = OrangeGlow, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(CyanNeon.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                                            .border(0.5.dp, CyanNeon.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = server.protocol,
                                            fontSize = 8.sp,
                                            color = CyanNeon,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "IP: ${server.fakeIp} • โหลด: ${server.loadPercent}% • สปีด: ${server.speedMbps} Mbps",
                                    fontSize = 11.sp,
                                    color = Slate400
                                )
                            }
                        }

                        // Ping Indicator
                        Column(horizontalAlignment = Alignment.End) {
                            if (server.currentPing >= 0) {
                                Text(
                                    text = "${server.currentPing} ms",
                                    color = getPingColor(server.currentPing),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "ค่าปิง",
                                    color = Slate400,
                                    fontSize = 9.sp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.NetworkCheck,
                                    contentDescription = "No Ping Test",
                                    tint = Slate400,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "ไม่ได้ทดสอบ",
                                    color = Slate400,
                                    fontSize = 9.sp
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
fun SplitTunnelingScreen(viewModel: VpnViewModel) {
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }

    val filteredApps = remember(searchQuery, installedApps) {
        if (searchQuery.isBlank()) {
            installedApps
        } else {
            installedApps.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "การสลับอุโมงค์แอพ (Split Tunneling)",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "เลือกแอพพลิเคชันที่ต้องการให้เลี่ยงการเข้ารหัสอุโมงค์ VPN (ทำงานผ่านเครือข่ายเน็ตปกติ)",
            fontSize = 11.sp,
            color = Slate400,
            lineHeight = 14.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        // App Search Bar
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("ค้นหาแอพพลิเคชัน...", color = Slate400, fontSize = 13.sp) },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(0.5.dp, Slate700, RoundedCornerShape(12.dp))
                .testTag("app_search_field"),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Slate800,
                unfocusedContainerColor = Slate800,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Slate400) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Slate400)
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (filteredApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Empty",
                        tint = Slate400,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "ไม่พบแอพพลิเคชันตามที่ค้นหา",
                        color = Slate400,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Slate800),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.5.dp, Slate700, RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Simple placeholder avatar for App icons to prevent heavy Coil calls
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(CyanNeon.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Android,
                                        contentDescription = "App Icon",
                                        tint = CyanNeon,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = app.appName,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = app.packageName,
                                        fontSize = 10.sp,
                                        color = Slate400,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Switch(
                                checked = app.isBypassed,
                                onCheckedChange = { viewModel.toggleAppBypass(app) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Slate900,
                                    checkedTrackColor = CyanNeon,
                                    uncheckedThumbColor = Slate400,
                                    uncheckedTrackColor = Slate700
                                ),
                                modifier = Modifier.testTag("app_toggle_${app.packageName}")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryLogsScreen(viewModel: VpnViewModel) {
    val connectionHistory by viewModel.connectionHistory.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    var activeSubTab by remember { mutableStateOf(0) } // 0 = History, 1 = Console Logs

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "ประวัติและระบบล็อก",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            if (activeSubTab == 0 && connectionHistory.isNotEmpty()) {
                TextButton(
                    onClick = { viewModel.clearSessionLogs() },
                    colors = ButtonDefaults.textButtonColors(contentColor = RedAlert),
                    modifier = Modifier.testTag("clear_history_btn")
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear History", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ล้างประวัติ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Custom iOS-like Segmented Control
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(Slate800, RoundedCornerShape(10.dp))
                .border(0.5.dp, Slate700, RoundedCornerShape(10.dp))
                .padding(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (activeSubTab == 0) Slate700 else Color.Transparent)
                    .clickable { activeSubTab = 0 },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ประวัติเชื่อมโยง",
                    color = if (activeSubTab == 0) Color.White else Slate400,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (activeSubTab == 1) Slate700 else Color.Transparent)
                    .clickable { activeSubTab = 1 },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "บันทึกระบบคอนโซล",
                    color = if (activeSubTab == 1) Color.White else Slate400,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (activeSubTab == 0) {
            // HISTORY LIST
            if (connectionHistory.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Timeline,
                            contentDescription = "Empty History",
                            tint = Slate400,
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "ยังไม่มีประวัติการเชื่อมโยงเครือข่าย",
                            color = Slate400,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(connectionHistory) { session ->
                        SessionLogCardItem(session = session)
                    }
                }
            }
        } else {
            // CONSOLE LOGS
            Column(modifier = Modifier.fillMaxSize()) {
                // Quick Test Simulation Action Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.simulateConnectionDrop() },
                        border = BorderStroke(0.5.dp, RedAlert.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .testTag("simulate_drop_btn")
                    ) {
                        Icon(Icons.Default.WifiOff, contentDescription = null, tint = RedAlert, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ทดสอบเชื่อมต่อหลุด", fontSize = 11.sp, color = RedAlert)
                    }

                    OutlinedButton(
                        onClick = { viewModel.simulateConnectionTimeout() },
                        border = BorderStroke(0.5.dp, OrangeGlow.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .testTag("simulate_timeout_btn")
                    ) {
                        Icon(Icons.Default.TimerOff, contentDescription = null, tint = OrangeGlow, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ทดสอบ Timeout", fontSize = 11.sp, color = OrangeGlow)
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .border(1.dp, Slate700, RoundedCornerShape(12.dp))
                ) {
                    if (logs.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "ไม่มีข้อมูลบันทึกระบบขณะนี้",
                                color = Slate400,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(logs) { logLine ->
                                Text(
                                    text = logLine,
                                    color = if (logLine.contains("สำเร็จ") || logLine.contains("เสถียร")) GreenGlow else if (logLine.contains("ล้มเหลว") || logLine.contains("ข้อผิดพลาด")) RedAlert else Slate200,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 14.sp
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
fun SessionLogCardItem(session: ConnectionSession) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Slate800),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, Slate700, RoundedCornerShape(14.dp))
            .testTag("session_log_item_${session.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top Row: Flag + Server Name + Timestamp + Status Indicator Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = when (session.countryCode.lowercase()) {
                            "th" -> "🇹🇭"
                            "sg" -> "🇸🇬"
                            "jp" -> "🇯🇵"
                            "us" -> "🇺🇸"
                            "kr" -> "🇰🇷"
                            "de" -> "🇩🇪"
                            "gb" -> "🇬🇧"
                            else -> "🌐"
                        },
                        fontSize = 20.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = session.serverName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = "Timestamp",
                                tint = Slate400,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = formatDateTime(session.connectTime),
                                fontSize = 11.sp,
                                color = Slate400
                            )
                        }
                    }
                }

                // Status Indicator Badge
                Box(
                    modifier = Modifier
                        .background(GreenGlow.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                        .border(0.5.dp, GreenGlow.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(GreenGlow, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "สำเร็จ (Active)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = GreenGlow
                        )
                    }
                }
            }

            HorizontalDivider(color = Slate700, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("ระยะเวลา", fontSize = 10.sp, color = Slate400)
                    Text(
                        text = formatDuration(session.durationSecs),
                        fontSize = 13.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ส่งแพ็กเก็ตออก", fontSize = 10.sp, color = Slate400)
                    Text(
                        text = formatBytes(session.bytesUploaded),
                        fontSize = 13.sp,
                        color = OrangeGlow,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("ดาวน์โหลดแพ็กเก็ต", fontSize = 10.sp, color = Slate400)
                    Text(
                        text = formatBytes(session.bytesDownloaded),
                        fontSize = 13.sp,
                        color = CyanNeon,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionSessionLogsBottomSheet(
    viewModel: VpnViewModel,
    onDismiss: () -> Unit
) {
    val connectionHistory by viewModel.connectionHistory.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Slate900,
        contentColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Slate700) },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(CyanNeon.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = "Logs",
                            tint = CyanNeon,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "ประวัติการบันทึกเซสชัน VPN",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "รายการย้อนหลังพร้อมประทับเวลาและสถานะ",
                            fontSize = 11.sp,
                            color = Slate400
                        )
                    }
                }

                if (connectionHistory.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.clearSessionLogs() },
                        modifier = Modifier.testTag("clear_sheet_logs_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear History",
                            tint = RedAlert
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (connectionHistory.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "No Logs",
                            tint = Slate400,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "ยังไม่มีประวัติบันทึกการเชื่อมต่อในเซสชันนี้",
                            color = Slate400,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 450.dp)
                ) {
                    items(connectionHistory) { session ->
                        SessionLogCardItem(session = session)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// Helpers
fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", m, s)
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.getDefault(), "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

fun formatDateTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale("th", "TH"))
    return sdf.format(Date(timestamp))
}

fun getPingColor(ping: Int): Color {
    return when {
        ping < 0 -> Slate400
        ping < 60 -> GreenGlow
        ping < 150 -> OrangeGlow
        else -> RedAlert
    }
}

@Composable
fun VpnAlertNotificationBanner(
    alert: VpnViewModel.VpnAlert,
    onReconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RedAlert.copy(alpha = 0.95f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp)
            .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .testTag("vpn_alert_notification_banner")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (alert.isTimeout) Icons.Default.TimerOff else Icons.Default.WifiOff,
                        contentDescription = "Alert",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = alert.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = alert.message,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("dismiss_alert_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("รับทราบ (Dismiss)", fontSize = 11.sp, color = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onReconnect,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("reconnect_alert_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reconnect",
                        tint = RedAlert,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ลองใหม่ (Reconnect)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RedAlert)
                }
            }
        }
    }
}
