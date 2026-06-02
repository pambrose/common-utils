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

package com.pambrose.util

import com.pambrose.common.util.unzip
import com.pambrose.common.util.zip
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ZipExtensionTests : StringSpec() {
  init {
    "short string zip test" {
      val s = "kjwkjfhwekfjhwwewewerrr\nwdfwefwefwef\n"
      s.zip().unzip() shouldBe s

      val t = "kjwkjfhwekfjhwwewewerrr\nwdfwefwefwef\n"
      t.zip().unzip() shouldBe t
    }

    "long string zip test" {
      val s =
        "kjwkjfhwekf cdsc  ##444445 wekfnkfn ew fwefwejfewkjfwef  qweqweqweqwe wef w ef wefwef ezzzzxdweere\n"
      val g = buildString { repeat(100_000) { append(s) } }
      g.zip().unzip() shouldBe g
    }

    "empty string zip test" {
      "".zip() shouldBe ByteArray(0)
      "".zip().unzip() shouldBe ""
      ByteArray(0).unzip() shouldBe ""
    }

    "byte array zip round-trips through unzip" {
      val s = "metric_value 42\nhttp_requests_total{code=\"200\"} 1027\n# EOF\n"
      s.toByteArray(Charsets.UTF_8).zip().unzip() shouldBe s
    }

    "byte array zip round-trips multi-byte UTF-8 content" {
      val s = "世界 metrics: éèê\n"
      s.toByteArray(Charsets.UTF_8).zip().unzip() shouldBe s
    }

    "byte array zip produces identical output to string zip for the same content" {
      val s = "kjwkjfhwekf cdsc ##444445 世界 wefwef\n".repeat(1000)
      // ByteArray.zip() must be a drop-in for String.zip() when fed the string's UTF-8 bytes.
      s.toByteArray(Charsets.UTF_8).zip().toList() shouldBe s.zip().toList()
    }

    "empty byte array zip test" {
      ByteArray(0).zip() shouldBe ByteArray(0)
      ByteArray(0).zip().unzip() shouldBe ""
    }
  }
}
