#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <syslog.h>
#include <MQTTClient.h>

// STORE THE FOLLOWING IN ENVIRONMENT VARIABLES NOT IN THE CODE (THIS IS FOR DEMO ONLY)
#define MQTT_HOST "ssl://4938f87b6b6745b097662d232690deb4.s1.eu.hivemq.cloud:8883" // Placeholder MQTT broker
#define CLIENTID "LinuxClient" // Placeholder client ID
#define MQTT_TOPIC "HackGT" // Placeholder topic
#define MQTT_USERNAME "linux" // Placeholder username
#define MQTT_PASSWORD "Password1" // Placeholder password
#define QOS 0
#define TIMEOUT 10000L

MQTTClient client;

void take_os_snapshots() {
    int result;

    // Create snapshot directory
    result = system("mkdir -p /var/lib/airgap_lockdown");
    if (result == 0) {
        syslog(LOG_INFO, "Snapshot directory created.");
    } else {
        syslog(LOG_ERR, "Failed to create snapshot directory.");
    }

    // Save network interface states
    result = system("nmcli device status > /var/lib/airgap_lockdown/nmcli_status.txt");
    if (result == 0) {
        syslog(LOG_INFO, "Network interface states saved.");
    } else {
        syslog(LOG_ERR, "Failed to save network interface states.");
    }

    // Save rfkill states
    result = system("rfkill list > /var/lib/airgap_lockdown/rfkill_status.txt");
    if (result == 0) {
        syslog(LOG_INFO, "rfkill states saved.");
    } else {
        syslog(LOG_ERR, "Failed to save rfkill states.");
    }

    // Save iptables rules
    result = system("iptables-save > /var/lib/airgap_lockdown/iptables.rules");
    if (result == 0) {
        syslog(LOG_INFO, "iptables rules saved.");
    } else {
        syslog(LOG_ERR, "Failed to save iptables rules.");
    }

    syslog(LOG_INFO, "OS state snapshots have been taken.");
}

void disable_network_interfaces() {
    int result;

    // Disable networking
    result = system("nmcli networking off");
    if (result == 0) {
        syslog(LOG_INFO, "Networking disabled using nmcli.");
    } else {
        syslog(LOG_ERR, "Failed to disable networking using nmcli.");
    }

    // Block Bluetooth
    result = system("rfkill block bluetooth");
    if (result == 0) {
        syslog(LOG_INFO, "Bluetooth devices have been blocked.");
    } else {
        syslog(LOG_ERR, "Failed to block Bluetooth devices.");
    }

    // Block NFC
    result = system("rfkill block nfc");
    if (result == 0) {
        syslog(LOG_INFO, "NFC devices have been blocked.");
    } else {
        syslog(LOG_ERR, "Failed to block NFC devices.");
    }

    printf("Network interfaces have been disabled.\n");
}

void kill_active_connections() {
    int result;

    // Terminate all TCP connections
    result = system("ss -K");
    if (result == 0) {
        syslog(LOG_INFO, "All active network connections have been terminated.");
    } else {
        syslog(LOG_ERR, "Failed to terminate active network connections.");
    }

    printf("All active network connections have been terminated.\n");
}

void lock_screen() {
    int result;

    // Lock the user session
    result = system("loginctl lock-session");
    if (result == 0) {
        syslog(LOG_INFO, "Screen has been locked.");
    } else {
        syslog(LOG_ERR, "Failed to lock the screen.");
    }

    printf("Screen has been locked.\n");
}

void restore_network_interfaces() {
    int result;

    // Enable networking
    result = system("nmcli networking on");
    if (result == 0) {
        syslog(LOG_INFO, "Networking enabled using nmcli.");
    } else {
        syslog(LOG_ERR, "Failed to enable networking using nmcli.");
    }

    // Unblock Bluetooth
    result = system("rfkill unblock bluetooth");
    if (result == 0) {
        syslog(LOG_INFO, "Bluetooth devices have been unblocked.");
    } else {
        syslog(LOG_ERR, "Failed to unblock Bluetooth devices.");
    }

    // Unblock NFC
    result = system("rfkill unblock nfc");
    if (result == 0) {
        syslog(LOG_INFO, "NFC devices have been unblocked.");
    } else {
        syslog(LOG_ERR, "Failed to unblock NFC devices.");
    }

    printf("Network interfaces have been restored.\n");
}

