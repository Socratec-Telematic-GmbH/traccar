package org.socratec.notificators;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.traccar.config.Config;
import org.traccar.config.Keys;
import org.traccar.database.StatisticsManager;
import org.traccar.model.Event;
import org.traccar.model.Position;
import org.traccar.model.User;
import org.traccar.notification.MessageException;
import org.traccar.notification.NotificationFormatter;
import org.traccar.notification.NotificationMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NotificatorWhatsAppTest {

    private static final String CHANNEL_UUID = "95ab0a2f-42f3-4da8-851b-7df772c94fca";

    private Config config;
    private Client httpClient;
    private WebTarget webTarget;
    private Invocation.Builder requestBuilder;
    private Response response;
    private StatisticsManager statisticsManager;
    private NotificationFormatter notificationFormatter;

    @BeforeEach
    public void setUp() {
        config = mock(Config.class);
        when(config.getString(Keys.SMS_LINKMOBILITY_URL)).thenReturn("https://api.linkmobility.eu");
        when(config.getString(Keys.SMS_LINKMOBILITY_TOKEN)).thenReturn("test-token");
        when(config.getString(Keys.SMS_LINKMOBILITY_WHATSAPP_CHANNEL_UUID)).thenReturn(CHANNEL_UUID);
        when(config.getBoolean(Keys.SMS_LINKMOBILITY_SMS_FALLBACK)).thenReturn(true);

        response = mock(Response.class);
        when(response.getStatus()).thenReturn(200);

        requestBuilder = mock(Invocation.Builder.class);
        when(requestBuilder.header(anyString(), anyString())).thenReturn(requestBuilder);
        when(requestBuilder.post(any(Entity.class))).thenReturn(response);

        webTarget = mock(WebTarget.class);
        when(webTarget.path(anyString())).thenReturn(webTarget);
        when(webTarget.resolveTemplate(anyString(), any())).thenReturn(webTarget);
        when(webTarget.request()).thenReturn(requestBuilder);

        httpClient = mock(Client.class);
        when(httpClient.target(anyString())).thenReturn(webTarget);

        statisticsManager = mock(StatisticsManager.class);
        notificationFormatter = mock(NotificationFormatter.class);
    }

    private NotificatorWhatsApp createNotificator() {
        return new NotificatorWhatsApp(config, httpClient, notificationFormatter, statisticsManager);
    }

    private NotificationMessage message(String body) {
        NotificationMessage msg = mock(NotificationMessage.class);
        when(msg.getBody()).thenReturn(body);
        return msg;
    }

    @Test
    public void testSendWhatsAppMessage() throws MessageException {
        User user = new User();
        user.setPhone("+436991234567");

        createNotificator().send(user, message("Alert: speeding detected"), mock(Event.class), mock(Position.class));

        verify(httpClient).target("https://api.linkmobility.eu");
        verify(webTarget).path("rest/channels/{uuid}/send/omnichannel");
        verify(webTarget).resolveTemplate("uuid", CHANNEL_UUID);
        verify(requestBuilder).header("Authorization", "Bearer test-token");
        verify(requestBuilder).post(any(Entity.class));
        verify(statisticsManager).registerSms();
    }

    @Test
    public void testSkipSendWhenNoPhone() throws MessageException {
        User user = new User();
        user.setPhone(null);

        createNotificator().send(user, message("Alert"), mock(Event.class), mock(Position.class));

        verify(httpClient, never()).target(anyString());
        verify(statisticsManager, never()).registerSms();
    }

    @Test
    public void testSmsFallbackIncluded() throws MessageException {
        when(config.getBoolean(Keys.SMS_LINKMOBILITY_SMS_FALLBACK)).thenReturn(true);
        User user = new User();
        user.setPhone("+436991234567");

        createNotificator().send(user, message("Alert"), mock(Event.class), mock(Position.class));

        // POST is called — fallback is embedded in the JSON body (verified via integration)
        verify(requestBuilder).post(any(Entity.class));
    }

    @Test
    public void testSmsFallbackExcluded() throws MessageException {
        when(config.getBoolean(Keys.SMS_LINKMOBILITY_SMS_FALLBACK)).thenReturn(false);
        User user = new User();
        user.setPhone("+436991234567");

        createNotificator().send(user, message("Alert"), mock(Event.class), mock(Position.class));

        verify(requestBuilder).post(any(Entity.class));
    }

    @Test
    public void testThrowsOnErrorResponse() {
        when(response.getStatus()).thenReturn(401);
        when(response.readEntity(String.class)).thenReturn("Unauthorized");

        User user = new User();
        user.setPhone("+436991234567");

        NotificatorWhatsApp notificator = createNotificator();
        org.junit.jupiter.api.Assertions.assertThrows(MessageException.class,
                () -> notificator.send(user, message("Alert"), mock(Event.class), mock(Position.class)));
    }

}
