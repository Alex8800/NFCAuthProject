package com.example.nfcauth

import android.app.Activity
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.NfcEvent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import kotlin.random.Random

class MainActivity : Activity(), NfcAdapter.CreateNdefMessageCallback {

    private var nfcAdapter: NfcAdapter? = null
    private lateinit var statusTv: TextView
    private lateinit var sendBtn: Button
    private lateinit var generateBtn: Button
    private var myToken: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusTv = findViewById(R.id.statusTv)
        sendBtn = findViewById(R.id.sendBtn)
        generateBtn = findViewById(R.id.generateBtn)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC indisponibil", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (!nfcAdapter!!.isEnabled) {
            statusTv.text = "Activează NFC în setări."
            startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
        } else {
            statusTv.text = "NFC activ. Apropie alt telefon."
        }
        nfcAdapter?.setNdefPushMessageCallback(this, this)

        generateBtn.setOnClickListener {
            myToken = Random.nextInt(100000, 999999).toString()
            statusTv.text = "Token generat: $myToken"
        }

        sendBtn.setOnClickListener {
            statusTv.text = "Apropie alt telefon pentru a trimite token-ul: $myToken"
        }
    }

    override fun createNdefMessage(event: NfcEvent?): NdefMessage? {
        if (myToken.isEmpty()) return null
        val payload = "AUTH_TOKEN:${myToken}".toByteArray()
        val record = NdefRecord.createMime("text/plain", payload)
        return NdefMessage(arrayOf(record))
    }
}
