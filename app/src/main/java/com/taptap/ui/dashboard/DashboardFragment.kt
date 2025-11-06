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
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.taptap.R
import org.json.JSONObject
import java.nio.charset.StandardCharsets

class DashboardFragment : Fragment() {

    private lateinit var resultText: TextView
    private lateinit var scanButton: Button

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

        val textDashboard = view.findViewById<TextView>(R.id.text_dashboard)
        textDashboard.text = "Receive Profile"

        resultText = view.findViewById(R.id.result_text)
        scanButton = view.findViewById(R.id.scan_button)

        scanButton.setOnClickListener { startQrScanner() }

        checkForIncomingData()
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

    private fun startQrScanner() {
        val options = ScanOptions()
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        options.setPrompt("Scan a QR code from TapTap")
        options.setCameraId(0)
        options.setBeepEnabled(false)
        options.setBarcodeImageEnabled(true)
        qrScanner.launch(options)
    }

    private fun handleScannedQrCode(qrContent: String) {
        if (qrContent.startsWith("myapp://")) {
            // its a deep link, parse it
            val uri = Uri.parse(qrContent)
            handleDeepLink(uri)
        } else {
            // try to parse as direct json
            processReceivedData(qrContent, "QR Code")
        }
    }

    private fun handleDeepLink(uri: Uri?) {
        uri ?: return

        try {
            val encodedData = uri.getQueryParameter("data")
            if (encodedData != null) {
                // add padding back if needed
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
                processReceivedData(jsonString, "QR Code")
            }
        } catch (e: Exception) {
            showMessage("Invalid QR code data")
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
            showMessage("Error reading NFC data")
        }
    }

    private fun processReceivedData(jsonString: String, source: String) {
        try {
            val data = JSONObject(jsonString)

            // security check
            if (!"com.taptap".equals(data.optString("app_id", ""))) {
                showMessage("Data not from TapTap app")
                return
            }

            // display received profile
            val result = """
                 Received via $source
                
                👤 ${data.optString("fullName", "N/A")}
                📧 ${data.optString("email", "N/A")}
                📞 ${data.optString("phone", "N/A")}
                💼 ${data.optString("description", "N/A")}
                📍 ${data.optString("location", "N/A")}
                ${if (data.optString("linkedIn", "").isNotEmpty()) "🔗 ${data.optString("linkedIn")}" else ""}
            """.trimIndent()

            resultText.text = result
            showMessage("Profile received via $source")

        } catch (e: Exception) {
            showMessage("Invalid data format")
        }
    }

    private fun showMessage(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }
}
