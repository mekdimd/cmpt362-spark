package com.taptap.nfc

import android.app.Activity
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.Ndef
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.nio.charset.Charset

/**
 * Manager class for handling NFC reading operations
 * Provides a robust fallback strategy:
 * 1. Try standard Ndef tech first
 * 2. Fall back to ISO-DEP transceiver commands if Ndef fails
 */
class NfcReaderManager {

    companion object {
        private const val TAG = "NfcReaderManager"
    }

    private var nfcAdapter: NfcAdapter? = null
    private var isReaderModeEnabled = false

    private val _contactFound = MutableSharedFlow<String>()
    val contactFound: SharedFlow<String> = _contactFound.asSharedFlow()

    private val _error = MutableSharedFlow<String>()
    val error: SharedFlow<String> = _error.asSharedFlow()

    /**
     * NFC Reader callback - triggered when a tag is detected
     */
    private val readerCallback = NfcAdapter.ReaderCallback { tag ->
        try {
            Log.i(TAG, "╔═══════════════════════════════════════╗")
            Log.i(TAG, "║    NFC TAG DETECTED                   ║")
            Log.i(TAG, "╚═══════════════════════════════════════╝")
            Log.i(TAG, "Tag ID: ${tag.id.joinToString("") { "%02X".format(it) }}")
            Log.i(TAG, "Tag techs: ${tag.techList.joinToString()}")

            val ndefMessage = readNdefMessage(tag)

            if (ndefMessage != null) {
                Log.i(TAG, "NDEF message read successfully")
                Log.i(TAG, "Records count: ${ndefMessage.records.size}")

                val userId = extractUserIdFromDeepLink(ndefMessage)

                if (userId != null) {
                    Log.i(TAG, "Contact found: $userId")
                    kotlinx.coroutines.runBlocking {
                        _contactFound.emit(userId)
                    }
                } else {
                    Log.w(TAG, "No valid deep link found in NDEF message")
                    kotlinx.coroutines.runBlocking {
                        _error.emit("Invalid NFC data format")
                    }
                }
            } else {
                Log.w(TAG, "Failed to read NDEF message from tag")
                kotlinx.coroutines.runBlocking {
                    _error.emit("Could not read NFC tag")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling tag", e)
            kotlinx.coroutines.runBlocking {
                _error.emit("Error reading NFC: ${e.message}")
            }
        }
    }

    /**
     * Enable NFC reader mode
     * @param activity The current activity context
     * @return true if enabled successfully, false otherwise
     */
    fun enableReaderMode(activity: Activity): Boolean {
        Log.d(TAG, "=== enableReaderMode() called ===")

        if (isReaderModeEnabled) {
            Log.d(TAG, "Reader mode already enabled")
            return true
        }

        nfcAdapter = NfcAdapter.getDefaultAdapter(activity)
        Log.d(TAG, "NfcAdapter obtained: ${nfcAdapter != null}")

        if (nfcAdapter == null) {
            Log.e(TAG, "NFC not available on this device")
            return false
        }

        if (!nfcAdapter!!.isEnabled) {
            Log.w(TAG, "NFC is disabled in settings")
            return false
        }

        val flags = (
            NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
        )

        try {
            Log.d(TAG, "Calling NfcAdapter.enableReaderMode with flags: $flags")
            nfcAdapter?.enableReaderMode(activity, readerCallback, flags, null)
            isReaderModeEnabled = true
            Log.i(TAG, "NFC Reader mode enabled successfully")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable reader mode", e)
            return false
        }
    }

    /**
     * Disable NFC reader mode
     * @param activity The current activity context
     */
    fun disableReaderMode(activity: Activity) {
        Log.d(TAG, "=== disableReaderMode() called ===")

        if (!isReaderModeEnabled) {
            Log.d(TAG, "Reader mode already disabled")
            return
        }

        try {
            nfcAdapter?.disableReaderMode(activity)
            isReaderModeEnabled = false
            Log.i(TAG, "NFC Reader mode disabled successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disable reader mode", e)
        }
    }

    /**
     * Check if reader mode is currently enabled
     */
    fun isReaderModeEnabled(): Boolean = isReaderModeEnabled

    /**
     * Read NDEF message from tag using fallback strategy
     * 1. Try Ndef tech API
     * 2. Fall back to ISO-DEP transceiver commands
     */
    private fun readNdefMessage(tag: Tag): NdefMessage? {
        try {
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                val message = ndef.ndefMessage ?: ndef.cachedNdefMessage
                ndef.close()

                if (message != null) {
                    Log.i(TAG, "Successfully read NDEF via Ndef tech")
                    return message
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Ndef tech failed, trying ISO-DEP fallback", e)
        }

        return readNdefViaIsoDep(tag)
    }

    /**
     * Read NDEF message using ISO-DEP transceiver commands
     * Implements NFC Forum Type 4 Tag operation specification
     */
    private fun readNdefViaIsoDep(tag: Tag): NdefMessage? {
        try {
            val isoDep = IsoDep.get(tag) ?: return null
            isoDep.timeout = 3000
            isoDep.connect()

            val selectAid = byteArrayOf(
                0x00, 0xA4.toByte(), 0x04, 0x00,
                0x07,
                0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01,
                0x00 
            )

            var response = isoDep.transceive(selectAid)
            if (!isSuccess(response)) {
                Log.w(TAG, "Failed to select NDEF application")
                isoDep.close()
                return null
            }
            Log.d(TAG, "NDEF application selected")

            var ndefFileId: Pair<Byte, Byte>? = null
            val selectCc = byteArrayOf(
                0x00, 0xA4.toByte(), 0x00, 0x0C,
                0x02,
                0xE1.toByte(), 0x03
            )

            response = isoDep.transceive(selectCc)
            if (isSuccess(response)) {
                Log.d(TAG, "CC file selected")

                val readCc = byteArrayOf(
                    0x00, 0xB0.toByte(), 0x00, 0x00,
                    0x0F
                )

                response = isoDep.transceive(readCc)
                if (isSuccess(response)) {
                    val ccData = response.copyOfRange(0, response.size - 2)
                    ndefFileId = parseNdefFileIdFromCc(ccData)
                    Log.d(TAG, "Parsed NDEF file ID from CC: $ndefFileId")
                }
            }

            val fileId = ndefFileId ?: Pair(0xE1.toByte(), 0x04.toByte())
            val selectNdef = byteArrayOf(
                0x00, 0xA4.toByte(), 0x00, 0x0C,
                0x02,
                fileId.first, fileId.second
            )

            response = isoDep.transceive(selectNdef)
            if (!isSuccess(response)) {
                Log.w(TAG, "Failed to select NDEF file")
                isoDep.close()
                return null
            }
            Log.d(TAG, "NDEF file selected")

            val readNlen = byteArrayOf(
                0x00, 0xB0.toByte(), 0x00, 0x00,
                0x02
            )

            response = isoDep.transceive(readNlen)
            if (!isSuccess(response) || response.size < 4) {
                Log.w(TAG, "Failed to read NLEN")
                isoDep.close()
                return null
            }

            val nlen = ((response[0].toInt() and 0xFF) shl 8) or (response[1].toInt() and 0xFF)
            if (nlen <= 0) {
                Log.w(TAG, "Invalid NLEN: $nlen")
                isoDep.close()
                return null
            }
            Log.d(TAG, "NDEF length: $nlen bytes")

            val ndefData = ByteArray(nlen)
            var offset = 0
            var fileOffset = 2

            while (offset < nlen) {
                val remaining = nlen - offset
                val chunkSize = minOf(0xF0, remaining)

                val p1 = (fileOffset shr 8).toByte()
                val p2 = (fileOffset and 0xFF).toByte()

                val readChunk = byteArrayOf(
                    0x00, 0xB0.toByte(), p1, p2,
                    chunkSize.toByte()
                )

                response = isoDep.transceive(readChunk)
                if (!isSuccess(response)) {
                    Log.w(TAG, "Failed to read NDEF data at offset $fileOffset")
                    isoDep.close()
                    return null
                }

                val chunk = response.copyOfRange(0, response.size - 2)
                System.arraycopy(chunk, 0, ndefData, offset, chunk.size)
                offset += chunk.size
                fileOffset += chunk.size
            }

            isoDep.close()

            Log.i(TAG, "Successfully read NDEF via ISO-DEP (${ndefData.size} bytes)")
            return NdefMessage(ndefData)

        } catch (e: Exception) {
            Log.e(TAG, "ISO-DEP read failed", e)
            return null
        }
    }

    /**
     * Check if APDU response indicates success (SW1=0x90, SW2=0x00)
     */
    private fun isSuccess(response: ByteArray): Boolean {
        return response.size >= 2 &&
               response[response.size - 2] == 0x90.toByte() &&
               response[response.size - 1] == 0x00.toByte()
    }

    /**
     * Parse NDEF File ID from Capability Container
     * Looks for NDEF File Control TLV (T=0x04)
     */
    private fun parseNdefFileIdFromCc(cc: ByteArray): Pair<Byte, Byte>? {
        var i = 2

        while (i + 1 < cc.size) {
            val t = cc[i].toInt() and 0xFF
            val l = cc[i + 1].toInt() and 0xFF
            val valueStart = i + 2
            val valueEnd = valueStart + l

            if (t == 0x04 && l >= 6 && valueEnd <= cc.size) {
                return Pair(cc[valueStart], cc[valueStart + 1])
            }

            i = valueEnd
        }

        return null
    }

    /**
     * Extract user ID from deep link in NDEF message
     * Expected format: taptap://connect/{userId}
     */
    private fun extractUserIdFromDeepLink(message: NdefMessage): String? {
        for (record in message.records) {
            val uri = parseNdefRecordToUri(record)

            if (uri != null && uri.startsWith("taptap://connect/")) {
                val userId = uri.substringAfter("taptap://connect/").trim()
                if (userId.isNotEmpty()) {
                    return userId
                }
            }
        }
        return null
    }

    /**
     * Parse NDEF record to URI string
     * Handles both URI and Text record types
     */
    private fun parseNdefRecordToUri(record: NdefRecord): String? {
        return try {
            when {
                record.tnf == NdefRecord.TNF_WELL_KNOWN &&
                record.type.contentEquals(NdefRecord.RTD_URI) -> {
                    val payload = record.payload
                    if (payload.isEmpty()) return null

                    val prefixCode = payload[0].toInt() and 0xFF
                    val uriPart = String(payload, 1, payload.size - 1, Charset.forName("UTF-8"))

                    val prefixes = arrayOf(
                        "", "http://www.", "https://www.", "http://", "https://",
                        "tel:", "mailto:", "ftp://anonymous:anonymous@", "ftp://ftp.", "ftps://",
                        "sftp://", "smb://", "nfs://", "ftp://", "dav://",
                        "news:", "telnet://", "imap:", "rtsp://", "urn:",
                        "pop:", "sip:", "sips:", "tftp:", "btspp://",
                        "btl2cap://", "btgoep://", "tcpobex://", "irdaobex://", "file://",
                        "urn:epc:id:", "urn:epc:tag:", "urn:epc:pat:", "urn:epc:raw:", "urn:epc:",
                        "urn:nfc:"
                    )

                    val prefix = if (prefixCode in prefixes.indices) prefixes[prefixCode] else ""
                    prefix + uriPart
                }

                record.tnf == NdefRecord.TNF_WELL_KNOWN &&
                record.type.contentEquals(NdefRecord.RTD_TEXT) -> {
                    val payload = record.payload
                    val statusByte = payload[0].toInt()
                    val languageCodeLength = statusByte and 0x3F
                    val isUtf16 = (statusByte and 0x80) != 0

                    val encoding = if (isUtf16) Charsets.UTF_16 else Charsets.UTF_8
                    String(payload, 1 + languageCodeLength, payload.size - 1 - languageCodeLength, encoding)
                }

                else -> {
                    String(record.payload, Charset.forName("UTF-8"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse NDEF record", e)
            null
        }
    }
}

