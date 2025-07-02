package com.bernardolansing.dnfyu

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MainActivityLayout()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        deviceId: Int
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults, deviceId)
        if (requestCode == RequestCodes.REQUEST_BT_PERMISSIONS) {
            Log.i(null, "Received Bluetooth permissions prompt result")
            if (grantResults.all { status -> status == PackageManager.PERMISSION_GRANTED }) {
                Log.i(null, "Permissions were granted!")
                if (checkIfBluetoothIsOn()) {
                    // TODO: handle all permissions turned OK event
                } else {
                    // TODO: handle permissions granted, but BT was not turned on event
                }
            }
            else {
                // TODO: handle permissions were denied event
            }
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

    private fun askBluetoothPermissions() {
        Log.i(null, "Prompting for Bluetooth permissions")
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
        requestPermissions(permissions, RequestCodes.REQUEST_BT_PERMISSIONS)
    }
}

private object RequestCodes {
    const val REQUEST_BT_PERMISSIONS = 1
}

@Preview(showBackground = true)
@Composable
fun MainActivityLayout() {
    DoNotForgetYourUmbrellaTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { contentPadding ->
            Column(
                modifier = Modifier.fillMaxSize()
                    .padding(contentPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PermissionsFrame()
            }
        }
    }
}

@Composable
fun PermissionsFrame() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(250.dp),
    ) {
        Text(
            text = "We need Bluetooth permissions to be able to find your umbrella!",
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(25.dp))
        Button(
            onClick = {
                // TODO: ask for permissions
            }
        ) {
            Text(text = "Grant permissions")
        }
    }
}
