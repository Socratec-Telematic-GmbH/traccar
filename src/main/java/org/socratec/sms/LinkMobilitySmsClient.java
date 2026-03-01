package org.socratec.sms;

import org.socratec.config.SocratecKeys;
import org.traccar.config.Config;
import org.traccar.notification.MessageException;
import org.traccar.sms.SmsManager;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SMS client backed by the LINK Mobility SMS Messaging API.
 * Endpoint: POST /rest/smsmessaging/text
 * Authentication: Bearer token
 */
public class LinkMobilitySmsClient implements SmsManager {

    private final Client client;
    private final String url;
    private final String token;
    private final String sender;

    public LinkMobilitySmsClient(Config config, Client client) {
        this.client = client;
        this.url = config.getString(SocratecKeys.SMS_LINKMOBILITY_URL);
        this.token = config.getString(SocratecKeys.SMS_LINKMOBILITY_TOKEN);
        this.sender = config.getString(SocratecKeys.SMS_LINKMOBILITY_SENDER);
    }

    @Override
    public void sendMessage(String phone, String message, boolean command) throws MessageException {
        String normalized = phone.replaceAll("[^0-9]", "");
        if (normalized.startsWith("00")) {
            normalized = normalized.substring(2);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messageContent", message);

        List<String> recipients = new ArrayList<>();
        recipients.add(normalized);
        body.put("recipientAddressList", recipients);
        body.put("contentCategory", "informational");

        if (sender != null && !sender.isEmpty()) {
            body.put("senderAddress", sender);
            body.put("senderAddressType", "alphanumeric");
        }

        try (Response response = client
                .target(url)
                .path("rest/smsmessaging/text")
                .request()
                .header("Authorization", "Bearer " + token)
                .post(Entity.json(body))) {
            if (response.getStatus() / 100 != 2) {
                throw new MessageException(response.readEntity(String.class));
            }
        }
    }

}
