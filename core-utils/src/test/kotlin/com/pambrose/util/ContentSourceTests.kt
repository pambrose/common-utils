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

package com.pambrose.util

import com.pambrose.common.util.FileSource
import com.pambrose.common.util.FileSystemSource
import com.pambrose.common.util.GitHubFile
import com.pambrose.common.util.GitHubRepo
import com.pambrose.common.util.GitLabFile
import com.pambrose.common.util.GitLabRepo
import com.pambrose.common.util.OwnerType
import com.pambrose.common.util.UrlSource
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Files

class ContentSourceTests : StringSpec() {
  init {
    "OwnerType.isUser and isOrganization" {
      OwnerType.User.isUser() shouldBe true
      OwnerType.User.isOrganization() shouldBe false
      OwnerType.Organization.isUser() shouldBe false
      OwnerType.Organization.isOrganization() shouldBe true
    }

    "FileSystemSource exposes pathPrefix as sourcePrefix and is local" {
      val fs = FileSystemSource("/tmp/data")
      fs.pathPrefix shouldBe "/tmp/data"
      fs.sourcePrefix shouldBe "/tmp/data"
      fs.remote shouldBe false
      fs.toString() shouldContain "/tmp/data"
    }

    "FileSystemSource.file returns a FileSource with the given path" {
      val source = FileSystemSource("/tmp").file("notes.txt")
      source.shouldBeInstanceOf<FileSource>()
      source.source shouldBe "notes.txt"
      source.remote shouldBe false
    }

    "FileSource reads content from disk" {
      val tmp = Files.createTempFile("content-source-test", ".txt").toFile()
      tmp.writeText("hello on disk")
      tmp.deleteOnExit()
      val src = FileSource(tmp.absolutePath)
      src.source shouldBe tmp.absolutePath
      src.content shouldBe "hello on disk"
      src.remote shouldBe false
    }

    "UrlSource reports its url and remote=true" {
      val url = "https://example.com/file.txt"
      val src = UrlSource(url)
      src.source shouldBe url
      src.remote shouldBe true
    }

    "GitHubRepo builds sourcePrefix from owner and repo" {
      val repo = GitHubRepo(OwnerType.Organization, "anthropic", "claude")
      repo.scheme shouldBe "https://"
      repo.domainName shouldBe "github.com"
      repo.ownerName shouldBe "anthropic"
      repo.repoName shouldBe "claude"
      repo.sourcePrefix shouldBe "https://github.com/anthropic/claude"
      repo.rawSourcePrefix shouldBe "https://raw.githubusercontent.com/anthropic/claude"
      repo.remote shouldBe true
      repo.toString() shouldContain "anthropic"
    }

    "GitHubRepo.file returns a UrlSource with the given path" {
      val repo = GitHubRepo(OwnerType.User, "pambrose", "common-utils")
      val source = repo.file("README.md")
      source.shouldBeInstanceOf<UrlSource>()
      source.source shouldBe "README.md"
      source.remote shouldBe true
    }

    "GitLabRepo builds sourcePrefix and uses same scheme for raw" {
      val repo = GitLabRepo(OwnerType.User, "alice", "demo")
      repo.sourcePrefix shouldBe "https://gitlab.com/alice/demo"
      repo.rawSourcePrefix shouldBe "https://gitlab.com/alice/demo"
      repo.remote shouldBe true
      repo.toString() shouldContain "alice"
    }

    "GitHubFile builds raw.githubusercontent URL" {
      val repo = GitHubRepo(OwnerType.User, "pambrose", "common-utils")
      val file = GitHubFile(repo, branchName = "master", srcPath = "core-utils/README", fileName = "README.md")
      file.source shouldBe "https://raw.githubusercontent.com/pambrose/common-utils/master/core-utils/README/README.md"
      file.remote shouldBe true
      file.toString() shouldContain "branchName='master'"
    }

    "GitLabFile builds blob URL" {
      val repo = GitLabRepo(OwnerType.Organization, "anthropic", "demo")
      val file = GitLabFile(repo, branchName = "main", srcPath = "src", fileName = "App.kt")
      file.source shouldBe "https://gitlab.com/anthropic/demo/-/blob/main/src/App.kt"
      file.remote shouldBe true
      file.toString() shouldContain "fileName='App.kt'"
    }
  }
}
