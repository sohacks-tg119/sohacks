package com.hackerman.sohacksrev2

import android.Manifest
import android.app.Activity
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import java.util.UUID



class MainActivity : AppCompatActivity() {

    // BLE
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null


    // UI
    private lateinit var btnConnect: MaterialButton
    private lateinit var btnECO: MaterialButton
    private lateinit var btnNormal: MaterialButton
    private lateinit var btnSport: MaterialButton
    private lateinit var btnDev: MaterialButton
    private lateinit var btnLock: MaterialButton
    private lateinit var btnUnlock: MaterialButton
    private lateinit var btnChangeDevice: MaterialButton

    private lateinit var btn8kmh: MaterialButton
    private lateinit var btn15kmh: MaterialButton
    private lateinit var btn20kmh: MaterialButton
    private lateinit var btn25kmh: MaterialButton
    private lateinit var btn30kmh: MaterialButton

    private lateinit var sliderSpeedModifier: Slider
    private lateinit var tvSpeedModifier: TextView
    private lateinit var switchAdvanced: Switch
    private lateinit var layoutAdvancedContent: LinearLayout

    // Advanced controls
    private lateinit var spinnerModes: Spinner
    private lateinit var txtCmdHex: EditText
    private lateinit var btnSendHex: MaterialButton
    private lateinit var tvBleOutput: TextView

    private lateinit var prefs: SharedPreferences

