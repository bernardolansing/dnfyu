package com.bernardolansing.dnfyu

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.os.ParcelUuid
import android.text.style.BackgroundColorSpan
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.bernardolansing.dnfyu.ui.theme.DoNotForgetYourUmbrellaTheme
import kotlinx.coroutines.delay

private enum class Status {
    MissingPermissions,
    Searching,
    TrackingUmbrella,
    ForgottenUmbrella,
}

class MainActivity : ComponentActivity() {
    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val forgottalEvaluator = ForgottalEvaluator()

        setContent {
            val context = LocalContext.current

            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (isGranted) {
                    Log.i("PERMISSION", "Notificação permitida")
                } else {
                    Log.i("PERMISSION", "Notificação negada")
                }
            }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED

                    if (!hasPermission) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            val status = remember {
                if (checkBluetoothPermissions(context))
                    mutableStateOf(Status.Searching)
                else
                    mutableStateOf(Status.MissingPermissions)
            }
            val signalStrength: MutableState<Int?> = remember { mutableStateOf(null) }
            val packetRate: MutableState<Int> = remember { mutableIntStateOf(0) }

            LaunchedEffect(status) {
                if (status.value == Status.Searching) {
                    startBleScan(context) { intensity ->
                        Log.i(null, "Received advertisement packet from umbrella")
                        forgottalEvaluator.reportPacketReceipt(intensity)
                        signalStrength.value = intensity
                        if (status.value == Status.Searching) {
                            status.value = Status.TrackingUmbrella
                        }
                    }
                }
            }

            LaunchedEffect(Unit) {
                while (true) {
                    delay(1000)
                    if (status.value == Status.TrackingUmbrella
                        || status.value == Status.ForgottenUmbrella) {
                        forgottalEvaluator.update()
                        packetRate.value = forgottalEvaluator.getPacketReceiptRate()
                        val umbrellaInReach = forgottalEvaluator.isUmbrellaInReach()

                        if (status.value == Status.TrackingUmbrella && ! umbrellaInReach) {
                            Log.i(null, "Umbrella seems to have been forgotten")
                            status.value = Status.ForgottenUmbrella
                            ringAlertSound(context)
                        }
                        else if (status.value == Status.ForgottenUmbrella && umbrellaInReach) {
                            Log.i(null, "Umbrella is in reach again")
                            status.value = Status.TrackingUmbrella
                        }
                        else if (forgottalEvaluator.wasUmbrellaTurnedOff()) {
                            Log.i(null, "Umbrella seems to have been turned off")
                            status.value = Status.Searching
                        }
                    }
                }
            }

            val requestBluetoothActivationLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) { result ->
                if (result.resultCode == RESULT_OK) {
                    Log.i(null, "User enabled Bluetooth after prompt")
                    status.value = Status.Searching
                } else {
                    Log.i(null, "User refused to enable Bluetooth")
                    // TODO: provide better feedback
                }
            }

            val requestPermissionsLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) { grantResults: Map<String, Boolean> ->
                if (grantResults.values.all { granted -> granted }) {
                    Log.i(null, "Bluetooth permissions were granted")
                    // Bluetooth permissions are okay, let's check if the Bluetooth service is on.
                    if (checkIfBluetoothActivated(context)) {
                        // Bluetooth is turned on, let's move to the next step.
                        status.value = Status.Searching
                    } else {
                        // Bluetooth is off, so we're prompting the user for its activation.
                        Log.i(null, "Prompting for Bluetooth activation")
                        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                        requestBluetoothActivationLauncher.launch(intent)
                    }
                } else {
                    Log.i(null, "Bluetooth permissions were denied")
                    // TODO: provide better feedback
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
                }
            }

            MainActivityLayout(
                status = status.value,
                signalIntensity = signalStrength.value,
                packetsPerSec = packetRate.value,
                onGrantPermissions = {
                    Log.i(null, "Requesting Bluetooth permissions")
                    val permissions = arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                    )
                    requestPermissionsLauncher.launch(permissions)
                },
            )
        }
    }
}

