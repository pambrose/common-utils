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
}

class FileSystemRoot(val pathPrefix: String) : ContentRoot {
  override val sourcePrefix = pathPrefix
}

abstract class AbstractRepo(val scheme: String,
                            val domainName: String,
                            val organizationName: String,
                            val repoName: String) : ContentRoot {

  override val sourcePrefix: String get() = scheme + listOf(domainName, organizationName, repoName).toPath()
}

class GitHubRepo(organizationName: String,
                 repoName: String,
                 scheme: String = "https://",
                 domainName: String = "github.com") : AbstractRepo(scheme,
                                                                   domainName,
                                                                   organizationName,
                                                                   repoName)

class GitLabRepo(organizationName: String,
                 repoName: String,
                 scheme: String = "https://",
                 domainName: String = "gitlab.com") : AbstractRepo(scheme,
                                                                   domainName,
                                                                   organizationName,
                                                                   repoName)

interface ContentSource {
  val source: String
  val content: String
}

open class GitHubFile(val repo: GitHubRepo,
                      val branchName: String = "master",
                      val srcPath: String,
                      val fileName: String)
  : UrlSource(repo.scheme + listOf("raw.githubusercontent.com",
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
}

class FileSystemSource(val fileName: String) : ContentSource {
  override val source: String
    get() = fileName

  override val content: String
    get() = File(fileName).readText()
}