package org.socratec.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.socratec.model.ais.AisMessage;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class AisStreamWebSocketClient extends WebSocketClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(AisStreamWebSocketClient.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final long CONNECTION_TIMEOUT_MINUTES = 5;

    private final String mmsi;
    private final String subscriptionMessage;
    private final ScheduledExecutorService scheduler;
    private final Runnable onCloseCallback;
    private final Runnable onMessageReceivedCallback;
    private ScheduledFuture<?> timeoutFuture;

    public AisStreamWebSocketClient(URI serverUri, String mmsi, String subscriptionMessage,
                                   ScheduledExecutorService scheduler,
                                   Runnable onCloseCallback,
                                   Runnable onMessageReceivedCallback) {
        super(serverUri);
        this.mmsi = mmsi;
        this.subscriptionMessage = subscriptionMessage;
        this.scheduler = scheduler;
        this.onCloseCallback = onCloseCallback;
        this.onMessageReceivedCallback = onMessageReceivedCallback;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        LOGGER.info("WebSocket connected for MMSI: {}", mmsi);

        try {
            // Send subscription message
            send(subscriptionMessage);
            LOGGER.info("Subscription message sent for MMSI: {}", mmsi);

            // Schedule automatic disconnection after 5 minutes
            timeoutFuture = scheduler.schedule(() -> {
                LOGGER.info("Connection timeout reached for MMSI: {}, closing connection", mmsi);
                close();
            }, CONNECTION_TIMEOUT_MINUTES, TimeUnit.MINUTES);

        } catch (Exception e) {
            LOGGER.error("Error sending subscription message for MMSI: {}", mmsi, e);
            close();
        }
    }

    @Override
    public void onMessage(String message) {
        LOGGER.debug("Received text message for MMSI: {}", mmsi);
        processAisMessage(message);
    }

    @Override
    public void onMessage(ByteBuffer bytes) {
        try {
            // Convert byte buffer to string
            String message = StandardCharsets.UTF_8.decode(bytes).toString();
            processAisMessage(message);
        } catch (Exception e) {
            LOGGER.error("Error processing binary AIS message for MMSI: {}", mmsi, e);
        }
    }

    private void processAisMessage(String message) {
        try {
            // Parse the JSON message using DTO
            AisMessage aisMessage = OBJECT_MAPPER.readValue(message, AisMessage.class);

            // Check if this is a position report
            if (aisMessage.getMessage() != null && aisMessage.getMessage().getPositionReport() != null) {
                AisMessage.PositionReport positionReport = aisMessage.getMessage().getPositionReport();
                String timestamp = aisMessage.getMetaData() != null
                    ? aisMessage.getMetaData().getTimeUtc() : "N/A";

                // Print to console
                System.out.println("=== AIS Position Report ===");
                System.out.println("MMSI: " + mmsi);
                System.out.println("Timestamp: " + timestamp);
                System.out.println("Latitude: " + positionReport.getLatitude());
                System.out.println("Longitude: " + positionReport.getLongitude());
                System.out.println("Speed Over Ground: " + positionReport.getSog() + " knots");
                System.out.println("Course Over Ground: " + positionReport.getCog() + " degrees");
                System.out.println("True Heading: " + positionReport.getTrueHeading() + " degrees");
                System.out.println("===========================");

                // Notify callback that message was received
                if (onMessageReceivedCallback != null) {
                    onMessageReceivedCallback.run();
                }

                // Close connection after receiving message
                LOGGER.info("Message received for MMSI: {}, closing connection", mmsi);
                close();

            } else {
                LOGGER.debug("Received non-position report message for MMSI: {}", mmsi);
            }

        } catch (Exception e) {
            LOGGER.error("Error parsing AIS message for MMSI: {}", mmsi, e);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        LOGGER.info("WebSocket closed for MMSI: {} - Code: {}, Reason: {}, Remote: {}",
            mmsi, code, reason, remote);
        cleanup();
    }

    @Override
    public void onError(Exception ex) {
        LOGGER.error("WebSocket error for MMSI: {}", mmsi, ex);
    }

    private void cleanup() {
        if (timeoutFuture != null && !timeoutFuture.isDone()) {
            timeoutFuture.cancel(false);
        }
        if (onCloseCallback != null) {
            onCloseCallback.run();
        }
    }
}
