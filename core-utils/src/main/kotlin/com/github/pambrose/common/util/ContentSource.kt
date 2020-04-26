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

open class GitHubSource(val scheme: String = "https://",
                        val organization: String,
                        val repo: String,
                        val branch: String = "master",
                        val srcPath: String,
                        val fileName: String)
  : UrlSource(scheme + listOf("raw.githubusercontent.com", organization, repo, branch, srcPath, fileName).toPath(false))

open class GitLabSource(val scheme: String = "https://",
                        val organization: String,
                        val repo: String,
                        val branch: String = "master",
                        val srcPath: String,
                        val fileName: String)
  : UrlSource(scheme + listOf("gitlab.com", organization, repo, "-/blob", branch, srcPath, fileName).toPath(false))

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