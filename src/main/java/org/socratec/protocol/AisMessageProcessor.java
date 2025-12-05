package org.socratec.protocol;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.socratec.model.Carrier;
import org.socratec.protocol.model.AISPositionReport;
import org.traccar.ProcessingHandler;
import org.traccar.model.Position;
import org.traccar.storage.Storage;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;

import java.util.Collection;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Singleton
public class AisMessageProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(AisMessageProcessor.class);
    private static final int THREAD_POOL_SIZE = 10;

    private final Storage storage;
    private final ProcessingHandler processingHandler;
    private final ExecutorService processingExecutor;

    @Inject
    public AisMessageProcessor(Storage storage, ProcessingHandler processingHandler) {
        this.storage = storage;
        this.processingHandler = processingHandler;
        this.processingExecutor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        LOGGER.info("AIS Message Processor initialized with {} threads", THREAD_POOL_SIZE);
    }

    public void processMessageAsync(AISPositionReport aisPosition) {
        processingExecutor.submit(() -> processMessage(aisPosition));
    }

    private void processMessage(AISPositionReport aisPosition) {
        try {
            Collection<Carrier> carriers = storage.getObjects(Carrier.class, new Request(
                    new Columns.All(),
                    new Condition.Equals("carrierId", aisPosition.mmsi())));

            if (carriers.isEmpty()) {
                LOGGER.warn("Carrier with MMSI {} not tracked.", aisPosition.mmsi());
                return;
            }

            for (Carrier carrier : carriers) {
                Position position = mapToPosition(aisPosition, carrier.getId());
                processingHandler.onReleased(null, position);
                LOGGER.debug("Position queued for processing for device {} (MMSI: {})",
                        carrier.getId(), aisPosition.mmsi());
            }
        } catch (StorageException e) {
            LOGGER.error("Failed to process AIS position message for MMSI: {}", aisPosition.mmsi(), e);
        } catch (Exception e) {
            LOGGER.error("Unexpected error processing AIS position message for MMSI: {}", aisPosition.mmsi(), e);
        }
    }

    private static Position mapToPosition(AISPositionReport aisPosition, long deviceId) {
        Position position = new Position();
        position.setProtocol("AIS");
        position.setDeviceId(deviceId);

        Date timestamp = Date.from(aisPosition.timestamp());
        position.setServerTime(new Date());
        position.setDeviceTime(timestamp);
        position.setFixTime(timestamp);

        position.setValid(true);
        position.setLatitude(aisPosition.position().latitude());
        position.setLongitude(aisPosition.position().longitude());

        position.setSpeed(aisPosition.sog());
        position.setCourse(aisPosition.cog());
        position.setAltitude(0);

        position.set(Position.KEY_TYPE, "AIS");
        position.set("MMSI", aisPosition.mmsi());
        position.set("trueHeading", aisPosition.trueHeading());

        return position;
    }

    public void shutdown() {
        if (processingExecutor != null && !processingExecutor.isShutdown()) {
            LOGGER.info("Shutting down AIS message processing executor");
            processingExecutor.shutdown();
            try {
                if (!processingExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    processingExecutor.shutdownNow();
                }
                LOGGER.info("AIS message processing executor shutdown complete");
            } catch (InterruptedException e) {
                LOGGER.warn("Interrupted while waiting for executor shutdown", e);
                processingExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
