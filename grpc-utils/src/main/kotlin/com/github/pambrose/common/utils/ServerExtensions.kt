package com.github.pambrose.common.utils

import io.grpc.Server
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

fun Server.shutdownWithJvm(maxWaitTime: Duration) {
  Runtime.getRuntime().addShutdownHook(Thread(Runnable {
    try {
      shutdownGracefully(maxWaitTime)
    } catch (e: InterruptedException) {
      // do nothing
    }
  }))
}

@Throws(InterruptedException::class)
fun Server.shutdownGracefully(maxWaitTime: Duration) =
  shutdownGracefully(maxWaitTime.toLongMilliseconds(), TimeUnit.MILLISECONDS)

@Throws(InterruptedException::class)
fun Server.shutdownGracefully(timeout: Long, unit: TimeUnit) {
  require(timeout > 0) { "timeout must be greater than 0" }
  shutdown()
  try {
    awaitTermination(timeout, unit)
  } finally {
    shutdownNow()
  }
}