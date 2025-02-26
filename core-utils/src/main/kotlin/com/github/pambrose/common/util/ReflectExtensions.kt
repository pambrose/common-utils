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

package com.github.pambrose.common.util

import java.lang.reflect.ParameterizedType
import kotlin.reflect.full.isSubclassOf

val Any.typeParameterCount: Int
  get() =
    when {
      this is Array<*> -> 1 // Must come first in evaluation
      !javaClass.genericSuperclass.javaClass.kotlin.isSubclassOf(ParameterizedType::class) -> 0
      else -> (javaClass.genericSuperclass as ParameterizedType).actualTypeArguments.size
    }
