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

@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.github.pambrose.common.service

data class MetricsConfig(
  val enabled: Boolean,
  val port: Int,
  val path: String,
  val standardExportsEnabled: Boolean,
  val memoryPoolsExportsEnabled: Boolean,
  val garbageCollectorExportsEnabled: Boolean,
  val threadExportsEnabled: Boolean,
  val classLoadingExportsEnabled: Boolean,
  val versionInfoExportsEnabled: Boolean
)


