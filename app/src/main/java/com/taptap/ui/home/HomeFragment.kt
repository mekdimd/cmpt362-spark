package com.taptap.ui.home

import android.graphics.Bitmap
import android.graphics.Color
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import com.taptap.MainActivity
import com.taptap.R
import com.taptap.viewmodel.UserViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import java.nio.charset.StandardCharsets

class HomeFragment : Fragment() {

    private var userViewModel: UserViewModel? = null
    private var nfcAdapter: NfcAdapter? = null

    // ui elements
    private lateinit var userName: android.widget.TextView
    private lateinit var userTitle: android.widget.TextView
    private lateinit var userLinkedIn: android.widget.TextView
    private lateinit var userWebsite: android.widget.TextView
    private lateinit var nfcButton: com.google.android.material.button.MaterialButton
    private lateinit var receiveButton: com.google.android.material.button.MaterialButton
    private lateinit var qrButton: com.google.android.material.button.MaterialButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_home, container, false)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        userViewModel = (requireActivity() as MainActivity).userViewModel

        // setup ui elements
        setupViews(view)
        setupShareButtons()
        observeUserData()
        updateNfcButtonVisibility()
    }

    private fun setupViews(view: View) {
        userName = view.findViewById(R.id.user_name)
        userTitle = view.findViewById(R.id.user_title)
        userLinkedIn = view.findViewById(R.id.user_linkedin)
        userWebsite = view.findViewById(R.id.user_website)
        nfcButton = view.findViewById(R.id.nfc_button)
        receiveButton = view.findViewById(R.id.receive_button)
        qrButton = view.findViewById(R.id.qr_button)

        nfcAdapter = (requireActivity() as MainActivity).getNfcAdapter()
    }

    private fun updateNfcButtonVisibility() {
        if (nfcAdapter == null) {
            nfcButton.visibility = View.GONE
        } else {
            nfcButton.visibility = View.VISIBLE
        }
    }

    private fun observeUserData() {
        userViewModel!!.currentUser.observe(viewLifecycleOwner) { user ->
            displayUserInfo(user)
        }
    }

    private fun displayUserInfo(user: com.taptap.model.TapTapUser) {
        userName.text = user.fullName
        userTitle.text = user.description
        userLinkedIn.text = user.linkedIn

        // Use email as website if no website provided
        userWebsite.text = if (user.email.isNotEmpty()) user.email else "Add website"

        // Hide LinkedIn if empty
        userLinkedIn.visibility = if (user.linkedIn.isNotEmpty()) View.VISIBLE else View.GONE

        // Hide website/email if empty
        userWebsite.visibility = if (user.email.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun setupShareButtons() {
        nfcButton.setOnClickListener { startNfcSharing() }
        receiveButton.setOnClickListener {
            // just show a message instead of navigating away
            showMessage("Ready to receive: others can tap their phone or scan your QR code")
        }
        qrButton.setOnClickListener { showQrCode() }
    }

    private fun startNfcSharing() {
        if (nfcAdapter == null) {
            showMessage("NFC not available on this device")
            return
        }

        if (!nfcAdapter!!.isEnabled) {
            showMessage("Please enable NFC in settings")
            return
        }

        showMessage("Ready to share via NFC: Hold phones back-to-back")
    }

    private fun showQrCode() {
        try {
            val profile = userViewModel!!.getUserProfileJson()
            val jsonString = profile.toString()
            val qrCode = generateQrCode(jsonString)

            val imageView = ImageView(requireContext())
            imageView.setImageBitmap(qrCode)

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Scan to Share Profile")
                .setView(imageView)
                .setPositiveButton("Close", null)
                .show()
        } catch (e: Exception) {
            showMessage("Error generating QR code: ${e.message}")
        }
    }

    private fun generateQrCode(data: String): Bitmap {
        try {
            // Use URL-safe Base64 encoding (i.e. without padding)
            val base64Data = Base64.encodeToString(
                data.toByteArray(StandardCharsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            )

            // Create deep link with the data
            val deepLink = "taptap://share?data=$base64Data"

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
                    bitmap.setPixel(x, y, if (isBlack) Color.BLACK else Color.WHITE)
                }
            }
            return bitmap
        } catch (e: Exception) {
            throw RuntimeException("Failed to generate QR code", e)
        }
    }

    private fun showMessage(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_LONG).show()
    }
}
