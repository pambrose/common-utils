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
@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.servlet

/**
 * A [LambdaServlet] that responds to HTTP GET requests with a fixed plain-text version string.
 *
 * Responses include `Cache-Control: must-revalidate,no-cache,no-store` headers and are encoded as UTF-8.
 *
 * @param version the version string to return in the response body.
 */
class VersionServlet(
  version: String,
) : LambdaServlet({ version }) {
  companion object {
    private const val serialVersionUID = -9115048679370256251L
  }
}
