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

package com.pambrose.common.util

import java.io.File
import java.net.URI
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.toKotlinDuration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

/**
 * Root abstraction for a content location, either a local directory or a remote repository.
 */
interface ContentRoot {
  /** The prefix path or URL used to resolve files within this root. */
  val sourcePrefix: String

  /** Whether this root points to a remote location. */
  val remote: Boolean

  /**
   * Creates a [ContentSource] for [path] within this root.
   *
   * A relative [path] is resolved against the root: against [FileSystemSource.pathPrefix] on the local file
   * system, and against [AbstractRepo.rawSourcePrefix] for repositories, where the path begins with the
   * branch name (e.g. `"main/src/App.kt"`). An absolute file path, or for repositories a full URL (one that starts with
   * a scheme such as `https://`), is used unchanged.
   *
   * No containment is applied: `..` segments and absolute paths escape a [FileSystemSource]'s directory, and a
   * repository reads any full URL, including `file:` URLs and internal hosts. Validate a path that comes from user
   * input before passing it here.
   *
   * @param path the path to the file, relative to this root
   * @return a [ContentSource] that can be used to read the file's content
   */
  fun file(path: String): ContentSource
}

/**
 * A [ContentRoot] backed by the local file system.
 *
 * @param pathPrefix the base directory path used to resolve files
 */
class FileSystemSource(
  val pathPrefix: String,
) : ContentRoot {
  override val sourcePrefix = pathPrefix
  override val remote = false

  override fun file(path: String) = FileSource(File(pathPrefix).resolve(path).path)

  override fun toString() = "FileSystemSource(pathPrefix='$pathPrefix', sourcePrefix='$sourcePrefix')"
}

/**
 * Represents the type of owner for a Git repository.
 */
enum class OwnerType {
  User,
  Organization,
  ;

  /** Returns `true` if the owner is a [User]. */
  fun isUser() = this == User

  /** Returns `true` if the owner is an [Organization]. */
  fun isOrganization() = this == Organization
}

/**
 * Base class for Git repository content roots, providing URL construction for hosted repos.
 *
 * @param scheme the URL scheme (e.g., `"https://"`)
 * @param domainName the host domain (e.g., `"github.com"`)
 * @param ownerType whether the owner is a user or organization
 * @param ownerName the repository owner's name
 * @param repoName the repository name
 */
abstract class AbstractRepo(
  val scheme: String,
  val domainName: String,
  val ownerType: OwnerType,
  val ownerName: String,
  val repoName: String,
) : ContentRoot {
  override val sourcePrefix: String get() = scheme + [domainName, ownerName, repoName].join()
  override val remote = true

  /** The URL prefix used to access raw file content from this repository. */
  abstract val rawSourcePrefix: String

  override fun file(path: String) = UrlSource(resolve(path))

  /**
   * Creates a [UrlSource] for [path], as [file] does, with the given timeouts.
   *
   * @param path the path to the file, relative to this root, or a full URL
   * @param connectTimeout the maximum time to wait for a connection
   * @param readTimeout the maximum time to wait for data once connected
   */
  fun file(
    path: String,
    connectTimeout: Duration,
    readTimeout: Duration,
  ) = UrlSource(resolve(path), connectTimeout, readTimeout)

  // Only a leading scheme makes path a full URL: a "://" further in, as in a query string, is part of a relative path.
  private fun resolve(path: String) = if (URL_SCHEME.containsMatchIn(path)) path else [rawSourcePrefix, path].join()

  private companion object {
    val URL_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")
  }
}

private const val GITHUB = "github.com"
private val DEFAULT_CONNECT_TIMEOUT = 10.seconds
private val DEFAULT_READ_TIMEOUT = 30.seconds
private const val GITHUB_USER_CONTENT = "raw.githubusercontent.com"

