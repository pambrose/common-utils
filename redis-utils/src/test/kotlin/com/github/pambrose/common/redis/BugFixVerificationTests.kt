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
  fun redisClientCreationWithUserInfoWithoutColonDoesNotCrash() {
    // URL with username only (no colon/password in userInfo)
    shouldNotThrow<IndexOutOfBoundsException> {
      val client = RedisUtils.newRedisClient(
        redisUrl = "redis://username@localhost:6379",
        maxPoolSize = 1,
      )
      client shouldNotBe null
      client.close()
    }
  }

  @Test
  fun redisClientCreationWithNoUserInfoDoesNotCrash() {
    shouldNotThrow<Exception> {
      val client = RedisUtils.newRedisClient(
        redisUrl = "redis://localhost:6379",
        maxPoolSize = 1,
      )
      client shouldNotBe null
      client.close()
    }
  }

  @Test
  fun redisClientCreationWithStandardUserInfoWorks() {
    shouldNotThrow<Exception> {
      val client = RedisUtils.newRedisClient(
        redisUrl = "redis://user:password@localhost:6379",
        maxPoolSize = 1,
      )
      client shouldNotBe null
      client.close()
    }
  }
}
