package com.hazel.bletest;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.view.View;

import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "BLETest";
    private static final String DEVICE_NAME = "ESP32 Battery";
    private static final UUID BATTERY_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB");
    private static final UUID BATTERY_LEVEL_UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB");
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt bluetoothGatt;
    private TextView batteryLevelText;
    private ProgressBar batteryProgress;
    private Button startScanButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        batteryLevelText = findViewById(R.id.battery_level);
        batteryProgress = findViewById(R.id.battery_progress);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        scanner = bluetoothAdapter.getBluetoothLeScanner();
        startScanButton = findViewById(R.id.start_scan_button);

        Log.d(TAG, "onCreate: Initializing BLE components");

        if (checkPermissions()) {
            Log.d(TAG, "onCreate: Permissions granted, starting scan");
            startScan();
        } else {
            Log.d(TAG, "onCreate: Permissions not granted, requesting permissions");
            requestPermissions();
        }

        startScanButton.setOnClickListener(v -> {
            Log.d(TAG, "startScanButton clicked");
            startScan();
        });
    }

    private boolean checkPermissions() {
        Log.d(TAG, "checkPermissions: Checking if permissions are granted");
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "checkPermissions: Permissions granted");
            return true;
        } else {
            Log.d(TAG, "checkPermissions: Permissions not granted");
            requestPermissions();
            return false;
        }
    }

    private void requestPermissions() {
        Log.d(TAG, "requestPermissions: Requesting Bluetooth permissions");
        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
        }, REQUEST_BLUETOOTH_PERMISSIONS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(TAG, "onRequestPermissionsResult: Permissions result received");
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "onRequestPermissionsResult: Permissions granted, starting scan");
                startScan();
            } else {
                Log.e(TAG, "onRequestPermissionsResult: Bluetooth permissions denied");
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void startScan() {
        Log.d(TAG, "startScan: Starting Bluetooth scan");
        if (checkPermissions()) {
            startScanButton.setEnabled(false); // Disable the button once scan starts
            scanner.startScan(new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    Log.d(TAG, "onScanResult: Scan result received, processing...");
                    BluetoothDevice device = result.getDevice();
                    Log.d(TAG, "onScanResult: Found device - " + device.getName() + " with address " + device.getAddress());
                    if (DEVICE_NAME.equals(device.getName())) {
                        Log.d(TAG, "onScanResult: Found target device, stopping scan");
                        scanner.stopScan(this);
                        if (checkPermissions()) {
                            Log.d(TAG, "onScanResult: Connecting to GATT server for device " + device.getName());
                            device.connectGatt(MainActivity.this, false, gattCallback);
                        }
                    } else {
                        Log.d(TAG, "onScanResult: Device is not the target, ignoring");
                    }
                }

                @Override
                public void onScanFailed(int errorCode) {
                    Log.e(TAG, "onScanFailed: Scan failed with error code " + errorCode);
                }
            });
        } else {
            Log.e(TAG, "startScan: Permissions not granted, cannot start scan");
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d(TAG, "onConnectionStateChange: Connection state changed. Status: " + status + ", New State: " + newState);
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                Log.d(TAG, "onConnectionStateChange: Connected to device, discovering services");
                if (checkPermissions()) {
                    gatt.discoverServices();
                }
            } else {
                Log.e(TAG, "onConnectionStateChange: Connection failed or disconnected. Status: " + status);
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(TAG, "onServicesDiscovered: Services discovered. Status: " + status);
            if (checkPermissions()) {
                BluetoothGattService service = gatt.getService(BATTERY_SERVICE_UUID);
                if (service != null) {
                    Log.d(TAG, "onServicesDiscovered: Battery service found, reading battery level");
                    BluetoothGattCharacteristic characteristic = service.getCharacteristic(BATTERY_LEVEL_UUID);
                    if (characteristic != null) {
                        gatt.readCharacteristic(characteristic);
                    } else {
                        Log.e(TAG, "onServicesDiscovered: Battery level characteristic not found");
                    }
                } else {
                    Log.e(TAG, "onServicesDiscovered: Battery service not found");
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.d(TAG, "onCharacteristicRead: Characteristic read. Status: " + status + ", UUID: " + characteristic.getUuid());
            if (BATTERY_LEVEL_UUID.equals(characteristic.getUuid())) {
                int batteryLevel = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                Log.d(TAG, "onCharacteristicRead: Battery level: " + batteryLevel + "%");
                runOnUiThread(() -> {
                    batteryLevelText.setText("Battery: " + batteryLevel + "%");
                    batteryProgress.setProgress(batteryLevel);
                });
            } else {
                Log.e(TAG, "onCharacteristicRead: Unexpected characteristic UUID");
            }
        }
    };
}
