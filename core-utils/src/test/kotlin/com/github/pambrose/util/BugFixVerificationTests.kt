@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.github.pambrose.util

import com.github.pambrose.common.concurrent.Atomic
import com.github.pambrose.common.util.md5
import com.github.pambrose.common.util.sha256
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class BugFixVerificationTests {
  // Bug #1: MD5/SHA-256 should produce standard hash values
  // Before fix: toByteArraySecure() used Java serialization, producing non-standard hashes
  // After fix: toByteArray(Charsets.UTF_8) produces standard UTF-8 byte hashes

  @Test
  fun md5ProducesStandardHashValues() {
    "hello".md5() shouldBe "5d41402abc4b2a76b9719d911017c592"
    "".md5() shouldBe "d41d8cd98f00b204e9800998ecf8427e"
    "The quick brown fox".md5() shouldBe "a2004f37730b9445670a738fa0fc9ee5"
  }

  @Test
  fun sha256ProducesStandardHashValues() {
    "hello".sha256() shouldBe "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
    "".sha256() shouldBe "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
  }

  @Test
  fun md5WithSaltProducesConsistentResults() {
    val hash1 = "hello".md5("salt")
    val hash2 = "hello".md5("salt")
    hash1 shouldBe hash2

    // Different salt should produce different hash
    val hash3 = "hello".md5("different")
    (hash1 != hash3) shouldBe true
  }

  // Bug #2: Atomic.value should be read-only; mutation only via setWithLock
  // Before fix: value was a public var, bypassing the Mutex
  // After fix: value is a read-only val; _value is @PublishedApi internal

  @Test
  fun atomicValueIsReadOnlyAndSetWithLockWorks() {
    runBlocking {
      val atomic = Atomic(10)
      atomic.value shouldBe 10

      // Only way to change the value is through setWithLock
      atomic.setWithLock { it + 5 }
      atomic.value shouldBe 15

      atomic.setWithLock { 0 }
      atomic.value shouldBe 0
    }
  }

  @Test
  fun atomicConcurrentSetWithLockIsSafe() {
    runBlocking {
      val atomic = Atomic(0)
      val jobs = (1..500).map {
        launch {
          atomic.setWithLock { it + 1 }
        }
      }
      jobs.forEach { it.join() }
      atomic.value shouldBe 500
    }
  }
}
