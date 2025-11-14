package com.nativeespmodule

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import com.espressif.provisioning.DeviceConnectionEvent
import com.espressif.provisioning.ESPConstants
import com.espressif.provisioning.ESPProvisionManager
import com.espressif.provisioning.WiFiAccessPoint
import com.espressif.provisioning.listeners.BleScanListener
import com.espressif.provisioning.listeners.ProvisionListener
import com.espressif.provisioning.listeners.ResponseListener
import com.espressif.provisioning.listeners.WiFiScanListener
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.annotations.ReactModule
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.lang.Exception
import java.util.ArrayList

@ReactModule(name = EspModule.NAME)
class EspModule(reactContext: ReactApplicationContext): NativeEspModuleSpec(reactContext) {
  companion object {
    const val NAME = NativeEspModuleSpec.NAME
    const val TAG = "EspModule"
  }

  private var provisionManager: ESPProvisionManager = ESPProvisionManager.getInstance(reactApplicationContext)
  private var bluetoothDevices: HashMap<String, BluetoothDevice> = HashMap()

  init {
    provisionManager.createESPDevice(ESPConstants.TransportType.TRANSPORT_BLE, ESPConstants
      .SecurityType.SECURITY_2)
    Log.d(TAG, "EventBus register")
    EventBus.getDefault().register(this)
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onEvent(event: DeviceConnectionEvent) {
    Log.d(TAG, "on Event")
    when (event.eventType) {
      ESPConstants.EVENT_DEVICE_CONNECTED -> {
        Log.d(TAG, "Device connected")
        val proof = "123456"
        provisionManager?.espDevice?.proofOfPossession = proof
        Log.d(TAG, "initSession")
        provisionManager?.espDevice?.initSession(object : ResponseListener {
          override fun onSuccess(returnData: ByteArray?) {
            Log.d(TAG, "initSession success")
            Log.d(TAG, "start scan wifi")
            provisionManager?.espDevice?.scanNetworks(object : WiFiScanListener {
              override fun onWifiListReceived(wifiList: ArrayList<WiFiAccessPoint>?) {
                Log.d(TAG, "onWifiListReceived ${wifiList}")
                // Convert ArrayList<WiFiAccessPoint> to WritableArray
                val wifiArray = Arguments.createArray()

                wifiList?.forEach { wifi ->
                  val wifiMap = Arguments.createMap().apply {
                    putString("name", wifi.wifiName)
                    putInt("rssi", wifi.rssi)
                  }
                  wifiArray.pushMap(wifiMap)
                }

                emitOnWifiScan(wifiArray)
              }

              override fun onWiFiScanFailed(e: java.lang.Exception?) {
                Log.d(TAG, "onWiFiScanFailed")
              }
            })
          }

          override fun onFailure(e: java.lang.Exception?) {
            Log.d(TAG, "initSession failed")
          }
        })
      }

      ESPConstants.EVENT_DEVICE_DISCONNECTED -> {
        Log.d(TAG, "EVENT_DEVICE_DISCONNECTED")
      }

      ESPConstants.EVENT_DEVICE_CONNECTION_FAILED -> {
        Log.d(TAG, "EVENT_DEVICE_CONNECTION_FAILED")
      }
    }
  }

  override fun startScan() {
    bluetoothDevices.clear()

    Log.d(TAG, "startScan")
    if (ActivityCompat.checkSelfPermission(
        reactApplicationContext, Manifest.permission
          .ACCESS_FINE_LOCATION
      ) === PackageManager.PERMISSION_GRANTED
    ) {
      Log.d(TAG, "searchBleEspDevices")
      provisionManager?.searchBleEspDevices("", object :
        BleScanListener {
        override fun scanCompleted() {
          Log.d(TAG, "scanCompleted")
        }

        override fun scanStartFailed() {
          Log.d(TAG, "scanStartFailed")
        }

        override fun onPeripheralFound(device: BluetoothDevice?, scanResult: ScanResult?) {
          Log.d(TAG, "onPeripheralFound")
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                reactApplicationContext, Manifest.permission
                  .BLUETOOTH_CONNECT
              ) === PackageManager.PERMISSION_GRANTED
            ) {
              Log.d(TAG, "onPeripheralFound ${device?.name}")
            }
          } else {
            Log.d(TAG, "onPeripheralFound ${device?.name}")
          }
          var deviceExists = false

          if (scanResult?.scanRecord?.serviceUuids != null
            && scanResult.scanRecord?.serviceUuids?.size!! > 0) {
            val bleDevice = BleDevice()
            bleDevice.name = scanResult.scanRecord!!.deviceName
            bleDevice.bluetoothDevice = device
            val serviceUuid: String = scanResult.scanRecord?.serviceUuids?.get(0).toString()

            if (bluetoothDevices.containsKey(serviceUuid)) {
              deviceExists = true
            }

            if (!deviceExists) {
              bluetoothDevices[serviceUuid] = device!!
              emitOnPeripheralFound(Arguments.createMap().apply {
                putString("deviceName", device?.name)
                putString("serviceUuid", serviceUuid)
              })
            }
          }
        }

        override fun onFailure(e: java.lang.Exception?) {
          Log.d(TAG, "onFailure ${e}")
        }
      })
    }
  }

  @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
  override fun stopScan() {
    if (ActivityCompat.checkSelfPermission(
        reactApplicationContext,
        Manifest.permission.ACCESS_FINE_LOCATION
      ) != PackageManager.PERMISSION_GRANTED
    ) {
      // TODO: Consider calling
      //    ActivityCompat#requestPermissions
      // here to request the missing permissions, and then overriding
      //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
      //                                          int[] grantResults)
      // to handle the case where the user grants the permission. See the documentation
      // for ActivityCompat#requestPermissions for more details.
      return
    }
    Log.d(TAG, "Stop ble scan")
    provisionManager?.stopBleScan()
  }

  @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
  override fun connectBLEDevice(serviceUuid: String?) {
    val bleDevice = bluetoothDevices[serviceUuid]
    Log.d(TAG, "Connect to device ${bleDevice?.name} - UUID: ${serviceUuid}")
    if (provisionManager == null) {
      Log.d(TAG, "provisionManager null")
    }
    if (provisionManager?.espDevice == null) {
      Log.d(TAG, "espDevice null")
    }
    provisionManager?.espDevice?.connectBLEDevice(bleDevice, serviceUuid)

  }

  override fun connectWiFi(ssid: String?, pass: String?, promise: Promise?) {
    provisionManager?.espDevice?.provision(ssid, pass, object: ProvisionListener {
      override fun deviceProvisioningSuccess() {
        Log.d(TAG, "deviceProvisioningSuccess")
      }

      override fun wifiConfigSent() {
        Log.d(TAG, "wifiConfigSent")
      }

      override fun wifiConfigFailed(e: Exception?) {
        Log.d(TAG, "wifiConfigFailed")
      }

      override fun wifiConfigApplied() {
        Log.d(TAG, "wifiConfigApplied")
      }

      override fun wifiConfigApplyFailed(e: Exception?) {
        Log.d(TAG, "wifiConfigApplyFailed")
      }

      override fun createSessionFailed(e: Exception?) {
        Log.d(TAG, "createSessionFailed")
      }

      override fun onProvisioningFailed(e: Exception?) {
        Log.d(TAG, "onProvisioningFailed")
      }

      override fun provisioningFailedFromDevice(failureReason: ESPConstants.ProvisionFailureReason?) {
        Log.d(TAG, "provisioningFailedFromDevice")
      }
    })


  }
}