int messageArrived(void *context, char *topicName, int topicLen, MQTTClient_message *message) {
    printf("Received a message on topic: %s, message: %s\n", topicName, (char *)message->payload);
    syslog(LOG_INFO, "Received message on topic: %s, payload: %s", topicName, (char *)message->payload);

    if (strcmp(topicName, MQTT_TOPIC) == 0) {
        if (strcmp((char *)message->payload, "lock") == 0) {
            syslog(LOG_INFO, "Received 'lock' message from MQTT broker.");
            printf("Starting lockdown sequence...\n");
            take_os_snapshots();
            disable_network_interfaces();
            kill_active_connections();
            lock_screen();
            syslog(LOG_INFO, "Lockdown sequence completed.");
            exit(0);  // Exit after locking
        }
    }

    MQTTClient_freeMessage(&message);
    MQTTClient_free(topicName);

    return 1;
}

int main(int argc, char *argv[]) {
    openlog("airgap_lockdown", LOG_PID | LOG_NDELAY, LOG_USER);

    if (geteuid() != 0) {
        fprintf(stderr, "Please run this program as root.\n");
        closelog();
        return 1;
    }

    // Check if the "unlock" command is issued; no MQTT required
    if (argc == 2 && strcmp(argv[1], "unlock") == 0) {
        syslog(LOG_INFO, "Starting unlock sequence...");
        printf("Starting unlock sequence...\n");

        restore_network_interfaces();

        syslog(LOG_INFO, "Unlock sequence completed.");
        printf("Unlock sequence completed.\n");
        closelog();
        return 0;
    }

    // Initialize MQTT client and connection options for lock
    MQTTClient_connectOptions conn_opts = MQTTClient_connectOptions_initializer;
    MQTTClient_SSLOptions ssl_opts = MQTTClient_SSLOptions_initializer;

    conn_opts.keepAliveInterval = 20;
    conn_opts.cleansession = 1;
    conn_opts.username = MQTT_USERNAME;
    conn_opts.password = MQTT_PASSWORD;

    // Set SSL options
    ssl_opts.enableServerCertAuth = 1;
    conn_opts.ssl = &ssl_opts;

    int rc;

    // Create MQTT client
    if ((rc = MQTTClient_create(&client, MQTT_HOST, CLIENTID, MQTTCLIENT_PERSISTENCE_NONE, NULL)) != MQTTCLIENT_SUCCESS) {
        fprintf(stderr, "Failed to create MQTT client, return code %d\n", rc);
        syslog(LOG_ERR, "Failed to create MQTT client, return code %d", rc);
        closelog();
        return rc;
    }

    // Set callbacks
    MQTTClient_setCallbacks(client, NULL, NULL, messageArrived, NULL);

    // Connect to MQTT broker
    if ((rc = MQTTClient_connect(client, &conn_opts)) != MQTTCLIENT_SUCCESS) {
        fprintf(stderr, "Failed to connect to MQTT broker, return code %d\n", rc);
        syslog(LOG_ERR, "Failed to connect to MQTT broker, return code %d", rc);
        MQTTClient_destroy(&client);
        closelog();
        return rc;
    }

    printf("Connected to MQTT broker.\n");
    syslog(LOG_INFO, "Connected to MQTT broker.");

    // Subscribe to the topic
    if ((rc = MQTTClient_subscribe(client, MQTT_TOPIC, QOS)) != MQTTCLIENT_SUCCESS) {
        fprintf(stderr, "Failed to subscribe to topic, return code %d\n", rc);
        syslog(LOG_ERR, "Failed to subscribe to topic, return code %d", rc);
        MQTTClient_disconnect(client, TIMEOUT);
        MQTTClient_destroy(&client);
        closelog();
        return rc;
    }

    printf("Subscribed to topic: %s\n", MQTT_TOPIC);
    syslog(LOG_INFO, "Subscribed to topic: %s", MQTT_TOPIC);

    // Keep the client running to listen for messages
    while (1) {
        sleep(1);
    }

    // Cleanup on exit
    MQTTClient_disconnect(client, TIMEOUT);
    MQTTClient_destroy(&client);
    closelog();

    return 0;
}
