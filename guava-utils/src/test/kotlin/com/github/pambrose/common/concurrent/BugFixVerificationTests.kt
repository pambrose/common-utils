@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.github.pambrose.common.concurrent

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class BugFixVerificationTests {
  // Bug #5: Lambda-based logging methods logged the lambda object, not its result
  // Before fix: logger.debug { msg } logged the lambda's toString()
  // After fix: logger.debug { msg() } invokes the lambda to get the message

  @Test
  fun debugLambdaIsInvoked() {
    var invoked = false
    val action = BooleanMonitor.debug {
      invoked = true
      "debug message"
    }
    action()
    invoked shouldBe true
  }

  @Test
  fun infoLambdaIsInvoked() {
    var invoked = false
    val action = BooleanMonitor.info {
      invoked = true
      "info message"
    }
    action()
    invoked shouldBe true
  }

  @Test
  fun warnLambdaIsInvoked() {
    var invoked = false
    val action = BooleanMonitor.warn {
      invoked = true
      "warn message"
    }
    action()
    invoked shouldBe true
  }

  @Test
  fun errorLambdaIsInvoked() {
    var invoked = false
    val action = BooleanMonitor.error {
      invoked = true
      "error message"
    }
    action()
    invoked shouldBe true
  }
}
