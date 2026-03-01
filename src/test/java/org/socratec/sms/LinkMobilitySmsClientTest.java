package org.socratec.sms;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.traccar.config.Config;
import org.traccar.config.Keys;
import org.traccar.notification.MessageException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LinkMobilitySmsClientTest {

    private Config config;
    private Client httpClient;
    private WebTarget webTarget;
    private Invocation.Builder requestBuilder;
    private Response response;

    @BeforeEach
    public void setUp() {
        config = mock(Config.class);
        when(config.getString(Keys.SMS_LINKMOBILITY_URL)).thenReturn("https://api.linkmobility.eu");
        when(config.getString(Keys.SMS_LINKMOBILITY_TOKEN)).thenReturn("test-token");
        when(config.getString(Keys.SMS_LINKMOBILITY_SENDER)).thenReturn(null);

        response = mock(Response.class);
        when(response.getStatus()).thenReturn(200);

        requestBuilder = mock(Invocation.Builder.class);
        when(requestBuilder.header(anyString(), anyString())).thenReturn(requestBuilder);
        when(requestBuilder.post(any(Entity.class))).thenReturn(response);

        webTarget = mock(WebTarget.class);
        when(webTarget.path(anyString())).thenReturn(webTarget);
        when(webTarget.request()).thenReturn(requestBuilder);

        httpClient = mock(Client.class);
        when(httpClient.target(anyString())).thenReturn(webTarget);
    }

    @Test
    public void testSendMessageSuccess() throws MessageException {
        LinkMobilitySmsClient smsClient = new LinkMobilitySmsClient(config, httpClient);
        smsClient.sendMessage("+436991234567", "Test notification", false);

        verify(httpClient).target("https://api.linkmobility.eu");
        verify(webTarget).path("rest/smsmessaging/text");
        verify(requestBuilder).header("Authorization", "Bearer test-token");
        verify(requestBuilder).post(any(Entity.class));
    }

    @Test
    public void testSendMessageWithSender() throws MessageException {
        when(config.getString(Keys.SMS_LINKMOBILITY_SENDER)).thenReturn("Traccar");

        LinkMobilitySmsClient smsClient = new LinkMobilitySmsClient(config, httpClient);
        smsClient.sendMessage("+436991234567", "Test notification", false);

        verify(requestBuilder).post(any(Entity.class));
    }

    @Test
    public void testSendMessageThrowsOnErrorResponse() {
        when(response.getStatus()).thenReturn(401);
        when(response.readEntity(String.class)).thenReturn("Unauthorized");

        LinkMobilitySmsClient smsClient = new LinkMobilitySmsClient(config, httpClient);
        assertThrows(MessageException.class,
                () -> smsClient.sendMessage("+436991234567", "Test notification", false));
    }

    @Test
    public void testSendMessageThrowsOnServerError() {
        when(response.getStatus()).thenReturn(500);
        when(response.readEntity(String.class)).thenReturn("Internal Server Error");

        LinkMobilitySmsClient smsClient = new LinkMobilitySmsClient(config, httpClient);
        assertThrows(MessageException.class,
                () -> smsClient.sendMessage("+436991234567", "Test notification", false));
    }

}
