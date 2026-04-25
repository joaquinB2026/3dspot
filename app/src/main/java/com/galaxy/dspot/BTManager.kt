package com.galaxy.dspot

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class BTManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun getPairedDevices(): List<BluetoothDevice> {
        if (!hasPermission()) return emptyList()
        return try {
            bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: SecurityException) { emptyList() }
    }

    fun getDeviceType(device: BluetoothDevice): String {
        return try {
            when (device.bluetoothClass?.majorDeviceClass) {
                0x0100 -> "🖥️ Computer"
                0x0200 -> "📱 Phone"
                0x0400 -> "🌐 Network"
                0x0600 -> "🎧 Audio"
                0x0500 -> "🎮 Peripheral"
                0x0700 -> "📷 Imaging"
                0x0800 -> "⌚ Wearable"
                0x0900 -> "🧸 Toy"
                else -> "📡 Device"
            }
        } catch (e: SecurityException) { "📡 Device" }
    }

    fun getDeviceName(device: BluetoothDevice): String {
        return try {
            device.name ?: device.address
        } catch (e: SecurityException) { device.address }
    }

    fun isConnected(device: BluetoothDevice): Boolean {
        return try {
            val method = device.javaClass.getMethod("isConnected")
            method.invoke(device) as Boolean
        } catch (e: Exception) { false }
    }

    private fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }
}
