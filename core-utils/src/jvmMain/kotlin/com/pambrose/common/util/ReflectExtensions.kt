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

package com.pambrose.common.util

/**
 * Returns the number of generic type parameters declared by this object's class.
 *
 * Object arrays (`Array<T>`) return 1; primitive arrays such as [IntArray] and non-generic classes return 0.
 * Only the runtime class's own type parameters count: a generic class that extends `Object`, such as [Pair],
 * reports its parameters, while a non-generic subclass of a generic class, such as `java.util.Properties`
 * (which extends `Hashtable<Object, Object>`), reports 0.
 *
 * Extension property on [Any].
 */
val Any.typeParameterCount: Int
  // Array classes declare no type parameters of their own, so Array<T> is special-cased.
  get() = if (this is Array<*>) 1 else javaClass.typeParameters.size
