package com.hazel.bletest;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
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
        Log.d(TAG, "Lifecycle State: " + state.name());
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        batteryLevelText = findViewById(R.id.battery_level);
        batteryProgress = findViewById(R.id.battery_progress);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        scanner = bluetoothAdapter.getBluetoothLeScanner();
        startScanButton = findViewById(R.id.start_scan_button);

        if (checkPermissions()) {
            startScan();
        } else {
            requestPermissions();
        }

        startScanButton.setOnClickListener(v -> startScan());
    }

    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
        }, REQUEST_BLUETOOTH_PERMISSIONS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScan();
            } else {
                Log.e(TAG, "Bluetooth permissions denied");
            }
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
        @Override
        @SuppressLint("MissingPermission")
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (DEVICE_NAME.equals(device.getName())) {
                stopScan();
                setLifecycleState(BLELifecycleState.Connecting);
                device.connectGatt(MainActivity.this, false, gattCallback);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "onScanFailed: Scan failed with error code " + errorCode);
            stopScan();
        }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                setLifecycleState(BLELifecycleState.ConnectedDiscovering);
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                setLifecycleState(BLELifecycleState.Disconnected);
                gatt.close();
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            BluetoothGattService service = gatt.getService(BATTERY_SERVICE_UUID);
            if (service != null) {
                setLifecycleState(BLELifecycleState.Connected);
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(BATTERY_LEVEL_UUID);
                if (characteristic != null) {
                    gatt.readCharacteristic(characteristic);
                } else {
                    Log.e(TAG, "onServicesDiscovered: Battery level characteristic not found");
                }
            } else {
                gatt.disconnect();
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (BATTERY_LEVEL_UUID.equals(characteristic.getUuid())) {
                int batteryLevel = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                runOnUiThread(() -> {
                    batteryLevelText.setText("Battery: " + batteryLevel + "%");
                    batteryProgress.setProgress(batteryLevel);
                });
            }
        }
    };
}