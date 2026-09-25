# reCAPTCHA Utils

Google reCAPTCHA support for Ktor servers: a configuration interface, server-side token verification against
Google's siteverify endpoint, and kotlinx.html helpers that load the reCAPTCHA script and render the widget.

The verification and HTML helpers are members of the `RecaptchaService` object, so bring it into scope with
`with(RecaptchaService) { ... }` at the call site.

## Features

### Configuration

- **`RecaptchaConfig`**: supplies `isRecaptchaEnabled`, `recaptchaSiteKey` and `recaptchaSecretKey`

### Verification

- **`validateRecaptcha(config, params)`**: verifies the submitted token, responding `400` itself when it fails
- **`RecaptchaResponse`**: the decoded siteverify response

### HTML Helpers

- **`loadRecaptchaScript(config)`**: adds Google's `api.js` to the `<head>`
- **`recaptchaWidget(config)`**: renders the `g-recaptcha` widget `<div>`

### Lifecycle

- **`close()`**: releases the long-lived `HttpClient` used for verification

## Usage Examples

### Configuration

```kotlin
import com.pambrose.common.recaptcha.RecaptchaConfig

object AppConfig : RecaptchaConfig {
  override val isRecaptchaEnabled = System.getenv("RECAPTCHA_ENABLED").toBoolean()
  override val recaptchaSiteKey: String? = System.getenv("RECAPTCHA_SITE_KEY")
  override val recaptchaSecretKey: String? = System.getenv("RECAPTCHA_SECRET_KEY")
}
```

All three helpers share one "fully configured" gate: reCAPTCHA must be enabled **and** both keys must be
present and non-blank. Requiring both keys keeps rendering and validation in lockstep, so a widget is never
shown whose response cannot actually be verified server-side.

| `isRecaptchaEnabled` | Site key         | Secret key       | Script and widget | Verification                                |
|----------------------|------------------|------------------|-------------------|---------------------------------------------|
| `false`              | any              | any              | not rendered      | skipped; the request passes                 |
| `true`               | missing or blank | any              | not rendered      | skipped; the request passes, warning logged |
| `true`               | present          | missing or blank | not rendered      | skipped; the request passes, warning logged |
| `true`               | present          | present          | rendered          | performed                                   |

Enabled with a key missing is a configuration mistake that leaves no bot protection at all, so it does not pass
silently: a warning is logged the first time the gate sees it. The flag behind that warning is process-wide, so
the message appears once no matter how many requests pass through.

### Rendering the Widget

```kotlin
import com.pambrose.common.recaptcha.RecaptchaService
import io.ktor.server.html.respondHtml
import kotlinx.html.body
import kotlinx.html.head
import kotlinx.html.postForm
import kotlinx.html.submitInput

get("/signup") {
  call.respondHtml {
    with(RecaptchaService) {
      head {
        loadRecaptchaScript(AppConfig)
      }
      body {
        postForm(action = "/signup") {
          recaptchaWidget(AppConfig)
          submitInput { value = "Sign up" }
        }
      }
    }
  }
}
```

`loadRecaptchaScript` emits `<script src="https://www.google.com/recaptcha/api.js" async defer>`, and
`recaptchaWidget` emits `<div class="g-recaptcha" data-sitekey="...">`. Neither emits anything at all when the
configuration is not fully configured, so the same templates work with reCAPTCHA switched off.

The example renders with `call.respondHtml`, which comes from `ktor-server-html-builder`. This module brings in
only `ktor-server-core` and kotlinx.html, so add that artifact yourself if you build pages that way; the two
helpers themselves are plain kotlinx.html extensions and work with `createHTML()` just as well.

### Validating a Submission

```kotlin
import com.pambrose.common.recaptcha.RecaptchaService
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondText

post("/signup") {
  val params = call.receiveParameters()

  val passed = with(RecaptchaService) { validateRecaptcha(AppConfig, params) }
  if (!passed)
    return@post  // validateRecaptcha has already written the 400 response

  call.respondText("Signed up")
}
```

`validateRecaptcha` reads the `g-recaptcha-response` form parameter that the widget adds to the submission,
and returns `true` when verification succeeds or when reCAPTCHA is not fully configured. **When it returns
`false` it has already responded**, so the handler must not write a second response.

| Situation                                               | Response                                | Returns |
|---------------------------------------------------------|-----------------------------------------|---------|
| Not fully configured                                    | none                                    | `true`  |
| Token missing or blank                                  | `400` `reCAPTCHA verification required` | `false` |
| Google reports `success: false`                         | `400` `reCAPTCHA verification failed`   | `false` |
| Verification could not be completed                     | `400` `reCAPTCHA verification failed`   | `false` |
| Reply is not a 2xx, or its body is not a valid response | `400` `reCAPTCHA verification failed`   | `false` |
| Google reports `success: true` in a 2xx reply           | none                                    | `true`  |

