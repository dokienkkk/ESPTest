package com.nativeespmodule

import android.bluetooth.BluetoothDevice

data class BleDevice(
  var name: String? = null,
  var bluetoothDevice: BluetoothDevice? = null
)