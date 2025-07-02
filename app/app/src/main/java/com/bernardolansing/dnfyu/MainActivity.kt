package com.bernardolansing.dnfyu

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.bernardolansing.dnfyu.ui.theme.DoNotForgetYourUmbrellaTheme

class MainActivity : ComponentActivity() {
    private var status = Status.MissingPermissions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (checkBluetoothPermissions()) {
            startSearchingForAnUmbrella()
        }
        setContent {
            val requestPermissionsLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) { grantResults: Map<String, Boolean> ->
                if (grantResults.values.all { granted -> granted }) {
                    Log.i(null, "Bluetooth permissions were granted")
                    // Bluetooth permissions are okay, let's check if the Bluetooth service is on.
                    // TODO: check if BT is on.
                } else {
                    Log.i(null, "Bluetooth permissions were denied")
                }
            }

            MainActivityLayout(
                status = status,
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

    private fun checkIfBluetoothIsOn(): Boolean {
        Log.i(null, "Checking if Bluetooth service is powered")
        val btManager = applicationContext
            .getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        if (! btManager.adapter.isEnabled) {
            Log.i(null, "Bluetooth service is turned off")
            return false
        }

        Log.i(null, "Bluetooth service is turned on")
        return true
    }

    private fun checkBluetoothPermissions(): Boolean {
        Log.i(null, "Checking Bluetooth permissions")
        val scanStatus = ContextCompat
            .checkSelfPermission(applicationContext, Manifest.permission.BLUETOOTH_SCAN)
        val connectStatus = ContextCompat
            .checkSelfPermission(applicationContext, Manifest.permission.BLUETOOTH_CONNECT)
        if (scanStatus == PackageManager.PERMISSION_DENIED
            || connectStatus == PackageManager.PERMISSION_DENIED) {
            Log.i(null, "Bluetooth permissions are not granted")
            return false
        }
        Log.i(null, "Bluetooth permissions are OK")

        if (checkIfBluetoothIsOn()) {
            Log.i(null, "All Bluetooth requirements all fulfilled, we're good to go")
            return true
        }
        return false
    }

    private fun promptForBluetoothActivation() {
        Log.i(null, "Prompting for Bluetooth activation")
        val activityResultLauncher = registerForActivityResult(StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                Log.i(null, "User enabled Bluetooth after prompt")
                startSearchingForAnUmbrella()
            } else {
                Log.i(null, "User refused to enable Bluetooth")
            }
        }
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        activityResultLauncher.launch(intent)
    }

    private fun startSearchingForAnUmbrella() {
        Log.i(null, "Switching to 'searching' state")
        status = Status.Searching
        // TODO: start the Bluetooth scan
    }
}

private enum class Status {
    MissingPermissions,
    Searching,
}

@Composable
private fun MainActivityLayout(
    status: Status,
    onGrantPermissions: () -> Unit = {},
) {
    DoNotForgetYourUmbrellaTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { contentPadding ->
            Column(
                modifier = Modifier.fillMaxSize()
                    .padding(contentPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (status) {
                    Status.MissingPermissions -> PermissionsFrame(onGrantPermissions)
                    Status.Searching -> OngoingScanFrame()
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
            modifier = Modifier.width(100.dp)
                .height(100.dp),
            strokeWidth = 6.dp
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MissingPermissionsMainActivityLayout() {
    MainActivityLayout(Status.MissingPermissions)
}

@Preview(showBackground = true)
@Composable
fun OngoingScanMainActivityLayout() {
    MainActivityLayout(Status.Searching)
}
