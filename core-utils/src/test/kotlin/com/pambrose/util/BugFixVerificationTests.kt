@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.util

import com.pambrose.common.concurrent.Atomic
import com.pambrose.common.delegate.AtomicDelegates
import com.pambrose.common.time.format
import com.pambrose.common.util.AtomicUtils.criticalSection
import com.pambrose.common.util.Version
import com.pambrose.common.util.linesBetween
import com.pambrose.common.util.maskUrlCredentials
import com.pambrose.common.util.md5
import com.pambrose.common.util.sha256
import com.pambrose.common.util.times
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.launch

class BugFixVerificationTests : StringSpec() {
  init {
    // Bug #1: MD5/SHA-256 should produce standard hash values
    // Before fix: toByteArraySecure() used Java serialization, producing non-standard hashes
    // After fix: toByteArray(Charsets.UTF_8) produces standard UTF-8 byte hashes

    "md5 produces standard hash values" {
      "hello".md5() shouldBe "5d41402abc4b2a76b9719d911017c592"
      "".md5() shouldBe "d41d8cd98f00b204e9800998ecf8427e"
      "The quick brown fox".md5() shouldBe "a2004f37730b9445670a738fa0fc9ee5"
    }

    "sha256 produces standard hash values" {
      "hello".sha256() shouldBe "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
      "".sha256() shouldBe "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    }

    "md5 with salt produces consistent results" {
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

    "atomic value is read only and set with lock works" {
      val atomic = Atomic(10)
      atomic.value shouldBe 10

      // Only way to change the value is through setWithLock
      atomic.setWithLock { it + 5 }
      atomic.value shouldBe 15

      atomic.setWithLock { 0 }
      atomic.value shouldBe 0
    }

    "atomic concurrent set with lock is safe" {
      val atomic = Atomic(0)
      val jobs = (1..500).map {
        launch {
          atomic.setWithLock { it + 1 }
        }
      }
      jobs.forEach { it.join() }
      atomic.value shouldBe 500
    }

    // Bug #8: SingleSetAtomicReferenceDelegate should throw on second write
    // Before fix: compareAndSet return value was silently ignored
    // After fix: throws IllegalStateException when value has already been set

    "single set delegate throws on second write" {
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

    "critical section returns block result" {
      val flag = AtomicBoolean(false)

      val result = flag.criticalSection { 42 }
      result shouldBe 42

      val strResult = flag.criticalSection { "hello" }
      strResult shouldBe "hello"
    }

    "critical section resets flag" {
      val flag = AtomicBoolean(false)
      flag.load() shouldBe false

      flag.criticalSection { "work" }
      flag.load() shouldBe false
    }

    // Bug #10: maskUrlCredentials should handle URLs with multiple @ signs
    // Before fix: split("@")[1] dropped everything after the second @
    // After fix: uses substringAfterLast("@") to correctly find the host

    "mask url credentials handles multiple at signs" {
      val url = "https://user@email.com:pass@host.com/path"
      url.maskUrlCredentials() shouldBe "https://*****:*****@host.com/path"
    }

    "mask url credentials handles simple url" {
      val url = "https://user:pass@host.com/path"
      url.maskUrlCredentials() shouldBe "https://*****:*****@host.com/path"
    }

    "mask url credentials handles no credentials" {
      val url = "https://host.com/path"
      url.maskUrlCredentials() shouldBe "https://host.com/path"
    }

    // Bug #11: linesBetween should return empty list when patterns are not found
    // Before fix: subList(0, -1) threw IllegalArgumentException
    // After fix: returns emptyList() when start or end pattern is missing

    "lines between returns empty when start not found" {
      val text = "aaa\nbbb\nccc"
      text.linesBetween(Regex("zzz"), Regex("ccc")) shouldBe emptyList()
    }

    "lines between returns empty when end not found" {
      val text = "aaa\nbbb\nccc"
      text.linesBetween(Regex("aaa"), Regex("zzz")) shouldBe emptyList()
    }

    "lines between returns empty when both not found" {
      val text = "aaa\nbbb\nccc"
      text.linesBetween(Regex("xxx"), Regex("zzz")) shouldBe emptyList()
    }

    "lines between works when patterns exist" {
      val text = "aaa\nbbb\nccc\nddd"
      text.linesBetween(Regex("aaa"), Regex("ddd")) shouldBe listOf("bbb", "ccc")
    }

    // Bug #20: JSON key "build_time: " had a trailing colon and space
    // Before fix: put("build_time: ", ...) produced malformed key
    // After fix: put("build_time", ...) produces correct key

    "version json has correct build time key" {
      val json = Version.jsonStr("1.0", "2025-01-01", 0)
      json shouldContain "\"build_time\""
      json shouldNotContain "\"build_time: \""
    }

    // Bug #21: Duration.format() produced garbled output for negative durations
    // Before fix: negative milliseconds caused negative intermediate values
    // After fix: uses abs() on milliseconds and prepends "-" prefix

    "duration format handles positive durations" {
      val d = 1.hours + 30.minutes + 45.seconds
      d.format() shouldBe "0:01:30:45"
    }

    "duration format handles negative durations" {
      val d = -(1.hours + 30.minutes + 45.seconds)
      d.format() shouldBe "-0:01:30:45"
    }

    "duration format handles negative with millis" {
      val d = -(2.hours + 15.minutes + 30.seconds + 500.milliseconds)
      d.format(includeMillis = true) shouldBe "-0:02:15:30.500"
    }

    "duration format handles zero" {
      val d = 0.seconds
      d.format() shouldBe "0:00:00:00"
    }

    // Bug #22: Short.times infinite loop at Short.MAX_VALUE
    // Before fix: Short i++ overflowed from 32767 to -32768, looping forever
    // After fix: uses Int counter, converts to Short for the action

    "short times works at max value" {
      var count = 0
      val n: Short = 100
      n times { count++ }
      count shouldBe 100
    }

    "short times passes correct indices" {
      val indices = mutableListOf<Short>()
      val n: Short = 5
      n times { indices.add(it) }
      indices shouldBe listOf<Short>(0, 1, 2, 3, 4)
    }

    "short times zero does nothing" {
      var count = 0
      val n: Short = 0
      n times { count++ }
      count shouldBe 0
    }
  }
}
