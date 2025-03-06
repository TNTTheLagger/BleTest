package com.example.bletest;

import android.Manifest;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "BLETest";
    private static final String DEVICE_NAME = "ESP32"; // Change this to match your device's name
    private static final UUID BATTERY_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb");
    private static final UUID BATTERY_LEVEL_UUID = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb");

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    private BluetoothGatt bluetoothGatt;
    private TextView statusText;
    private TextView batteryText;
    private Button scanButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        batteryText = findViewById(R.id.batteryText);
        scanButton = findViewById(R.id.scanButton);

        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        bleScanner = bluetoothAdapter.getBluetoothLeScanner();

        scanButton.setOnClickListener(v -> startScan());
    }

    private void startScan() {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN}, 1);
            return;
        }

        statusText.setText("Scanning...");
        bleScanner.startScan(new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                BluetoothDevice device = result.getDevice();
                if (DEVICE_NAME.equals(device.getName())) {
                    Log.d(TAG, "Found device: " + device.getName() + " (" + device.getAddress() + ")");
                    statusText.setText("Device found! Connecting...");
                    bleScanner.stopScan(this);
                    connectToDevice(device);
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                Log.e(TAG, "Scan failed: " + errorCode);
            }
        });
    }

    private void connectToDevice(BluetoothDevice device) {
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 2);
            return;
        }

        bluetoothGatt = device.connectGatt(this, false, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    Log.d(TAG, "Connected to device");
                    runOnUiThread(() -> statusText.setText("Connected. Discovering services..."));
                    gatt.discoverServices();
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    Log.d(TAG, "Disconnected from device");
                    runOnUiThread(() -> statusText.setText("Disconnected"));
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    BluetoothGattService service = gatt.getService(BATTERY_SERVICE_UUID);
                    if (service != null) {
                        BluetoothGattCharacteristic characteristic = service.getCharacteristic(BATTERY_LEVEL_UUID);
                        if (characteristic != null) {
                            Log.d(TAG, "Reading battery level...");
                            gatt.readCharacteristic(characteristic);
                        } else {
                            Log.e(TAG, "Battery characteristic not found!");
                        }
                    } else {
                        Log.e(TAG, "Battery service not found!");
                    }
                }
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS && BATTERY_LEVEL_UUID.equals(characteristic.getUuid())) {
                    int batteryLevel = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                    Log.d(TAG, "Battery Level: " + batteryLevel + "%");

                    runOnUiThread(() -> {
                        batteryText.setText("Battery Level: " + batteryLevel + "%");
                        statusText.setText("Data received!");
                    });
                }
            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                if (BATTERY_LEVEL_UUID.equals(characteristic.getUuid())) {
                    int batteryLevel = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                    Log.d(TAG, "Battery Level Changed: " + batteryLevel + "%");

                    runOnUiThread(() -> batteryText.setText("Battery Level: " + batteryLevel + "%"));
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
        super.onDestroy();
    }
}
