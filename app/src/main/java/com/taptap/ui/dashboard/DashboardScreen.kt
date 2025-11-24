package com.taptap.ui.dashboard

import android.content.Intent
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.json.JSONObject
import java.nio.charset.StandardCharsets

@Composable
fun DashboardScreen(
    intent: Intent?
) {
    val context = LocalContext.current
    var resultText by remember { mutableStateOf("No profile received yet") }

    val qrScanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            resultText = handleScannedQrCode(result.contents, context)
        }
    }

    LaunchedEffect(intent) {
        intent?.let {
            resultText = checkForIncomingData(it, context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Receive Profile",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Button(
            onClick = {
                val options = ScanOptions().apply {
                    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    setPrompt("Scan a QR code from TapTap")
                    setCameraId(0)
                    setBeepEnabled(false)
                    setBarcodeImageEnabled(true)
                }
                qrScanner.launch(options)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text("Scan QR Code")
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text(
                text = resultText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            )
        }
    }
}

private fun checkForIncomingData(intent: Intent, context: android.content.Context): String {
    return when (intent.action) {
        Intent.ACTION_VIEW -> handleDeepLink(intent.data, context)
        NfcAdapter.ACTION_NDEF_DISCOVERED -> handleNfcData(intent, context)
        else -> "No profile received yet"
    }
}

private fun handleScannedQrCode(qrContent: String, context: android.content.Context): String {
    return if (qrContent.startsWith("myapp://")) {
        val uri = Uri.parse(qrContent)
        handleDeepLink(uri, context)
    } else {
        processReceivedData(qrContent, "QR Code", context)
    }
}

private fun handleDeepLink(uri: Uri?, context: android.content.Context): String {
    uri ?: return "No profile received yet"

    return try {
        val encodedData = uri.getQueryParameter("data")
        if (encodedData != null) {
            var paddedData = encodedData
            val paddingNeeded = paddedData.length % 4
            if (paddingNeeded > 0) {
                paddedData += "====".substring(0, 4 - paddingNeeded)
            }

            val finalData = paddedData
                .replace("_", "/")
                .replace("-", "+")

            val decoded = Base64.decode(finalData, Base64.DEFAULT)
            val jsonString = String(decoded, StandardCharsets.UTF_8)
            processReceivedData(jsonString, "QR Code", context)
        } else {
            "Invalid QR code data"
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Invalid QR code data", Toast.LENGTH_SHORT).show()
        "Invalid QR code data"
    }
}

private fun handleNfcData(intent: Intent, context: android.content.Context): String {
    return try {
        val messages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        if (messages != null && messages.isNotEmpty()) {
            val message = messages[0] as NdefMessage
            if (message.records.isNotEmpty()) {
                val jsonString = String(message.records[0].payload, StandardCharsets.UTF_8)
                processReceivedData(jsonString, "NFC", context)
            } else {
                "Error reading NFC data"
            }
        } else {
            "Error reading NFC data"
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Error reading NFC data", Toast.LENGTH_SHORT).show()
        "Error reading NFC data"
    }
}

private fun processReceivedData(jsonString: String, source: String, context: android.content.Context): String {
    return try {
        val data = JSONObject(jsonString)

        if (!"com.taptap".equals(data.optString("app_id", ""))) {
            Toast.makeText(context, "Data not from TapTap app", Toast.LENGTH_SHORT).show()
            return "Data not from TapTap app"
        }

        Toast.makeText(context, "Profile received via $source", Toast.LENGTH_SHORT).show()

        """
            Received via $source
            
            👤 ${data.optString("fullName", "N/A")}
            📧 ${data.optString("email", "N/A")}
            📞 ${data.optString("phone", "N/A")}
            💼 ${data.optString("description", "N/A")}
            📍 ${data.optString("location", "N/A")}
            ${if (data.optString("linkedIn", "").isNotEmpty()) "🔗 ${data.optString("linkedIn")}" else ""}
        """.trimIndent()

    } catch (e: Exception) {
        Toast.makeText(context, "Invalid data format", Toast.LENGTH_SHORT).show()
        "Invalid data format"
    }
}

