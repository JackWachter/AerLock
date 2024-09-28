package com.example.airgaplockdownapp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    // Track lock/unlock state
    private var isLocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        title = "AerLock"

        // Get the button reference
        val btnLockUnlock = findViewById<Button>(R.id.btnLockUnlock)

        // Request Bluetooth and Wi-Fi permissions if needed
        checkAndRequestPermissions()

        // Set the initial state and click listener
        btnLockUnlock.setOnClickListener {
            if (isLocked) {
                // Unlock logic here
                unlockDevice()
                btnLockUnlock.text = "Lock"  // Change button text to Lock
            } else {
                // Lock logic here
                lockDevice()
                btnLockUnlock.text = "Unlock"  // Change button text to Unlock
            }
            isLocked = !isLocked  // Toggle the state
        }
    }

    // Function to check and request necessary permissions
    private fun checkAndRequestPermissions() {
        // Request Wi-Fi permission
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CHANGE_WIFI_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CHANGE_WIFI_STATE),
                REQUEST_CODE_WIFI
            )
        }

        // Request Bluetooth permission for Android 12 and above
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                REQUEST_CODE_BLUETOOTH
            )
        }
    }

    // Handle permission result callback
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CODE_BLUETOOTH -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Bluetooth permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Bluetooth permission denied", Toast.LENGTH_SHORT).show()
                }
            }
            REQUEST_CODE_WIFI -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Wi-Fi permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Wi-Fi permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Function to lock the device by redirecting to Airplane Mode settings
    private fun lockDevice() {
        // Redirect to Airplane Mode settings
        openAirplaneModeSettings()
    }

    // Function to unlock the device by redirecting to Airplane Mode settings
    private fun unlockDevice() {
        // Redirect to Airplane Mode settings
        openAirplaneModeSettings()
    }

    // Function to open the Airplane Mode settings
    private fun openAirplaneModeSettings() {
        val intent = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
        startActivity(intent)
    }

    companion object {
        const val REQUEST_CODE_BLUETOOTH = 100
        const val REQUEST_CODE_WIFI = 101
    }
}
