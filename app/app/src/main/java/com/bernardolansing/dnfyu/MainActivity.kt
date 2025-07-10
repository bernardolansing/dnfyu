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
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.bernardolansing.dnfyu.ui.theme.DoNotForgetYourUmbrellaTheme
import kotlinx.coroutines.delay
import kotlin.math.absoluteValue

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
                        Log.i(null, "Updating packet rate")
                        try {
                            packetRate.value = forgottalEvaluator.getPacketReceiptRate()
                            if (status.value == Status.ForgottenUmbrella) {
                                Log.i(null, "Umbrella is in reach again")
                                status.value = Status.TrackingUmbrella
                            }
                        } catch (error: UmbrellaWasForgotten) {
                            if (status.value != Status.ForgottenUmbrella) {
                                status.value = Status.ForgottenUmbrella
                                ringAlertSound(context)
                            }
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

    btManager.adapter.bluetoothLeScanner.startScan(scanFilters, scanSettings, scanCallback)
}

private fun guessDistanceFromSignalIntensity(intensity: Int): Int {
    // TODO: this was not even tested; perform some real measures to model the signal strength decay
    return (intensity.absoluteValue - 30) / 2
}

private fun ringAlertSound(context: Context) {
    Log.i(null, "Ringing alert scream to notify of the forgottal")
    val mediaPlayer = MediaPlayer.create(context, R.raw.alert_scream)
    val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .build()
    mediaPlayer.setAudioAttributes(attributes)
    mediaPlayer.start()
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
        Text(
            text = "Wait while we search for your umbrella in the surroundings.",
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(25.dp))
        CircularProgressIndicator(
            modifier = Modifier
                .width(100.dp)
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
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = "Umbrella figure",
        )

        Text(
            text = "Your umbrella is near!",
            textAlign = TextAlign.Center,
            fontSize = 28.sp,
        )

        Spacer(modifier = Modifier.height(50.dp))

        Text(text = "Signal intensity: $intensity dBm")
        Text(text = "Distância estimada: ${guessDistanceFromSignalIntensity(intensity)} m")
        Text(text = "Packets por segundo: $packetsPerSec Hz")
    }
}

@Composable
private fun ForgottenUmbrellaFrame() {
    Image(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        colorFilter = ColorFilter.tint(color = Color.Red),
        modifier = Modifier.rotate(180.0F),
        contentDescription = "Umbrella figure",
    )

    Spacer(modifier = Modifier.height(30.dp))

    Text(
        text = "You have forgotten your umbrella!!!",
        textAlign = TextAlign.Center,
        lineHeight = 32.sp,
        fontSize = 28.sp,
    )
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

@Preview(showBackground = true)
@Composable
fun ForgottenUmbrellaMainActivityLayout() {
    MainActivityLayout(status = Status.ForgottenUmbrella, signalIntensity = null)
}
