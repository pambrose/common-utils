/*
 *   Copyright © 2026 Paul Ambrose (pambrose@mac.com)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.pambrose.common.features

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.ApplicationCallPipeline.ApplicationPhase.Plugins
import io.ktor.server.application.Plugin
import io.ktor.server.application.call
import io.ktor.server.plugins.origin
import io.ktor.server.request.header
import io.ktor.server.request.path
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.util.AttributeKey

/** A predicate applied to an [ApplicationCall] to determine whether HTTPS redirect should be skipped. */
typealias CallPredicate = (ApplicationCall) -> Boolean

private val logger = KotlinLogging.logger {}

// The schemes of the absolute-form request targets (RFC 9112 section 3.2.2) that are redirected.
private val absoluteFormSchemes = listOf("http://", "https://")

// An ASCII host name or IPv4 address: dot-separated labels of letters, digits, '-' and '_', and an optional
// trailing dot. An internationalized name must be given in its ASCII (punycode) form.
private val hostNameRegex = Regex("""[A-Za-z0-9_-]+(\.[A-Za-z0-9_-]+)*\.?""")

// A bracketed IPv6 literal, such as [2001:db8::1] or [::ffff:192.0.2.1].
private val ipv6LiteralRegex = Regex("""\[[0-9A-Fa-f.]*:[0-9A-Fa-f:.]*]""")

// Whether [host] can be placed in a URL's authority as is: anything else (userinfo, a port, a path, a query,
// whitespace) would either change where the redirect goes or produce a malformed Location.
private fun isValidRedirectHost(host: String) = hostNameRegex.matches(host) || ipv6LiteralRegex.matches(host)

// A trailing ":port" on a configured host. sslPort is the documented way to set the port, but a configured host has
// always been placed in the URL as given, so "myapp.com:8443" redirected to https://myapp.com:8443 and still does.
private val portSuffixRegex = Regex(""":([0-9]{1,5})$""")

// Whether a configured [host] is a valid redirect host, optionally followed by a port from 1 to 65535.
private fun isValidConfiguredHost(host: String): Boolean {
  val portSuffix = portSuffixRegex.find(host) ?: return isValidRedirectHost(host)
  return portSuffix.groupValues[1].toInt() in 1..65535 && isValidRedirectHost(host.substring(0, portSuffix.range.first))
}

// The raw path and query of the request [target], to append to the HTTPS origin unchanged, or null when the target
// is neither origin-form ("/path?query") nor an absolute-form http(s) URI. Appended verbatim, a target such as
// "@evil.com/phish" or ".evil.com/phish" would become part of the redirect's authority.
private fun redirectPathAndQuery(target: String): String? {
  if (target.startsWith("/")) return target
  val scheme = absoluteFormSchemes.firstOrNull { target.startsWith(it, ignoreCase = true) } ?: return null
  // Only the path and query are kept: the authority ends at the first '/', '?' or '#', and an empty path means "/"
  // in an http(s) URI (RFC 9110 section 4.2.3).
  val afterScheme = target.substring(scheme.length)
  val authorityEnd = afterScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }
  return when {
    authorityEnd == -1 -> "/"
    afterScheme[authorityEnd] == '/' -> afterScheme.substring(authorityEnd)
    else -> "/" + afterScheme.substring(authorityEnd)
  }
}

/**
 * Redirect non-secure requests to HTTPS.
 *
 * A request whose `x-forwarded-proto` header is `http`, and that no exclusion predicate matches, is redirected to the
 * same path and query string, byte for byte, on `https://` [host]. The request gets `400 Bad Request` instead when
 * its target is neither origin-form (`/path?query`) nor an absolute-form `http://` or `https://` URI (whose path and
 * query alone are kept), or when [host] is not set and the request's host is not a valid host name or IP literal.
 *
 * Set [host] explicitly unless an upstream router (such as Heroku's) only forwards requests addressed to your
 * application's own domains. Otherwise the redirect goes to whatever host the client names in its `Host` header.
 *
 * @throws IllegalArgumentException if the configured [host] is not an ASCII host name, an IPv4 address or a
 *   bracketed IPv6 literal, optionally followed by a `:port` from 1 to 65535
 */