    // ===== Hardcoded mode commands 0..254 (we use 1..254 from the dropdown) =====
    private val MODE_CMDS: Array<String> = arrayOf(
        "D706A30000A90D0A",
        "D706A30001AA0D0A",
        "D706A30002AB0D0A",
        "D706A30003AC0D0A",
        "D706A30004AD0D0A",
        "D706A30005AE0D0A",
        "D706A30006AF0D0A",
        "D706A30007B00D0A",
        "D706A30008B10D0A",
        "D706A30009B20D0A",
        "D706A3000AB30D0A",
        "D706A3000BB40D0A",
        "D706A3000CB50D0A",
        "D706A3000DB60D0A",
        "D706A3000EB70D0A",
        "D706A3000FB80D0A",
        "D706A30010B90D0A",
        "D706A30011BA0D0A",
        "D706A30012BB0D0A",
        "D706A30013BC0D0A",
        "D706A30014BD0D0A",
        "D706A30015BE0D0A",
        "D706A30016BF0D0A",
        "D706A30017C00D0A",
        "D706A30018C10D0A",
        "D706A30019C20D0A",
        "D706A3001AC30D0A",
        "D706A3001BC40D0A",
        "D706A3001CC50D0A",
        "D706A3001DC60D0A",
        "D706A3001EC70D0A",
        "D706A3001FC80D0A",
        "D706A30020C90D0A",
        "D706A30021CA0D0A",
        "D706A30022CB0D0A",
        "D706A30023CC0D0A",
        "D706A30024CD0D0A",
        "D706A30025CE0D0A",
        "D706A30026CF0D0A",
        "D706A30027D00D0A",
        "D706A30028D10D0A",
        "D706A30029D20D0A",
        "D706A3002AD30D0A",
        "D706A3002BD40D0A",
        "D706A3002CD50D0A",
        "D706A3002DD60D0A",
        "D706A3002ED70D0A",
        "D706A3002FD80D0A",
        "D706A30030D90D0A",
        "D706A30031DA0D0A",
        "D706A30032DB0D0A",
        "D706A30033DC0D0A",
        "D706A30034DD0D0A",
        "D706A30035DE0D0A",
        "D706A30036DF0D0A",
        "D706A30037E00D0A",
        "D706A30038E10D0A",
        "D706A30039E20D0A",
        "D706A3003AE30D0A",
        "D706A3003BE40D0A",
        "D706A3003CE50D0A",
        "D706A3003DE60D0A",
        "D706A3003EE70D0A",
        "D706A3003FE80D0A",
        "D706A30040E90D0A",
        "D706A30041EA0D0A",
        "D706A30042EB0D0A",
        "D706A30043EC0D0A",
        "D706A30044ED0D0A",
        "D706A30045EE0D0A",
        "D706A30046EF0D0A",
        "D706A30047F00D0A",
        "D706A30048F10D0A",
        "D706A30049F20D0A",
        "D706A3004AF30D0A",
        "D706A3004BF40D0A",
        "D706A3004CF50D0A",
        "D706A3004DF60D0A",
        "D706A3004EF70D0A",
        "D706A3004FF80D0A",
        "D706A30050F90D0A",
        "D706A30051FA0D0A",
        "D706A30052FB0D0A",
        "D706A30053FC0D0A",
        "D706A30054FD0D0A",
        "D706A30055FE0D0A",
        "D706A300560F0D0A",
        "D706A30057100D0A",
        "D706A30058110D0A",
        "D706A30059120D0A",
        "D706A3005A130D0A",
        "D706A3005B140D0A",
        "D706A3005C150D0A",
        "D706A3005D160D0A",
        "D706A3005E170D0A",
        "D706A3005F180D0A",
        "D706A30060190D0A",
        "D706A300611A0D0A",
        "D706A300621B0D0A",
        "D706A300631C0D0A",
        "D706A300641D0D0A",
        "D706A300651E0D0A",
        "D706A300661F0D0A",
        "D706A30067200D0A",
        "D706A30068210D0A",
        "D706A30069220D0A",
        "D706A3006A230D0A",
        "D706A3006B240D0A",
        "D706A3006C250D0A",
        "D706A3006D260D0A",
        "D706A3006E270D0A",
        "D706A3006F280D0A",
        "D706A30070290D0A",
        "D706A300712A0D0A",
        "D706A300722B0D0A",
        "D706A300732C0D0A",
        "D706A300742D0D0A",
        "D706A300752E0D0A",
        "D706A300762F0D0A",
        "D706A30077300D0A",
        "D706A30078310D0A",
        "D706A30079320D0A",
        "D706A3007A330D0A",
        "D706A3007B340D0A",
        "D706A3007C350D0A",
        "D706A3007D360D0A",
        "D706A3007E370D0A",
        "D706A3007F380D0A",
        "D706A30080390D0A",
        "D706A300813A0D0A",
        "D706A300823B0D0A",
        "D706A300833C0D0A",
        "D706A300843D0D0A",
        "D706A300853E0D0A",
        "D706A300863F0D0A",
        "D706A30087400D0A",
        "D706A30088410D0A",
        "D706A30089420D0A",
        "D706A3008A430D0A",
        "D706A3008B440D0A",
        "D706A3008C450D0A",
        "D706A3008D460D0A",
        "D706A3008E470D0A",
        "D706A3008F480D0A",
        "D706A30090490D0A",
        "D706A300914A0D0A",
        "D706A300924B0D0A",
        "D706A300934C0D0A",
        "D706A300944D0D0A",
        "D706A300954E0D0A",
        "D706A300964F0D0A",
        "D706A30097500D0A",
        "D706A30098510D0A",
        "D706A30099520D0A",
        "D706A3009A530D0A",
        "D706A3009B540D0A",
        "D706A3009C550D0A",
        "D706A3009D560D0A",
        "D706A3009E570D0A",
        "D706A3009F580D0A",
        "D706A300A0590D0A",
        "D706A300A15A0D0A",
        "D706A300A25B0D0A",
        "D706A300A35C0D0A",
        "D706A300A45D0D0A",
        "D706A300A55E0D0A",
        "D706A300A65F0D0A",
        "D706A300A7600D0A",
        "D706A300A8610D0A",
        "D706A300A9620D0A",
        "D706A300AA630D0A",
        "D706A300AB640D0A",
        "D706A300AC650D0A",
        "D706A300AD660D0A",
        "D706A300AE670D0A",
        "D706A300AF680D0A",
        "D706A300B0690D0A",
        "D706A300B16A0D0A",
        "D706A300B26B0D0A",
        "D706A300B36C0D0A",
        "D706A300B46D0D0A",
        "D706A300B56E0D0A",
        "D706A300B66F0D0A",
        "D706A300B7700D0A",
        "D706A300B8710D0A",
        "D706A300B9720D0A",
        "D706A300BA730D0A",
        "D706A300BB740D0A",
        "D706A300BC750D0A",
        "D706A300BD760D0A",
        "D706A300BE770D0A",
        "D706A300BF780D0A",
        "D706A300C0790D0A",
        "D706A300C17A0D0A",
        "D706A300C27B0D0A",
        "D706A300C37C0D0A",
        "D706A300C47D0D0A",
        "D706A300C57E0D0A",
        "D706A300C67F0D0A",
        "D706A300C7800D0A",
        "D706A300C8810D0A",
        "D706A300C9820D0A",
        "D706A300CA830D0A",
        "D706A300CB840D0A",
        "D706A300CC850D0A",
        "D706A300CD860D0A",
        "D706A300CE870D0A",
        "D706A300CF880D0A",
        "D706A300D0890D0A",
        "D706A300D18A0D0A",
        "D706A300D28B0D0A",
        "D706A300D38C0D0A",
        "D706A300D48D0D0A",
        "D706A300D58E0D0A",
        "D706A300D68F0D0A",
        "D706A300D7900D0A",
        "D706A300D8910D0A",
        "D706A300D9920D0A",
        "D706A300DA930D0A",
        "D706A300DB940D0A",
        "D706A300DC950D0A",
        "D706A300DD960D0A",
        "D706A300DE970D0A",
        "D706A300DF980D0A",
        "D706A300E0990D0A",
        "D706A300E19A0D0A",
        "D706A300E29B0D0A",
        "D706A300E39C0D0A",
        "D706A300E49D0D0A",
        "D706A300E59E0D0A",
        "D706A300E69F0D0A",
        "D706A300E7A00D0A",
        "D706A300E8A10D0A",
        "D706A300E9A20D0A",
        "D706A300EAA30D0A",
        "D706A300EBA40D0A",
        "D706A300ECA50D0A",
        "D706A300EDA60D0A",
        "D706A300EEA70D0A",
        "D706A300EFA80D0A",
        "D706A300F0A90D0A",
        "D706A300F1AA0D0A",
        "D706A300F2AB0D0A",
        "D706A300F3AC0D0A",
        "D706A300F4AD0D0A",
        "D706A300F5AE0D0A",
        "D706A300F6AF0D0A",
        "D706A300F7B00D0A",
        "D706A300F8B10D0A",
        "D706A300F9B20D0A",
        "D706A300FAB30D0A",
        "D706A300FBB40D0A",
        "D706A300FCB50D0A",
        "D706A300FDB60D0A",
        "D706A300FEB70D0A"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bm = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bm.adapter
        prefs = getSharedPreferences("BLE_Prefs", Context.MODE_PRIVATE)

        // Bind UI
        btnConnect = findViewById(R.id.btnConnect)
        btnECO = findViewById(R.id.btnECO)
        btnNormal = findViewById(R.id.btnNormal)
        btnSport = findViewById(R.id.btnSport)
        btnDev = findViewById(R.id.btnDev)
        btnLock = findViewById(R.id.btnLock)
        btnUnlock = findViewById(R.id.btnUnlock)
        btnChangeDevice = findViewById(R.id.btnChangeDevice)

        btn8kmh = findViewById(R.id.btn8kmh)
        btn15kmh = findViewById(R.id.btn15kmh)
        btn20kmh = findViewById(R.id.btn20kmh)
        btn25kmh = findViewById(R.id.btn25kmh)
        btn30kmh = findViewById(R.id.btn30kmh)

        sliderSpeedModifier = findViewById(R.id.sliderSpeedModifier)
        tvSpeedModifier = findViewById(R.id.tvSpeedModifier)

        switchAdvanced = findViewById(R.id.switchAdvanced)
        layoutAdvancedContent = findViewById(R.id.layoutAdvancedContent)

        // Advanced controls
        spinnerModes = findViewById(R.id.advanced_dropdown_1_to_254)
        txtCmdHex = findViewById(R.id.txt_cmd_hex)
        btnSendHex = findViewById(R.id.btnSendHex)
        tvBleOutput = findViewById(R.id.tvBleOutput)

        // === Disclaimer beim ersten Start ===
        maybeShowDisclaimer()

        ensurePermissions()

        btnConnect.setOnClickListener { if (bluetoothGatt == null) pickDevice() else disconnect() }
        btnChangeDevice.setOnClickListener { disconnect(); pickDevice() }

        btnECO.setOnClickListener { sendHex("D707A45A00005") }
        btnNormal.setOnClickListener { sendHex("D706A30001AA") }
        btnSport.setOnClickListener { sendHex("D706A30002AB") }
        btnDev.setOnClickListener { sendHex("D706A30003AC") }
        btnLock.setOnClickListener { sendHex("D707A0000101A9") }
        btnUnlock.setOnClickListener { sendHex("D707A0000301AB") }

        btn8kmh.setOnClickListener { sendHex(getSpeedCommand(8)) }
        btn15kmh.setOnClickListener { sendHex(getSpeedCommand(15)) }
        btn20kmh.setOnClickListener { sendHex(getSpeedCommand(20)) }
        btn25kmh.setOnClickListener { sendHex(getSpeedCommand(25)) }
        btn30kmh.setOnClickListener { sendHex(getSpeedCommand(30)) }


        val advancedContainer = findViewById<androidx.core.widget.NestedScrollView>(R.id.advancedContainer)

// Switch zeigt/versteckt den dynamischen Container (ohne Animation)
        switchAdvanced.setOnCheckedChangeListener { _, isChecked ->
            advancedContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
            advancedContainer.requestLayout() // sorgt für sofortiges Re-Layout im ConstraintLayout
        }

        // === Advanced Spinner Setup (1..254) ===
        val modeLabels = (1..254).map { "Mode $it" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modeLabels)
        spinnerModes.adapter = adapter
        spinnerModes.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!switchAdvanced.isChecked) return
                val mode = position + 1 // 1..254
                val cmd = MODE_CMDS.getOrNull(mode)
                if (cmd != null) {
                    sendHex(cmd)
                    Toast.makeText(this@MainActivity, "Send mode $mode", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Kein CMD für Mode $mode", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Hex manual send
        btnSendHex.setOnClickListener {
            val hex = txtCmdHex.text.toString().trim()
            if (hex.isEmpty()) {
                Toast.makeText(this, "Bitte Hex eingeben", Toast.LENGTH_SHORT).show()
            } else {
                sendHex(hex)
            }
        }

        sliderSpeedModifier.addOnChangeListener { _, value, _ ->
            val speed = value.toInt().coerceIn(8, 30)
            tvSpeedModifier.text = "Speed Modifier: ${speed} km/h"
            sendHex(getSpeedCommand(speed))
        }

        // === Auto-Reconnect to last device ===
        autoReconnectLastDevice()
    }

    private fun maybeShowDisclaimer() {
        val accepted = prefs.getBoolean("disclaimerAccepted", false)
        if (accepted) return

        MaterialAlertDialogBuilder(this)
            .setTitle("Wichtiger Hinweis / Disclaimer")
            .setMessage(
                "Die Nutzung dieser App kann das Verhalten deines Scooters verändern. " +
                        "Tuning und veränderte Geschwindigkeiten können im Straßenverkehr illegal sein und zu Bußgeldern, " +
                        "Versicherungsverlust oder Gefährdungen führen.\n\n" +
                        "Diese App wird ohne Gewähr bereitgestellt. Der Entwickler übernimmt keine Haftung für Schäden, " +
                        "Rechtsfolgen oder Fehlfunktionen. Nutze die App ausschließlich auf eigene Verantwortung und nur dort, " +
                        "wo es rechtlich zulässig ist."
            )
            .setPositiveButton("Ich akzeptiere das Risiko") { _, _ ->
                prefs.edit().putBoolean("disclaimerAccepted", true).apply()
            }
            .setNegativeButton("Abbrechen") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun autoReconnectLastDevice() {
        val addr = prefs.getString("device_address", null) ?: return
        val adapter = bluetoothAdapter ?: return
        try {
            val dev = adapter.getRemoteDevice(addr)
            btnConnect.text = "Connecting…"
            bluetoothGatt = dev.connectGatt(this, false, gattCallback)
            Log.d("BLE", "Auto-reconnect to $addr initiated")
        } catch (e: IllegalArgumentException) {
            Log.e("BLE", "Invalid last device address: $addr")
        }
    }

    private fun ensurePermissions() {
        val need = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            need += Manifest.permission.BLUETOOTH_SCAN
            need += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            need += Manifest.permission.ACCESS_FINE_LOCATION
        }
        val toReq = need.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (toReq.isNotEmpty()) ActivityCompat.requestPermissions(this, toReq.toTypedArray(), 2001)
    }

    private fun pickDevice() {
        val i = Intent(this, DeviceSelectionActivity1::class.java)
        startActivityForResult(i, 2002)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2002 && resultCode == Activity.RESULT_OK) {
            val addr = data?.getStringExtra("DEVICE_ADDRESS") ?: return
            prefs.edit().putString("device_address", addr).apply()
            val dev = bluetoothAdapter?.getRemoteDevice(addr)
            bluetoothGatt = dev?.connectGatt(this, false, gattCallback)
            btnConnect.text = "Connecting…"
        }
    }

