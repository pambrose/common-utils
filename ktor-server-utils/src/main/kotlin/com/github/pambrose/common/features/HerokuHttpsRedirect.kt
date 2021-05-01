/*
 * Copyright © 2021 Paul Ambrose (pambrose@mac.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.github.pambrose.common.features

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Redirect non-secure requests to HTTPS
 */
class HerokuHttpsRedirect(config: Configuration) {
  /**
   * HTTPS host to redirect to
   */
  val host: String = config.host

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
  @KtorExperimentalAPI
  val excludePredicates: List<(ApplicationCall) -> Boolean> = config.excludePredicates.toList()

  /**
   * Redirect feature configuration
   */
  class Configuration {
    /**
     * HTTPS host to redirect to
     */
    var host: String = "localhost"

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
    @KtorExperimentalAPI
    val excludePredicates: MutableList<(ApplicationCall) -> Boolean> = ArrayList()

    /**
     * Exclude calls with paths matching the [pathPrefix] from being redirected to https by this feature.
     */
    @KtorExperimentalAPI
    fun excludePrefix(pathPrefix: String) {
      exclude { call ->
        call.request.origin.uri.startsWith(pathPrefix)
      }
    }

    /**
     * Exclude calls with paths matching the [pathSuffix] from being redirected to https by this feature.
     */
    @KtorExperimentalAPI
    fun excludeSuffix(pathSuffix: String) {
      exclude { call ->
        call.request.origin.uri.endsWith(pathSuffix)
      }
    }

    /**
     * Exclude calls matching the [predicate] from being redirected to https by this feature.
     */
    @KtorExperimentalAPI
    fun exclude(predicate: (call: ApplicationCall) -> Boolean) {
      excludePredicates.add(predicate)
    }
  }

  /**
   * Feature installation object
   */
  companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, HerokuHttpsRedirect> {
    override val key = AttributeKey<HerokuHttpsRedirect>("HerokuHttpsRedirect")
    override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): HerokuHttpsRedirect {
      val feature = HerokuHttpsRedirect(Configuration().apply(configure))
      pipeline.intercept(ApplicationCallPipeline.Features) {
        // See https://jaketrent.com/post/https-redirect-node-heroku/
        val scheme = call.request.header("x-forwarded-proto") ?: "none"
        if (scheme == "http" && feature.excludePredicates.none { predicate -> predicate(call) }) {
          val redirectUrl =
            call.url {
              protocol = URLProtocol.HTTPS
              host = feature.host
              port = feature.redirectPort
            }
          logger.debug { "Redirecting to: $redirectUrl" }
          call.respondRedirect(redirectUrl, feature.permanent)
          finish()
        } else {
          logger.trace { "Not redirecting: $scheme://${feature.host}${call.request.origin.uri}" }
        }
      }
      return feature
    }
  }
}
