package org.socratec.notificators;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.socratec.config.SocratecKeys;
import org.traccar.config.Config;
import org.traccar.database.StatisticsManager;
import org.traccar.model.Event;
import org.traccar.model.Position;
import org.traccar.model.User;
import org.traccar.notification.MessageException;
import org.traccar.notification.NotificationFormatter;
import org.traccar.notification.NotificationMessage;
import org.traccar.notificators.Notificator;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sends notifications via WhatsApp using the LINK Mobility Omnichannel API.
 * Endpoint: POST /rest/channels/{uuid}/send/omnichannel
 * Primary channel: WhatsApp text message
 * Optional fallback: SMS (controlled by sms.linkmobility.smsFallback)
 */
@Singleton
public class NotificatorWhatsApp extends Notificator {

    private final Client client;
    private final StatisticsManager statisticsManager;
    private final String url;
    private final String token;
    private final String channelUuid;
    private final boolean smsFallback;

    @Inject
    public NotificatorWhatsApp(
            Config config,
            Client client,
            NotificationFormatter notificationFormatter,
            StatisticsManager statisticsManager) {
        super(notificationFormatter, "short");
        this.client = client;
        this.statisticsManager = statisticsManager;
        this.url = config.getString(SocratecKeys.SMS_LINKMOBILITY_URL);
        this.token = config.getString(SocratecKeys.SMS_LINKMOBILITY_TOKEN);
        this.channelUuid = config.getString(SocratecKeys.SMS_LINKMOBILITY_WHATSAPP_CHANNEL_UUID);
        this.smsFallback = config.getBoolean(SocratecKeys.SMS_LINKMOBILITY_SMS_FALLBACK);
    }

    @Override
    public void send(User user, NotificationMessage message, Event event, Position position) throws MessageException {
        if (user.getPhone() == null) {
            return;
        }

        String phone = user.getPhone().replaceAll("[^0-9]", "");
        if (phone.startsWith("00")) {
            phone = phone.substring(2);
        }

        Map<String, Object> whatsappContent = new LinkedHashMap<>();
        whatsappContent.put("type", "text");
        whatsappContent.put("text", Map.of("body", message.getBody()));

        Map<String, Object> whatsappMessage = new LinkedHashMap<>();
        whatsappMessage.put("recipientAddress", phone);
        whatsappMessage.put("messageContent", whatsappContent);

        Map<String, Object> messageWrapper = new LinkedHashMap<>();
        messageWrapper.put("messageType", "whatsapp");
        messageWrapper.put("whatsapp", whatsappMessage);

        if (smsFallback) {
            Map<String, Object> smsMessage = new LinkedHashMap<>();
            smsMessage.put("recipientAddress", phone);
            smsMessage.put("messageContent", message.getBody());

            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("messageType", "sms");
            fallback.put("sms", smsMessage);

            messageWrapper.put("fallback", fallback);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", messageWrapper);

        statisticsManager.registerSms();

        try (Response response = client
                .target(url)
                .path("rest/channels/{uuid}/send/omnichannel")
                .resolveTemplate("uuid", channelUuid)
                .request()
                .header("Authorization", "Bearer " + token)
                .post(Entity.json(body))) {
            if (response.getStatus() / 100 != 2) {
                throw new MessageException(response.readEntity(String.class));
            }
        }
    }

}