private fun checkIfBluetoothActivated(context: Context): Boolean {
    Log.i(null, "Checking if Bluetooth service is powered")
    val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    if (! btManager.adapter.isEnabled) {
        Log.i(null, "Bluetooth service is turned off")
        return false
    }

    Log.i(null, "Bluetooth service is turned on")
    return true
}

private fun checkBluetoothPermissions(context: Context): Boolean {
    Log.i(null, "Checking Bluetooth permissions")
    val scanStatus = ContextCompat
        .checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
    val connectStatus = ContextCompat
        .checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
    if (scanStatus == PackageManager.PERMISSION_DENIED
        || connectStatus == PackageManager.PERMISSION_DENIED) {
        Log.i(null, "Bluetooth permissions are not granted")
        return false
    }
    Log.i(null, "Bluetooth permissions are OK")

    if (checkIfBluetoothActivated(context)) {
        Log.i(null, "All Bluetooth requirements all fulfilled, we're good to go")
        return true
    }
    return false
}

@RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
private fun startBleScan(context: Context, onUmbrellaFound: (Int) -> Unit) {
    Log.i(null, "Starting BLE scan")
    val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    val scanFilters = listOf(
        // Filter advertisements that provide the dnfyu UUID packet.
        ScanFilter.Builder()
            .setServiceUuid(ParcelUuid.fromString("9f3b8e2a-0000-1000-8000-00805f9b34fb"))
            .build(),
    )

    val scanSettings = ScanSettings.Builder()
        .setReportDelay(0)
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    val scanCallback = object : ScanCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            if (result != null) {
                onUmbrellaFound.invoke(result.rssi)
            }
        }
    }

    val channelId = "umbrella_foreground_channel"

    val channel = NotificationChannel(
        channelId,
        "Umbrella Scan Service",
        NotificationManager.IMPORTANCE_LOW
    )

    val notificationManager = context.getSystemService(NotificationManager::class.java)
    notificationManager.createNotificationChannel(channel)

    val notification = NotificationCompat.Builder(context, channelId)
        .setContentTitle("Looking for your umbrella...")
        .setContentText("BLE scan in progress")
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setOngoing(true)
        .build()

    val service = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        == PackageManager.PERMISSION_GRANTED) {
        try {
            NotificationManagerCompat.from(context).notify(2, notification)
        } catch (e: SecurityException) {
            Log.e("BLE", "Notification permission denied", e)
        }
    }

    btManager.adapter.bluetoothLeScanner.startScan(scanFilters, scanSettings, scanCallback)
}

private fun ringAlertSound(context: Context) {
    Log.i(null, "Ringing alert scream to notify of the forgottal")
    val mediaPlayer = MediaPlayer.create(context, R.raw.alert_scream)
    val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .build()
    mediaPlayer.setAudioAttributes(attributes)
    mediaPlayer.start()

    showUmbrellaForgottenNotification(context)
}

private fun showUmbrellaForgottenNotification(context: Context) {
    val channelId = "umbrella_alert_channel"

    // Cria o canal de notificação (obrigatório no Android 8+)
    val name = "Umbrella Alerts"
    val descriptionText = "Alerts when umbrella is forgotten"
    val importance = NotificationManager.IMPORTANCE_HIGH
    val channel = NotificationChannel(channelId, name, importance).apply {
        description = descriptionText
        enableVibration(true)
        enableLights(true)
    }
    val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.createNotificationChannel(channel)

    // Intenção ao tocar na notificação (pode abrir o app)
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val pendingIntent = PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    // Monta a notificação
    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_launcher_foreground) // Ícone pequeno (pode mudar)
        .setContentTitle("Forgot your umbrella?")
        .setContentText("It looks like your umbrella is out of range.")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setDefaults(NotificationCompat.DEFAULT_ALL)
        .setVibrate(longArrayOf(0, 500, 500, 500))// Toca som, vibra, etc.

    with(NotificationManagerCompat.from(context)) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED) {
            try {
                NotificationManagerCompat.from(context).notify(1, builder.build())
            } catch (e: SecurityException) {
                Log.e("BLE", "Notification permission denied", e)
            }
        }
    }
}

