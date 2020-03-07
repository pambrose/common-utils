package com.github.pambrose.util

import com.github.pambrose.common.util.unzip
import com.github.pambrose.common.util.zip
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

class ZipExtensionTests {
  @Test
  fun shortStringZipTest() {
    val s = "kjwkjfhwekfjhwwewewerrr\nwdfwefwefwef\n"
    s.zip().unzip() shouldBeEqualTo s

    val t = "kjwkjfhwekfjhwwewewerrr\nwdfwefwefwef\n"
    t.zip().unzip() shouldBeEqualTo t
  }

  @Test
  fun longStringZipTest() {
    val s =
      "kjwkjfhwekfjhwwewewerrr cdsc  ##444445 wekfnkfn ew fwefwejfewkjfwef  qweqweqweqwe wef wef w ef wefwef ezzzzxdweere\n"
    val builder = StringBuilder()
    repeat(100_000) { builder.append(s) }
    val g = builder.toString()
    g.zip().unzip() shouldBeEqualTo g
  }

  @Test
  fun empyStringZipTest() {
    "".zip() shouldBeEqualTo ByteArray(0)
    "".zip().unzip() shouldBeEqualTo ""
    ByteArray(0).unzip() shouldBeEqualTo ""
  }
}