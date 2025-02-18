package com.hazel.bletest;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic targetCharacteristic;
    private static final UUID SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b");
    private static final UUID CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8");
    private Handler handler = new Handler();
    private boolean isConnected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Bluetooth not supported or not enabled", Toast.LENGTH_SHORT).show();
            finish();
        }

        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();

        startScan();
    }

    private void startScan() {
        if (ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            // Request permissions
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{android.Manifest.permission.BLUETOOTH_SCAN}, 1);
            return;
        }

        ScanCallback scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                if (ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    // Request permissions
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{android.Manifest.permission.BLUETOOTH_CONNECT}, 2);
                    return;
                }

                if (result.getDevice().getName() != null && result.getDevice().getName().equals("MyESP32")) {
                    bluetoothLeScanner.stopScan(this);
                    connectToDevice(result.getDevice());
                }
            }
        };

        bluetoothLeScanner.startScan(scanCallback);

        handler.postDelayed(() -> bluetoothLeScanner.stopScan(scanCallback), 10000); // Stop scan after 10 seconds
    }

    private void connectToDevice(BluetoothDevice device) {
        if (ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // Request permissions
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{android.Manifest.permission.BLUETOOTH_CONNECT}, 2);
            return;
        }

        bluetoothGatt = device.connectGatt(this, false, new BluetoothGattCallback() {
            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                super.onServicesDiscovered(gatt, status);

                BluetoothGattService service = gatt.getService(SERVICE_UUID);
                if (service != null) {
                    targetCharacteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                    isConnected = true;
                    Toast.makeText(MainActivity.this, "Connected to device", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void writeToCharacteristic(String data) {
        if (isConnected && targetCharacteristic != null) {
            targetCharacteristic.setValue(data);
            if (ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            bluetoothGatt.writeCharacteristic(targetCharacteristic);
        }
    }

    private void readFromCharacteristic() {
        if (isConnected && targetCharacteristic != null) {
            if (ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            bluetoothGatt.readCharacteristic(targetCharacteristic);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (bluetoothGatt != null) {
            if (ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            bluetoothGatt.close();
        }
    }
}
