package com.taptap.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import com.google.firebase.auth.FirebaseAuth

/**
 * Host Card Emulation service for sharing user profile via NFC
 * Emulates an NFC Forum Type 4 Tag (T4T) to send deep links
 * Format: taptap://connect/{userId}
 */
class ProfileHceService : HostApduService() {

    companion object {
        private const val TAG = "ProfileHceService"

        private val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
        private val SW_FILE_NOT_FOUND = byteArrayOf(0x6A.toByte(), 0x82.toByte())
        private val SW_WRONG_PARAMETERS = byteArrayOf(0x6B.toByte(), 0x00)
        private val SW_INS_NOT_SUPPORTED = byteArrayOf(0x6D.toByte(), 0x00)
        private val SW_CLA_NOT_SUPPORTED = byteArrayOf(0x6E.toByte(), 0x00)

        private val NDEF_AID = byteArrayOf(
            0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01
        )

        private val CC_FILE_ID = byteArrayOf(0xE1.toByte(), 0x03) 
        private val NDEF_FILE_ID = byteArrayOf(0xE1.toByte(), 0x04)

        private const val CC_LEN = 0x000F
        private const val MAPPING_VERSION = 0x20
        private const val MLe = 0x003B
        private const val MLc = 0x0034
        private const val MAX_NDEF_SIZE = 0x03E8

        /**
         * Build Capability Container file per NFC Forum T4T specification
         */
        private fun buildCcFile(): ByteArray {
            return byteArrayOf(
                (CC_LEN shr 8).toByte(), (CC_LEN and 0xFF).toByte(),
                MAPPING_VERSION.toByte(),
                (MLe shr 8).toByte(), (MLe and 0xFF).toByte(),
                (MLc shr 8).toByte(), (MLc and 0xFF).toByte(),
                0x04, 0x06,
                NDEF_FILE_ID[0], NDEF_FILE_ID[1],
                (MAX_NDEF_SIZE shr 8).toByte(), (MAX_NDEF_SIZE and 0xFF).toByte(),
                0x00,
                0xFF.toByte()
            )
        }

        /**
         * Build NDEF file containing the deep link URI
         * Format: [NLEN(2)][NDEF Message]
         */
        private fun buildNdefFile(deepLink: String): ByteArray {
            val record = NdefRecord.createUri(deepLink)
            val message = NdefMessage(arrayOf(record))
            val ndefBytes = message.toByteArray()

            val nlen = ndefBytes.size
            return byteArrayOf(
                (nlen shr 8).toByte(),
                (nlen and 0xFF).toByte()
            ) + ndefBytes
        }
    }

