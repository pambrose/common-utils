/*
 * Copyright © 2020 Paul Ambrose (pambrose@mac.com)
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
}

abstract class AbstractRepo(val scheme: String,
                            val domainName: String,
                            val organizationName: String,
                            val repoName: String) : ContentRoot {

  override val sourcePrefix: String get() = scheme + listOf(domainName, organizationName, repoName).toPath()
  override val remote = true
  abstract val rawSourcePrefix: String

  override fun file(path: String) = UrlSource(path)
}

private const val github = "github.com"
private const val githubUserContent = "raw.githubusercontent.com"

class GitHubRepo(organizationName: String,
                 repoName: String,
                 scheme: String = "https://",
                 domainName: String = github) : AbstractRepo(scheme,
                                                             domainName,
                                                             organizationName,
                                                             repoName) {
  override val rawSourcePrefix = sourcePrefix.replace(github, githubUserContent)
}


class GitLabRepo(organizationName: String,
                 repoName: String,
                 scheme: String = "https://",
                 domainName: String = "gitlab.com") : AbstractRepo(scheme,
                                                                   domainName,
                                                                   organizationName,
                                                                   repoName) {
  override val rawSourcePrefix = sourcePrefix
}

interface ContentSource {
  val source: String
  val content: String
  val remote: Boolean
}

open class GitHubFile(val repo: GitHubRepo,
                      val branchName: String = "master",
                      val srcPath: String,
                      val fileName: String)
  : UrlSource(repo.scheme + listOf(githubUserContent,
                                   repo.organizationName,
                                   repo.repoName,
                                   branchName,
                                   srcPath,
                                   fileName).toPath(false))

open class GitLabFile(val repo: GitLabRepo,
                      val branchName: String = "master",
                      val srcPath: String,
                      val fileName: String)
  : UrlSource(repo.scheme + listOf("gitlab.com",
                                   repo.organizationName,
                                   repo.repoName,
                                   "-/blob",
                                   branchName,
                                   srcPath,
                                   fileName).toPath(false))

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