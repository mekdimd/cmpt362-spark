package com.taptap.ui.dashboard

import android.content.Intent
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.taptap.R
import org.json.JSONObject
import java.nio.charset.StandardCharsets

class DashboardFragment : Fragment() {

    // qr scanner launcher
    private val qrScanner = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            handleScannedQrCode(result.contents)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_dashboard, container, false)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // TODO: Layout shows static connections right now
    }

    override fun onResume() {
        super.onResume()
        checkForIncomingData()
    }

    private fun checkForIncomingData() {
        activity?.intent?.let { handleIntent(it) }
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_VIEW -> handleDeepLink(intent.data)
            NfcAdapter.ACTION_NDEF_DISCOVERED -> handleNfcData(intent)
        }
    }

    private fun handleNfcData(intent: Intent) {
        try {
            val messages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            if (messages != null && messages.isNotEmpty()) {
                val message = messages[0] as NdefMessage
                if (message.records.isNotEmpty()) {
                    val jsonString = String(message.records[0].payload, StandardCharsets.UTF_8)
                    processReceivedData(jsonString, "NFC")
                }
            }
        } catch (e: Exception) {
            showMessage("Error reading NFC data: ${e.message}")
        }
    }

    private fun handleScannedQrCode(qrContent: String) {
        try {
            if (qrContent.startsWith("taptap://")) {
                // Parse the deep link
                val uri = Uri.parse(qrContent)
                handleDeepLink(uri)
            } else {
                // Try to parse as direct JSON
                processReceivedData(qrContent, "QR Code")
            }
        } catch (e: Exception) {
            showMessage("Error processing QR code: ${e.message}")
        }
    }

    private fun handleDeepLink(uri: Uri?) {
        uri ?: return

        try {
            val encodedData = uri.getQueryParameter("data")
            if (encodedData != null) {
                // URL-safe Base64 decoding
                val decoded = Base64.decode(encodedData, Base64.URL_SAFE)
                val jsonString = String(decoded, StandardCharsets.UTF_8)
                processReceivedData(jsonString, "QR Code")
            } else {
                showMessage("No data found in QR code")
            }
        } catch (e: Exception) {
            showMessage("Invalid QR code data: ${e.message}")
        }
    }

    private fun processReceivedData(jsonString: String, source: String) {
        try {
            val data = JSONObject(jsonString)

            //  make sure it's from our app
            if (!"com.taptap".equals(data.optString("app_id", ""))) {
                showMessage("Data not from TapTap app")
                return
            }

            // Show success message (instead of saving to db for now...)
            val userName = data.optString("fullName", "Unknown User")
            showMessage("Connection received via $source: $userName")

            // Note: In a real implementation, you would save this connection to a database
            // and update the connections list in the UI

        } catch (e: Exception) {
            showMessage("Invalid data format: ${e.message}")
        }
    }

    private fun showMessage(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
    }
}
