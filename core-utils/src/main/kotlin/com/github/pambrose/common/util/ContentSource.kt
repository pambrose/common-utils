/*
 * Copyright © 2023 Paul Ambrose (pambrose@mac.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.github.pambrose.common.util

import java.io.File
import java.net.URL

interface ContentRoot {
  val sourcePrefix: String
  val remote: Boolean

  fun file(path: String): ContentSource
}

class FileSystemSource(val pathPrefix: String) : ContentRoot {
  override val sourcePrefix = pathPrefix
  override val remote = false

  override fun file(path: String) = FileSource(path)

  override fun toString() =
    "FileSystemSource(pathPrefix='$pathPrefix', sourcePrefix='$sourcePrefix')"
}

enum class OwnerType {
  User,
  Organization,
  ;

  fun isUser() = this == User

  fun isOrganization() = this == Organization
}

abstract class AbstractRepo(
  val scheme: String,
  val domain: String,
  val ownerType: OwnerType,
  val owner: String,
  val repo: String,
) : ContentRoot {
  override val sourcePrefix: String get() = scheme + listOf(domain, owner, repo).join()
  override val remote = true
  abstract val rawSourcePrefix: String

  override fun file(path: String) = UrlSource(path)
}

private const val GITHUB = "github.com"
private const val GITHUB_USER_CONTENT = "raw.githubusercontent.com"

class GitHubRepo(
  ownerType: OwnerType,
  ownerName: String,
  repoName: String,
  scheme: String = "https://",
  domainName: String = GITHUB,
) : AbstractRepo(scheme, domainName, ownerType, ownerName, repoName) {
  override val rawSourcePrefix = sourcePrefix.replace(GITHUB, GITHUB_USER_CONTENT)

  override fun toString() =
    "GitHubRepo(scheme='$scheme', domainName='$domain', ownerName='$owner', repoName='$repo', " +
      "rawSourcePrefix='$rawSourcePrefix')"
}

class GitLabRepo(
  ownerType: OwnerType,
  ownerName: String,
  repoName: String,
  scheme: String = "https://",
  domainName: String = "gitlab.com",
) : AbstractRepo(scheme, domainName, ownerType, ownerName, repoName) {
  override val rawSourcePrefix = sourcePrefix

  override fun toString() =
    "GitLabRepo(scheme='$scheme', domainName='$domain', ownerName='$owner', repoName='$repo', " +
      "rawSourcePrefix='$rawSourcePrefix')"
}

interface ContentSource {
  val source: String
  val content: String
  val remote: Boolean
}

open class GitHubFile(
  val repo: GitHubRepo,
  val branch: String,
  val srcPath: String,
  val file: String,
) :
  UrlSource(repo.scheme + listOf(GITHUB_USER_CONTENT, repo.owner, repo.repo, branch, srcPath, file).join()) {
  override fun toString() =
    "GitHubFile(repo=$repo, branchName='$branch', srcPath='$srcPath', fileName='$file')"
}

open class GitLabFile(
  val repo: GitLabRepo,
  val branch: String,
  val srcPath: String,
  val fileName: String,
) : UrlSource(repo.scheme + listOf("gitlab.com", repo.owner, repo.repo, "-/blob", branch, srcPath, fileName).join()) {
  override fun toString() =
    "GitLabFile(repo=$repo, branchName='$branch', srcPath='$srcPath', fileName='$fileName')"
}

open class UrlSource(override val source: String) : ContentSource {
  override val content: String
    get() = URL(source).readText()

  override val remote = true
}

open class FileSource(val fileName: String) : ContentSource {
  override val source: String
    get() = fileName

  override val content: String
    get() = File(fileName).readText()

  override val remote = false
}
