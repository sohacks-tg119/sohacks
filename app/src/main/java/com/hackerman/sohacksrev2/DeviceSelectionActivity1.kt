package com.hackerman.sohacksrev2

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class DeviceSelectionActivity1 : AppCompatActivity() {

    private lateinit var rvDevices: RecyclerView
    private lateinit var btnBack: Button
    private lateinit var btnStartScan: Button
    private lateinit var btnStopScan: Button
    private var cbShowUnnamed: CheckBox? = null
    private lateinit var adapter: DeviceAdapter

    private var bluetoothAdapter: BluetoothAdapter? = null
    private val handler = Handler(Looper.getMainLooper())
    private var scanning = false
    private val allDevices = LinkedHashMap<String, Device>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_selection)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        rvDevices = findViewById(R.id.rvDevices)
        btnBack = findViewById(R.id.btnBack)
        btnStartScan = findViewById(R.id.btnStartScan)
        btnStopScan = findViewById(R.id.btnStopScan)
        cbShowUnnamed = findViewById(R.id.cbShowUnnamed)

        adapter = DeviceAdapter(mutableListOf()) { device ->
            stopScan()
            val returnIntent = intent
            returnIntent.putExtra(EXTRA_DEVICE_ADDRESS, device.address)
            device.inferredModelId?.let { returnIntent.putExtra(EXTRA_MODEL_ID, it) }
            setResult(Activity.RESULT_OK, returnIntent)
            finish()
        }
        rvDevices.layoutManager = LinearLayoutManager(this)
        rvDevices.adapter = adapter

        btnStartScan.isEnabled = true
        btnStopScan.isEnabled = false

        btnBack.setOnClickListener {
            stopScan()
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
        btnStartScan.setOnClickListener { ensurePermissionsThenScan() }
        btnStopScan.setOnClickListener { stopScan() }
        cbShowUnnamed?.setOnCheckedChangeListener { _, _ -> refreshList() }
    }

    override fun onPause() {
        super.onPause()
        stopScan()
    }

    private fun ensurePermissionsThenScan() {
        val neededPermissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            neededPermissions += Manifest.permission.BLUETOOTH_SCAN
            neededPermissions += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            neededPermissions += Manifest.permission.ACCESS_FINE_LOCATION
        }

        val missingPermissions = neededPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), REQ_PERMS)
            return
        }

        if (!isLocationEnabled()) {
            Toast.makeText(this, "Bitte Standort einschalten (nur für den Scan).", Toast.LENGTH_SHORT).show()
            startActivityForResult(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), REQ_ENABLE_LOCATION)
            return
        }
        startScan()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMS) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                ensurePermissionsThenScan()
            } else {
                Toast.makeText(this, "Berechtigungen fehlen", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_ENABLE_LOCATION) {
            if (isLocationEnabled()) {
                startScan()
            } else {
                Toast.makeText(this, "Standort weiterhin aus", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return try {
            locationManager.isLocationEnabled
        } catch (_: Exception) {
            true
        }
    }

    private fun showUnnamedAllowed(): Boolean = cbShowUnnamed?.isChecked ?: true

    private fun startScan() {
        if (scanning) return
        val adapter = bluetoothAdapter ?: return

        if (!adapter.isEnabled) {
            Toast.makeText(this, "Bluetooth ist aus", Toast.LENGTH_SHORT).show()
            return
        }

        val scanner = adapter.bluetoothLeScanner ?: return

        Toast.makeText(this, "Scan läuft...", Toast.LENGTH_SHORT).show()
        Log.d(TAG_SCAN, "start BLE scan")

        allDevices.clear()
        this.adapter.clear()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0L)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .build()

        scanning = true
        btnStartScan.isEnabled = false
        btnStopScan.isEnabled = true
        scanner.startScan(null, settings, scanCallback)

        if (SCAN_PERIOD_MS > 0) {
            handler.postDelayed({ stopScan() }, SCAN_PERIOD_MS)
        }
    }

    private fun stopScan() {
        if (!scanning) return
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        scanning = false
        scanner.stopScan(scanCallback)
        btnStartScan.isEnabled = true
        btnStopScan.isEnabled = false
    }

    private fun refreshList() {
        val allowUnnamed = showUnnamedAllowed()
        adapter.clear()
        for (device in allDevices.values) {
            if (allowUnnamed || !device.name.isNullOrBlank()) {
                adapter.addDevice(device)
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            upsertScanResult(result)
            Log.d(TAG_SCAN, "hit rssi=${result.rssi} addr=${result.device?.address}")
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            var changed = false
            for (result in results) {
                changed = upsertScanResult(result, refresh = false) || changed
            }
            if (changed) refreshList()
            Log.d(TAG_SCAN, "batch size=${results.size}")
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG_SCAN, "failed: $errorCode")
            Toast.makeText(this@DeviceSelectionActivity1, "Scan fehlgeschlagen: $errorCode", Toast.LENGTH_SHORT).show()
        }
    }

    private fun upsertScanResult(result: ScanResult, refresh: Boolean = true): Boolean {
        val device = result.device ?: return false
        val address = device.address ?: return false
        val name = device.name
        val inferredModelId = inferModelId(result)
        val existing = allDevices[address]
        val shouldUpdate = existing == null ||
            (existing.name.isNullOrBlank() && !name.isNullOrBlank()) ||
            (existing.inferredModelId == null && inferredModelId != null)

        if (shouldUpdate) {
            allDevices[address] = Device(
                device = device,
                name = name ?: existing?.name,
                address = address,
                inferredModelId = inferredModelId ?: existing?.inferredModelId
            )
            if (refresh) refreshList()
        }
        return shouldUpdate
    }

    private fun inferModelId(result: ScanResult): String? {
        val manufacturerHex = result.scanRecord?.manufacturerSpecificData?.let { sparse ->
            buildString {
                for (index in 0 until sparse.size()) {
                    append(sparse.valueAt(index).joinToString("") { "%02X".format(it) })
                }
            }
        }

        if (manufacturerHex?.startsWith("6001") == true) return "so6"

        return inferModelId(result.device.name)
    }

    private fun inferModelId(name: String?): String? {
        val normalized = name?.uppercase() ?: return null
        return when {
            normalized.startsWith("SFSO1") || normalized.startsWith("SFSC1") || normalized.startsWith("SFS1") -> "so1"
            normalized.startsWith("SFSO2") || normalized.startsWith("SFSC2") || normalized.startsWith("SFS2") -> "so2_air"
            normalized.startsWith("SFSO3") || normalized.startsWith("SFSC3") || normalized.startsWith("SFS3") -> "so3"
            normalized.startsWith("SFSO4UL") -> "so4ul"
            normalized.startsWith("SFSO4") || normalized.startsWith("SFS4") -> SO4_FAMILY_MODEL_ID
            normalized.startsWith("SFSOMT") -> "somytier"
            normalized.startsWith("SFSO5") || normalized.startsWith("SFSC5") -> "so5"
            normalized.startsWith("SFSO6") || normalized.startsWith("SFSC6") -> "so6"
            else -> null
        }
    }

    companion object {
        private const val SCAN_PERIOD_MS = 12000L
        private const val REQ_PERMS = 1001
        private const val REQ_ENABLE_LOCATION = 1002
        private const val EXTRA_DEVICE_ADDRESS = "DEVICE_ADDRESS"
        private const val EXTRA_MODEL_ID = "MODEL_ID"
        private const val SO4_FAMILY_MODEL_ID = "so4_family"
        private const val TAG_SCAN = "SCAN"
    }
}
