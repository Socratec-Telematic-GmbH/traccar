package org.traccar.protocol;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.socratec.aisstreamio.AisMessageProcessor;
import org.socratec.aisstreamio.AisStreamService;
import org.traccar.BaseProtocol;
import org.traccar.ProcessingHandler;
import org.traccar.TrackerConnector;
import org.traccar.config.Config;
import org.traccar.storage.Storage;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Request;
import org.socratec.model.Carrier;

import java.util.stream.Collectors;

public class AisStreamProtocol extends BaseProtocol {

    private static final Logger LOGGER = LoggerFactory.getLogger(AisStreamProtocol.class);

    private final EmbeddedChannel syntheticChannel;
    private final AisStreamService aisStreamService;
    private final AisMessageProcessor aisMessageProcessor;
    private final Storage storage;

    @Inject
    public AisStreamProtocol(
            Config config,
            ProcessingHandler processingHandler,
            AisStreamService aisStreamService,
            AisMessageProcessor aisMessageProcessor,
            Storage storage) {

        this.aisStreamService = aisStreamService;
        this.aisMessageProcessor = aisMessageProcessor;
        this.storage = storage;

        // Create synthetic channel with processing pipeline
        this.syntheticChannel = new EmbeddedChannel(processingHandler);
        LOGGER.info("AIS Stream Protocol initialized with synthetic channel");

        // Configure the message processor with the synthetic channel
        aisMessageProcessor.setSyntheticChannel(syntheticChannel);

        // Add a custom connector that manages the WebSocket lifecycle
        getConnectorList().add(new AisStreamClient());
    }

    private class AisStreamClient implements TrackerConnector {

        private final ChannelGroup customChannelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

        AisStreamClient() {
            customChannelGroup.add(syntheticChannel);
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
            return customChannelGroup;
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
                aisStreamService.connectToAisStream(uniqueCarrierIds, aisMessageProcessor::processMessageAsync);
            } catch (StorageException e) {
                LOGGER.error("Failed to load carriers for AIS tracking", e);
                throw new Exception("Failed to start AIS Stream client", e);
            }
        }

        @Override
        public void stop() {
            LOGGER.info("Stopping AIS Stream client");
            aisStreamService.shutdown();
            aisMessageProcessor.shutdown();
            customChannelGroup.close().awaitUninterruptibly();
        }
    }
}
