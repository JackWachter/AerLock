# AerLock - Secure Your Device with a Touch

AerLock is a cross-platform security solution designed to give you complete control over the wireless features of your devices. By enabling or disabling Wi-Fi and Bluetooth functionality through secure MQTT connections, AerLock ensures that your devices remain air-gapped when necessary, enhancing privacy and security.

## Features

### Android App
The AerLock Android app allows users to lock and unlock their devices remotely. When the device is locked:
- Wi-Fi and Bluetooth are disabled.
- Airplane mode is activated for maximum security.
- The app integrates with an MQTT broker to relay lock and unlock commands, ensuring seamless connectivity and synchronization across platforms.

### Windows & Linux Clients
The Windows and Linux clients work in conjunction with the Android app, listening for MQTT messages that trigger security actions on the device. When the lock command is received:
- Wi-Fi and Bluetooth are turned off on the system.
- Devices are "locked down," preventing any unauthorized network access.
  
The unlock command restores connectivity while ensuring the same level of security and privacy.

## How It Works
- **MQTT Broker Integration**: AerLock uses HiveMQ Cloud for MQTT messaging between your devices. The Android app sends lock/unlock commands to the broker, which are received by the Windows and Linux clients.
- **Airplane Mode Control (Android)**: The Android app prompts the user to enable or disable Airplane mode to lock or unlock the device securely.
- **Cross-Platform**: AerLock is compatible with Android, Windows, and Linux platforms, ensuring seamless operation across different environments.

## Key Components
### Android App
- **MQTT Communication**: The app connects securely to the HiveMQ Cloud using TLS and sends lock/unlock commands via MQTT.
- **UI/UX**: The app interface dynamically updates to reflect the current lock status, changing both the background and the lock button's image.
- **Airplane Mode Handling**: Android users are prompted to enable or disable Airplane mode to enhance security.

### Windows and Linux Clients
- **MQTT Listener**: The Windows and Linux clients are always listening for MQTT messages to receive lock/unlock commands.
- **Network Control**: Upon receiving a lock command, the clients automatically disable Wi-Fi and Bluetooth to air-gap the system. Unlock commands restore network functionality.

## Supported Platforms
- **Android**: Version 8.0 and above.
- **Windows**: Windows 10 and above.
- **Linux**: Most modern Linux distributions (Ubuntu, Fedora, etc.).

## Security
- **TLS Encryption**: All communications between the Android app, Windows/Linux clients, and the HiveMQ Cloud are encrypted with TLS for secure transmission.
- **MQTT Authentication**: The app and clients authenticate using secure credentials to connect to the HiveMQ Cloud.

## Getting Started
For installation instructions, please visit our [download page](https://www.aerlock.co/download).

## Website
Learn more about AerLock at our [official website](https://www.aerlock.co).

---

© 2024 AerLock, All rights reserved.
