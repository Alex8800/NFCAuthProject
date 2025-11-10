package com.example.nfcauth

import android.content.Intent
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import java.util.Arrays

class MyHostApduService : HostApduService() {

    companion object {
        var personToEmulate: Person? = null
        const val ACTION_SUCCESSFUL_SIGN_IN = "com.example.nfcauth.SUCCESSFUL_SIGN_IN"

        private const val AID = "F0010203040506"
        private val GET_DATA_COMMAND = "GET_USER_DATA".toByteArray()
        private val TRANSACTION_SUCCESSFUL_COMMAND = "TRANSACTION_OK".toByteArray()

        private val SELECT_OK_SW = byteArrayOf(0x90.toByte(), 0x00)
        private val UNKNOWN_COMMAND_SW = byteArrayOf(0x6F, 0x00)
        private val DATA_NOT_FOUND_SW = byteArrayOf(0x6A, 0x82.toByte())
    }

    override fun onDeactivated(reason: Int) { }

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        if (isSelectAidApdu(commandApdu)) {
            return SELECT_OK_SW
        }

        if (Arrays.equals(GET_DATA_COMMAND, commandApdu)) {
            val person = personToEmulate
            return if (person != null) {
                val payload = "ID:${person.id};Name:${person.name}".toByteArray()
                payload + SELECT_OK_SW
            } else {
                DATA_NOT_FOUND_SW
            }
        }

        if (Arrays.equals(TRANSACTION_SUCCESSFUL_COMMAND, commandApdu)) {
            val intent = Intent(ACTION_SUCCESSFUL_SIGN_IN)
            // Make the broadcast explicit to ensure it's received by our app
            intent.setPackage(packageName)
            sendBroadcast(intent)
            return SELECT_OK_SW
        }

        return UNKNOWN_COMMAND_SW
    }

    private fun isSelectAidApdu(apdu: ByteArray): Boolean {
        if (apdu.size < 5) return false
        if (apdu[0] != 0x00.toByte() || apdu[1] != 0xA4.toByte() || apdu[2] != 0x04.toByte() || apdu[3] != 0x00.toByte()) {
            return false
        }
        val lc = apdu[4].toInt() and 0xFF
        if (apdu.size < 5 + lc) return false
        val data = apdu.copyOfRange(5, 5 + lc)
        val receivedAid = data.joinToString("") { "%02x".format(it) }
        return receivedAid.equals(AID, ignoreCase = true)
    }
}
