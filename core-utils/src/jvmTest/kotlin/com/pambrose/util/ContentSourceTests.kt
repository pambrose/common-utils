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
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.nio.file.Files
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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

    "FileSystemSource.file resolves a relative path against pathPrefix" {
      val source = FileSystemSource("/tmp").file("notes.txt")
      source.shouldBeInstanceOf<FileSource>()
      source.source shouldBe "/tmp/notes.txt"
      source.remote shouldBe false
    }

    "FileSystemSource.file reads content from the file under the root" {
      val dir = tempdir("content-root-test")
      dir.resolve("notes.txt").writeText("under the root")

      FileSystemSource(dir.absolutePath).file("notes.txt").content shouldBe "under the root"
    }

    "FileSystemSource.file leaves an absolute path unchanged" {
      FileSystemSource("/tmp").file("/etc/hosts").source shouldBe "/etc/hosts"
    }

    "FileSystemSource.file keeps a relative ./ root usable" {
      FileSystemSource("./").file("src/Main.kt").source shouldBe "./src/Main.kt"
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

    "UrlSource reads content from a file URL" {
      val tmp = Files.createTempFile("url-source-test", ".txt").toFile()
      tmp.writeText("hello via url")
      tmp.deleteOnExit()
      val src = UrlSource(tmp.toURI().toURL().toString())
      src.content shouldBe "hello via url"
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

    "GitHubRepo.file resolves a relative path against rawSourcePrefix" {
      val repo = GitHubRepo(OwnerType.User, "pambrose", "common-utils")
      val source = repo.file("master/README.md")
      source.shouldBeInstanceOf<UrlSource>()
      source.source shouldBe "https://raw.githubusercontent.com/pambrose/common-utils/master/README.md"
      source.remote shouldBe true
      // A leading slash is still relative to the repository, not the host.
      repo.file("/master/README.md").source shouldBe
        "https://raw.githubusercontent.com/pambrose/common-utils/master/README.md"
    }

    "GitLabRepo.file resolves a relative path against the raw-content prefix" {
      val repo = GitLabRepo(OwnerType.User, "alice", "demo")
      repo.file("main/src/App.kt").source shouldBe "https://gitlab.com/alice/demo/-/raw/main/src/App.kt"
    }

    "repository file leaves a full URL unchanged" {
      val repo = GitHubRepo(OwnerType.User, "pambrose", "common-utils")
      repo.file("https://example.com/file.txt").source shouldBe "https://example.com/file.txt"
    }

    // Any "://" used to make a path a full URL, so a relative path with one in its query string threw.
    "a repository path is a full URL only when it starts with a scheme" {
      val repo = GitHubRepo(OwnerType.User, "u", "r")
      repo.file("main/app.kt?next=https://x.com").source shouldBe
        "https://raw.githubusercontent.com/u/r/main/app.kt?next=https://x.com"
      repo.file("file:///etc/hosts").source shouldBe "file:///etc/hosts"
    }

    "repository files and Java callers can set UrlSource timeouts" {
      val repo = GitHubRepo(OwnerType.User, "u", "r")
      repo.file("main/app.kt", 1.seconds, 2.seconds).source shouldBe "https://raw.githubusercontent.com/u/r/main/app.kt"
      GitHubFile(repo, "main", "src", "App.kt", connectTimeout = 1.seconds).source shouldBe
        "https://raw.githubusercontent.com/u/r/main/src/App.kt"
      GitHubFile(repo, "main", "src", "App.kt", readTimeout = 2.seconds).source shouldBe
        "https://raw.githubusercontent.com/u/r/main/src/App.kt"
      val gitLab = GitLabRepo(OwnerType.User, "u", "r")
      GitLabFile(gitLab, "main", "src", "App.kt", connectTimeout = 1.seconds).source shouldBe
        "https://gitlab.com/u/r/-/raw/main/src/App.kt"
      GitLabFile(gitLab, "main", "src", "App.kt", readTimeout = 2.seconds).source shouldBe
        "https://gitlab.com/u/r/-/raw/main/src/App.kt"
      UrlSource("https://example.com", java.time.Duration.ofSeconds(1), java.time.Duration.ofSeconds(2)).source shouldBe
        "https://example.com"
      shouldThrow<IllegalArgumentException> {
        UrlSource("https://example.com", java.time.Duration.ofSeconds(-1), java.time.Duration.ofSeconds(2))
      }
    }

    "GitLabRepo builds sourcePrefix and a raw-content rawSourcePrefix" {
      val repo = GitLabRepo(OwnerType.User, "alice", "demo")
      repo.sourcePrefix shouldBe "https://gitlab.com/alice/demo"
      // GitLab serves raw file content under /-/raw/; /-/blob/ is the HTML viewer page.
      repo.rawSourcePrefix shouldBe "https://gitlab.com/alice/demo/-/raw"
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

    "GitLabFile builds raw URL" {
      val repo = GitLabRepo(OwnerType.Organization, "anthropic", "demo")
      val file = GitLabFile(repo, branchName = "main", srcPath = "src", fileName = "App.kt")
      file.source shouldBe "https://gitlab.com/anthropic/demo/-/raw/main/src/App.kt"
      file.remote shouldBe true
      file.toString() shouldContain "fileName='App.kt'"
    }

    // Bug #1: GitLabFile hardcoded "gitlab.com" instead of using repo.domainName, so self-hosted
    // GitLab instances always produced gitlab.com URLs.
    "GitLabFile uses the repo domain for self-hosted GitLab" {
      val repo = GitLabRepo(OwnerType.User, "alice", "demo", domainName = "gitlab.example.com")
      val file = GitLabFile(repo, branchName = "main", srcPath = "src", fileName = "App.kt")
      repo.rawSourcePrefix shouldBe "https://gitlab.example.com/alice/demo/-/raw"
      file.source shouldBe "https://gitlab.example.com/alice/demo/-/raw/main/src/App.kt"
    }

    "GitLabFile honors a custom scheme and domain with a port" {
      val repo = GitLabRepo(OwnerType.User, "alice", "demo", scheme = "http://", domainName = "git.internal:8080")
      val file = GitLabFile(repo, branchName = "dev", srcPath = "a/b", fileName = "x.kt")
      file.source shouldBe "http://git.internal:8080/alice/demo/-/raw/dev/a/b/x.kt"
    }

    "GitHubFile with an empty srcPath does not produce a double slash" {
      val repo = GitHubRepo(OwnerType.User, "pambrose", "common-utils")
      GitHubFile(repo, branchName = "master", srcPath = "", fileName = "README.md").source shouldBe
        "https://raw.githubusercontent.com/pambrose/common-utils/master/README.md"
    }

    "GitHubRepo.rawSourcePrefix only rewrites the host, not a repository name containing github.com" {
      GitHubRepo(OwnerType.User, "bob", "bob.github.com").rawSourcePrefix shouldBe
        "https://raw.githubusercontent.com/bob/bob.github.com"
    }

    "GitHubRepo on a GitHub Enterprise domain serves raw content from the /raw/ path" {
      val repo = GitHubRepo(OwnerType.Organization, "team", "app", domainName = "github.corp.net")
      repo.rawSourcePrefix shouldBe "https://github.corp.net/raw/team/app"
      GitHubFile(repo, branchName = "main", srcPath = "src", fileName = "App.kt").source shouldBe
        "https://github.corp.net/raw/team/app/main/src/App.kt"
      repo.file("main/src/App.kt").source shouldBe "https://github.corp.net/raw/team/app/main/src/App.kt"
    }

    "UrlSource times out instead of blocking forever on an unresponsive server" {
      // The OS completes the TCP handshake from the listen backlog, but nothing ever reads the request or replies.
      ServerSocket(0).use { server ->
        shouldThrow<SocketTimeoutException> {
          UrlSource("http://127.0.0.1:${server.localPort}/slow", readTimeout = 200.milliseconds).content
        }
      }
    }

    // URLConnection takes an Int of milliseconds: Duration.INFINITE converted to -1, and anything past
    // Int.MAX_VALUE ms (about 24.8 days) wrapped around, both of which URLConnection rejects.
    "UrlSource accepts an infinite or very long timeout" {
      val url = tempFileUrl("long timeouts")
      UrlSource(url, connectTimeout = Duration.INFINITE, readTimeout = Duration.INFINITE).content shouldBe
        "long timeouts"
      UrlSource(url, connectTimeout = 30.days, readTimeout = 365.days).content shouldBe "long timeouts"
    }

    "UrlSource rejects a negative timeout when it is created" {
      shouldThrow<IllegalArgumentException> { UrlSource("file:/tmp/x", connectTimeout = (-1).seconds) }
      shouldThrow<IllegalArgumentException> { UrlSource("file:/tmp/x", readTimeout = (-1).milliseconds) }
    }

    // A sub-millisecond timeout truncated to 0, which URLConnection treats as no timeout at all.
    "UrlSource rounds a sub-millisecond timeout up instead of disabling it" {
      ServerSocket(0).use { server ->
        // Closing the listener after a while resets the pending connection, so a read that ignores the
        // timeout fails with a different exception instead of hanging the test.
        thread(isDaemon = true) {
          Thread.sleep(3_000)
          server.close()
        }
        shouldThrow<SocketTimeoutException> {
          UrlSource("http://127.0.0.1:${server.localPort}/slow", readTimeout = 100.microseconds).content
        }
      }
    }
  }
}

private fun tempFileUrl(text: String): String {
  val tmp = Files.createTempFile("url-source-test", ".txt").toFile()
  tmp.writeText(text)
  tmp.deleteOnExit()
  return tmp.toURI().toURL().toString()
}
