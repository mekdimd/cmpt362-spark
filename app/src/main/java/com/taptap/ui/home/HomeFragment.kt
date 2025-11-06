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
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
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
    private lateinit var userInfo: TextView
    private lateinit var nfcButton: Button
    private lateinit var qrButton: Button

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
        val textHome = view.findViewById<TextView>(R.id.text_home)
        textHome.text = "Share Your Profile"

        userInfo = view.findViewById(R.id.user_info)
        nfcButton = view.findViewById(R.id.nfc_button)
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
        var userInfoText = "👤 ${user.fullName}\n"
        userInfoText += "📧 ${user.email}\n"
        userInfoText += "📞 ${user.phone}\n"
        userInfoText += "📍 ${user.location}\n"

        if (user.description.isNotEmpty()) {
            userInfoText += "💼 ${user.description}\n"
        }

        if (user.linkedIn.isNotEmpty()) {
            userInfoText += "🔗 ${user.linkedIn}"
        }

        userInfo.text = userInfoText
    }

    private fun setupShareButtons() {
        nfcButton.setOnClickListener { startNfcSharing() }
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
            showMessage("Error generating QR code")
        }
    }

    private fun generateQrCode(data: String): Bitmap {
        var base64Data = Base64.encodeToString(data.toByteArray(), Base64.DEFAULT)
        base64Data = base64Data.replace("\n", "")
        base64Data = base64Data.replace("=", "")
        base64Data = base64Data.replace("/", "_")
        base64Data = base64Data.replace("+", "-")

        val deepLink = "myapp://share?data=$base64Data"

        val hints = java.util.EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8")
        hints.put(EncodeHintType.MARGIN, 1)

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

    private fun showMessage(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_LONG).show()
    }
}
