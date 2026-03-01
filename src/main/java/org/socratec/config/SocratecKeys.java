package org.socratec.config;

import org.traccar.config.ConfigKey;
import org.traccar.config.Keys;

/**
 * Convenience aliases for Socratec-specific configuration keys defined in
 * {@link Keys}. Keeps a single import point for all custom config keys.
 */
public final class SocratecKeys {

    private SocratecKeys() {
    }

    // ---- LINK Mobility ----
    public static final ConfigKey<String> SMS_LINKMOBILITY_URL = Keys.SMS_LINKMOBILITY_URL;
    public static final ConfigKey<String> SMS_LINKMOBILITY_TOKEN = Keys.SMS_LINKMOBILITY_TOKEN;
    public static final ConfigKey<Boolean> SMS_LINKMOBILITY_ENABLE = Keys.SMS_LINKMOBILITY_ENABLE;
    public static final ConfigKey<String> SMS_LINKMOBILITY_WHATSAPP_CHANNEL_UUID = Keys.SMS_LINKMOBILITY_WHATSAPP_CHANNEL_UUID;
    public static final ConfigKey<Boolean> SMS_LINKMOBILITY_SMS_FALLBACK = Keys.SMS_LINKMOBILITY_SMS_FALLBACK;
    public static final ConfigKey<String> SMS_LINKMOBILITY_SENDER = Keys.SMS_LINKMOBILITY_SENDER;

}