    private enum class SelectedFile { NONE, CC, NDEF }
    private var selectedFile: SelectedFile = SelectedFile.NONE

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null || commandApdu.isEmpty()) {
            Log.w(TAG, "Received null or empty APDU")
            return SW_CLA_NOT_SUPPORTED
        }

        val cla = commandApdu[0]
        val ins = commandApdu[1]
        val p1 = commandApdu[2]
        val p2 = commandApdu[3]

        Log.d(TAG, "APDU: CLA=${String.format("%02X", cla)} INS=${String.format("%02X", ins)} P1=${String.format("%02X", p1)} P2=${String.format("%02X", p2)}")

        if (cla.toInt() != 0x00) {
            Log.w(TAG, "Unsupported CLA: ${String.format("%02X", cla)}")
            return SW_CLA_NOT_SUPPORTED
        }

        return when (ins.toInt() and 0xFF) {
            0xA4 -> handleSelect(p1, p2, commandApdu)
            0xB0 -> handleReadBinary(p1, p2, commandApdu)
            else -> {
                Log.w(TAG, "Unsupported INS: ${String.format("%02X", ins)}")
                SW_INS_NOT_SUPPORTED
            }
        }
    }

    /**
     * Handle SELECT command (INS=0xA4)
     * Supports SELECT by AID (P1=0x04) and SELECT by File ID (P1=0x00)
     */
    private fun handleSelect(p1: Byte, p2: Byte, apdu: ByteArray): ByteArray {
        return when (p1.toInt() and 0xFF) {
            0x04 -> {
                val lc = apdu.getOrNull(4)?.toInt()?.and(0xFF) ?: return SW_WRONG_PARAMETERS
                if (apdu.size < 5 + lc) return SW_WRONG_PARAMETERS

                val aid = apdu.copyOfRange(5, 5 + lc)

                return if (aid.contentEquals(NDEF_AID)) {
                    selectedFile = SelectedFile.NONE
                    Log.i(TAG, "NDEF Application selected")
                    SW_OK
                } else {
                    Log.w(TAG, "Unknown AID")
                    SW_FILE_NOT_FOUND
                }
            }

            0x00 -> {
                val lc = apdu.getOrNull(4)?.toInt()?.and(0xFF) ?: return SW_WRONG_PARAMETERS
                if (lc != 2 || apdu.size < 7) return SW_WRONG_PARAMETERS

                val fileId = apdu.copyOfRange(5, 7)

                selectedFile = when {
                    fileId.contentEquals(CC_FILE_ID) -> SelectedFile.CC
                    fileId.contentEquals(NDEF_FILE_ID) -> SelectedFile.NDEF
                    else -> SelectedFile.NONE
                }

                return if (selectedFile == SelectedFile.NONE) {
                    Log.w(TAG, "Unknown File ID")
                    SW_FILE_NOT_FOUND
                } else {
                    Log.i(TAG, "File selected: $selectedFile")
                    SW_OK
                }
            }

            else -> {
                Log.w(TAG, "Unsupported SELECT mode P1=${String.format("%02X", p1)}")
                SW_WRONG_PARAMETERS
            }
        }
    }

    /**
     * Handle READ BINARY command (INS=0xB0)
     * Returns file data starting at offset P1P2 with length Le
     */
    private fun handleReadBinary(p1: Byte, p2: Byte, apdu: ByteArray): ByteArray {
        val offset = ((p1.toInt() and 0xFF) shl 8) or (p2.toInt() and 0xFF)

        val le = if (apdu.size >= 5) (apdu[4].toInt() and 0xFF) else 0x00
        val maxLe = if (le == 0) 256 else le

        Log.d(TAG, "READ BINARY: offset=$offset, maxLe=$maxLe, selectedFile=$selectedFile")

        val fileData = when (selectedFile) {
            SelectedFile.CC -> buildCcFile()
            SelectedFile.NDEF -> {
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                if (userId == null) {
                    Log.e(TAG, "No user logged in, cannot create deep link")
                    return SW_FILE_NOT_FOUND
                }
                val deepLink = "taptap://connect/$userId"
                Log.i(TAG, "Serving deep link: $deepLink")
                buildNdefFile(deepLink)
            }
            else -> {
                Log.w(TAG, "No file selected")
                return SW_FILE_NOT_FOUND
            }
        }

        if (offset > fileData.size) {
            Log.w(TAG, "Invalid offset: $offset (file size: ${fileData.size})")
            return SW_WRONG_PARAMETERS
        }

        val remaining = fileData.size - offset
        val dataToSend = if (remaining <= 0) {
            byteArrayOf()
        } else {
            val length = minOf(remaining, maxLe)
            fileData.copyOfRange(offset, offset + length)
        }

        Log.d(TAG, "Sending ${dataToSend.size} bytes")
        return dataToSend + SW_OK
    }

    override fun onDeactivated(reason: Int) {
        val reasonStr = when (reason) {
            DEACTIVATION_LINK_LOSS -> "LINK_LOSS"
            DEACTIVATION_DESELECTED -> "DESELECTED"
            else -> "UNKNOWN($reason)"
        }
        Log.i(TAG, "HCE deactivated: $reasonStr")
        selectedFile = SelectedFile.NONE
    }
}

