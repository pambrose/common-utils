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

package com.pambrose.common.service

/**
 * Configuration data for connecting to a Zipkin distributed tracing server.
 *
 * Used by [ZipkinReporterService] to construct the reporter URL and identify the service in traces.
 *
 * @property enabled Whether Zipkin tracing is enabled.
 * @property hostname The hostname of the Zipkin server.
 * @property port The port of the Zipkin server.
 * @property path The URL path for the Zipkin API endpoint.
 * @property serviceName The name used to identify this service in Zipkin traces.
 */
data class ZipkinConfig(
  val enabled: Boolean,
  val hostname: String,
  val port: Int,
  val path: String,
  val serviceName: String,
)
