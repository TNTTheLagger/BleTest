package com.hazel.bletest;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelUuid;
import android.util.Log;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.Collections;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "BLETest";
    private static final String DEVICE_NAME = "ESP32";
    private static final UUID BATTERY_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB");
    private static final UUID BATTERY_LEVEL_UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB");
    private static final UUID BUTTON_PRESSED_UUID = UUID.fromString("12345678-1234-1234-1234-1234567890AB");
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt bluetoothGatt;
    private TextView batteryLevelText;
    private ProgressBar batteryProgress;
    private Button startScanButton;
    private Button sendDataButton;
    private Handler handler = new Handler();
    private static final long SCAN_PERIOD = 10000;
    private boolean scanning;

    private enum BLELifecycleState {
        Disconnected,
        Scanning,
        Connecting,
        ConnectedDiscovering,
        ConnectedSubscribing,
        Connected
    }

    private BLELifecycleState lifecycleState = BLELifecycleState.Disconnected;

    private void setLifecycleState(BLELifecycleState state) {
        lifecycleState = state;
        Log.d(TAG, "Lifecycle State changed to: " + state.name());
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        batteryLevelText = findViewById(R.id.battery_level);
        batteryProgress = findViewById(R.id.battery_progress);
        startScanButton = findViewById(R.id.start_scan_button);
        sendDataButton = findViewById(R.id.send_data_button);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        scanner = bluetoothAdapter.getBluetoothLeScanner();

        Log.d(TAG, "onCreate: Initializing Bluetooth scanner");

        if (checkPermissions()) {
            startScan();
        } else {
            requestPermissions();
        }

        startScanButton.setOnClickListener(v -> startScan());

        sendDataButton.setOnClickListener(v -> sendDataToDevice("Hello, ESP32!"));
    }

    private boolean checkPermissions() {
        boolean hasPermissions = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        Log.d(TAG, "checkPermissions: " + hasPermissions);
        return hasPermissions;
    }

    private void requestPermissions() {
        Log.d(TAG, "requestPermissions: Requesting Bluetooth permissions");
        ActivityCompat.requestPermissions(this, new String[] {
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
        }, REQUEST_BLUETOOTH_PERMISSIONS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            Log.d(TAG, "onRequestPermissionsResult: Bluetooth permissions granted = " + granted);
            if (granted) startScan();
        }
    }

    @SuppressLint("MissingPermission")
    private void startScan() {
        Log.d(TAG, "startScan: Starting Bluetooth scan");
        if (checkPermissions()) {
            setLifecycleState(BLELifecycleState.Scanning);
            scanner.startScan(Collections.singletonList(scanFilter), scanSettings, leScanCallback);
        } else {
            Log.e(TAG, "startScan: Permissions not granted, cannot start scan");
        }
    }

    @SuppressLint("MissingPermission")
    private void stopScan() {
        Log.d(TAG, "stopScan: Stopping Bluetooth scan");
        scanner.stopScan(leScanCallback);
        setLifecycleState(BLELifecycleState.Disconnected);
    }

    private ScanFilter scanFilter = new ScanFilter.Builder()
            .setServiceUuid(ParcelUuid.fromString(BATTERY_SERVICE_UUID.toString()))
            .build();

    private ScanSettings scanSettings = new ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .setReportDelay(0)
            .build();

    private ScanCallback leScanCallback = new ScanCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            Log.d(TAG, "onScanResult: Found device " + device.getName() + " (" + device.getAddress() + ")");
            if (DEVICE_NAME.equals(device.getName())) {
                stopScan();
                setLifecycleState(BLELifecycleState.Connecting);
                bluetoothGatt = device.connectGatt(MainActivity.this, false, gattCallback);
            }
        }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d(TAG, "onConnectionStateChange: Status " + status + ", New State " + newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                setLifecycleState(BLELifecycleState.ConnectedDiscovering);
                bluetoothGatt = gatt;
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                setLifecycleState(BLELifecycleState.Disconnected);
                gatt.close();
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(TAG, "onServicesDiscovered: Status " + status);
            BluetoothGattService service = gatt.getService(BATTERY_SERVICE_UUID);
            if (service != null) {
                BluetoothGattCharacteristic batteryLevelCharacteristic = service.getCharacteristic(BATTERY_LEVEL_UUID);
                if (batteryLevelCharacteristic != null) {
                    // Enable notifications for the battery level characteristic
                    gatt.setCharacteristicNotification(batteryLevelCharacteristic, true);

                    BluetoothGattDescriptor descriptor = batteryLevelCharacteristic.getDescriptor(
                            UUID.fromString("00002902-0000-1000-8000-00805F9B34FB"));
                    if (descriptor != null) {
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        gatt.writeDescriptor(descriptor);
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (BATTERY_LEVEL_UUID.equals(characteristic.getUuid())) {
                int batteryLevel = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                Log.d(TAG, "Battery Level: " + batteryLevel + "%");

                runOnUiThread(() -> {
                    batteryLevelText.setText("Battery Level: " + batteryLevel + "%");
                    batteryProgress.setProgress(batteryLevel);
                });
            }
        }
    };

    // Method to send data to the device (write to a custom characteristic)
    @SuppressLint("MissingPermission")
    private void sendDataToDevice(String data) {
        if (bluetoothGatt != null) {
            BluetoothGattService service = bluetoothGatt.getService(BATTERY_SERVICE_UUID);
            if (service != null) {
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(BUTTON_PRESSED_UUID);
                if (characteristic != null) {
                    characteristic.setValue(data);
                    bluetoothGatt.writeCharacteristic(characteristic);
                    Log.d(TAG, "Data sent to device: " + data);
                }
            }
        }
    }
}
