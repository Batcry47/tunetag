package com.tunetag.player.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { TuneTagRoot(vm) }
    }

    override fun onStart() {
        super.onStart()
        vm.player.setForeground(true)
    }

    override fun onStop() {
        vm.player.setForeground(false)
        super.onStop()
    }
}

private fun hasStoragePermission(ctx: Context): Boolean =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

@Composable
fun TuneTagRoot(vm: AppViewModel) {
    val user by vm.user.collectAsState()
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasStoragePermission(ctx)) }

    LaunchedEffect(granted) { if (granted) vm.refresh() }

    TuneTagTheme(user.settings) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (!granted) PermissionScreen { granted = true }
            else if (user.settings.playerStyle == 1) IpodRoot(vm)
            else AppScaffold(vm)
        }
    }
}

@Composable
private fun PermissionScreen(onGranted: () -> Unit) {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasStoragePermission(ctx)) onGranted()
    }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("TuneTag", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(12.dp))
        Text(
            "TuneTag needs access to your storage to find your music and to write tags and artwork into your files.",
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button({
            launcher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }) { Text("Grant access") }
    }
}