class HerokuHttpsRedirect(
  config: Configuration,
) {
  /**
   * HTTPS host to redirect to, or `null` to keep the host of the incoming request (which, without an upstream router
   * that checks it, is whatever the client sent in its `Host` header). Set the port with [redirectPort] (configured
   * as [Configuration.sslPort]); a `:port` given in the host is still accepted for compatibility.
   */
  val host: String? = config.host

  init {
    require(host == null || isValidConfiguredHost(host)) {
      "Invalid HerokuHttpsRedirect host \"$host\": expected an ASCII host name, an IPv4 address or a bracketed IPv6 " +
        "literal, optionally followed by a port from 1 to 65535, with no scheme, userinfo or path " +
        "(sslPort is the way to set the port)"
    }
  }

  /**
   * HTTPS port to redirect to
   */
  val redirectPort: Int = config.sslPort

  /**
   * If it does permanent redirect
   */
  val permanent: Boolean = config.permanentRedirect

  /**
   * The list of call predicates for redirect exclusion.
   * Any call matching any of the predicates will not be redirected by this feature.
   */
  val excludePredicates: List<CallPredicate> = config.excludePredicates.toList()

  /**
   * Redirect feature configuration
   */
  class Configuration {
    /**
     * HTTPS host to redirect to: an ASCII host name, an IPv4 address or a bracketed IPv6 literal. Set the port with
     * [sslPort]; a `:port` (1 to 65535) appended to the host is still accepted for compatibility, and is then used as
     * given, so leave [sslPort] at its default when doing that. Defaults to `null`, which keeps the host of the
     * incoming request, taken from the client's `Host` header. Set it explicitly unless an upstream router only
     * forwards requests addressed to your application's own domains.
     */
    var host: String? = null

    /**
     * HTTPS port (443 by default) to redirect to
     */
    var sslPort: Int = URLProtocol.HTTPS.defaultPort

    /**
     * Use permanent redirect or temporary
     */
    var permanentRedirect: Boolean = true

    /**
     * The list of call predicates for redirect exclusion.
     * Any call matching any of the predicates will not be redirected by this feature.
     */
    val excludePredicates: MutableList<CallPredicate> = []

    /**
     * Exclude calls whose path (not including the query string) starts with [pathPrefix] from being redirected
     * to https by this feature.
     */
    fun excludePrefix(pathPrefix: String) {
      exclude { call ->
        call.request.path().startsWith(pathPrefix)
      }
    }

    /**
     * Exclude calls whose path (not including the query string) ends with [pathSuffix] from being redirected
     * to https by this feature.
     */
    fun excludeSuffix(pathSuffix: String) {
      exclude { call ->
        call.request.path().endsWith(pathSuffix)
      }
    }

    /**
     * Exclude calls matching the [predicate] from being redirected to https by this feature.
     */
    fun exclude(predicate: (call: ApplicationCall) -> Boolean) {
      excludePredicates.add(predicate)
    }
  }

  /**
   * Feature installation object
   */
  companion object Feature : Plugin<ApplicationCallPipeline, Configuration, HerokuHttpsRedirect> {
    override val key = AttributeKey<HerokuHttpsRedirect>("HerokuHttpsRedirect")

    /**
     * Installs the [HerokuHttpsRedirect] plugin into the Ktor [pipeline].
     *
     * @param pipeline the application call pipeline to intercept
     * @param configure a configuration block for [Configuration]
     * @return the configured [HerokuHttpsRedirect] instance
     * @throws IllegalArgumentException if the configured host is not an ASCII host name, an IPv4 address or a
     *   bracketed IPv6 literal, optionally followed by a `:port` from 1 to 65535
     */
    override fun install(
      pipeline: ApplicationCallPipeline,
      configure: Configuration.() -> Unit,
    ): HerokuHttpsRedirect {
      val feature = HerokuHttpsRedirect(Configuration().apply(configure))
      pipeline.intercept(Plugins) {
        // See https://jaketrent.com/post/https-redirect-node-heroku/
        val scheme = call.request.header("x-forwarded-proto") ?: "none"
        if (scheme == "http" &&
          feature.excludePredicates.none { predicate: CallPredicate -> predicate(call) }
        ) {
          // A configured host was validated when the plugin was created; the request's comes from the client.
          val host = feature.host ?: call.request.origin.serverHost.takeIf(::isValidRedirectHost)
          // Append the raw request target rather than rebuilding it from the decoded, case-insensitive query
          // parameters, which reorders them and changes their case and encoding (breaking signed URLs).
          val pathAndQuery = redirectPathAndQuery(call.request.uri)
          if (host == null || pathAndQuery == null) {
            logger.debug {
              "Not redirecting a request with host \"${call.request.origin.serverHost}\" and target " +
                "\"${call.request.uri}\": responding 400"
            }
            call.respond(HttpStatusCode.BadRequest)
          } else {
            val origin =
              URLBuilder(protocol = URLProtocol.HTTPS, host = host, port = feature.redirectPort)
                .buildString()
                .removeSuffix("/")
            val redirectUrl = origin + pathAndQuery
            logger.debug { "Redirecting to: $redirectUrl" }
            call.respondRedirect(redirectUrl, feature.permanent)
          }
          finish()
        } else {
          logger.trace { "Not redirecting: $scheme://${call.request.origin.serverHost}${call.request.origin.uri}" }
        }
      }
      return feature
    }
  }
}
