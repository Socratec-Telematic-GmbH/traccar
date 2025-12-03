package org.socratec.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.socratec.model.ais.AisSubscriptionMessage;

import java.net.URI;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Singleton
public class AisStreamService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AisStreamService.class);
    private static final String AIS_STREAM_URL = "wss://stream.aisstream.io/v0/stream";
    private static final String API_KEY_PLACEHOLDER = "5ebb5e243d96cdbd029c600568de7be785c40c65";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 1000;
    private static final long RECONNECT_DELAY_SECONDS = 30;

    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<String, AisStreamWebSocketClient> activeConnections;
    private final ConcurrentHashMap<String, ConnectionContext> connectionContexts;

    public AisStreamService() {
        this.scheduler = Executors.newScheduledThreadPool(10);
        this.activeConnections = new ConcurrentHashMap<>();
        this.connectionContexts = new ConcurrentHashMap<>();
        LOGGER.info("AIS Stream Service initialized");
    }

    // Inner class to store connection context
    private static class ConnectionContext {
        final String mmsi;
        final double[][][] boundingBoxes;

        ConnectionContext(String mmsi, double[][][] boundingBoxes) {
            this.mmsi = mmsi;
            this.boundingBoxes = boundingBoxes;
        }
    }

    public void connectToAisStream(String mmsi, double[][][] boundingBoxes) {
        LOGGER.info("Initiating AIS Stream connection for MMSI: {}", mmsi);

        // Create a unique connection key (allows multiple connections for the same MMSI)
        String connectionKey = mmsi + "_" + System.currentTimeMillis();

        // Store connection context for reconnection
        connectionContexts.put(connectionKey, new ConnectionContext(mmsi, boundingBoxes));

        // Attempt connection with retry logic
        connectWithRetry(mmsi, boundingBoxes, connectionKey, 0);
    }

    private void connectWithRetry(String mmsi, double[][][] boundingBoxes,
                                 String connectionKey, int attemptNumber) {
        if (attemptNumber >= MAX_RETRY_ATTEMPTS) {
            LOGGER.error("Max retry attempts ({}) reached for MMSI: {}", MAX_RETRY_ATTEMPTS, mmsi);
            return;
        }

        try {
            // Convert bounding boxes to List<Object>
            List<Object> boundingBoxList = null;
            if (boundingBoxes != null && boundingBoxes.length > 0) {
                boundingBoxList = List.of((Object) boundingBoxes[0]);
            }

            // Build subscription message
            AisSubscriptionMessage subscription = new AisSubscriptionMessage(
                API_KEY_PLACEHOLDER,
                boundingBoxList,
                List.of(mmsi),
                List.of("PositionReport")
            );
            String subscriptionJson = OBJECT_MAPPER.writeValueAsString(subscription);

            // Create WebSocket client with cleanup and message received callbacks
            URI serverUri = new URI(AIS_STREAM_URL);
            AisStreamWebSocketClient wsClient = new AisStreamWebSocketClient(
                serverUri,
                mmsi,
                subscriptionJson,
                scheduler,
                () -> removeConnection(connectionKey),
                () -> scheduleReconnection(connectionKey)
            );

            // Store the connection
            activeConnections.put(connectionKey, wsClient);

            // Connect to AIS Stream
            boolean connected = wsClient.connectBlocking(30, TimeUnit.SECONDS);

            if (connected) {
                LOGGER.info("Successfully connected to AIS Stream for MMSI: {} (attempt {})",
                    mmsi, attemptNumber + 1);
            } else {
                throw new Exception("Connection timeout");
            }

        } catch (Exception e) {
            LOGGER.error("Failed to connect to AIS Stream for MMSI: {} (attempt {})",
                mmsi, attemptNumber + 1, e);

            // Remove failed connection from active connections
            activeConnections.remove(connectionKey);

            // Schedule retry with exponential backoff
            if (attemptNumber + 1 < MAX_RETRY_ATTEMPTS) {
                long delayMs = INITIAL_RETRY_DELAY_MS * (long) Math.pow(2, attemptNumber);
                LOGGER.info("Scheduling retry for MMSI: {} in {}ms", mmsi, delayMs);

                scheduler.schedule(
                    () -> connectWithRetry(mmsi, boundingBoxes, connectionKey, attemptNumber + 1),
                    delayMs,
                    TimeUnit.MILLISECONDS
                );
            }
        }
    }

    private void removeConnection(String connectionKey) {
        AisStreamWebSocketClient removed = activeConnections.remove(connectionKey);
        if (removed != null) {
            LOGGER.info("Connection removed from active connections: {}", connectionKey);
        }
    }

    private void scheduleReconnection(String connectionKey) {
        ConnectionContext context = connectionContexts.get(connectionKey);
        if (context != null) {
            LOGGER.info("Scheduling reconnection for MMSI: {} in {} seconds", 
                context.mmsi, RECONNECT_DELAY_SECONDS);
            
            scheduler.schedule(
                () -> {
                    // Remove old connection key and create new one
                    connectionContexts.remove(connectionKey);
                    connectToAisStream(context.mmsi, context.boundingBoxes);
                },
                RECONNECT_DELAY_SECONDS,
                TimeUnit.SECONDS
            );
        } else {
            LOGGER.warn("No connection context found for key: {}, skipping reconnection", connectionKey);
        }
    }

    public int getActiveConnectionCount() {
        return activeConnections.size();
    }

    public void shutdown() {
        LOGGER.info("Shutting down AIS Stream Service");

        try {
            // Close all active connections
            activeConnections.values().forEach(client -> {
                try {
                    if (client.isOpen()) {
                        client.close();
                    }
                } catch (Exception e) {
                    LOGGER.error("Error closing WebSocket connection", e);
                }
            });

            activeConnections.clear();
            connectionContexts.clear();

            // Shutdown the scheduler
            scheduler.shutdown();
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }

            LOGGER.info("AIS Stream Service shutdown complete");
        } catch (Exception e) {
            LOGGER.error("Error during shutdown", e);
        }
    }
}
