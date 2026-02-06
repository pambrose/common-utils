@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.github.pambrose.common.redis

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

class BugFixVerificationTests {
  // Bug #4: urlDetails() threw IndexOutOfBoundsException when userInfo had no colon
  // Before fix: split(colon, 2).get(1) crashed on single-element list
  // After fix: getOrElse(1) { "" } returns empty string when index is missing

  @Test
  fun jedisPoolCreationWithUserInfoWithoutColonDoesNotCrash() {
    // URL with username only (no colon/password in userInfo)
    shouldNotThrow<IndexOutOfBoundsException> {
      val pool = RedisUtils.newJedisPool(
        redisUrl = "redis://username@localhost:6379",
        maxPoolSize = 1,
      )
      pool shouldNotBe null
      pool.close()
    }
  }

  @Test
  fun jedisPoolCreationWithNoUserInfoDoesNotCrash() {
    shouldNotThrow<Exception> {
      val pool = RedisUtils.newJedisPool(
        redisUrl = "redis://localhost:6379",
        maxPoolSize = 1,
      )
      pool shouldNotBe null
      pool.close()
    }
  }

  @Test
  fun jedisPoolCreationWithStandardUserInfoWorks() {
    shouldNotThrow<Exception> {
      val pool = RedisUtils.newJedisPool(
        redisUrl = "redis://user:password@localhost:6379",
        maxPoolSize = 1,
      )
      pool shouldNotBe null
      pool.close()
    }
  }
}
