package org.socratec.aisstreamio;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.socratec.aisstreamio.model.AISPositionReport;
import org.traccar.config.Config;
import org.traccar.config.Keys;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Singleton
public class AisStreamService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AisStreamService.class);
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RECONNECT_DELAY_SECONDS = 10;

    private final boolean isSuccessfullyConfigured;
    private final ScheduledExecutorService scheduler;
    private final String serverUri;
    private final String apiKey;
    private AisStreamWebSocketClient client;
    private final Set<String> connectionContexts = ConcurrentHashMap.newKeySet();
    private Consumer<AISPositionReport> messageProcessor;

    @Inject
    public AisStreamService(Config config) {
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.serverUri = config.getString(Keys.AISSTREAMIO_SERVER_URI);
        this.apiKey = config.getString(Keys.AISSTREAMIO_API_KEY);
        if (serverUri == null || serverUri.isEmpty() || apiKey == null || apiKey.isEmpty()) {
            LOGGER.debug("AIS Stream server URI or APIKey not configured. AIS Stream subscription won't be started.");
            isSuccessfullyConfigured = false;
            return;
        }
        isSuccessfullyConfigured = true;
        LOGGER.debug("AIS Stream Service initialized");
    }

    public void subscribeAISStream(Set<String> mmsis, Consumer<AISPositionReport> messageProcessor) {
        this.messageProcessor = messageProcessor;

        // Check if configuration is available
        if (!isSuccessfullyConfigured) {
            LOGGER.debug("AIS Stream not properly configured. Skipping AIS Stream subscription.");
            return;
        }

        // Scenario 1: No carriers to track - shutdown if running
        if (mmsis == null || mmsis.isEmpty()) {
            if (client != null && client.isOpen()) {
                LOGGER.debug("No carriers to track. Shutting down AIS Stream connection.");
                shutdown();
            }
            return;
        }

        // Scenario 2: Carriers exist but client not running - start the client
        if (client == null || !client.isOpen()) {
            LOGGER.debug("Client not running but carriers exist. Starting AIS Stream connection for {} carriers",
                    mmsis.size());
            connectionContexts.clear();
            connectionContexts.addAll(mmsis);
            connectWithRetry(1);
            return;
        }

        // Scenario 3: Compare current and new carrier sets
        if (!connectionContexts.equals(mmsis)) {
            LOGGER.debug("Carrier list changed. Updating subscriptions. Current: {}, New: {}",
                    connectionContexts.size(), mmsis.size());

            // Update connection contexts
            connectionContexts.clear();
            connectionContexts.addAll(mmsis);

            // Update WebSocket subscriptions
            client.updateSubscriptions(connectionContexts);
            LOGGER.debug("Updated AIS Stream subscriptions. Now tracking {} carriers", connectionContexts.size());
        } else {
            LOGGER.debug("No changes detected in carrier list");
        }
    }

    public Set<String> getCurrentTrackedCarriers() {
        return Set.copyOf(connectionContexts);
    }

    public boolean isSuccessfullyConfigured() {
        return isSuccessfullyConfigured;
    }

    private void connectWithRetry(int attemptNumber) {
        if (attemptNumber >= MAX_RETRY_ATTEMPTS) {
            LOGGER.error("Max retry attempts ({}) reached for MMSI: {}", MAX_RETRY_ATTEMPTS, connectionContexts);
            shutdown();
            return;
        }

        try {
            // Create WebSocket client with cleanup and message received callbacks
            client = new AisStreamWebSocketClient(
                    serverUri,
                    apiKey,
                    connectionContexts,
                    this::processMessage,
                    () -> scheduleReconnection(attemptNumber)
            );

            // Connect to AIS Stream
            boolean connected = client.connectBlocking(30, TimeUnit.SECONDS);

            if (connected) {
                LOGGER.debug("Successfully connected to AIS Stream for MMSI: {} (attempt {})",
                        connectionContexts, attemptNumber);
            } else {
                throw new Exception("Connection timeout");
            }

        } catch (Exception e) {
            LOGGER.error("Failed to connect to AIS Stream for MMSI: {} (attempt {})",
                    connectionContexts, attemptNumber, e);
            scheduleReconnection(attemptNumber);
        }
    }

    private void processMessage(AISPositionReport positionReport) {
        LOGGER.debug("Received AIS position report: {}", positionReport);
        if (messageProcessor != null) {
            messageProcessor.accept(positionReport);
        }
    }

    private void scheduleReconnection(int attemptNumber) {
        LOGGER.debug("Scheduling reconnection for MMSI: {} in {} seconds", connectionContexts, RECONNECT_DELAY_SECONDS);

        scheduler.schedule(
                () -> connectWithRetry(attemptNumber + 1),
                RECONNECT_DELAY_SECONDS,
                TimeUnit.SECONDS
        );
    }

    public void shutdown() {
        LOGGER.debug("Shutting down AIS Stream Service");

        // Shutdown the scheduler
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }

            LOGGER.debug("AIS Stream Service shutdown complete");
        } catch (Exception e) {
            LOGGER.error("Error during shutdown", e);
        }

        if (client.isOpen()) {
            client.close();
        }

        client = null;
        connectionContexts.clear();
    }

}
