import subprocess
import sys
import os
import logging
import ctypes
from pathlib import Path
import time
import threading
import argparse

import paho.mqtt.client as mqtt

# Configuration
MQTT_HOST = "4938f87b6b6745b097662d232690deb4.s1.eu.hivemq.cloud"  # Removed 'ssl://'
MQTT_PORT = 8883
CLIENTID = "WindowsClient"
MQTT_TOPIC = "HackGT"
MQTT_USERNAME = "linux"
MQTT_PASSWORD = "Password1"
QOS = 0

# Setup Logging
logging.basicConfig(
    filename='airgap_lockdown.log',
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)

def is_admin():
    """
    Check if the script is running with administrative privileges.
    """
    try:
        return ctypes.windll.shell32.IsUserAnAdmin()
    except:
        return False

def run_powershell_command(command):
    """
    Executes a PowerShell command and returns the output.
    """
    try:
        completed = subprocess.run(["powershell", "-Command", command], capture_output=True, text=True, check=True)
        logging.info(f"Executed command: {command.strip()}")
        logging.debug(f"Command output: {completed.stdout}")
        return completed.stdout
    except subprocess.CalledProcessError as e:
        logging.error(f"Command failed: {command.strip()}")
        logging.error(f"Error: {e.stderr}")
        return None

def disable_networking():
    """
    Disables Ethernet, Wi-Fi, Bluetooth, and NFC devices.
    """
    logging.info("Disabling Ethernet and Wi-Fi adapters...")
    run_powershell_command("""
        Get-NetAdapter -Physical | Where-Object { $_.Status -eq "Up" } | Disable-NetAdapter -Confirm:$false
    """)

    logging.info("Disabling Bluetooth devices...")
    run_powershell_command("""
        Get-PnpDevice -Class Bluetooth | Where-Object { $_.Status -eq "OK" } | Disable-PnpDevice -Confirm:$false
    """)

    logging.info("Disabling NFC devices...")
    run_powershell_command("""
        Get-PnpDevice | Where-Object { $_.FriendlyName -like "*NFC*" -and $_.Status -eq "OK" } | Disable-PnpDevice -Confirm:$false
    """)

def enable_networking():
    """
    Enables Ethernet, Wi-Fi, Bluetooth, and NFC devices.
    """
    logging.info("Enabling Ethernet and Wi-Fi adapters...")
    run_powershell_command("""
        Get-NetAdapter -Physical | Where-Object { $_.Status -eq "Disabled" } | Enable-NetAdapter -Confirm:$false
    """)

    logging.info("Enabling Bluetooth devices...")
    run_powershell_command("""
        Get-PnpDevice -Class Bluetooth | Where-Object { $_.Status -eq "Error" } | Enable-PnpDevice -Confirm:$false
    """)

    logging.info("Enabling NFC devices...")
    run_powershell_command("""
        Get-PnpDevice | Where-Object { $_.FriendlyName -like "*NFC*" -and $_.Status -eq "Error" } | Enable-PnpDevice -Confirm:$false
    """)

def disable_usb():
    """
    Disables USB storage by modifying the registry.
    """
    logging.info("Disabling USB storage devices...")
    run_powershell_command("""
        Set-ItemProperty -Path "HKLM:\\SYSTEM\\CurrentControlSet\\Services\\USBSTOR" -Name "Start" -Value 4
    """)

def enable_usb():
    """
    Enables USB storage by modifying the registry.
    """
    logging.info("Enabling USB storage devices...")
    run_powershell_command("""
        Set-ItemProperty -Path "HKLM:\\SYSTEM\\CurrentControlSet\\Services\\USBSTOR" -Name "Start" -Value 3
    """)

def lock_workstation():
    """
    Locks the Windows workstation.
    """
    logging.info("Locking the workstation...")
    try:
        ctypes.windll.user32.LockWorkStation()
        logging.info("Workstation locked successfully.")
    except Exception as e:
        logging.error(f"Failed to lock workstation: {e}")

def unlock_sequence():
    """
    Performs the unlock sequence by enabling networking and USB.
    """
    logging.info("Starting unlock sequence...")
    enable_networking()
    enable_usb()
    logging.info("Unlock sequence completed. All devices have been re-enabled.")

