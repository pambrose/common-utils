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
 * Configuration data for administrative HTTP endpoints exposed by [GenericService] and [GenericKtorService].
 *
 * Controls whether the admin servlet group is enabled and defines the port and URL paths
 * for standard operational endpoints (ping, version, health check, thread dump).
 *
 * @property enabled Whether the admin endpoints are enabled.
 * @property port The HTTP port on which admin servlets are served.
 * @property pingPath The URL path for the ping (liveness) endpoint.
 * @property versionPath The URL path for the version information endpoint.
 * @property healthCheckPath The URL path for the health check endpoint.
 * @property threadDumpPath The URL path for the thread dump endpoint.
 */
data class AdminConfig(
  val enabled: Boolean,
  val port: Int,
  val pingPath: String,
  val versionPath: String,
  val healthCheckPath: String,
  val threadDumpPath: String,
)
