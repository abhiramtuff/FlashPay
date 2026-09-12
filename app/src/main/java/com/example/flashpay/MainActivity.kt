package com.example.flashpay

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.flashpay.ui.theme.FlashPayTheme

/**
 * Simple screen states for the app.
 * OFF = flashlight is off, ON = flashlight is on (turning off requires payment),
 * PAID = the fake payment was completed this round.
 */
private enum class FlashState { OFF, ON, PAID }

class MainActivity : ComponentActivity() {

    private val torchController = TorchController(this)

    // Screen state is hoisted here so the lifecycle methods can reset it when
    // the activity leaves the foreground and the flashlight is turned off.
    private var screenState by mutableStateOf(FlashState.OFF)
    private var errorMessage by mutableStateOf<String?>(null)
    private var showPaymentDialog by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FlashPayTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    FlashPayScreen(
                        torch = torchController,
                        screenState = screenState,
                        errorMessage = errorMessage,
                        showPaymentDialog = showPaymentDialog,
                        onScreenStateChange = { screenState = it },
                        onErrorMessageChange = { errorMessage = it },
                        onShowPaymentDialogChange = { showPaymentDialog = it },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        torchController.attach()
    }

    override fun onStop() {
        super.onStop()
        // Safety: whenever the activity leaves the foreground, make sure the
        // flashlight is off if this app turned it on, and reset the UI state
        // so the user must turn it on again when returning to the app.
        torchController.release()
        screenState = FlashState.OFF
        errorMessage = null
        showPaymentDialog = false
    }

    override fun onDestroy() {
        super.onDestroy()
        // Final safety net if onStop was skipped (should not happen, but be safe).
        torchController.release()
        screenState = FlashState.OFF
        errorMessage = null
        showPaymentDialog = false
    }
}

/**
 * Small wrapper around CameraManager.setTorchMode().
 *
 * Rules:
 *  - Only turns the torch ON while the activity is in the foreground.
 *  - Tracks whether THIS app turned the torch on.
 *  - Never retries in a loop; one attempt per user action.
 *  - All camera errors are caught and reported as a simple message, never a crash.
 */
private class TorchController(private val context: Context) {

    var torchIsOn = false
        private set

    var unavailableReason: String? = null
        private set

    private var cameraManager: CameraManager? = null
    private var flashCameraId: String? = null

    /** Called from onStart: look up a camera that has a flash unit. */
    fun attach() {
        try {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            cameraManager = manager
            val id = manager?.cameraIdList?.firstOrNull { cameraId ->
                try {
                    manager.getCameraCharacteristics(cameraId)
                        .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                } catch (_: Exception) {
                    false
                }
            }
            flashCameraId = id
            unavailableReason = if (id == null) {
                "No flash available on this device."
            } else {
                null
            }
        } catch (e: CameraAccessException) {
            cameraManager = null
            flashCameraId = null
            unavailableReason = "Camera unavailable."
        } catch (e: IllegalArgumentException) {
            cameraManager = null
            flashCameraId = null
            unavailableReason = "Camera unavailable."
        } catch (e: SecurityException) {
            cameraManager = null
            flashCameraId = null
            unavailableReason = "Camera access was denied."
        } catch (_: Exception) {
            cameraManager = null
            flashCameraId = null
            unavailableReason = "Camera unavailable."
        }
    }

    /**
     * Try to turn the torch ON. Returns null on success or an error message.
     * A single attempt; no retries, no loops, no timers.
     */
    fun turnOn(): String? {
        if (unavailableReason != null) return unavailableReason
        val manager = cameraManager ?: return "Camera unavailable."
        val id = flashCameraId ?: return "No flash available on this device."
        return try {
            manager.setTorchMode(id, true)
            torchIsOn = true
            null
        } catch (e: CameraAccessException) {
            "Could not turn on the flashlight."
        } catch (e: IllegalArgumentException) {
            "Could not turn on the flashlight."
        } catch (e: SecurityException) {
            "Could not turn on the flashlight."
        } catch (_: Exception) {
            "Could not turn on the flashlight."
        }
    }

    /**
     * Try to turn the torch OFF (single attempt) and forget that we own it.
     */
    fun turnOff() {
        val manager = cameraManager
        val id = flashCameraId
        if (manager != null && id != null && torchIsOn) {
            try {
                manager.setTorchMode(id, false)
            } catch (_: Exception) {
                // Best effort. If this fails the system will reclaim the torch
                // when the camera device goes away.
            }
        }
        torchIsOn = false
    }

    /** Called from onStop/onDestroy: turn off if this app turned it on. */
    fun release() {
        turnOff()
    }
}

@Composable
private fun FlashPayScreen(
    torch: TorchController,
    screenState: FlashState,
    errorMessage: String?,
    showPaymentDialog: Boolean,
    onScreenStateChange: (FlashState) -> Unit,
    onErrorMessageChange: (String?) -> Unit,
    onShowPaymentDialogChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "FlashPay",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Because turning off a flashlight should be easy.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "Status:")
        Text(
            text = when {
                errorMessage != null -> "ERROR"
                screenState == FlashState.OFF -> "OFF"
                else -> "ON"
            },
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = if (screenState == FlashState.OFF) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        when (screenState) {
            FlashState.OFF -> {
                Button(
                    onClick = {
                        val error = torch.turnOn()
                        if (error == null) {
                            onErrorMessageChange(null)
                            onScreenStateChange(FlashState.ON)
                        } else {
                            onErrorMessageChange(error)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("TURN ON FLASHLIGHT")
                }
            }
            FlashState.ON -> {
                OutlinedButton(
                    onClick = { onShowPaymentDialogChange(true) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("TURN OFF FLASHLIGHT")
                }
            }
            FlashState.PAID -> {
                Text("Payment Successful ✓", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("You have paid ₹5 to turn off a flashlight.")
                Text("Humanity has progressed backwards.")
                Button(
                    onClick = {
                        torch.turnOff()
                        onScreenStateChange(FlashState.OFF)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("TURN OFF FLASHLIGHT")
                }
            }
        }

        errorMessage?.let { message ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "v1.0",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
    }

    if (showPaymentDialog) {
        AlertDialog(
            onDismissRequest = { onShowPaymentDialogChange(false) },
            title = { Text("Turn Off Flashlight") },
            text = {
                Text(
                    "Apparently, turning off your flashlight costs ₹5.\n\n" +
                        "This is a completely fake payment. No money will be charged."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Fake payment: only local state changes. No real payment,
                        // no UPI, no network, nothing external.
                        onShowPaymentDialogChange(false)
                        onScreenStateChange(FlashState.PAID)
                    }
                ) {
                    Text("PAY ₹5")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onShowPaymentDialogChange(false) }
                ) {
                    Text("CANCEL")
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FlashPayScreenPreview() {
    var screenState by remember { mutableStateOf(FlashState.OFF) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showPaymentDialog by remember { mutableStateOf(false) }
    FlashPayTheme {
        FlashPayScreen(
            torch = TorchController(LocalContext.current),
            screenState = screenState,
            errorMessage = errorMessage,
            showPaymentDialog = showPaymentDialog,
            onScreenStateChange = { screenState = it },
            onErrorMessageChange = { errorMessage = it },
            onShowPaymentDialogChange = { showPaymentDialog = it }
        )
    }
}
