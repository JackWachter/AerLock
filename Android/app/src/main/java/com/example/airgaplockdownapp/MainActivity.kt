package com.example.airgaplockdownapp

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import java.nio.charset.StandardCharsets
import java.util.*

class MainActivity : AppCompatActivity() {

    // HiveMQ client
    private lateinit var mqttClient: Mqtt5AsyncClient
    private val serverUri = "4938f87b6b6745b097662d232690deb4.s1.eu.hivemq.cloud"  // Your HiveMQ Cloud server
    private val serverPort = 8883 // Secure port for TLS
    private val clientId = UUID.randomUUID().toString() // Unique client ID
    private val publishTopic = "HackGT"  // Replace with your actual MQTT topic
    private val username = "tester"
    private val passwordString = "Password1"
    private var reconnectHandler: Handler = Handler(Looper.getMainLooper())
    private val reconnectInterval: Long = 5000 // 5 seconds

    // Track lock/unlock state
    private var isLocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize HiveMQ MQTT client
        mqttClient = MqttClient.builder()
            .identifier(clientId)  // Set the client ID
            .serverHost(serverUri)  // HiveMQ Cloud server
            .serverPort(serverPort)  // Port for TLS connection
            .sslWithDefaultConfig()  // Use SSL for secure connection
            .useMqttVersion5()  // Use MQTT version 5
            .buildAsync()

        // Connect to MQTT broker
        connectMQTT()

        // Handle button click for lock/unlock
        val btnLockUnlock = findViewById<Button>(R.id.btnLockUnlock)
        btnLockUnlock.setOnClickListener {
            if (isLocked) {
                unlockDevice()
                btnLockUnlock.text = "Lock"  // Change button text to "Lock" after unlocking
            } else {
                lockDevice()
                btnLockUnlock.text = "Unlock"  // Change button text to "Unlock" after locking
            }
            isLocked = !isLocked
        }
    }

    // Connect to HiveMQ Cloud using TLS and username/password
    private fun connectMQTT() {
        mqttClient.connectWith()
            .simpleAuth()
            .username(username)
            .password(passwordString.toByteArray(StandardCharsets.UTF_8))
            .applySimpleAuth()
            .send()
            .whenComplete { _, throwable ->
                runOnUiThread {
                    if (throwable != null) {
                        // Connection failed, try again after 5 seconds
                        Toast.makeText(this, "MQTT Connection Failed: ${throwable.message}. Retrying...", Toast.LENGTH_LONG).show()
                        scheduleReconnect()
                    } else {
                        // Connection succeeded
                        Toast.makeText(this, "MQTT Connected", Toast.LENGTH_SHORT).show()
                    }
                }
            }
    }

    // Schedule reconnection attempt after 5 seconds
    private fun scheduleReconnect() {
        reconnectHandler.postDelayed({
            connectMQTT()
        }, reconnectInterval)
    }

    // Publish a message to the MQTT broker
    private fun publishMessage(message: String) {
        if (mqttClient.state.isConnected) {
            mqttClient.publishWith()
                .topic(publishTopic)
                .payload(message.toByteArray(StandardCharsets.UTF_8))
                .send()
                .whenComplete { _, throwable ->
                    runOnUiThread {
                        if (throwable != null) {
                            Toast.makeText(this, "Publish failed: ${throwable.message}", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Message published", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
        } else {
            Toast.makeText(this, "MQTT Client is not connected", Toast.LENGTH_SHORT).show()
        }
    }

    // Lock the device by disabling Wi-Fi, Bluetooth, and enabling airplane mode
    private fun lockDevice() {
        if (mqttClient.state.isConnected) {
            publishMessage("lock")
            enableAirplaneMode()
            Toast.makeText(this, "Enable Airplane Mode", Toast.LENGTH_SHORT).show()
            Thread.sleep(2_000)
            Toast.makeText(this, "Device locked", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "MQTT not connected", Toast.LENGTH_SHORT).show()
        }
    }

    // Unlock the device by disabling airplane mode, then reconnect and publish the unlock message
    private fun unlockDevice() {
        disableAirplaneMode()
        Toast.makeText(this, "Disable Airplane Mode", Toast.LENGTH_SHORT).show()
        Thread.sleep(2_000)
        Toast.makeText(this, "Device unlocked", Toast.LENGTH_SHORT).show()

        // Delay reconnect and publish until after airplane mode is disabled
        reconnectHandler.postDelayed({
            if (!isAirplaneModeEnabled()) {
                connectMQTT()  // Attempt to reconnect MQTT after airplane mode is disabled
                publishMessage("unlock")
                Toast.makeText(this, "Device unlocked", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Airplane mode still enabled", Toast.LENGTH_SHORT).show()
            }
        }, 5000) // Adjust the delay based on your preference
    }

    // Enable Airplane Mode (Note: Airplane mode can't be directly enabled or disabled from code in recent Android versions, so we redirect to settings)
    private fun enableAirplaneMode() {
        val intent = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
        startActivity(intent)
    }

    // Disable Airplane Mode
    private fun disableAirplaneMode() {
        val intent = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
        startActivity(intent)
    }

    // Check if airplane mode is enabled
    private fun isAirplaneModeEnabled(): Boolean {
        return Settings.Global.getInt(
            contentResolver,
            Settings.Global.AIRPLANE_MODE_ON, 0
        ) != 0
    }

    override fun onDestroy() {
        super.onDestroy()
        // Safely disconnect the MQTT client when the app is closed
        mqttClient.disconnect()
        // Remove any scheduled reconnect attempts
        reconnectHandler.removeCallbacksAndMessages(null)
    }

    companion object {
        const val REQUEST_CODE_BLUETOOTH = 100
        const val REQUEST_CODE_WIFI = 101
    }
}
