package com.hazel.bletest;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "BLE_UART";
    private static final String DEVICE_NAME = "UART Service";
    private static final UUID SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID CHAR_UUID_RX = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID CHAR_UUID_TX = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");

    private static final int PERMISSION_REQUEST_CODE = 1;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic txCharacteristic;

    private TextView statusText;
    private Button sendButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        sendButton = findViewById(R.id.sendButton);
        sendButton.setEnabled(false); // Disable button until connection is established

        // Check for required permissions
        checkPermissions();
    }

    /**
     * Checks Bluetooth permissions before proceeding.
     */
    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.BLUETOOTH_SCAN,
                                Manifest.permission.BLUETOOTH_CONNECT
                        },
                        PERMISSION_REQUEST_CODE);
                return;
            }
        } else { // Android 11 and below
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        PERMISSION_REQUEST_CODE);
                return;
            }
        }
        initializeBluetooth();
    }

    /**
     * Handles permission request results.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                initializeBluetooth();
            } else {
                statusText.setText("Permissions Denied! App cannot function.");
            }
        }
    }

    /**
     * Initializes Bluetooth and starts scanning for devices.
     */
    private void initializeBluetooth() {
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            statusText.setText("Bluetooth is disabled!");
            return;
        }

        startScan();
    }

    /**
     * Scans for the ESP32 device and attempts to connect.
     */
    private void startScan() {
        try {
            for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
                if (device.getName() != null && device.getName().equals(DEVICE_NAME)) {
                    connectToDevice(device);
                    return;
                }
            }
            statusText.setText("ESP32 not found. Pair it first!");
        } catch (SecurityException e) {
            statusText.setText("Bluetooth permissions denied.");
            Log.e(TAG, "Permission denied in startScan()", e);
        }
    }

    /**
     * Connects to the specified Bluetooth device.
     */
    private void connectToDevice(BluetoothDevice device) {
        try {
            statusText.setText("Connecting to " + device.getName());
            bluetoothGatt = device.connectGatt(this, false, gattCallback);
        } catch (SecurityException e) {
            statusText.setText("Cannot connect: Permissions denied.");
            Log.e(TAG, "Permission denied in connectToDevice()", e);
        }
    }

    /**
     * Handles Bluetooth GATT events.
     */
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            try{
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    Log.d(TAG, "Connected to GATT server.");
                    gatt.discoverServices();
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    Log.d(TAG, "Disconnected from GATT server.");
                    runOnUiThread(() -> statusText.setText("Disconnected"));
                }
            } catch (SecurityException e) {
                statusText.setText("Bluetooth permissions denied.");
                Log.e(TAG, "Permission denied in onConnectionStateChange()", e);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(SERVICE_UUID);
                if (service != null) {
                    txCharacteristic = service.getCharacteristic(CHAR_UUID_TX);
                    BluetoothGattCharacteristic rxCharacteristic = service.getCharacteristic(CHAR_UUID_RX);
                    if (rxCharacteristic != null) {
                        try {
                            gatt.setCharacteristicNotification(rxCharacteristic, true);
                        } catch (SecurityException e) {
                            Log.e(TAG, "Permission denied in onServicesDiscovered()", e);
                        }
                    }
                    runOnUiThread(() -> {
                        statusText.setText("Connected to ESP32");
                        sendButton.setEnabled(true);
                    });
                }
            }
        }
    };

    /**
     * Sends data to the ESP32.
     */
    private void sendData(String data) {
        if (txCharacteristic != null && bluetoothGatt != null) {
            try {
                txCharacteristic.setValue(data.getBytes());
                bluetoothGatt.writeCharacteristic(txCharacteristic);
                runOnUiThread(() -> statusText.setText("Sent: " + data));
            } catch (SecurityException e) {
                Log.e(TAG, "Permission denied in sendData()", e);
                runOnUiThread(() -> statusText.setText("Cannot send data: Permissions denied"));
            }
        }
    }
}