@Composable
private fun MainActivityLayout(
    status: Status,
    signalIntensity: Int?,
    packetsPerSec: Int = 0,
    onGrantPermissions: () -> Unit = {},
) {
    DoNotForgetYourUmbrellaTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (status) {
                    Status.MissingPermissions -> PermissionsFrame(onGrantPermissions)
                    Status.Searching -> OngoingScanFrame()
                    Status.TrackingUmbrella -> TrackingUmbrellaFrame(
                        intensity = signalIntensity!!,
                        packetsPerSec = packetsPerSec,
                    )
                    Status.ForgottenUmbrella -> ForgottenUmbrellaFrame()
                }
            }
        }
    }
}

@Composable
private fun PermissionsFrame(onGrantPermissions: () -> Unit = {}) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(250.dp),
    ) {
        Text(
            text = "We need Bluetooth permissions to be able to find your umbrella!",
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(25.dp))
        Button(onClick = onGrantPermissions) {
            Text(text = "Grant permissions")
        }
    }
}

@Composable
private fun OngoingScanFrame() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(250.dp),
    ) {
        val imageColorFilter = if (isSystemInDarkTheme()) {
            ColorFilter.tint(color = Color.White)
        } else {
            null
        }
        Image(
            painter = painterResource(R.drawable.johnny_travolta),
            contentDescription = "Confused Johnny Travolta",
            modifier = Modifier.height(140.dp),
            colorFilter = imageColorFilter
        )

        Spacer(modifier = Modifier.height(25.dp))

        Text(
            text = "Wait while we search for your umbrella in the surroundings.",
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(25.dp))

        CircularProgressIndicator(
            modifier = Modifier.width(100.dp)
                .height(100.dp),
            strokeWidth = 6.dp,
        )
    }
}

@Composable
private fun TrackingUmbrellaFrame(intensity: Int, packetsPerSec: Int = 0) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val imageColorFilter = if (isSystemInDarkTheme()) {
            ColorFilter.tint(Color.White)
        } else {
            null
        }
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = "Umbrella figure",
            colorFilter = imageColorFilter
        )

        Text(
            text = "Your umbrella is near!",
            textAlign = TextAlign.Center,
            fontSize = 28.sp,
        )

        Spacer(modifier = Modifier.height(50.dp))

        Text(text = "Signal intensity: $intensity dBm")
        Text(text = "Packets per second: $packetsPerSec Hz")
    }
}

@Composable
private fun ForgottenUmbrellaFrame() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xffff474c)), // Fundo vermelho apenas aqui
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            modifier = Modifier
                .size(180.dp)
                .rotate(180.0F),
            contentDescription = "Umbrella figure",
            colorFilter = ColorFilter.tint(Color.White),
        )

        Spacer(modifier = Modifier.height(30.dp))

        Text(
            text = "You have forgotten your umbrella!!!".uppercase(),
            textAlign = TextAlign.Center,
            lineHeight = 32.sp,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MissingPermissionsMainActivityLayout() {
    MainActivityLayout(status = Status.MissingPermissions, signalIntensity = null)
}

@Preview(showBackground = true)
@Composable
fun OngoingScanMainActivityLayout() {
    MainActivityLayout(status = Status.Searching, signalIntensity = null)
}

@Preview(showBackground = true)
@Composable
fun TrackingUmbrellaMainActivityLayout() {
    MainActivityLayout(status = Status.TrackingUmbrella, signalIntensity = 50)
}

@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Composable
fun ForgottenUmbrellaMainActivityLayout() {
    MainActivityLayout(status = Status.ForgottenUmbrella, signalIntensity = null)
}
