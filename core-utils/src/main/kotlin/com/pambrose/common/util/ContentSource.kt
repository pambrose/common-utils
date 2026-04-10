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
import java.net.URL

/**
 * Root abstraction for a content location, either a local directory or a remote repository.
 */
interface ContentRoot {
  /** The prefix path or URL used to resolve files within this root. */
  val sourcePrefix: String

  /** Whether this root points to a remote location. */
  val remote: Boolean

  /**
   * Creates a [ContentSource] for the given relative [path] within this root.
   *
   * @param path the relative path to the file
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

  override fun file(path: String) = FileSource(path)

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
  override val sourcePrefix: String get() = scheme + listOf(domainName, ownerName, repoName).join()
  override val remote = true

  /** The URL prefix used to access raw file content from this repository. */
  abstract val rawSourcePrefix: String

  override fun file(path: String) = UrlSource(path)
}

private const val GITHUB = "github.com"
private const val GITHUB_USER_CONTENT = "raw.githubusercontent.com"

/**
 * A [ContentRoot] representing a GitHub repository.
 *
 * Raw content is fetched via `raw.githubusercontent.com`.
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
  override val rawSourcePrefix = sourcePrefix.replace(GITHUB, GITHUB_USER_CONTENT)

  override fun toString() =
    "GitHubRepo(scheme='$scheme', domainName='$domainName', ownerName='$ownerName', repoName='$repoName', " +
      "rawSourcePrefix='$rawSourcePrefix')"
}

/**
 * A [ContentRoot] representing a GitLab repository.
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
  override val rawSourcePrefix = sourcePrefix

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
 * Constructs the raw content URL from the repository, branch, path, and file name.
 *
 * @param repo the GitHub repository
 * @param branchName the branch or tag name
 * @param srcPath the directory path within the repository
 * @param fileName the file name
 */
open class GitHubFile(
  val repo: GitHubRepo,
  val branchName: String,
  val srcPath: String,
  val fileName: String,
) : UrlSource(
  repo.scheme + listOf(
    GITHUB_USER_CONTENT,
    repo.ownerName,
    repo.repoName,
    branchName,
    srcPath,
    fileName,
  ).join(),
) {
  override fun toString() = "GitHubFile(repo=$repo, branchName='$branchName', srcPath='$srcPath', fileName='$fileName')"
}

/**
 * A [ContentSource] pointing to a specific file in a GitLab repository.
 *
 * Constructs the blob URL from the repository, branch, path, and file name.
 *
 * @param repo the GitLab repository
 * @param branchName the branch or tag name
 * @param srcPath the directory path within the repository
 * @param fileName the file name
 */
open class GitLabFile(
  val repo: GitLabRepo,
  val branchName: String,
  val srcPath: String,
  val fileName: String,
) : UrlSource(
  repo.scheme + listOf(
    "gitlab.com",
    repo.ownerName,
    repo.repoName,
    "-/blob",
    branchName,
    srcPath,
    fileName,
  ).join(),
) {
  override fun toString() = "GitLabFile(repo=$repo, branchName='$branchName', srcPath='$srcPath', fileName='$fileName')"
}

/**
 * A [ContentSource] that reads content from a URL.
 *
 * @param source the URL to read content from
 */
open class UrlSource(
  override val source: String,
) : ContentSource {
  override val content: String
    get() = URL(source).readText()

  override val remote = true
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
