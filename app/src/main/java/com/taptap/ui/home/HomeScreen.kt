package com.taptap.ui.home

import android.graphics.Bitmap
import android.graphics.Color
import android.nfc.NfcAdapter
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.taptap.model.TapTapUser
import com.taptap.viewmodel.UserViewModel

@Composable
fun HomeScreen(
    userViewModel: UserViewModel,
    nfcAdapter: NfcAdapter?
) {
    val context = LocalContext.current
    val currentUser by userViewModel.currentUser.observeAsState(TapTapUser())
    var showQrDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Share Your Profile",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // User Info Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                UserInfoItem(icon = "👤", text = currentUser.fullName)
                UserInfoItem(icon = "📧", text = currentUser.email)
                UserInfoItem(icon = "📞", text = currentUser.phone)
                UserInfoItem(icon = "📍", text = currentUser.location)

                if (currentUser.description.isNotEmpty()) {
                    UserInfoItem(icon = "💼", text = currentUser.description)
                }

                if (currentUser.linkedIn.isNotEmpty()) {
                    UserInfoItem(icon = "🔗", text = currentUser.linkedIn)
                }
            }
        }

        // Share Buttons
        if (nfcAdapter != null) {
            Button(
                onClick = {
                    if (!nfcAdapter.isEnabled) {
                        Toast.makeText(context, "Please enable NFC in settings", Toast.LENGTH_SHORT)
                            .show()
                    } else {
                        Toast.makeText(
                            context,
                            "Ready to share via NFC: Hold phones back-to-back",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Text("Share via NFC")
            }
        }

        OutlinedButton(
            onClick = { showQrDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Generate QR Code")
        }
    }

    if (showQrDialog) {
        QrCodeDialog(
            userViewModel = userViewModel,
            onDismiss = { showQrDialog = false }
        )
    }
}

@Composable
fun UserInfoItem(icon: String, text: String) {
    if (text.isNotEmpty()) {
        Text(
            text = "$icon $text",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}

@Composable
fun QrCodeDialog(
    userViewModel: UserViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val qrCodeBitmap = remember {
        try {
            generateQrCode(userViewModel)
        } catch (e: Exception) {
            Toast.makeText(context, "Error generating QR code", Toast.LENGTH_SHORT).show()
            null
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Scan to Share Profile",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                qrCodeBitmap?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier
                            .size(300.dp)
                            .padding(8.dp)
                    )
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

private fun generateQrCode(userViewModel: UserViewModel): Bitmap {
    val profile = userViewModel.getUserProfileJson()
    val jsonString = profile.toString()

    var base64Data = Base64.encodeToString(jsonString.toByteArray(), Base64.DEFAULT)
    base64Data = base64Data.replace("\n", "")
    base64Data = base64Data.replace("=", "")
    base64Data = base64Data.replace("/", "_")
    base64Data = base64Data.replace("+", "-")

    val deepLink = "myapp://share?data=$base64Data"

    val hints = java.util.EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
    hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
    hints[EncodeHintType.MARGIN] = 1

    val bitMatrix: BitMatrix = MultiFormatWriter().encode(
        deepLink, BarcodeFormat.QR_CODE, 500, 500, hints
    )

    val width = bitMatrix.width
    val height = bitMatrix.height
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

    for (x in 0 until width) {
        for (y in 0 until height) {
            val isBlack = bitMatrix.get(x, y)
            if (isBlack) {
                bitmap.setPixel(x, y, Color.BLACK)
            } else {
                bitmap.setPixel(x, y, Color.WHITE)
            }
        }
    }
    return bitmap
}
