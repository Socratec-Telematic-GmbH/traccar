# [Traccar](https://www.traccar.org)

## Overview

Traccar is an open source GPS tracking system. This repository contains Java-based back-end service. It supports more than 200 GPS protocols and more than 2000 models of GPS tracking devices. Traccar can be used with any major SQL database system. It also provides easy to use [REST API](https://www.traccar.org/traccar-api/).

Other parts of Traccar solution include:

- [Traccar web app](https://github.com/traccar/traccar-web)
- [Traccar Manager Android app](https://github.com/traccar/traccar-manager-android)
- [Traccar Manager iOS app](https://github.com/traccar/traccar-manager-ios)

There is also a set of mobile apps that you can use for tracking mobile devices:

- [Traccar Client Android app](https://github.com/traccar/traccar-client-android)
- [Traccar Client iOS app](https://github.com/traccar/traccar-client-ios)

## Local Setup
1. Checkout Traccar repository

### Starting the Application
#### Method 1: Using Gradle (Recommended for Development)

```bash
./gradlew run
```

This will:

- Build the application automatically
- Start the server using the `debug.xml` configuration
- Use H2 database (file-based, no setup required)
- Enable debug mode and console logging

#### Method 2: Using Java directly

```bash
# First build the application
./gradlew build

# Then run with Java
java -jar target/traccar-6.7.1.jar debug.xml
```

#### Method 3: Using custom configuration

```bash
./gradlew run -Pargs="your-config.xml"
```

### Debug.xml configuration details

The gradle run task uses `debug.xml` by default, which configures results in having the supported services accessible at:

- __Web interface__: `./traccar-web/simple` (currently needs submodule initialization)
- __Database__: H2 file database at `./target/database` and Web admin interface enabled
- __Media storage__: `./target/media`

## Accessing the Services
Once started, the services will be accessible at:

| Service                   | URL                           | Note  |
| -------                   | ---                           | ---   |
| Web App (legacy app)      | http://localhost:8082         |       |
| Web App                   | http://localhost:3000         | Web app needs to be setup and started separately, see section further down.   |
| APIs                      | http://localhost:8082/api/    |       |
| Database                  | http://localhost:8082/console |       |

### Access H2 database
1. Open your browser and navigate to http://localhost:8082/console
1. Use these connection settings:
- __JDBC URL__: `jdbc:h2:./target/database`
- __User Name__: `sa`
- __Password__: (leave empty)
- __Driver Class__: `org.h2.Driver`

### Start and access Web App
1. Clone the repository traccar/traccar-web
2. The web-app is react based and can locally be hosted using nodejs. Therefor run `npm install` after you cloned the repo to fetch all required dependencies.
3. To start the web server use `start.sh` or respective `start.bat`. By default the web server is configured using `vite.config.js`. The config defines the APIs used by the web app to be available on `localhost:8082`.
4. After startup, the web app is locally available via http://localhost:3000/.

## Notifications via LINK Mobility

This Socratec fork adds support for sending notifications through [LINK Mobility](https://www.linkmobility.com/), enabling both **SMS** and **WhatsApp** delivery channels.

### Prerequisites

You need an active LINK Mobility account with:
- A **Bearer token** for API authentication
- A **channel UUID** for each channel you want to use (SMS and/or WhatsApp)

Contact LINK Mobility to obtain these credentials.

### Configuration

Add the following keys to your Traccar configuration file (e.g. `traccar.xml` or `debug.xml`):

```xml
<!-- Enable the desired notification channels -->
<entry key='notificator.types'>web,mail,sms,whatsapp</entry>

<!-- LINK Mobility credentials (required) -->
<entry key='sms.linkmobility.url'>https://api.linkmobility.eu</entry>
<entry key='sms.linkmobility.token'>YOUR_BEARER_TOKEN</entry>

<!-- SMS channel (required to activate LINK Mobility SMS) -->
<entry key='sms.linkmobility.smsChannelUuid'>YOUR_SMS_CHANNEL_UUID</entry>

<!-- WhatsApp channel (required to activate WhatsApp notifications) -->
<entry key='sms.linkmobility.whatsappChannelUuid'>YOUR_WHATSAPP_CHANNEL_UUID</entry>

<!-- Optional: alphanumeric sender ID shown on SMS messages -->
<entry key='sms.linkmobility.sender'>YourCompanyName</entry>

<!-- Optional: fall back to SMS if a WhatsApp message cannot be delivered (default: true) -->
<entry key='sms.linkmobility.smsFallback'>true</entry>
```

### Configuration Keys Reference

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `sms.linkmobility.url` | String | `https://api.linkmobility.eu` | LINK Mobility API base URL |
| `sms.linkmobility.token` | String | — | Bearer token for API authentication **(required)** |
| `sms.linkmobility.smsChannelUuid` | String | — | Channel UUID for SMS. **Presence of this key activates the LINK Mobility SMS client.** |
| `sms.linkmobility.whatsappChannelUuid` | String | — | Channel UUID for WhatsApp. Required when `whatsapp` is listed in `notificator.types`. |
| `sms.linkmobility.sender` | String | — | Optional alphanumeric sender ID for SMS messages |
| `sms.linkmobility.smsFallback` | Boolean | `true` | Send an SMS fallback if the WhatsApp message fails to be delivered |

### Activating Channels

Channels are activated by two independent mechanisms:

**SMS via LINK Mobility**
The LINK Mobility SMS client is automatically selected as the active `SmsManager` when `sms.linkmobility.smsChannelUuid` is present in the configuration. This replaces the built-in HTTP/SNS SMS clients (which are checked first — see priority below).

> SMS client priority: HTTP (`sms.http.url`) → AWS SNS (`sms.aws.region`) → **LINK Mobility** (`sms.linkmobility.smsChannelUuid`)

**WhatsApp**
The WhatsApp notificator is registered under the type name `whatsapp`. To enable it:
1. Add `whatsapp` to the `notificator.types` list.
2. Set `sms.linkmobility.whatsappChannelUuid` to your WhatsApp channel UUID.

Users will then be able to select "WhatsApp" as a notification channel in their account settings. Notifications are sent to the phone number configured on the Traccar user account.

### WhatsApp SMS Fallback

When `sms.linkmobility.smsFallback=true` (the default), every WhatsApp message is sent with an automatic SMS fallback. If the recipient cannot receive the WhatsApp message (e.g. they don't have WhatsApp), LINK Mobility will deliver the same content as a plain SMS instead. Set the key to `false` to disable this behaviour.

> **Note:** The SMS fallback uses the same LINK Mobility account and phone number. It does **not** require a separate SMS channel UUID — it is handled entirely by the LINK Mobility platform.