def lock_sequence():
    """
    Performs the lock sequence by disabling networking and USB, then locking the workstation.
    """
    logging.info("Starting lockdown sequence...")
    disable_networking()
    disable_usb()
    lock_workstation()
    logging.info("Lockdown sequence completed.")

def on_connect(client, userdata, flags, rc, properties=None):
    if rc == 0:
        logging.info("Connected to MQTT broker successfully.")
        client.subscribe(MQTT_TOPIC, qos=QOS)
        logging.info(f"Subscribed to MQTT topic: {MQTT_TOPIC}")
    else:
        logging.error(f"Failed to connect to MQTT broker. Return code: {rc}")

def on_message(client, userdata, msg):
    message = msg.payload.decode().strip().lower()
    topic = msg.topic
    logging.info(f"Received message on topic '{topic}': {message}")

    if topic == MQTT_TOPIC:
        if message == "lock":
            logging.info("Received 'lock' command. Initiating lockdown sequence.")
            lock_sequence()
        elif message == "unlock":
            logging.info("Received 'unlock' command. Initiating unlock sequence.")
            unlock_sequence()
        else:
            logging.warning(f"Unknown command received: {message}")

def on_disconnect(client, userdata, rc, properties=None):
    if rc != 0:
        logging.warning("Unexpected disconnection from MQTT broker.")
    else:
        logging.info("Disconnected from MQTT broker.")

def setup_mqtt_client():
    client = mqtt.Client(client_id=CLIENTID, protocol=mqtt.MQTTv5)

    # Configure TLS
    client.tls_set()  # This uses default CA certificates

    # Set username and password
    client.username_pw_set(MQTT_USERNAME, MQTT_PASSWORD)

    # Assign event callbacks
    client.on_connect = on_connect
    client.on_message = on_message
    client.on_disconnect = on_disconnect

    return client

def perform_manual_action(action):
    """
    Performs manual lock or unlock based on the action.
    """
    if action == "lock":
        logging.info("Manually initiating lockdown sequence.")
        lock_sequence()
        print("Lockdown sequence completed.")
    elif action == "unlock":
        logging.info("Manually initiating unlock sequence.")
        unlock_sequence()
        print("Unlock sequence completed. All devices have been re-enabled.")
    else:
        logging.error(f"Invalid manual action: {action}")
        print(f"Invalid action: {action}. Use 'lock' or 'unlock'.")

def main():
    parser = argparse.ArgumentParser(description="Airgap Lockdown Script with MQTT and Manual Controls")
    parser.add_argument(
        "action",
        nargs='?',
        choices=['lock', 'unlock'],
        help="Manually trigger 'lock' or 'unlock' actions."
    )
    args = parser.parse_args()

    if not is_admin():
        logging.error("Script is not running with administrative privileges.")
        print("Please run this script as an administrator.")
        sys.exit(1)

    if args.action:
        # Manual Mode: Perform the specified action and exit
        perform_manual_action(args.action)
        sys.exit(0)
    else:
        # Automatic Mode: Run as MQTT listener
        client = setup_mqtt_client()

        try:
            client.connect(MQTT_HOST, MQTT_PORT, keepalive=60)
        except Exception as e:
            logging.error(f"Failed to connect to MQTT broker: {e}")
            print(f"Failed to connect to MQTT broker: {e}")
            sys.exit(1)

        # Start the MQTT client in a separate thread
        mqtt_thread = threading.Thread(target=client.loop_forever)
        mqtt_thread.start()

        logging.info("MQTT client started and listening for messages.")
        print("MQTT client started and listening for messages. Press Ctrl+C to exit.")

        try:
            while True:
                time.sleep(1)  # Keep the main thread alive
        except KeyboardInterrupt:
            logging.info("KeyboardInterrupt received. Exiting...")
            print("\nExiting...")
        finally:
            client.disconnect()
            mqtt_thread.join()
            logging.info("MQTT client disconnected. Script terminated.")

if __name__ == "__main__":
    main()
