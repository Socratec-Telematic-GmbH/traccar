package org.socratec.aisstreamio;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Singleton
public class AisMessageProcessor implements TrackerConnector {

    private static final Logger LOGGER = LoggerFactory.getLogger(AisMessageProcessor.class);
    private static final int THREAD_POOL_SIZE = 10;

    private final Storage storage;
    private final AisStreamService aisStreamService;
    private EmbeddedChannel syntheticChannel;
    private final ExecutorService processingExecutor;
    private final ChannelGroup channelGroup;

    @Inject
    public AisMessageProcessor(Storage storage, AisStreamService aisStreamService) {
        this.storage = storage;
        this.aisStreamService = aisStreamService;
        this.processingExecutor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        this.channelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
        LOGGER.info("AIS Message Processor initialized with {} threads", THREAD_POOL_SIZE);
    }

    public void setSyntheticChannel(EmbeddedChannel syntheticChannel) {
        this.syntheticChannel = syntheticChannel;
        this.channelGroup.add(syntheticChannel);
        LOGGER.info("Synthetic channel configured for AIS Message Processor");
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
        try {
            // Get all carriers from database
            var carriers = storage.getObjects(Carrier.class, new Request(new Columns.All()));
            if (carriers.isEmpty()) {
                LOGGER.warn("No carriers configured for AIS tracking");
                return;
            }

            var uniqueCarrierIds = carriers.stream()
                    .map(Carrier::getCarrierId)
                    .collect(Collectors.toSet());

            LOGGER.info("Starting AIS tracking for {} carriers: {}", uniqueCarrierIds.size(), uniqueCarrierIds);

            // Connect to AIS Stream
            aisStreamService.connectToAisStream(uniqueCarrierIds, this::processMessageAsync);
        } catch (StorageException e) {
            LOGGER.error("Failed to load carriers for AIS tracking", e);
            throw new Exception("Failed to start AIS Stream client", e);
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
                LOGGER.warn("Carrier with MMSI {} not tracked.", aisPosition.mmsi());
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
