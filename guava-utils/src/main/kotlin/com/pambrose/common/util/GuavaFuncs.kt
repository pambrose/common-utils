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

package com.pambrose.common.util

import com.google.common.base.StandardSystemProperty

/** Whether the current operating system is Windows. */
val isWindows by lazy { StandardSystemProperty.OS_NAME.value().orEmpty().contains("Windows") }

/** Whether the current operating system is Mac OS X. */
val isMac by lazy { StandardSystemProperty.OS_NAME.value().orEmpty().contains("Mac OS X") }

/** Whether the current JVM version is Java 6. */
val isJava6 by lazy { StandardSystemProperty.JAVA_VERSION.value().orEmpty().startsWith("1.6") }
