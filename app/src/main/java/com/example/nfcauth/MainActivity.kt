package com.example.nfcauth

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Group

class MainActivity : Activity(), NfcAdapter.ReaderCallback {

    private var nfcAdapter: NfcAdapter? = null

    // UI Groups
    private lateinit var groupRoleSelection: Group
    private lateinit var groupEmployeeUi: ConstraintLayout
    private lateinit var groupReaderUi: ConstraintLayout
    private lateinit var textAccessGranted: TextView
    private lateinit var textSuccess: TextView

    // Role Selection Screen
    private lateinit var buttonRoleEmployee: Button
    private lateinit var buttonRoleReader: Button

    // Employee Screen
    private lateinit var textEmployeeName: TextView

    private var deviceRole: Role? = null
    enum class Role { EMPLOYEE, READER }

    private val successReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == MyHostApduService.ACTION_SUCCESSFUL_SIGN_IN) {
                showSuccessScreen()
            }
        }
    }

    companion object {
        private const val AID = "F0010203040506"
        private val GET_DATA_COMMAND = "GET_USER_DATA".toByteArray()
        private val TRANSACTION_SUCCESSFUL_COMMAND = "TRANSACTION_OK".toByteArray()
        private val SELECT_OK_SW = byteArrayOf(0x90.toByte(), 0x00)

        fun createSelectAidApdu(aid: String): ByteArray {
            val aidBytes = aid.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            return byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, aidBytes.size.toByte()) + aidBytes
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // --- Find all UI elements ---
        groupRoleSelection = findViewById(R.id.group_role_selection)
        groupEmployeeUi = findViewById(R.id.group_employee_ui)
        groupReaderUi = findViewById(R.id.group_reader_ui)
        textAccessGranted = findViewById(R.id.text_access_granted)
        textSuccess = findViewById(R.id.text_success)
        buttonRoleEmployee = findViewById(R.id.button_role_employee)
        buttonRoleReader = findViewById(R.id.button_role_reader)
        textEmployeeName = findViewById(R.id.text_employee_name)

        // --- NFC Setup ---
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC is not available on this device.", Toast.LENGTH_LONG).show()
            finish(); return
        }

        if (!nfcAdapter!!.isEnabled) {
            Toast.makeText(this, "Please enable NFC in your settings.", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
        } else {
            showRoleSelectionScreen()
        }

        buttonRoleEmployee.setOnClickListener { setupAsEmployee() }
        buttonRoleReader.setOnClickListener { setupAsReader() }
    }

    private fun showRoleSelectionScreen() {
        deviceRole = null
        groupRoleSelection.visibility = View.VISIBLE
        groupEmployeeUi.visibility = View.GONE
        groupReaderUi.visibility = View.GONE
        textAccessGranted.visibility = View.GONE
        textSuccess.visibility = View.GONE
    }

    private fun setupAsEmployee() {
        deviceRole = Role.EMPLOYEE
        val employee = Person(id = "1337", name = "Jane Doe")
        MyHostApduService.personToEmulate = employee

        groupRoleSelection.visibility = View.GONE
        groupEmployeeUi.visibility = View.VISIBLE
        groupReaderUi.visibility = View.GONE
        textAccessGranted.visibility = View.GONE
        textSuccess.visibility = View.GONE

        textEmployeeName.text = employee.name

        val intentFilter = IntentFilter(MyHostApduService.ACTION_SUCCESSFUL_SIGN_IN)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(successReceiver, intentFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(successReceiver, intentFilter)
        }
    }

    private fun setupAsReader() {
        deviceRole = Role.READER
        groupRoleSelection.visibility = View.GONE
        groupEmployeeUi.visibility = View.GONE
        groupReaderUi.visibility = View.VISIBLE
        textAccessGranted.visibility = View.GONE
        textSuccess.visibility = View.GONE

        nfcAdapter?.enableReaderMode(this, this,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
            null)
    }

    private fun showSuccessScreen() {
        groupRoleSelection.visibility = View.GONE
        groupEmployeeUi.visibility = View.GONE
        groupReaderUi.visibility = View.GONE
        textAccessGranted.visibility = View.GONE
        textSuccess.visibility = View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        if (deviceRole == Role.READER) {
            setupAsReader() // Re-enable reader mode
        }
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(successReceiver)
        } catch (e: IllegalArgumentException) {
            // In case it was never registered
        }
    }

    override fun onTagDiscovered(tag: Tag?) {
        if (deviceRole != Role.READER) return

        val isoDep = IsoDep.get(tag)
        if (isoDep != null) {
            try {
                isoDep.connect()
                val selectAidApdu = createSelectAidApdu(AID)
                val result = isoDep.transceive(selectAidApdu)

                if (result.contentEquals(SELECT_OK_SW)) {
                    val response = isoDep.transceive(GET_DATA_COMMAND)
                    if (response.size > 2) {
                        val payload = response.copyOfRange(0, response.size - 2)
                        val status = response.copyOfRange(response.size - 2, response.size)

                        if (status.contentEquals(SELECT_OK_SW)) {
                            isoDep.transceive(TRANSACTION_SUCCESSFUL_COMMAND)
                            val person = parsePerson(String(payload, Charsets.UTF_8))
                            if (person != null) {
                                runOnUiThread {
                                    groupReaderUi.visibility = View.GONE
                                    textAccessGranted.visibility = View.VISIBLE
                                    textAccessGranted.text = "Access Granted for\n${person.name} (ID: ${person.id})"

                                    // Reset to reader screen after a delay
                                    Handler(Looper.getMainLooper()).postDelayed({ setupAsReader() }, 3000)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Handle errors
            } finally {
                isoDep.close()
            }
        }
    }

    private fun parsePerson(data: String): Person? {
        return try {
            val parts = data.split(";").associate { val (k, v) = it.split(":", limit=2); k to v }
            Person(parts.getValue("ID"), parts.getValue("Name"))
        } catch (e: Exception) { null }
    }
}
