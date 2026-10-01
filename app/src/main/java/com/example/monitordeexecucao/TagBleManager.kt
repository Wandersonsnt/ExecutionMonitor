package com.example.monitordeexecucao

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import java.util.UUID

@SuppressLint("MissingPermission") // As permissões serão validadas na MainActivity
class TagBleManager(private val context: Context, private val viewModel: TagViewModel) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothGatt: BluetoothGatt? = null

    // UUIDs do Nordic UART Service (NUS)
    private val NUS_SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    private val NUS_RX_CHARACTERISTIC = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
    private val CCCD_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    fun startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return
        bluetoothAdapter.bluetoothLeScanner?.startScan(scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            // Procura especificamente pela nossa TAG
            if (device.name == "LTM_Tag") {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(this)
                bluetoothGatt = device.connectGatt(context, false, gattCallback)
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                viewModel.isConnected = true
                gatt.discoverServices() // Acorda a TAG para listar os serviços
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                viewModel.isConnected = false
                startScan() // Tenta reconectar automaticamente
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(NUS_SERVICE_UUID)
                val characteristic = service?.getCharacteristic(NUS_RX_CHARACTERISTIC)

                if (characteristic != null) {
                    // Assina (Subscribe) para receber notificações da TAG em tempo real
                    gatt.setCharacteristicNotification(characteristic, true)
                    val descriptor = characteristic.getDescriptor(CCCD_DESCRIPTOR_UUID)
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == NUS_RX_CHARACTERISTIC) {
                val data = characteristic.getStringValue(0)
                if (data != null) {
                    // Envia a String separada por vírgulas para a nossa matemática calcular[cite: 2]
                    viewModel.processIncomingData(data)
                }
            }
        }
    }
}