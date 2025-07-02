package com.bernardolansing.dnfyu

import android.os.Bundle
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
import com.bernardolansing.dnfyu.ui.theme.DoNotForgetYourUmbrellaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MainActivityLayout()
        }
    }
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
