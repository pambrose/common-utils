# Email Utils

Helpers for sending email through the [Resend](https://resend.com) API: a value-class address type with a
pragmatic validator, an HTML body builder with an embedded stylesheet, and models for Resend's webhook payloads.

## Features

### Email Addresses

- **`Email`**: a `@Serializable` `@JvmInline value class` wrapping an address string
- **`String.toResendEmail()`**: normalizes a string into an `Email` by lowercasing and trimming it
- **`Parameters.getEmail(name)`**: reads a normalized `Email` out of Ktor form parameters
- **`EMPTY_EMAIL` / `UNKNOWN_EMAIL`**: sentinels for an unset and an unknown address

### Validation

- **`String.isValidEmail()` / `String.isNotValidEmail()`**: a pragmatic, permissive address check
- **`Email.isBlank()` / `isNotBlank()`**: whether the wrapped string is blank
- **`Email.isBlankOrEmpty()` / `isNotBlankOrEmpty()`**: aliases of the two above, since blankness already
  covers the empty case
- **`Email.isNotValidEmail()`**: applies the same validator to the wrapped string

### HTML Email Bodies

- **`EmailUtils.email(cssFilename) { }`**: builds a complete HTML document with an embedded stylesheet

### Sending

- **`ResendService(envResendApiKey)`**: a service bound to one Resend API key
- **`sendEmail(from, to, cc, bcc, subject, html)`**: sends an email through the Resend API

### Webhook Payloads

- **`ResendWebhookMsg`**: the webhook envelope, with a preconfigured `json` and a `decode(body)` helper
- **`Data`**: the event payload — ids, sender, recipients, headers, tags, and optional bounce/click details
- **`Bounce` / `Click` / `Header`**: the nested event models

## Usage Examples

### Email Addresses

`Email` wraps a single string, so it costs nothing at runtime while keeping addresses from being confused with
other strings. `toString()` returns the wrapped value.

```kotlin
import com.pambrose.common.email.Email.Companion.EMPTY_EMAIL
import com.pambrose.common.email.Email.Companion.getEmail
import com.pambrose.common.email.Email.Companion.toResendEmail

// Normalization lowercases and trims, so the same address from two sources compares equal
val typed = "  User@Example.COM ".toResendEmail()   // Email("user@example.com")

// Reading from a Ktor form submission, normalized the same way
val params = call.receiveParameters()
val sender = params.getEmail("email")

// An absent parameter — or one whose value is blank — yields EMPTY_EMAIL
if (sender == EMPTY_EMAIL) error("no address supplied")
```

### Validation

`isValidEmail` is a deliberately pragmatic check rather than a full RFC 5322 implementation.

| Accepted                                                | Rejected                                          |
|---------------------------------------------------------|---------------------------------------------------|
| letters, digits and `._%+-` in the local part           | quoted local parts                                |
| plus-addressing (`user+tag@example.com`)                | internationalized (Unicode) domains               |
| single-character domain labels (`user@x.io`)            | bracketed or IPv6 address literals                |
| long TLDs, 2–63 characters (`user@example.photography`) | whitespace anywhere in the address                |
| bare IPv4-literal domains (`user@192.168.1.1`)          | a missing local part or domain, and blank strings |

```kotlin
import com.pambrose.common.email.EmailUtils.isNotValidEmail
import com.pambrose.common.email.EmailUtils.isValidEmail

"first.last+filter@example.co.uk".isValidEmail()  // true
"spaces in@email.com".isNotValidEmail()           // true
```

### Building an HTML Body

`email` returns a complete HTML document: a `<head>` holding the stylesheet inlined in a `<style>` element and
`color-scheme` / `supported-color-schemes` meta tags pinned to `light`, followed by a `<body>` built from the
block. Inlining the CSS matters because mail clients do not fetch external stylesheets.

```kotlin
import com.pambrose.common.email.EmailUtils.email
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.p

val html =
  email {
    h1 { +"Welcome" }
    p { +"Thanks for signing up." }
    div(classes = "footer") {
      a(href = "https://example.com/unsubscribe") { +"Unsubscribe" }
    }
  }

// Or supply your own stylesheet from the classpath
val branded = email(cssFilename = "css/branded-email.css") { h1 { +"Welcome" } }
```

`cssFilename` is a classpath resource path, resolved through the thread context classloader. It defaults to
`css/email.css`, a small default stylesheet that ships in this module's own main resources — so the default
resolves for consumers of the published jar, not just within this project. A filename that resolves to nothing
throws `IllegalArgumentException` rather than producing a document with no styles.

### Sending Email

```kotlin
import com.pambrose.common.email.Email
import com.pambrose.common.email.EmailUtils.email
import com.pambrose.common.email.ResendService
import kotlinx.html.h1

val service = ResendService(System.getenv("RESEND_API_KEY"))

service.sendEmail(
  from = Email("noreply@example.com"),
  to = [Email("first@example.com"), Email("second@example.com")],
  cc = [Email("manager@example.com")],   // defaults to empty
  bcc = [Email("audit@example.com")],    // defaults to empty
  subject = "Welcome",
  html = email { h1 { +"Welcome" } },
)
```

A successful send is logged at info level with the recipient counts and the Resend message id. The addresses
themselves are never logged, since they are personal data. A failure is thrown as a `ResendException` and is
deliberately not logged here, so it is reported once — by the caller that catches it — instead of twice.

### Decoding Webhooks

```kotlin
import com.pambrose.common.webhook.ResendWebhookMsg

val msg = ResendWebhookMsg.decode(call.receiveText())

when (msg.type) {
  "email.bounced" -> msg.data.bounce?.let { logger.warn { "Bounced (${it.type}): ${it.message}" } }
  "email.clicked" -> msg.data.click?.let { logger.info { "Clicked ${it.link} from ${it.ipAddress}" } }
  else -> logger.info { "${msg.type} for email ${msg.data.emailId}" }
}
```

`decode` uses the companion's `json`, which ignores unknown keys so an event carrying fields these models do
not declare still decodes instead of failing outright. Register that same `Json` wherever Ktor decodes the body
for you, so `call.receive<ResendWebhookMsg>()` is just as tolerant:

```kotlin
import com.pambrose.common.webhook.ResendWebhookMsg
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation

install(ContentNegotiation) {
  json(ResendWebhookMsg.json)
}
```

This module brings in only `ktor-http` (for `Parameters`), so add the Ktor server and content-negotiation
artifacts yourself if you decode payloads that way.

Every field that Resend documents as optional, or that only some event types carry, is nullable. Note the
mixed naming: the envelope and `Data` use snake_case on the wire (`created_at`, `email_id`, `broadcast_id`),
while `Bounce` and `Click` really do arrive in camelCase (`subType`, `ipAddress`, `userAgent`). The models
declare each `@SerialName` accordingly, so Kotlin-side property names stay uniformly camelCase.

## API Reference

### `Email`

- `@Serializable @JvmInline value class Email(val value: String)`
- `isBlank(): Boolean`, `isNotBlank(): Boolean`
- `isBlankOrEmpty(): Boolean`, `isNotBlankOrEmpty(): Boolean`
- `isNotValidEmail(): Boolean`
- `toString(): String` — the wrapped value

### `Email.Companion`

- `EMPTY_EMAIL: Email` — `Email("")`
- `UNKNOWN_EMAIL: Email` — `Email("Unknown")`
- `fun String.toResendEmail(): Email`
- `fun Parameters.getEmail(name: String): Email` — `EMPTY_EMAIL` when the parameter is absent

### `EmailUtils`

- `fun String.isValidEmail(): Boolean`
- `fun String.isNotValidEmail(): Boolean`
- `fun email(cssFilename: String = "css/email.css", block: BODY.() -> Unit): String`

### `ResendService`

- `class ResendService(envResendApiKey: String)`
- `fun sendEmail(from: Email, to: List<Email>, cc: List<Email> = emptyList(), bcc: List<Email> = emptyList(), subject: String, html: String)`
  — throws `com.resend.core.exception.ResendException` if the API call fails

### Webhook Models

- `data class ResendWebhookMsg(createdAt: String, data: Data, type: String)`
- `ResendWebhookMsg.json: Json` — configured with `ignoreUnknownKeys = true`
- `ResendWebhookMsg.decode(body: String): ResendWebhookMsg` — throws `SerializationException` on an invalid payload
- `data class Data(createdAt, emailId, from, subject?, to?, headers?, bounce?, click?, tags?, broadcastId?, messageId?, templateId?)`
- `data class Bounce(message: String, subType: String? = null, type: String? = null)`
- `data class Click(ipAddress: String, link: String, linkTags: String? = null, timestamp: String, userAgent: String)`
- `data class Header(name: String, value: String)`

## Dependencies

This module depends on:

- Kotlin Standard Library
- core-utils
- Resend Java SDK (`com.resend:resend-java`)
- kotlinx.html
- Ktor Http (for `Parameters`)
- kotlinx.serialization JSON

## Installation

[![Maven Central](https://img.shields.io/maven-central/v/com.pambrose.common-utils/email-utils)](https://central.sonatype.com/artifact/com.pambrose.common-utils/email-utils)

### Gradle

```kotlin
dependencies {
  implementation("com.pambrose.common-utils:email-utils:LATEST_VERSION")
}
```

### Maven

```xml
<dependency>
  <groupId>com.pambrose.common-utils</groupId>
  <artifactId>email-utils</artifactId>
  <version>LATEST_VERSION</version>
</dependency>
```

## Notes

- `isValidEmail` is a syntax check only — it says nothing about whether the mailbox exists or accepts mail
- Normalize addresses with `toResendEmail` before comparing or storing them; `Email` equality is exact string
  equality, so an untrimmed or mixed-case address will not match its normalized form
- `sendEmail` returns `Unit`; the Resend message id appears only in the info-level log line

## Security Considerations

⚠️ **The webhook models do not verify Resend's signature.**

- `ResendWebhookMsg` models the payload only. It does not check the Svix headers Resend sends (`svix-id`,
  `svix-timestamp`, `svix-signature`)
- A handler that decodes a request without verifying those headers first will accept forged events from anyone
  who knows the endpoint URL — verify the signature, then decode
- Recipient addresses are personal data: `sendEmail` logs counts and the message id instead. Keep that
  property in your own handlers, and avoid logging the contents of `Data.to` or `Click.ipAddress`

## Thread Safety

- `Email` is an immutable value class, and `EmailUtils` holds only a lazily compiled, immutable `Pattern`
- `ResendWebhookMsg.json` is a single immutable `Json` instance shared by every call to `decode`
- Each `ResendService` creates its own Resend client in its constructor and reuses it for every send

## License

Licensed under the Apache License, Version 2.0.
