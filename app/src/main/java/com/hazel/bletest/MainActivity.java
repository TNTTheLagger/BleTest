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
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

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

        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        if (checkPermissions()) {
            startScan();
        }

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendData("Hello ESP32");
            }
        });
    }

    private boolean checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1);
            return false;
        }
        return true;
    }

    private void startScan() {
        try {
            for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
                if (device.getName() != null && device.getName().equals(DEVICE_NAME)) {
                    connectToDevice(device);
                    return;
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied for Bluetooth scan", e);
            statusText.setText("Bluetooth permission denied");
        }
    }

    private void connectToDevice(BluetoothDevice device) {
        try {
            statusText.setText("Connecting to " + device.getName());
            bluetoothGatt = device.connectGatt(this, false, gattCallback);
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied for Bluetooth connection", e);
            statusText.setText("Bluetooth permission denied");
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            try{
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    Log.d(TAG, "Connected to GATT server.");
                    gatt.discoverServices();
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    Log.d(TAG, "Disconnected from GATT server.");
                    statusText.setText("Disconnected");
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Permission denied for Bluetooth connection", e);
                statusText.setText("Bluetooth permission denied");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                try {
                    BluetoothGattService service = gatt.getService(SERVICE_UUID);
                    if (service != null) {
                        txCharacteristic = service.getCharacteristic(CHAR_UUID_TX);
                        BluetoothGattCharacteristic rxCharacteristic = service.getCharacteristic(CHAR_UUID_RX);
                        if (rxCharacteristic != null) {
                            gatt.setCharacteristicNotification(rxCharacteristic, true);
                        }
                        statusText.setText("Connected");
                    }
                } catch (SecurityException e) {
                    Log.e(TAG, "Permission denied for discovering services", e);
                    statusText.setText("Bluetooth permission denied");
                }
            }
        }
    };

    private void sendData(String data) {
        try {
            if (txCharacteristic != null && bluetoothGatt != null) {
                txCharacteristic.setValue(data.getBytes());
                bluetoothGatt.writeCharacteristic(txCharacteristic);
                statusText.setText("Sent: " + data);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied for sending data", e);
            statusText.setText("Bluetooth permission denied");
        }
    }
}