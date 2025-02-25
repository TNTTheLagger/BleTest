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

        if (checkPermissions()) {
            startScan();
        } else {
            requestPermissions();
        }

        startScanButton.setOnClickListener(v -> startScan());
    }

    private boolean checkPermissions() {
        while (true) {
            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                return true;
            } else {
                requestPermissions();
            }
        }
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
        if (checkPermissions()) {
            scanner.startScan(new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    BluetoothDevice device = result.getDevice();
                    if (DEVICE_NAME.equals(device.getName())) {
                        Log.d(TAG, "Found device: " + device.getName());
                        scanner.stopScan(this);
                        if (checkPermissions()) {
                            device.connectGatt(MainActivity.this, false, gattCallback);
                        }
                    }
                }
            });
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                Log.d(TAG, "Connected to device");
                if (checkPermissions()) {
                    gatt.discoverServices();
                }
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (checkPermissions()) {
                BluetoothGattService service = gatt.getService(BATTERY_SERVICE_UUID);
                if (service != null) {
                    BluetoothGattCharacteristic characteristic = service.getCharacteristic(BATTERY_LEVEL_UUID);
                    if (characteristic != null) {
                        gatt.readCharacteristic(characteristic);
                    }
                }
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