/**
 * A [ContentRoot] representing a GitHub repository.
 *
 * Raw content for `github.com` is fetched from `raw.githubusercontent.com`. For any other domain (GitHub
 * Enterprise Server) it is fetched from the `HOSTNAME/raw/` path, the form used when subdomain isolation is
 * disabled; a server with subdomain isolation enabled serves raw content from `raw.HOSTNAME` instead, which this
 * class does not detect.
 *
 * @param ownerType whether the owner is a user or organization
 * @param ownerName the GitHub username or organization name
 * @param repoName the repository name
 * @param scheme the URL scheme, defaults to `"https://"`
 * @param domainName the GitHub domain, defaults to `"github.com"`
 */
class GitHubRepo(
  ownerType: OwnerType,
  ownerName: String,
  repoName: String,
  scheme: String = "https://",
  domainName: String = GITHUB,
) : AbstractRepo(scheme, domainName, ownerType, ownerName, repoName) {
  override val rawSourcePrefix =
    if (domainName == GITHUB)
      scheme + [GITHUB_USER_CONTENT, ownerName, repoName].join()
    else
      scheme + [domainName, "raw", ownerName, repoName].join()

  override fun toString() =
    "GitHubRepo(scheme='$scheme', domainName='$domainName', ownerName='$ownerName', repoName='$repoName', " +
      "rawSourcePrefix='$rawSourcePrefix')"
}

/**
 * A [ContentRoot] representing a GitLab repository.
 *
 * Raw content is fetched from the repository's `/-/raw/` path; `/-/blob/` is GitLab's HTML viewer page.
 *
 * @param ownerType whether the owner is a user or organization
 * @param ownerName the GitLab username or group name
 * @param repoName the repository name
 * @param scheme the URL scheme, defaults to `"https://"`
 * @param domainName the GitLab domain, defaults to `"gitlab.com"`
 */
class GitLabRepo(
  ownerType: OwnerType,
  ownerName: String,
  repoName: String,
  scheme: String = "https://",
  domainName: String = "gitlab.com",
) : AbstractRepo(scheme, domainName, ownerType, ownerName, repoName) {
  override val rawSourcePrefix = [sourcePrefix, "-/raw"].join()

  override fun toString() =
    "GitLabRepo(scheme='$scheme', domainName='$domainName', ownerName='$ownerName', repoName='$repoName', " +
      "rawSourcePrefix='$rawSourcePrefix')"
}

/**
 * Represents a readable content source, either local or remote.
 */
interface ContentSource {
  /** The path or URL identifying this content source. */
  val source: String

  /** The textual content read from this source. */
  val content: String

  /** Whether this source is remote (URL-based). */
  val remote: Boolean
}

/**
 * A [ContentSource] pointing to a specific file in a GitHub repository.
 *
 * Resolves [branchName], [srcPath], and [fileName] against the repository's [GitHubRepo.rawSourcePrefix].
 *
 * @param repo the GitHub repository
 * @param branchName the branch or tag name
 * @param srcPath the directory path within the repository
 * @param fileName the file name
 * @param connectTimeout the maximum time to wait for a connection (default 10 seconds)
 * @param readTimeout the maximum time to wait for data once connected (default 30 seconds)
 */
open class GitHubFile(
  val repo: GitHubRepo,
  val branchName: String,
  val srcPath: String,
  val fileName: String,
  connectTimeout: Duration = DEFAULT_CONNECT_TIMEOUT,
  readTimeout: Duration = DEFAULT_READ_TIMEOUT,
) : UrlSource([repo.rawSourcePrefix, branchName, srcPath, fileName].join(), connectTimeout, readTimeout) {
  /** Creates a [GitHubFile] with the default timeouts. */
  constructor(
    repo: GitHubRepo,
    branchName: String,
    srcPath: String,
    fileName: String,
  ) : this(repo, branchName, srcPath, fileName, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT)

  override fun toString() = "GitHubFile(repo=$repo, branchName='$branchName', srcPath='$srcPath', fileName='$fileName')"
}

