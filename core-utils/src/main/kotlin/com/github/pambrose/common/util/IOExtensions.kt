/*
 * Copyright © 2025 Paul Ambrose (pambrose@mac.com)
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

@file:JvmName("IOUtils")
@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.github.pambrose.common.util

import java.io.*

@Throws(IOException::class)
fun Serializable.toByteArray(): ByteArray =
  ByteArrayOutputStream()
    .use { baos ->
      ObjectOutputStream(baos).use { oos -> oos.writeObject(this) }
      baos.flush()
      baos.toByteArray()
    }

@Throws(IOException::class, ClassNotFoundException::class)
fun ByteArray.toObject(): Serializable =
  ByteArrayInputStream(this)
    .use { bais ->
      ObjectInputStream(bais)
        .use { ois -> ois.readObject() as Serializable }
    }
