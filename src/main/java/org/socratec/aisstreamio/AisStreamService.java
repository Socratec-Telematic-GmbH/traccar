package org.socratec.aisstreamio;

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.socratec.aisstreamio.model.AISPositionReport;

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

    private final ScheduledExecutorService scheduler;
    private AisStreamWebSocketClient activeConnection;
    private final Set<String> connectionContexts = ConcurrentHashMap.newKeySet();
    private Consumer<AISPositionReport> messageProcessor;

    public AisStreamService() {
        this.scheduler = Executors.newScheduledThreadPool(1);
        LOGGER.info("AIS Stream Service initialized");
    }

    public void connectToAisStream(Set<String> mmsis, Consumer<AISPositionReport> messageProcessor) {
        LOGGER.info("Initiating AIS Stream connection for MMSI: {}", mmsis);
        this.messageProcessor = messageProcessor;
        connectionContexts.addAll(mmsis);

        // Attempt connection with retry logic
        connectWithRetry(1);
    }

    private void connectWithRetry(int attemptNumber) {
        if (attemptNumber >= MAX_RETRY_ATTEMPTS) {
            LOGGER.error("Max retry attempts ({}) reached for MMSI: {}", MAX_RETRY_ATTEMPTS, connectionContexts);
            shutdown();
            return;
        }

        try {
            // Create WebSocket client with cleanup and message received callbacks
            activeConnection = new AisStreamWebSocketClient(
                    connectionContexts,
                    this::processMessage,
                    () -> scheduleReconnection(attemptNumber)
            );

            // Connect to AIS Stream
            boolean connected = activeConnection.connectBlocking(30, TimeUnit.SECONDS);

            if (connected) {
                LOGGER.info("Successfully connected to AIS Stream for MMSI: {} (attempt {})",
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
        LOGGER.info("Scheduling reconnection for MMSI: {} in {} seconds", connectionContexts, RECONNECT_DELAY_SECONDS);

        scheduler.schedule(
                () -> connectWithRetry(attemptNumber + 1),
                RECONNECT_DELAY_SECONDS,
                TimeUnit.SECONDS
        );
    }

    public void shutdown() {
        LOGGER.info("Shutting down AIS Stream Service");

        // Shutdown the scheduler
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }

            LOGGER.info("AIS Stream Service shutdown complete");
        } catch (Exception e) {
            LOGGER.error("Error during shutdown", e);
        }

        if (activeConnection.isOpen()) {
            activeConnection.close();
        }

        activeConnection = null;
        connectionContexts.clear();
    }
}
