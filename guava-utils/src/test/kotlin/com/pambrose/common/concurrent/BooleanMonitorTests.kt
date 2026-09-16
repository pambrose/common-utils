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

package com.pambrose.common.concurrent

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.slf4j.LoggerFactory
import java.lang.reflect.Modifier
import kotlin.time.Duration.Companion.milliseconds

// Runs block with the BooleanMonitor logger set to DEBUG, and returns the events logged meanwhile as level to message.
// Setting the level keeps the result independent of logback's default configuration.
private fun capturingMonitorLogs(block: () -> Unit): List<Pair<Level, String>> {
  val appender = ListAppender<ILoggingEvent>().apply { start() }
  val logger = LoggerFactory.getLogger(BooleanMonitor::class.java) as Logger
  val savedLevel = logger.level
  logger.level = Level.DEBUG
  logger.addAppender(appender)
  try {
    block()
  } finally {
    logger.detachAppender(appender)
    logger.level = savedLevel
  }
  return appender.list.map { it.level to it.formattedMessage }
}

private val levelsByName = mapOf(
  "debug" to Level.DEBUG,
  "info" to Level.INFO,
  "warn" to Level.WARN,
  "error" to Level.ERROR,
)

// The message a factory argument, a string or a message lambda, is expected to log.
private fun Any.toMessage(): String = if (this is Function0<*>) this().toString() else toString()

class BooleanMonitorTests : StringSpec() {
  init {
    "boolean monitor initial value true" {
      val monitor = BooleanMonitor(true)
      monitor.get() shouldBe true
    }

    "boolean monitor initial value false" {
      val monitor = BooleanMonitor(false)
      monitor.get() shouldBe false
    }

    "boolean monitor set value" {
      val monitor = BooleanMonitor(false)
      monitor.get() shouldBe false

      monitor.set(true)
      monitor.get() shouldBe true

      monitor.set(false)
      monitor.get() shouldBe false
    }

    "boolean monitor to string" {
      val monitor = BooleanMonitor(true)
      monitor.toString() shouldContain "value=true"

      monitor.set(false)
      monitor.toString() shouldContain "value=false"
    }

    "boolean monitor wait until true timeout" {
      val monitor = BooleanMonitor(false)
      val result = monitor.waitUntilTrue(50.milliseconds)
      result shouldBe false
    }

    "boolean monitor wait until false timeout" {
      val monitor = BooleanMonitor(true)
      val result = monitor.waitUntilFalse(50.milliseconds)
      result shouldBe false
    }

    "boolean monitor wait until true already true" {
      val monitor = BooleanMonitor(true)
      val result = monitor.waitUntilTrue(50.milliseconds)
      result shouldBe true
    }

    "boolean monitor wait until false already false" {
      val monitor = BooleanMonitor(false)
      val result = monitor.waitUntilFalse(50.milliseconds)
      result shouldBe true
    }

    "each log-action factory logs its message at its own level when the action runs" {
      val actions: List<MonitorAction> =
        [
          BooleanMonitor.debug { "dbg-lambda" },
          BooleanMonitor.debug("dbg-str"),
          BooleanMonitor.info { "info-lambda" },
          BooleanMonitor.info("info-str"),
          BooleanMonitor.warn { "warn-lambda" },
          BooleanMonitor.warn("warn-str"),
          BooleanMonitor.error { "err-lambda" },
          BooleanMonitor.error("err-str"),
        ]

      // Creating an action logs nothing; only running it does.
      capturingMonitorLogs { actions.forEach { it() shouldBe true } } shouldBe
        [
          Level.DEBUG to "dbg-lambda",
          Level.DEBUG to "dbg-str",
          Level.INFO to "info-lambda",
          Level.INFO to "info-str",
          Level.WARN to "warn-lambda",
          Level.WARN to "warn-str",
          Level.ERROR to "err-lambda",
          Level.ERROR to "err-str",
        ]
    }

    "a lambda message is not evaluated when its level is disabled" {
      var evaluated = false
      val action =
        BooleanMonitor.debug {
          evaluated = true
          "never built"
        }

      val logger = LoggerFactory.getLogger(BooleanMonitor::class.java) as Logger
      val savedLevel = logger.level
      logger.level = Level.INFO
      try {
        action() shouldBe true
      } finally {
        logger.level = savedLevel
      }
      evaluated shouldBe false
    }

    // Java callers reach the factories through the @JvmStatic bridges on BooleanMonitor itself, which Kotlin
    // code never calls. The ABI dump pins that the bridges exist; this checks that they work.
    "BooleanMonitor log-action factories are static methods for Java callers" {
      val arguments: List<Pair<Class<*>, Any>> = [String::class.java to "str", Function0::class.java to { "lambda" }]
      for ((name, level) in levelsByName) {
        for ((type, argument) in arguments) {
          val bridge = BooleanMonitor::class.java.getMethod(name, type)
          Modifier.isStatic(bridge.modifiers) shouldBe true
          @Suppress("UNCHECKED_CAST")
          val action = bridge.invoke(null, argument) as MonitorAction
          capturingMonitorLogs { action() shouldBe true } shouldBe [level to argument.toMessage()]
        }
      }
    }
  }
}
