@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.github.pambrose.util

import com.github.pambrose.common.concurrent.Atomic
import com.github.pambrose.common.delegate.AtomicDelegates
import com.github.pambrose.common.util.AtomicUtils.criticalSection
import com.github.pambrose.common.util.linesBetween
import com.github.pambrose.common.util.maskUrlCredentials
import com.github.pambrose.common.util.md5
import com.github.pambrose.common.util.sha256
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.concurrent.atomics.AtomicBoolean
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

  // Bug #8: SingleSetAtomicReferenceDelegate should throw on second write
  // Before fix: compareAndSet return value was silently ignored
  // After fix: throws IllegalStateException when value has already been set

  @Test
  fun singleSetDelegateThrowsOnSecondWrite() {
    var value: String? by AtomicDelegates.singleSetReference<String>()
    value shouldBe null

    value = "first"
    value shouldBe "first"

    shouldThrow<IllegalStateException> {
      value = "second"
    }
    value shouldBe "first"
  }

  // Bug #9: criticalSection should return the block's result
  // Before fix: block's return value was discarded, function returned Unit
  // After fix: function returns T (the block's result)

  @Test
  fun criticalSectionReturnsBlockResult() {
    val flag = AtomicBoolean(false)

    val result = flag.criticalSection { 42 }
    result shouldBe 42

    val strResult = flag.criticalSection { "hello" }
    strResult shouldBe "hello"
  }

  @Test
  fun criticalSectionResetsFlag() {
    val flag = AtomicBoolean(false)
    flag.load() shouldBe false

    flag.criticalSection { "work" }
    flag.load() shouldBe false
  }

  // Bug #10: maskUrlCredentials should handle URLs with multiple @ signs
  // Before fix: split("@")[1] dropped everything after the second @
  // After fix: uses substringAfterLast("@") to correctly find the host

  @Test
  fun maskUrlCredentialsHandlesMultipleAtSigns() {
    val url = "https://user@email.com:pass@host.com/path"
    url.maskUrlCredentials() shouldBe "https://*****:*****@host.com/path"
  }

  @Test
  fun maskUrlCredentialsHandlesSimpleUrl() {
    val url = "https://user:pass@host.com/path"
    url.maskUrlCredentials() shouldBe "https://*****:*****@host.com/path"
  }

  @Test
  fun maskUrlCredentialsHandlesNoCredentials() {
    val url = "https://host.com/path"
    url.maskUrlCredentials() shouldBe "https://host.com/path"
  }

  // Bug #11: linesBetween should return empty list when patterns are not found
  // Before fix: subList(0, -1) threw IllegalArgumentException
  // After fix: returns emptyList() when start or end pattern is missing

  @Test
  fun linesBetweenReturnsEmptyWhenStartNotFound() {
    val text = "aaa\nbbb\nccc"
    text.linesBetween(Regex("zzz"), Regex("ccc")) shouldBe emptyList()
  }

  @Test
  fun linesBetweenReturnsEmptyWhenEndNotFound() {
    val text = "aaa\nbbb\nccc"
    text.linesBetween(Regex("aaa"), Regex("zzz")) shouldBe emptyList()
  }

  @Test
  fun linesBetweenReturnsEmptyWhenBothNotFound() {
    val text = "aaa\nbbb\nccc"
    text.linesBetween(Regex("xxx"), Regex("zzz")) shouldBe emptyList()
  }

  @Test
  fun linesBetweenWorksWhenPatternsExist() {
    val text = "aaa\nbbb\nccc\nddd"
    text.linesBetween(Regex("aaa"), Regex("ddd")) shouldBe listOf("bbb", "ccc")
  }
}
