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
    private var cbShowUnnamed: CheckBox? = null // optional, fällt auf true zurück
    private lateinit var adapter: DeviceAdapter

    private var bluetoothAdapter: BluetoothAdapter? = null
    private val handler = Handler(Looper.getMainLooper())
    private var scanning = false

    private val SCAN_PERIOD_MS = 12000L // auf 0 setzen, wenn du keinen Auto‑Stop willst
    private val REQ_PERMS = 1001
    private val REQ_ENABLE_LOCATION = 1002

    // Wir halten ALLE gefundenen Geräte hier und filtern nur für die Anzeige
    private val allDevices = LinkedHashMap<String, Device>() // addr -> Device (Insertion‑Order)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_selection)

        val bm = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bm.adapter

        rvDevices = findViewById(R.id.rvDevices)
        btnBack = findViewById(R.id.btnBack)
        btnStartScan = findViewById(R.id.btnStartScan)
        btnStopScan = findViewById(R.id.btnStopScan)
        // Checkbox muss im XML existieren: @+id/cbShowUnnamed. Falls nicht vorhanden -> immer anzeigen
        cbShowUnnamed = findViewById(R.id.cbShowUnnamed)

        adapter = DeviceAdapter(mutableListOf()) { dev ->
            stopScan()
            val returnIntent = intent
            returnIntent.putExtra("DEVICE_ADDRESS", dev.address)
            setResult(Activity.RESULT_OK, returnIntent)
            finish()
        }
        rvDevices.layoutManager = LinearLayoutManager(this)
        rvDevices.adapter = adapter

        btnStartScan.isEnabled = true
        btnStopScan.isEnabled = false

        btnBack.setOnClickListener { stopScan(); setResult(Activity.RESULT_CANCELED); finish() }
        btnStartScan.setOnClickListener { ensurePermissionsThenScan() }
        btnStopScan.setOnClickListener { stopScan() }

        cbShowUnnamed?.setOnCheckedChangeListener { _, _ -> refreshList() }
    }

    override fun onPause() {
        super.onPause()
        // Optional wie Scanner‑Apps: beim Verlassen stoppen
        stopScan()
    }

    private fun ensurePermissionsThenScan() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            needed += Manifest.permission.BLUETOOTH_SCAN
            needed += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            needed += Manifest.permission.ACCESS_FINE_LOCATION
        }
        val toRequest = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (toRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, toRequest.toTypedArray(), REQ_PERMS)
            return
        }
        // Viele OEMs liefern ohne aktiviertes Location 0 Ergebnisse – auch mit neverForLocation
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
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) ensurePermissionsThenScan()
            else Toast.makeText(this, "Berechtigungen fehlen", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_ENABLE_LOCATION) {
            if (isLocationEnabled()) startScan() else Toast.makeText(this, "Standort weiterhin aus", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isLocationEnabled(): Boolean {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return try { lm.isLocationEnabled } catch (_: Exception) { true }
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

        Toast.makeText(this, "Scanning…", Toast.LENGTH_SHORT).show()
        Log.d("SCAN", "start nRF‑style scan")

        // Reset Anzeige & Cache
        allDevices.clear()
        this.adapter.clear()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // maximale Rate
            .setReportDelay(0L) // Ergebnisse sofort
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .build()

        scanning = true
        btnStartScan.isEnabled = false
        btnStopScan.isEnabled = true

        // Wichtig: manche Stacks wollen *null* statt leerer Liste
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
        for (dev in allDevices.values) {
            if (allowUnnamed || !dev.name.isNullOrBlank()) {
                adapter.addDevice(dev)
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val d = result.device ?: return
            val addr = d.address ?: return
            val name = d.name // kann null/leer sein

            val existing = allDevices[addr]
            // Update, falls neu oder jetzt Name vorhanden
            if (existing == null || (existing.name.isNullOrBlank() && !name.isNullOrBlank())) {
                allDevices[addr] = Device(d, name, addr)
                refreshList()
            }
            Log.d("SCAN", "hit rssi=${result.rssi} name=${name} addr=${addr}")
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            var changed = false
            for (r in results) {
                val d = r.device ?: continue
                val addr = d.address ?: continue
                val name = d.name
                val existing = allDevices[addr]
                if (existing == null || (existing.name.isNullOrBlank() && !name.isNullOrBlank())) {
                    allDevices[addr] = Device(d, name, addr)
                    changed = true
                }
            }
            if (changed) refreshList()
            Log.d("SCAN", "batch size=${results.size}")
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("SCAN", "failed: $errorCode")
            Toast.makeText(this@DeviceSelectionActivity1, "Scan failed: $errorCode", Toast.LENGTH_SHORT).show()
        }
    }
}
