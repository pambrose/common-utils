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

package com.pambrose.util

import com.pambrose.common.util.readProperties
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import java.nio.file.Files

// Keys created by these tests are all prefixed with KEY_PREFIX so afterSpec can
// reliably remove them and avoid leaking state into other specs in the JVM.
private const val KEY_PREFIX = "com.pambrose.test.propfunc."

private fun writePropsFile(content: String): File =
  Files
    .createTempFile("property-functions-test", ".properties")
    .toFile()
    .apply {
      writeText(content)
      deleteOnExit()
    }

class PropertyFunctionsTests : StringSpec() {
  init {
    afterSpec {
      System
        .getProperties()
        .stringPropertyNames()
        .filter { name -> name.contains(KEY_PREFIX) }
        .forEach { name -> System.clearProperty(name) }
    }

    "readProperties(List) sets system properties from a file" {
      val file =
        writePropsFile(
          """
          ${KEY_PREFIX}host=localhost
          ${KEY_PREFIX}port=8080
          """.trimIndent(),
        )

      readProperties(listOf(file.absolutePath))

      System.getProperty("${KEY_PREFIX}host") shouldBe "localhost"
      System.getProperty("${KEY_PREFIX}port") shouldBe "8080"
    }

    "readProperties(vararg) sets system properties from a file" {
      val file = writePropsFile("${KEY_PREFIX}vararg=set")

      readProperties(file.absolutePath)

      System.getProperty("${KEY_PREFIX}vararg") shouldBe "set"
    }

    "readProperties trims whitespace around keys and values" {
      val file = writePropsFile("   ${KEY_PREFIX}name   =    Paul Ambrose   ")

      readProperties(listOf(file.absolutePath))

      System.getProperty("${KEY_PREFIX}name") shouldBe "Paul Ambrose"
    }

    "readProperties only splits on the first '=' so values may contain '='" {
      val url = "jdbc:mysql://host:3306/db?user=admin&ssl=true"
      val file = writePropsFile("${KEY_PREFIX}url=$url")

      readProperties(listOf(file.absolutePath))

      System.getProperty("${KEY_PREFIX}url") shouldBe url
    }

    "readProperties supports keys with an empty value" {
      val file = writePropsFile("${KEY_PREFIX}empty=")

      readProperties(listOf(file.absolutePath))

      System.getProperty("${KEY_PREFIX}empty") shouldBe ""
    }

    "readProperties skips comment lines starting with '#'" {
      val file =
        writePropsFile(
          """
          #${KEY_PREFIX}comment=should-not-be-set
          ${KEY_PREFIX}real=value
          """.trimIndent(),
        )

      readProperties(listOf(file.absolutePath))

      System.getProperty("${KEY_PREFIX}comment") shouldBe null
      System.getProperty("${KEY_PREFIX}real") shouldBe "value"
    }

    "readProperties skips lines without an '=' separator" {
      val file =
        writePropsFile(
          """
          this line has no separator
          ${KEY_PREFIX}kept=yes

          """.trimIndent(),
        )

      readProperties(listOf(file.absolutePath))

      System.getProperty("${KEY_PREFIX}kept") shouldBe "yes"
    }

    "readProperties processes every file passed to it" {
      val first = writePropsFile("${KEY_PREFIX}first=1")
      val second = writePropsFile("${KEY_PREFIX}second=2")

      readProperties(first.absolutePath, second.absolutePath)

      System.getProperty("${KEY_PREFIX}first") shouldBe "1"
      System.getProperty("${KEY_PREFIX}second") shouldBe "2"
    }

    "readProperties throws when a file does not exist" {
      val missing = File("does-not-exist-${KEY_PREFIX}.properties")

      val exception =
        shouldThrow<IllegalStateException> {
          readProperties(listOf(missing.path))
        }
      exception.message shouldContain "File not found"
      exception.message shouldContain missing.absolutePath
    }

    "readProperties is a no-op when given no files" {
      shouldNotThrowAny {
        readProperties()
        readProperties(emptyList())
      }
    }
  }
}
