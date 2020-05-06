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

interface ContentSource {
  val path: String
  val content: String
}

interface AbstractRepo {
  val organizationName: String
  val repoName: String
  val scheme: String
  val domainName: String

  val url: String get() = scheme + listOf(domainName + organizationName + repoName).toPath()
}

data class GitHubRepo(override val organizationName: String,
                      override val repoName: String,
                      override val scheme: String = "https://",
                      override val domainName: String = "github.com") : AbstractRepo

data class GitLabRepo(override val organizationName: String,
                      override val repoName: String,
                      override val scheme: String = "https://",
                      override val domainName: String = "gitlab.com") : AbstractRepo

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

open class UrlSource(override val path: String) : ContentSource {
  override val content: String
    get() = URL(path).readText()
}

class FileSource(val fileName: String) : ContentSource {
  override val path: String
    get() = fileName

  override val content: String
    get() = File(fileName).readText()
}