A call after `RecaptchaService.close()` is verified like any other: it builds a new client (see [Shutdown](#shutdown)).

The verification request is a form POST to `https://www.google.com/recaptcha/api/siteverify` carrying `secret`
and `response`, plus `remoteip` when one is available. That address comes from `call.request.origin.remoteAddress`
rather than `remoteHost`, because Google expects an IP and `remoteHost` can be a reverse-DNS hostname.

Each key is read from the config once per call, and the value that passed the gate is the one used.

Errors reaching or decoding Google's response are logged and treated as a failed verification. So is a reply
with a non-2xx status, even when its body would parse. There is one deliberate exception: a
`CancellationException` propagates instead. A client that disconnects mid-verification is a cancelled call, not
a bot, and swallowing it would break coroutine cancellation semantics.

### Shutdown

```kotlin
import com.pambrose.common.recaptcha.RecaptchaService
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped

fun Application.module() {
  monitor.subscribe(ApplicationStopped) {
    RecaptchaService.close()
  }
}
```

`close()` releases the underlying `HttpClient` and its connection and thread pools. `ApplicationStopped` also
fires on Ktor auto-reload, for a second embedded application in the same JVM, and between `testApplication`s, so
a verification after `close()` builds a new client (and logs that at info level) rather than failing.

## API Reference

### `RecaptchaConfig`

- `val isRecaptchaEnabled: Boolean`
- `val recaptchaSiteKey: String?`
- `val recaptchaSecretKey: String?`

### `RecaptchaService`

- `object RecaptchaService : Closeable`
- `suspend fun RoutingContext.validateRecaptcha(config: RecaptchaConfig, params: Parameters): Boolean`
  — throws `CancellationException` if the surrounding coroutine is cancelled
- `fun HEAD.loadRecaptchaScript(config: RecaptchaConfig)`
- `fun FlowContent.recaptchaWidget(config: RecaptchaConfig)`
- `override fun close()`

### `RecaptchaService.RecaptchaResponse`

- `@Serializable data class RecaptchaResponse(success: Boolean, errorCodes: List<String> = emptyList(), hostname: String? = null, challengeTs: String? = null)`
- `errorCodes` maps to `error-codes` and `challengeTs` to `challenge_ts` on the wire

## Dependencies

This module depends on:

- Kotlin Standard Library
- core-utils
- Ktor Server Core
- Ktor Client (CIO engine, ContentNegotiation and kotlinx JSON serialization)
- kotlinx.html

## Installation

[![Maven Central](https://img.shields.io/maven-central/v/com.pambrose.common-utils/recaptcha-utils)](https://central.sonatype.com/artifact/com.pambrose.common-utils/recaptcha-utils)

### Gradle

```kotlin
dependencies {
  implementation("com.pambrose.common-utils:recaptcha-utils:LATEST_VERSION")
}
```

### Maven

```xml
<dependency>
  <groupId>com.pambrose.common-utils</groupId>
  <artifactId>recaptcha-utils</artifactId>
  <version>LATEST_VERSION</version>
</dependency>
```

## Notes

- No server engine is pulled in — add the Ktor engine you want (CIO, Netty, …) yourself
- The widget and the validator agree on the `g-recaptcha-response` parameter name; a hand-written form must
  submit the field the reCAPTCHA script populates
- Verification is an outbound HTTPS call to Google on every validated submission, so it is unsuitable for
  hermetic tests unless the endpoint is faked

## Security Considerations

⚠️ **A disabled or partly configured reCAPTCHA lets every request through.**

- `validateRecaptcha` returns `true` when reCAPTCHA is not fully configured. That is what makes local and test
  environments usable, but it means production must be verified as enabled *with both keys set* — watch for the
  enabled-but-misconfigured warning at startup
- Keep the secret key server-side only. The site key is public and appears in rendered HTML; the secret key is
  sent only in the server-to-server siteverify request
- reCAPTCHA is one signal, not an authorization check — keep your own rate limiting and validation in place

## Thread Safety

- `RecaptchaService` is a singleton `object` holding one `HttpClient` shared by all concurrent verifications
- The enabled-but-misconfigured warning is guarded by an `AtomicBoolean`, so concurrent requests log it once
- `close()` may be called more than once without throwing

## License

Licensed under the Apache License, Version 2.0.
