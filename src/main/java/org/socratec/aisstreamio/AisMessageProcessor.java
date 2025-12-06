package org.socratec.aisstreamio;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.socratec.model.Carrier;
import org.socratec.aisstreamio.model.AISPositionReport;
import org.traccar.model.Position;
import org.traccar.storage.Storage;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;
import org.traccar.TrackerConnector;

import java.util.Collection;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class AisMessageProcessor implements TrackerConnector {

    private static final Logger LOGGER = LoggerFactory.getLogger(AisMessageProcessor.class);
    private static final int THREAD_POOL_SIZE = 10;
    private static final long CARRIER_SYNC_INTERVAL_MINUTES = 5;

    private final Storage storage;
    private final AisStreamService aisStreamService;
    private final EmbeddedChannel syntheticChannel;
    private final ExecutorService processingExecutor;
    private final ScheduledExecutorService scheduledExecutor;
    private final ChannelGroup channelGroup;

    public AisMessageProcessor(EmbeddedChannel syntheticChannel, AisStreamService aisStreamService, Storage storage) {
        this.syntheticChannel = syntheticChannel;
        this.aisStreamService = aisStreamService;
        this.storage = storage;
        this.processingExecutor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        this.scheduledExecutor = Executors.newScheduledThreadPool(1);
        this.channelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        this.channelGroup.add(syntheticChannel);
        LOGGER.info("AIS Message Processor initialized with {} threads", THREAD_POOL_SIZE);
    }

    @Override
    public boolean isDatagram() {
        return false;
    }

    @Override
    public boolean isSecure() {
        return true; // AIS Stream uses WSS
    }

    @Override
    public ChannelGroup getChannelGroup() {
        return channelGroup;
    }

    @Override
    public void start() throws Exception {
        LOGGER.info("Starting AIS Stream client");
        // Start periodic carrier synchronization job
        synchronizeCarriers();
        startCarrierSyncJob();
    }

    private void startCarrierSyncJob() {
        LOGGER.info("Starting periodic carrier synchronization job (every {} minutes)", CARRIER_SYNC_INTERVAL_MINUTES);
        scheduledExecutor.scheduleAtFixedRate(
                this::synchronizeCarriers,
                CARRIER_SYNC_INTERVAL_MINUTES,
                CARRIER_SYNC_INTERVAL_MINUTES,
                TimeUnit.MINUTES
        );
    }

    private void synchronizeCarriers() {
        try {
            LOGGER.debug("Running carrier synchronization job");

            // Get all carriers from database
            var carriers = storage.getObjects(Carrier.class, new Request(new Columns.All()));

            Set<String> currentCarrierIds = carriers.stream()
                    .map(Carrier::getCarrierId)
                    .collect(Collectors.toSet());

            LOGGER.debug("Found {} carriers in database", currentCarrierIds.size());

            // Update AIS Stream subscription
            aisStreamService.subscribeAISStream(currentCarrierIds, this::processMessageAsync);

        } catch (StorageException e) {
            LOGGER.error("Failed to synchronize carriers", e);
        } catch (Exception e) {
            LOGGER.error("Unexpected error during carrier synchronization", e);
        }
    }

    @Override
    public void stop() {
        shutdown();
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
                LOGGER.warn("Carrier with MMSI {} not tracked. Removing from tracking list.", aisPosition.mmsi());

                // Get current tracked carriers and remove this MMSI
                Set<String> currentTracked = aisStreamService.getCurrentTrackedCarriers();
                Set<String> updatedTracked = currentTracked.stream()
                        .filter(mmsi -> !mmsi.equals(aisPosition.mmsi()))
                        .collect(Collectors.toSet());

                // Update subscription without the removed carrier
                aisStreamService.subscribeAISStream(updatedTracked, this::processMessageAsync);
                return;
            }

            for (Carrier carrier : carriers) {
                Position position = mapToPosition(aisPosition, carrier.getId());
                syntheticChannel.writeInbound(position);
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
        LOGGER.info("Stopping AIS Stream client");

        // Shutdown scheduled executor
        if (scheduledExecutor != null && !scheduledExecutor.isShutdown()) {
            LOGGER.info("Shutting down carrier synchronization scheduler");
            scheduledExecutor.shutdown();
            try {
                if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduledExecutor.shutdownNow();
                }
                LOGGER.info("Carrier synchronization scheduler shutdown complete");
            } catch (InterruptedException e) {
                LOGGER.warn("Interrupted while waiting for scheduler shutdown", e);
                scheduledExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // Shutdown AIS Stream Service
        if (aisStreamService != null) {
            aisStreamService.shutdown();
        }

        // Shutdown processing executor
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

        // Close channel group
        if (channelGroup != null) {
            channelGroup.close().awaitUninterruptibly();
        }
    }
}
