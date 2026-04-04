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

package com.pambrose.common.redis

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldNotBe

class BugFixVerificationTests : StringSpec() {
  init {
    // Bug #4: urlDetails() threw IndexOutOfBoundsException when userInfo had no colon
    // Before fix: split(colon, 2).get(1) crashed on single-element list
    // After fix: getOrElse(1) { "" } returns empty string when index is missing

    "redis client creation with user info without colon does not crash" {
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

    "redis client creation with no user info does not crash" {
      shouldNotThrow<Exception> {
        val client = RedisUtils.newRedisClient(
          redisUrl = "redis://localhost:6379",
          maxPoolSize = 1,
        )
        client shouldNotBe null
        client.close()
      }
    }

    "redis client creation with standard user info works" {
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
}
