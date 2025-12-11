package org.socratec.aisstreamio;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.socratec.aisstreamio.model.AISPositionReport;
import org.socratec.aisstreamio.model.GPSCoordinates;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class AisStreamWebSocketClient extends WebSocketClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(AisStreamWebSocketClient.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final double[][][] BOUNDING_BOX = new double[][][] {
        {{-90, -180}, {90, 180}}
    };
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss")
            .optionalStart()
            .appendFraction(java.time.temporal.ChronoField.NANO_OF_SECOND, 0, 9, true)
            .optionalEnd()
            .appendPattern(" Z 'UTC'")
            .toFormatter();

    private final String apiKey;
    private final Set<String> mmsis;
    private final Consumer<AISPositionReport> onMessageReceivedCallback;
    private final Runnable onConnectionClosedCallback;

    public AisStreamWebSocketClient(
            String serverUri,
            String apiKey,
            Set<String> mmsis,
            Consumer<AISPositionReport> onMessageReceivedCallback,
            Runnable onConnectionClosedCallback
    ) {
        super(URI.create(serverUri));
        this.apiKey = apiKey;
        this.mmsis = mmsis;
        this.onMessageReceivedCallback = onMessageReceivedCallback;
        this.onConnectionClosedCallback = onConnectionClosedCallback;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        LOGGER.debug("WebSocket connected for MMSI: {}", mmsis);

        sendSubscribeMessage(mmsis);
    }

    @Override
    public void onMessage(String message) {
        LOGGER.debug("Received text message for MMSI: {}", mmsis);
        processAisMessage(message);
    }

    @Override
    public void onMessage(ByteBuffer bytes) {
        try {
            // Convert byte buffer to string
            String message = StandardCharsets.UTF_8.decode(bytes).toString();
            processAisMessage(message);
        } catch (Exception e) {
            LOGGER.error("Error processing binary AIS message for MMSI: {}", mmsis, e);
        }
    }

    private void processAisMessage(String message) {
        try {
            AISStreamIOMessage aisStreamIOMessage = OBJECT_MAPPER.readValue(message, AISStreamIOMessage.class);
            var mmsi = aisStreamIOMessage.getMetaData().getMmsi();
            if (aisStreamIOMessage.getMessage() != null
                    && aisStreamIOMessage.getMessage().getPositionReport() != null) {
                var position = getPosition(mmsi, aisStreamIOMessage);
                LOGGER.debug("Received AIS position report: {}", position);

                if (onMessageReceivedCallback != null) {
                    onMessageReceivedCallback.accept(position);
                }
            } else {
                LOGGER.debug("Received non-position report message for MMSI: {}", mmsi);
            }
        } catch (Exception e) {
            LOGGER.error("Error parsing AIS message for MMSI: {}", mmsis, e);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        LOGGER.debug("WebSocket closed- Code: {}, Reason: {}, Remote: {}", code, reason, remote);
        if (remote) {
            onConnectionClosedCallback.run();
        }
    }

    @Override
    public void onError(Exception ex) {
        LOGGER.error("WebSocket error for MMSI: {}", mmsis, ex);
    }

    public void updateSubscriptions(Set<String> connectionContexts) {
        sendSubscribeMessage(connectionContexts);
    }

    private void sendSubscribeMessage(Set<String> mmsis) {
        try {
            // Send subscription message
            var msg = createSubscriptionMessage(mmsis);
            send(msg);
            LOGGER.debug("Subscription message sent: {}", msg);
        } catch (Exception e) {
            LOGGER.error("Error sending subscription message for MMSI: {}", mmsis, e);
            close();
        }
    }

    private String createSubscriptionMessage(Set<String> mmsis) throws Exception {
        AISStreamIOSubscriptionMessage subscription = new AISStreamIOSubscriptionMessage(
                this.apiKey,
                List.of((Object) BOUNDING_BOX[0]),
                mmsis.stream().toList(),
                List.of("PositionReport")
        );
        return OBJECT_MAPPER.writeValueAsString(subscription);
    }

    private static AISPositionReport getPosition(String mmsi, AISStreamIOMessage aisStreamIOMessage) {
        AISStreamIOMessage.PositionReport positionReport = aisStreamIOMessage.getMessage().getPositionReport();
        String timestamp = aisStreamIOMessage.getMetaData() != null
                ? aisStreamIOMessage.getMetaData().getTimeUtc() : null;

        Instant parsedTimestamp = null;
        if (timestamp != null) {
            try {
                parsedTimestamp = ZonedDateTime.parse(timestamp, TIMESTAMP_FORMATTER).toInstant();
            } catch (Exception e) {
                LOGGER.warn("Failed to parse timestamp: {} - {}", timestamp, e.getMessage());
            }
        }

        return new AISPositionReport(
                mmsi,
                new GPSCoordinates(positionReport.getLatitude(), positionReport.getLongitude()),
                positionReport.getSog(),
                positionReport.getCog(),
                positionReport.getTrueHeading(),
                parsedTimestamp
        );
    }
}
