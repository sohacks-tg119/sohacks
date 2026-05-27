package com.hackerman.sohacksrev2

import android.bluetooth.BluetoothDevice

data class Device(
    val device: BluetoothDevice,
    val name: String?,
    val address: String
)