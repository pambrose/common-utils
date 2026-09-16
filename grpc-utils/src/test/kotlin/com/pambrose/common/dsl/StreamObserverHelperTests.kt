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

package com.pambrose.common.dsl

import io.grpc.CallOptions
import io.grpc.MethodDescriptor
import io.grpc.ServerServiceDefinition
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.ClientCalls
import io.grpc.stub.ServerCalls
import io.grpc.stub.StreamObserver
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.reflect.full.functions

// Streams back each comma-separated element of the request, then fails with NOT_FOUND if the last one is "fail".
private val splitMethod: MethodDescriptor<String, String> =
  MethodDescriptor.newBuilder<String, String>()
    .setType(MethodDescriptor.MethodType.SERVER_STREAMING)
    .setFullMethodName(MethodDescriptor.generateFullMethodName("SplitService", "Split"))
    .setRequestMarshaller(stringMarshaller)
    .setResponseMarshaller(stringMarshaller)
    .build()

private fun splitService(): ServerServiceDefinition =
  ServerServiceDefinition.builder("SplitService")
    .addMethod(
      splitMethod,
      ServerCalls.asyncServerStreamingCall { request, responseObserver ->
        val parts = request.split(",")
        parts.filter { it != "fail" }.forEach { responseObserver.onNext(it) }
        if (parts.last() == "fail")
          responseObserver.onError(Status.NOT_FOUND.withDescription("no more parts").asRuntimeException())
        else
          responseObserver.onCompleted()
      },
    )
    .build()

// What a DSL-built observer saw during one server-streaming call over the in-process transport.
private class StreamOutcome {
  val values: MutableList<String> = CopyOnWriteArrayList()
  val errors: MutableList<Throwable> = CopyOnWriteArrayList()
  val completions: MutableList<Unit> = CopyOnWriteArrayList()
}

private fun streamSplit(request: String): StreamOutcome {
  val outcome = StreamOutcome()
  val finished = CountDownLatch(1)
  val serverName = InProcessServerBuilder.generateName()
  val server = GrpcDsl.server(inProcessServerName = serverName) { addService(splitService()) }.start()
  val channel = GrpcDsl.channel(inProcessServerName = serverName) {}
  try {
    val observer =
      GrpcDsl.streamObserver<String> {
        onNext { outcome.values += it }
        onError {
          outcome.errors += it
          finished.countDown()
        }
        onCompleted {
          outcome.completions += Unit
          finished.countDown()
        }
      }
    val call = channel.newCall(splitMethod, CallOptions.DEFAULT.withDeadlineAfter(10, TimeUnit.SECONDS))
    ClientCalls.asyncServerStreamingCall(call, request, observer)
    finished.await(10, TimeUnit.SECONDS) shouldBe true
  } finally {
    channel.shutdownNow()
    server.shutdownNow()
  }
  return outcome
}

class StreamObserverHelperTests : StringSpec() {
  init {
    "on next callback" {
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

    "on error callback" {
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

    "on completed callback" {
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

    "all callbacks" {
      val receivedValues: MutableList<String> = []
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

      receivedValues shouldBe ["first", "second"]
      errorReceived shouldBe null
      completedCalled shouldBe true
    }

    "an observer without callbacks ignores every event" {
      val observer = GrpcDsl.streamObserver<String> {}

      shouldNotThrowAny {
        observer.onNext("value")
        observer.onError(RuntimeException("error"))
        observer.onCompleted()
      }
    }

    "a server-streaming call delivers each element in order, then onCompleted" {
      val outcome = streamSplit("a,b,c")

      outcome.values shouldBe ["a", "b", "c"]
      outcome.completions.size shouldBe 1
      outcome.errors.shouldBeEmpty()
    }

    "a failed server-streaming call delivers the elements sent before the failure, then its status to onError" {
      val outcome = streamSplit("a,b,fail")

      outcome.values shouldBe ["a", "b"]
      outcome.completions.shouldBeEmpty()
      outcome.errors.size shouldBe 1
      val status = outcome.errors.single().shouldBeInstanceOf<StatusRuntimeException>().status
      status.code shouldBe Status.Code.NOT_FOUND
      status.description shouldBe "no more parts"
    }

    // The DSL hands back a StreamObserver; the helper class is an implementation detail callers should not
    // have to name.
    "streamObserver is declared to return StreamObserver, not the helper type" {
      val returnType =
        GrpcDsl::class
          .functions
          .single { it.name == "streamObserver" }
          .returnType

      returnType.classifier shouldBe StreamObserver::class
    }

    // Each callback is single-assignment, which the KDoc now states outright.
    "registering the same callback twice fails" {
      val registrations: List<GrpcDsl.StreamObserverHelper<String>.() -> Unit> =
        [
          {
            onNext { }
            onNext { }
          },
          {
            onError { }
            onError { }
          },
          {
            onCompleted { }
            onCompleted { }
          },
        ]
      registrations.forEach { registerTwice ->
        val exception = shouldThrow<IllegalStateException> { GrpcDsl.streamObserver(registerTwice) }
        exception.message shouldContain "cannot be assigned more than once"
      }
    }
  }
}