    private fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        writeChar = null
        notifyChar = null
        btnConnect.text = "Connect"
        Toast.makeText(this, "Getrennt", Toast.LENGTH_SHORT).show()
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                runOnUiThread { btnConnect.text = "Connected" }
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                runOnUiThread { btnConnect.text = "Connect" }
                writeChar = null
                notifyChar = null
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            var foundWrite: BluetoothGattCharacteristic? = null
            var foundNotify: BluetoothGattCharacteristic? = null
            outer@ for (service in gatt.services) {
                var localWrite: BluetoothGattCharacteristic? = null
                var localNotify: BluetoothGattCharacteristic? = null
                for (ch in service.characteristics) {
                    val props = ch.properties
                    if (localWrite == null && (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 || props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)) {
                        localWrite = ch
                    }
                    if (localNotify == null && (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0)) {
                        localNotify = ch
                    }
                    if (localWrite != null && localNotify != null) {
                        foundWrite = localWrite
                        foundNotify = localNotify
                        break@outer
                    }
                }
            }
            writeChar = foundWrite
            notifyChar = foundNotify
            if (notifyChar != null) {
                gatt.setCharacteristicNotification(notifyChar, true)
                val cccd = notifyChar!!.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                if (cccd != null) {
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(cccd)
                }
            }
            runOnUiThread {
                if (writeChar != null && notifyChar != null) Toast.makeText(this@MainActivity, "UART-ähnliches Paar gefunden", Toast.LENGTH_SHORT).show()
                else Toast.makeText(this@MainActivity, "Kein WRITE/NOTIFY-Paar gefunden", Toast.LENGTH_LONG).show()
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val data = characteristic.value ?: return
            val hex = data.joinToString("") { String.format("%02X", it) }
            Log.d("BLE", "RX: $hex")
            runOnUiThread { tvBleOutput.text = hex }
        }
    }

    private fun sendHex(hex: String) {
        val w = writeChar ?: run {
            Toast.makeText(this, "Nicht verbunden / kein Write-Char", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val bytes = hexStringToByteArray(hex)
            w.value = bytes
            bluetoothGatt?.writeCharacteristic(w)
            Log.d("BLE", "TX: $hex")
        } catch (e: Exception) {
            Toast.makeText(this, "Senden fehlgeschlagen: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hexStringToByteArray(hex: String): ByteArray {
        val cleaned = hex.replace("\\s".toRegex(), "")
        require(cleaned.length % 2 == 0) { "Hex muss gerade Länge haben" }
        return ByteArray(cleaned.length / 2) { i -> cleaned.substring(i*2, i*2+2).toInt(16).toByte() }
    }

    private fun getSpeedCommand(speed: Int): String = when (speed) {
        8 -> "D707A900005000"
        9 -> "D707A900005A0A"
        10 -> "D707A900006414"
        11 -> "D707A900006E1E"
        12 -> "D707A900007828"
        13 -> "D707A900008232"
        14 -> "D707A900008C3C"
        15 -> "D707A900009646"
        16 -> "D707A90000A050"
        17 -> "D707A90000AA5A"
        18 -> "D707A90000B464"
        19 -> "D707A90000BE6E"
        20 -> "D707A90000C878"
        21 -> "D707A90000D282"
        22 -> "D707A90000DC8C"
        23 -> "D707A90000E696"
        24 -> "D707A90000F0A0"
        25 -> "D707A90000FAAA"
        26 -> "D707A9000104B5"
        27 -> "D707A900010EBF"
        28 -> "D707A9000118C9"
        29 -> "D707A9000122D3"
        30 -> "D707A900012CDD"
        else -> "D707A900005000"
    }
}
