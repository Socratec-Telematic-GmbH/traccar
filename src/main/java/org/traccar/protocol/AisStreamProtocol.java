package org.traccar.protocol;

import io.netty.channel.embedded.EmbeddedChannel;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.socratec.aisstreamio.AisMessageProcessor;
import org.socratec.aisstreamio.AisStreamService;
import org.traccar.BaseProtocol;
import org.traccar.ProcessingHandler;
import org.traccar.config.Config;
import org.traccar.storage.Storage;

public class AisStreamProtocol extends BaseProtocol {

    private static final Logger LOGGER = LoggerFactory.getLogger(AisStreamProtocol.class);

    @Inject
    public AisStreamProtocol(
            Config config,
            ProcessingHandler processingHandler,
            Storage storage,
            AisStreamService aisStreamService
    ) {

        // Create synthetic channel with processing pipeline
        EmbeddedChannel syntheticChannel = new EmbeddedChannel(processingHandler);
        LOGGER.info("AIS Stream Protocol initialized with synthetic channel");

        // Create message processor with the synthetic channel
        AisMessageProcessor aisMessageProcessor = new AisMessageProcessor(syntheticChannel, aisStreamService, storage);

        // Add the message processor as a connector (it implements TrackerConnector)
        getConnectorList().add(aisMessageProcessor);
    }
}
