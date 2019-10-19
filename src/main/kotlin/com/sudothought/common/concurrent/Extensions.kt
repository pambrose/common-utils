/*
 *
 *  Copyright © 2019 Paul Ambrose (pambrose@mac.com)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 */

@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.sudothought.common.concurrent

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore

val CountDownLatch.isFinished: Boolean get() = count == 0L

fun CountDownLatch.countDown(block: () -> Unit) {
    try {
        block()
    } finally {
        countDown()
    }
}

fun <T> Semaphore.withLock(block: () -> T): T {
    acquire()
    return try {
        block()
    } finally {
        release()
    }
}