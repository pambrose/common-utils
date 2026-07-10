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
@file:JvmName("StringExtensionsKt")
@file:JvmMultifileClass

package com.pambrose.common.util

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.security.SecureRandom

/** URL-decodes this [String] using UTF-8 encoding. */
fun String.decode(): String = URLDecoder.decode(this, UTF_8)

/** URL-encodes this [String] using UTF-8 encoding. */
fun String.encode(): String = URLEncoder.encode(this, UTF_8)

/**
 * Computes the MD5 hash of this [String] with an optional string [salt].
 *
 * @param salt the salt string (default empty)
 * @return the hex-encoded MD5 hash
 */
fun String.md5(salt: String = ""): String = encodedByteArray(this, { salt }, "MD5").asText

/**
 * Computes the SHA-256 hash of this [String] with an optional string [salt].
 *
 * @param salt the salt string (default empty)
 * @return the hex-encoded SHA-256 hash
 */
fun String.sha256(salt: String = ""): String = encodedByteArray(this, { salt }, "SHA-256").asText

/**
 * Computes the MD5 hash of this [String] with a byte array [salt].
 *
 * @param salt the salt bytes
 * @return the hex-encoded MD5 hash
 */
fun String.md5(salt: ByteArray): String = encodedByteArray(this, salt, "MD5").asText

/**
 * Computes the SHA-256 hash of this [String] with a byte array [salt].
 *
 * @param salt the salt bytes
 * @return the hex-encoded SHA-256 hash
 */
fun String.sha256(salt: ByteArray): String = encodedByteArray(this, salt, "SHA-256").asText

/**
 * Converts this [ByteArray] to a lowercase hex string.
 *
 * Extension property on [ByteArray].
 */
@Suppress("ImplicitDefaultLocale")
val ByteArray.asText get() = joinToString("") { "%02x".format(it) }

private fun encodedByteArray(
  input: String,
  salt: ByteArray,
  algorithm: String,
) = with(MessageDigest.getInstance(algorithm)) {
  update(salt)
  digest(input.toByteArray(Charsets.UTF_8))
}

private fun encodedByteArray(
  input: String,
  salt: (String) -> String,
  algorithm: String,
) = with(MessageDigest.getInstance(algorithm)) {
  update(salt(input).toByteArray(Charsets.UTF_8))
  digest(input.toByteArray(Charsets.UTF_8))
}

/**
 * Generates a cryptographically secure random byte array for use as a salt.
 *
 * @param len the length of the salt in bytes (default 16)
 * @return a random [ByteArray]
 */
fun newByteArraySalt(len: Int = 16): ByteArray = ByteArray(len).apply { SecureRandom().nextBytes(this) }

/**
 * Generates a cryptographically secure random string for use as a salt.
 *
 * @param len the length of the salt string (default 16)
 * @return a random alphanumeric string
 */
fun newStringSalt(len: Int = 16): String = randomId(len)

/**
 * Computes an MD5 hash of the given [keys] joined by [separator].
 *
 * @param keys the values to hash
 * @param separator the separator between key values (default `"|"`)
 * @return the hex-encoded MD5 hash
 */
fun md5Of(
  vararg keys: Any,
  separator: String = "|",
) = keys.joinToString(separator) { it.toString() }.md5()