/**
 * A [ContentSource] pointing to a specific file in a GitLab repository.
 *
 * Resolves [branchName], [srcPath], and [fileName] against the repository's [GitLabRepo.rawSourcePrefix].
 *
 * @param repo the GitLab repository
 * @param branchName the branch or tag name
 * @param srcPath the directory path within the repository
 * @param fileName the file name
 * @param connectTimeout the maximum time to wait for a connection (default 10 seconds)
 * @param readTimeout the maximum time to wait for data once connected (default 30 seconds)
 */
open class GitLabFile(
  val repo: GitLabRepo,
  val branchName: String,
  val srcPath: String,
  val fileName: String,
  connectTimeout: Duration = DEFAULT_CONNECT_TIMEOUT,
  readTimeout: Duration = DEFAULT_READ_TIMEOUT,
) : UrlSource([repo.rawSourcePrefix, branchName, srcPath, fileName].join(), connectTimeout, readTimeout) {
  /** Creates a [GitLabFile] with the default timeouts. */
  constructor(
    repo: GitLabRepo,
    branchName: String,
    srcPath: String,
    fileName: String,
  ) : this(repo, branchName, srcPath, fileName, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT)

  override fun toString() = "GitLabFile(repo=$repo, branchName='$branchName', srcPath='$srcPath', fileName='$fileName')"
}

/**
 * A [ContentSource] that reads content from a URL.
 *
 * [content] is fetched from [source] on every access; it is not cached.
 *
 * The timeouts are applied in whole milliseconds, as [java.net.URLConnection] requires. A positive timeout is
 * rounded up and capped at [Int.MAX_VALUE] milliseconds; [Duration.INFINITE] or [Duration.ZERO] means no
 * timeout.
 *
 * @param source the URL to read content from
 * @param connectTimeout the maximum time to wait for a connection (default 10 seconds)
 * @param readTimeout the maximum time to wait for data once connected (default 30 seconds)
 * @throws IllegalArgumentException if either timeout is negative.
 */
open class UrlSource(
  override val source: String,
  connectTimeout: Duration = DEFAULT_CONNECT_TIMEOUT,
  readTimeout: Duration = DEFAULT_READ_TIMEOUT,
) : ContentSource {
  /** Creates a [UrlSource] with the default timeouts. */
  constructor(source: String) : this(source, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT)

  /**
   * Creates a [UrlSource] with timeouts given as [java.time.Duration], for Java callers: the constructor taking
   * Kotlin durations cannot be called from Java.
   *
   * @param source the URL to read content from
   * @param connectTimeout the maximum time to wait for a connection
   * @param readTimeout the maximum time to wait for data once connected
   */
  constructor(
    source: String,
    connectTimeout: java.time.Duration,
    readTimeout: java.time.Duration,
  ) : this(source, connectTimeout.toKotlinDuration(), readTimeout.toKotlinDuration())

  private val connectTimeoutMillis = connectTimeout.toTimeoutMillis("connectTimeout")
  private val readTimeoutMillis = readTimeout.toTimeoutMillis("readTimeout")

  override val content: String
    get() =
      URI(source).toURL().openConnection().let { connection ->
        connection.connectTimeout = connectTimeoutMillis
        connection.readTimeout = readTimeoutMillis
        connection.getInputStream().use { it.readBytes().decodeToString() }
      }

  override val remote = true
}

// URLConnection takes an Int of milliseconds and treats 0 as no timeout. Rounding up keeps a sub-millisecond
// timeout from becoming 0, and the cap keeps a long one from wrapping around to a negative Int.
private fun Duration.toTimeoutMillis(name: String): Int {
  require(!isNegative()) { "$name must not be negative but was $this" }
  return when {
    isInfinite() -> 0
    else -> ceil(toDouble(DurationUnit.MILLISECONDS)).coerceAtMost(Int.MAX_VALUE.toDouble()).toInt()
  }
}

/**
 * A [ContentSource] that reads content from a local file.
 *
 * @param fileName the path to the local file
 */
open class FileSource(
  val fileName: String,
) : ContentSource {
  override val source: String
    get() = fileName

  override val content: String
    get() = File(fileName).readText()

  override val remote = false
}
