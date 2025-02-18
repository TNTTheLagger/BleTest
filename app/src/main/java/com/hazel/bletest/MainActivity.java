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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
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
    private static final String TARGET_DEVICE_NAME = "MyESP32";

    // Keep a reference to the target device for bonding and connection
    private BluetoothDevice targetDevice;

    // BroadcastReceiver to listen for bond state changes
    private final BroadcastReceiver bondStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (ActivityCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }
                if (device == null || device.getName() == null) {
                    return;
                }
                int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                int previousBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR);
                if (ActivityCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }
                Log.d("BondStateReceiver", "Device: " + device.getName() +
                        " bond state changed: " + previousBondState + " -> " + bondState);

                if (ActivityCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }
                if (device.getName().equals(TARGET_DEVICE_NAME)) {
                    if (bondState == BluetoothDevice.BOND_BONDED) {
                        Toast.makeText(MainActivity.this, "Bonded with device", Toast.LENGTH_SHORT).show();
                        // Now that bonding is complete, initiate the connection.
                        connectToDevice(device);
                    } else if (bondState == BluetoothDevice.BOND_NONE) {
                        // Bonding failed or was broken.
                        Toast.makeText(MainActivity.this, "Bonding failed or broken", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Register the bond state receiver
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(bondStateReceiver, filter);

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
        if (ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
            // Request BLUETOOTH_SCAN permission
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{android.Manifest.permission.BLUETOOTH_SCAN}, 1);
            return;
        }

        ScanCallback scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                if (ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {
                    // Request BLUETOOTH_CONNECT permission
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{android.Manifest.permission.BLUETOOTH_CONNECT}, 2);
                    return;
                }

                BluetoothDevice device = result.getDevice();
                if (device.getName() != null && device.getName().equals(TARGET_DEVICE_NAME)) {
                    // Found the target device, stop scanning.
                    bluetoothLeScanner.stopScan(this);
                    targetDevice = device;

                    // If the device is not yet bonded, initiate bonding.
                    if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                        Toast.makeText(MainActivity.this, "Initiating bonding...", Toast.LENGTH_SHORT).show();
                        device.createBond();
                    } else {
                        // If already bonded, connect directly.
                        connectToDevice(device);
                    }
                }
            }
        };

        bluetoothLeScanner.startScan(scanCallback);

        // Stop scanning after 10 seconds to conserve resources.
        handler.postDelayed(() -> {
            bluetoothLeScanner.stopScan(scanCallback);
            Toast.makeText(MainActivity.this, "Scan stopped", Toast.LENGTH_SHORT).show();
        }, 10000);
    }

    private void connectToDevice(BluetoothDevice device) {
        if (ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{android.Manifest.permission.BLUETOOTH_CONNECT}, 2);
            return;
        }

        bluetoothGatt = device.connectGatt(this, false, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    isConnected = true;
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Connected to device", Toast.LENGTH_SHORT).show());
                    // Discover services once connected.
                    if (ActivityCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return;
                    }
                    gatt.discoverServices();
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    isConnected = false;
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Disconnected from device", Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    BluetoothGattService service = gatt.getService(SERVICE_UUID);
                    if (service != null) {
                        targetCharacteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                        runOnUiThread(() -> Toast.makeText(MainActivity.this,
                                "Service discovered and characteristic obtained", Toast.LENGTH_SHORT).show());
                    } else {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Service not found", Toast.LENGTH_SHORT).show());
                    }
                } else {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Service discovery failed", Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "Characteristic write status: " + status, Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    final String data = new String(characteristic.getValue());
                    runOnUiThread(() -> Toast.makeText(MainActivity.this,
                            "Characteristic read: " + data, Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private void writeToCharacteristic(String data) {
        if (isConnected && targetCharacteristic != null) {
            targetCharacteristic.setValue(data);
            if (ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            bluetoothGatt.writeCharacteristic(targetCharacteristic);
        }
    }

    private void readFromCharacteristic() {
        if (isConnected && targetCharacteristic != null) {
            if (ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            bluetoothGatt.readCharacteristic(targetCharacteristic);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (bluetoothGatt != null) {
            if (ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unregister the bond state receiver
        unregisterReceiver(bondStateReceiver);
    }
}
