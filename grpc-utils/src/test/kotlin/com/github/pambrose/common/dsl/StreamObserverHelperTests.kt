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

@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.github.pambrose.common.dsl

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class StreamObserverHelperTests {
  @Test
  fun onNextCallbackTest() {
    var receivedValue: String? = null

    val observer =
      GrpcDsl.streamObserver<String> {
        onNext { value ->
          receivedValue = value
        }
      }

    observer.onNext("test value")
    receivedValue shouldBe "test value"
  }

  @Test
  fun onErrorCallbackTest() {
    var receivedError: Throwable? = null

    val observer =
      GrpcDsl.streamObserver<String> {
        onError { error ->
          receivedError = error
        }
      }

    val testException = RuntimeException("test error")
    observer.onError(testException)
    receivedError shouldBe testException
  }

  @Test
  fun onCompletedCallbackTest() {
    var completedCalled = false

    val observer =
      GrpcDsl.streamObserver<String> {
        onCompleted {
          completedCalled = true
        }
      }

    observer.onCompleted()
    completedCalled shouldBe true
  }

  @Test
  fun allCallbacksTest() {
    val receivedValues = mutableListOf<String>()
    var errorReceived: Throwable? = null
    var completedCalled = false

    val observer =
      GrpcDsl.streamObserver<String> {
        onNext { value ->
          receivedValues.add(value)
        }
        onError { error ->
          errorReceived = error
        }
        onCompleted {
          completedCalled = true
        }
      }

    observer.onNext("first")
    observer.onNext("second")
    observer.onCompleted()

    receivedValues shouldBe listOf("first", "second")
    errorReceived shouldBe null
    completedCalled shouldBe true
  }

  @Test
  fun noCallbacksSetTest() {
    // Should not throw when callbacks are not set
    val observer = GrpcDsl.streamObserver<String> {}

    observer.onNext("value")
    observer.onError(RuntimeException("error"))
    observer.onCompleted()
    // No exception means test passes
  }
}
