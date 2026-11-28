.PHONY: default help clean stop build lint detekt zizmor ci refresh tests tree depends versions versions-all kdocs \
	coverage coverage-html coverage-xml coverage-log coverage-verify coverage-open coverage-packages \
	coverage-modules coverage-clean mutation abi-check abi-update \
	publish-local publish-local-snapshot publish-snapshot publish-maven-central upgrade-wrapper \
	release release-check _confirm-release release-tag \
	_check-gpg-env _check-maven-central-env _require-version _require-gradle-version

VERSION := $(shell sed -n 's/^version=\(.*\)/\1/p' gradle.properties)
GRADLE_VERSION := $(shell sed -n 's/^gradle-wrapper = "\(.*\)"/\1/p' gradle/libs.versions.toml)
# VERSION with its dots escaped, for the extended regexes release-check matches it with
VERSION_RE := $(subst .,\.,$(VERSION))

# `make release` publishes only from a clean, up-to-date checkout of this branch whose GitHub CI has passed.
RELEASE_BRANCH ?= master
# The artifacts every release publishes to Maven Central, as com.pambrose.common-utils:<artifact>, at one version: every
# module settings.gradle.kts includes, the BOM among them, and the -jvm artifact of each module in the root build
# script's kmpModuleNames. Both lists are read from those files, so adding or removing a module needs no change here.
# The KMP modules also publish an artifact per native, JS and wasm target, which release-check doesn't probe.
MODULES := $(shell sed -n 's/^include("\(.*\)")$$/\1/p' settings.gradle.kts)
KMP_MODULES := $(shell sed -n '/^val kmpModuleNames = setOf($$/,/^)$$/s/^ *"\([^"]*\)",*$$/\1/p' build.gradle.kts)
MAVEN_ARTIFACTS := $(MODULES) $(addsuffix -jvm,$(KMP_MODULES))
MAVEN_GROUP := com.pambrose.common-utils
MAVEN_CENTRAL_URL := https://repo1.maven.org/maven2/com/pambrose/common-utils
# The files a release bumps to the new version besides gradle.properties, CHANGELOG.md and RELEASE_NOTES.md: README.md's
# install snippets and llms.txt's Version and Install lines. release-check fails if one doesn't mention VERSION, or still
# has the previous release's version in a coordinate, a Maven <version> or llms.txt's Version line.
RELEASE_VERSION_FILES := README.md llms.txt
# The CI jobs that must have passed for the release commit, as workflow-file/job-name, separated by semicolons since
# job names can hold spaces. make release itself runs make tests, which covers lint, ABI checks, the coverage floors and
# the tests of this host's targets, so these are what cover the Linux and Windows native tests, the local publish, the
# KDocs build and the workflow audit. Renaming one of these jobs, or adding a check a release should wait for, means
# updating this list.
RELEASE_REQUIRED_JOBS := test.yml/test;test.yml/native-apple;test.yml/native-windows;kdocs.yml/kdocs;zizmor.yml/zizmor

# The publishing secrets are resolved by shell commands that start the publish recipes' command line, and exported
# for the ./gradlew run that ends it, rather than given as prefix assignments on that command (VAR="$(...)" ./gradlew):
# a failing command substitution in a prefix assignment does not stop the command, so ./gradlew would run with the
# secret set to "". Each one is assigned on its own, which takes the substitution's exit status, then exported, and
# the recipe stops if the command fails or prints nothing. The errors name the source, never a value.
#
# The signing key, exported from GPG, and its passphrase, from the macOS keychain (entry gradle-signing-password,
# account gpg-signing).
GPG_ENV = \
	ORG_GRADLE_PROJECT_signingInMemoryKey="$$(gpg --armor --export-secret-keys "$$GPG_SIGNING_KEY_ID")" \
		&& [ -n "$$ORG_GRADLE_PROJECT_signingInMemoryKey" ] \
		|| { echo "ERROR: could not export the GPG secret key GPG_SIGNING_KEY_ID=$$GPG_SIGNING_KEY_ID" >&2; exit 1; }; \
	ORG_GRADLE_PROJECT_signingInMemoryKeyPassword="$$(security find-generic-password -a "gpg-signing" -s "gradle-signing-password" -w)" \
		&& [ -n "$$ORG_GRADLE_PROJECT_signingInMemoryKeyPassword" ] \
		|| { echo "ERROR: could not read keychain entry 'gradle-signing-password' (account 'gpg-signing'), or it is empty" >&2; exit 1; }; \
	ORG_GRADLE_PROJECT_signingInMemoryKeyId="$$GPG_SIGNING_KEY_ID"; \
	export ORG_GRADLE_PROJECT_signingInMemoryKey ORG_GRADLE_PROJECT_signingInMemoryKeyId \
		ORG_GRADLE_PROJECT_signingInMemoryKeyPassword;

# Maven Central's credentials, a user token's name and password from central.sonatype.com, from the macOS keychain, as
# the signing key's passphrase is: the maven-central-username and maven-central-password entries (account
# maven-central), shared by every repository that publishes this way. They reach only the publishing commands, as the
# vanniktech plugin's mavenCentralUsername and mavenCentralPassword, never ~/.gradle/gradle.properties, which every
# Gradle build on the machine is given. Add them once with
#   security add-generic-password -a maven-central -s maven-central-username -U -w
#   security add-generic-password -a maven-central -s maven-central-password -U -w
# each of which asks for the value, so it stays out of the shell's history.
MAVEN_CENTRAL_ENV = \
	ORG_GRADLE_PROJECT_mavenCentralUsername="$$(security find-generic-password -a "maven-central" -s "maven-central-username" -w)" \
		&& [ -n "$$ORG_GRADLE_PROJECT_mavenCentralUsername" ] \
		|| { echo "ERROR: could not read keychain entry 'maven-central-username' (account 'maven-central'), or it is empty" >&2; exit 1; }; \
	ORG_GRADLE_PROJECT_mavenCentralPassword="$$(security find-generic-password -a "maven-central" -s "maven-central-password" -w)" \
		&& [ -n "$$ORG_GRADLE_PROJECT_mavenCentralPassword" ] \
		|| { echo "ERROR: could not read keychain entry 'maven-central-password' (account 'maven-central'), or it is empty" >&2; exit 1; }; \
	export ORG_GRADLE_PROJECT_mavenCentralUsername ORG_GRADLE_PROJECT_mavenCentralPassword;

default: help

help:  ## Show this help (list of targets)
	@awk 'BEGIN {FS = ":.*?## "; printf "Usage: make <target>\n\nTargets:\n"} \
		/^[a-zA-Z0-9_-]+:.*?## / {printf "  \033[36m%-24s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)

clean: ## Run gradle clean
	./gradlew clean

stop: ## Stop the Gradle daemon
	./gradlew --stop

# koverVerify depends on the instrumented test tasks, so without excluding it the KMP modules'
# jvmTest still runs despite -x test -x allTests.
build: ## Build without running tests
	./gradlew build -x test -x allTests -x koverVerify

lint: ## Run Kotlinter and Detekt
	./gradlew lintKotlin detekt

detekt: ## Run Detekt static analysis
	./gradlew detekt

# The online audits (impostor commits, known-vulnerable actions, ref confusion) need a GitHub token: GH_TOKEN if set,
# otherwise gh's. zizmor rejects an empty token, so without one it runs only its offline audits.
zizmor: ## Audit the GitHub Actions workflows and Dependabot config with zizmor
	@command -v zizmor >/dev/null 2>&1 || { echo "ERROR: zizmor is not installed (brew install zizmor)" >&2; exit 1; }
	@token="$${GH_TOKEN:-$$(gh auth token 2>/dev/null)}"; \
	if [ -n "$$token" ]; then GH_TOKEN="$$token" zizmor .; else zizmor .; fi

# Runs the jobs of the CI workflows (test.yml, kdocs.yml and zizmor.yml) that this host can run, one after another,
# with the commands CI runs for a pull request, and stops at the first failure. zizmor goes first since it takes
# seconds. The local publish skips the javadoc jars' Dokka HTML, as a pull request's run does, because the KDocs step
# renders it anyway, and it publishes to build/ci-m2 rather than ~/.m2, so it never shadows released artifacts for
# other local builds. Not run: the Codecov upload, the native-windows job (mingwX64Test needs a Windows host) and, on
# macOS, the linuxX64 tests that `build` runs on Linux. On macOS, `build` already runs the Apple tests through check,
# so the native-apple step finds them up to date. Gradle reuses up-to-date and cached task results; `make tests`
# reruns every check task.
ci: zizmor ## Run the CI jobs this host can run: zizmor, build, local publish, coverage, Apple tests, KDocs
	./gradlew build
	./gradlew publishMavenPublicationToMavenLocal publishJvmPublicationToMavenLocal \
		publishKotlinMultiplatformPublicationToMavenLocal -x dokkaGeneratePublicationHtml \
		-Dmaven.repo.local=$(CURDIR)/build/ci-m2
	./gradlew koverXmlReport
	@if [ "$$(uname -s)" = Darwin ]; then \
		echo "./gradlew macosArm64Test iosSimulatorArm64Test"; ./gradlew macosArm64Test iosSimulatorArm64Test; \
	else \
		echo "Skipping the native-apple job, which needs a macOS host"; \
	fi
	./gradlew :dokkaGenerate
	@echo "Not run locally: the native-windows job (mingwX64Test) and the Codecov upload"

coverage: coverage-html coverage-xml ## Generate Kover HTML and XML reports

coverage-html: ## Generate Kover HTML coverage report
	./gradlew koverHtmlReport

coverage-xml: ## Generate Kover XML coverage report
	./gradlew koverXmlReport

coverage-log: ## Print Kover coverage summary to the build log
	./gradlew koverLog

coverage-verify: ## Run Kover coverage verification rules
	./gradlew koverVerify

coverage-open: coverage-html ## Generate and open the HTML coverage report
	open build/reports/kover/html/index.html

coverage-packages: coverage-xml ## Print per-package line/branch coverage, weakest branch coverage first
	@python3 scripts/coverage-packages.py

coverage-modules: coverage-xml ## Print per-module line/branch coverage, weakest branch coverage first
	@python3 scripts/coverage-packages.py --by-module

# PIT runs only in the modules listed in mutationModuleNames (root build.gradle.kts), and takes minutes.
mutation: ## Run PIT mutation testing (reports in <module>/build/reports/pitest)
	./gradlew pitest

abi-check: ## Check the public ABI against the committed <module>/api dumps (also part of check)
	./gradlew checkKotlinAbi

# Run on macOS: other hosts cannot compile every native target and reuse the committed klib declarations.
abi-update: ## Rewrite the <module>/api dumps after an intended public API change
	./gradlew updateKotlinAbi

# cleanTest covers the JVM modules and cleanJvmTest the KMP ones (whose jvmTest results Kover reads);
# cleanAllTests only removes the KMP aggregate test report. With the build cache on, a cleaned test task can
# still come back FROM-CACHE, so re-run coverage with --rerun-tasks.
coverage-clean: ## Clean Kover outputs and previous test results
	./gradlew cleanTest cleanJvmTest cleanAllTests
	rm -rf build/reports/kover build/kover */build/kover

refresh: ## Refresh dependencies and re-run dependencyUpdates
	./gradlew --refresh-dependencies dependencyUpdates --no-configuration-cache

tests: ## Run lint and tests (./gradlew --rerun-tasks check)
	./gradlew --rerun-tasks check

tree: ## Show dependency tree (quiet)
	./gradlew -q dependencies

depends: ## Show dependency tree (verbose)
	./gradlew dependencies

# dependencyUpdates is marked incompatible with the configuration cache (configureVersions() in build.gradle.kts), so
# a run with the cache on builds an entry only to discard it, and says so. --no-configuration-cache skips the cache for
# this run instead. The root project's task merges every module's dependencies and the root build script's plugins
# into one report. configureVersions() gives it a formatter that lists only what is behind; versions-all names the
# plugin's own plain report, which also lists everything up to date.
versions: ## List the dependencies and plugins with newer versions, and any that could not be checked
	./gradlew dependencyUpdates --no-configuration-cache

versions-all: ## The plugin's full dependency report, up-to-date dependencies and plugins included
	./gradlew dependencyUpdates --no-configuration-cache --output-formatter=plain

kdocs: ## Generate Dokka HTML documentation
	./gradlew :dokkaGenerate

publish-local: _require-version ## Install to local Maven repo
	./gradlew publishToMavenLocal

publish-local-snapshot: _require-version ## Install a -SNAPSHOT build to local Maven repo
	./gradlew -PoverrideVersion=$(VERSION)-SNAPSHOT publishToMavenLocal

publish-snapshot: _require-version _check-gpg-env _check-maven-central-env ## Publish a -SNAPSHOT to Maven Central
	$(GPG_ENV) $(MAVEN_CENTRAL_ENV) ./gradlew -PoverrideVersion=$(VERSION)-SNAPSHOT publishToMavenCentral

publish-maven-central: _require-version _check-gpg-env _check-maven-central-env ## Publish and release to Maven Central
	$(GPG_ENV) $(MAVEN_CENTRAL_ENV) ./gradlew publishAndReleaseToMavenCentral

# Publishes nothing: it fails unless this checkout is fit to publish VERSION. The local checks run first, then the
# network ones (the tag on GitHub, GitHub's CI checks for HEAD, Maven Central). A bumped file also fails if it still has
# the previous release's version (the CHANGELOG section below VERSION's) in a coordinate, a Maven <version> or
# llms.txt's Version line, and RELEASE_NOTES.md's VERSION section must link the diff from that version. A tag VERSION
# may already exist, here or on GitHub (creating the GitHub release first makes one), but only on HEAD. And every CI
# check passing isn't enough, since with [skip ci] or a disabled workflow a lone third-party check can be the whole
# list: each RELEASE_REQUIRED_JOBS job must also have run for HEAD and passed.
release-check: _require-version _check-gpg-env _check-maven-central-env ## Fail unless this checkout is fit to publish VERSION
	@case "$(VERSION)" in *SNAPSHOT*|*-rc*|*-beta*|*-alpha*) \
		echo "ERROR: $(VERSION) is a pre-release; make release publishes final versions" >&2; exit 1;; esac
	@test -z "$$(git status --porcelain)" \
		|| { echo "ERROR: the working tree is dirty; commit or stash first" >&2; exit 1; }
	@branch=$$(git rev-parse --abbrev-ref HEAD); [ "$$branch" = "$(RELEASE_BRANCH)" ] \
		|| { echo "ERROR: releases are cut from $(RELEASE_BRANCH), not $$branch" >&2; exit 1; }
	@git fetch -q origin $(RELEASE_BRANCH) \
		&& [ "$$(git rev-parse HEAD)" = "$$(git rev-parse origin/$(RELEASE_BRANCH))" ] \
		|| { echo "ERROR: HEAD is not origin/$(RELEASE_BRANCH); pull or push first" >&2; exit 1; }
	@grep -qE '^## \[$(VERSION_RE)\] - [0-9]{4}-[0-9]{2}-[0-9]{2}$$' CHANGELOG.md \
		|| { echo "ERROR: CHANGELOG.md has no dated '## [$(VERSION)] - YYYY-MM-DD' section" >&2; exit 1; }
	@grep -qE '^## v$(VERSION_RE) [^ ]+ [0-9]{4}-[0-9]{2}-[0-9]{2}$$' RELEASE_NOTES.md \
		|| { echo "ERROR: RELEASE_NOTES.md has no '## v$(VERSION)' section dated YYYY-MM-DD" >&2; exit 1; }
	@missing=$$(for f in $(RELEASE_VERSION_FILES); do grep -qF '$(VERSION)' "$$f" || echo "  $$f"; done); \
	[ -z "$$missing" ] || { printf 'ERROR: not bumped to $(VERSION):\n%s\n' "$$missing" >&2; exit 1; }
	@prev=$$(awk -v h='## [$(VERSION)]' 'f && /^## \[/ { sub(/^## \[/, ""); sub(/\].*/, ""); print; exit } index($$0, h) == 1 { f = 1 }' CHANGELOG.md); \
	[ -n "$$prev" ] || { echo "ERROR: CHANGELOG.md has no release section below [$(VERSION)]" >&2; exit 1; }; \
	p=$$(printf '%s' "$$prev" | sed 's/\./\\./g'); \
	stale=$$(grep -nE "$(subst .,\.,$(MAVEN_GROUP)):[^:\"]+:$$p([^0-9.]|\$$)|<version>$$p</version>|^- Version: $$p\$$" \
		$(RELEASE_VERSION_FILES) | sed 's/^/  /'); \
	[ -z "$$stale" ] || { printf 'ERROR: still at the previous version, %s:\n%s\n' "$$prev" "$$stale" >&2; exit 1; }; \
	awk -v h='## v$(VERSION) ' 'index($$0, h) == 1 { f = 1; next } f && /^## / { exit } f' RELEASE_NOTES.md \
		| grep -qF "**Full Changelog**: https://github.com/pambrose/common-utils/compare/$$prev...$(VERSION)" \
		|| { echo "ERROR: RELEASE_NOTES.md's v$(VERSION) section has no **Full Changelog** link to compare/$$prev...$(VERSION)" >&2; exit 1; }
	@head=$$(git rev-parse HEAD); \
	tagged=$$(git rev-parse -q --verify "refs/tags/$(VERSION)^{commit}"); \
	[ -z "$$tagged" ] || [ "$$tagged" = "$$head" ] \
		|| { echo "ERROR: tag $(VERSION) points at another commit" >&2; exit 1; }; \
	refs=$$(git ls-remote origin "refs/tags/$(VERSION)" "refs/tags/$(VERSION)^{}") \
		|| { echo "ERROR: could not read the tags on origin" >&2; exit 1; }; \
	remote=$$(printf '%s\n' "$$refs" | tail -n 1 | cut -f 1); \
	[ -z "$$remote" ] || [ "$$remote" = "$$head" ] \
		|| { echo "ERROR: tag $(VERSION) on origin points at another commit" >&2; exit 1; }
	@runs=$$(gh api "repos/{owner}/{repo}/commits/$$(git rev-parse HEAD)/check-runs" --paginate \
		--jq '.check_runs[] | "\(.status) \(.conclusion) \(.name)"') \
		|| { echo "ERROR: could not read HEAD's CI checks from GitHub; is gh logged in?" >&2; exit 1; }; \
	[ -n "$$runs" ] || { echo "ERROR: no CI checks have run for HEAD" >&2; exit 1; }; \
	failed=$$(echo "$$runs" | grep -Ev '^completed (success|skipped|neutral) ' | sed 's/^/  /'); \
	[ -z "$$failed" ] || { printf 'ERROR: CI has not passed for HEAD:\n%s\n' "$$failed" >&2; exit 1; }
	@wfruns=$$(gh api "repos/{owner}/{repo}/actions/runs?head_sha=$$(git rev-parse HEAD)&per_page=100" \
		--jq '.workflow_runs[] | "\(.id) \(.path | sub(".*/"; ""))"') \
		|| { echo "ERROR: could not read HEAD's workflow runs from GitHub; is gh logged in?" >&2; exit 1; }; \
	jobs=$$(echo "$$wfruns" | while read -r id wf; do \
			[ -n "$$id" ] || continue; \
			gh api "repos/{owner}/{repo}/actions/runs/$$id/jobs?per_page=100" \
				--jq ".jobs[] | \"$$wf/\(.name) \(.conclusion)\"" || exit 1; \
		done) \
		|| { echo "ERROR: could not read the jobs of HEAD's workflow runs from GitHub" >&2; exit 1; }; \
	missing=$$(echo '$(RELEASE_REQUIRED_JOBS)' | tr ';' '\n' | sed 's/^ *//' | while read -r job; do \
		echo "$$jobs" | grep -qFx "$$job success" || echo "  $$job"; done); \
	[ -z "$$missing" ] || { printf 'ERROR: required CI jobs have not passed for HEAD:\n%s\n' "$$missing" >&2; exit 1; }
	@for a in $(MAVEN_ARTIFACTS); do \
		code=$$(curl -sI -o /dev/null -w '%{http_code}' "$(MAVEN_CENTRAL_URL)/$$a/$(VERSION)/$$a-$(VERSION).pom"); \
		case "$$code" in \
			404) ;; \
			200) echo "ERROR: $(MAVEN_GROUP):$$a:$(VERSION) is already on Maven Central; bump the version" >&2; exit 1;; \
			*) echo "ERROR: could not check Maven Central for $(MAVEN_GROUP):$$a:$(VERSION) (HTTP $$code)" >&2; exit 1;; \
		esac; \
	done
	@echo "Ready to release $(VERSION) from $$(git rev-parse --short HEAD)"

_confirm-release:
	@echo "About to publish $(VERSION) to Maven Central. A Maven Central release can't be removed."
	@echo "  $(MAVEN_GROUP):<artifact>:$(VERSION) for these artifacts, plus each KMP module's per-target ones:"
	@echo "$(MAVEN_ARTIFACTS)" | fold -s -w 100 | sed 's/^/    /'
	@printf 'Continue? [y/N] ' && read ans && [ "$$ans" = "y" ] || { echo "Nothing published."; exit 1; }

# Each step runs only if the one before it passed, so nothing is published until the checkout passes release-check and
# make tests, which reruns every check task (lint, ABI, the tests of this host's targets and the coverage floors).
# release-tag then tags HEAD as VERSION and pushes the tag, if a GitHub release created first hasn't already. The GitHub
# release, when there is none yet, is printed as the next step. After a partial release, finish with the remaining
# targets: release-check refuses a version already published.
release: ## Gate, test, then publish VERSION to Maven Central (asks first) and tag it
	$(MAKE) release-check
	$(MAKE) tests
	@$(MAKE) --no-print-directory _confirm-release
	$(MAKE) publish-maven-central
	$(MAKE) release-tag
	@prev=$$(git describe --tags --abbrev=0 HEAD^ 2>/dev/null); \
	echo; \
	echo "Published $(VERSION) to Maven Central. It can take 10-30 minutes to appear under $(MAVEN_CENTRAL_URL)."; \
	if gh release view "$(VERSION)" >/dev/null 2>&1; then \
		echo "The GitHub release $(VERSION) already exists."; \
	else \
		echo "Next: create the GitHub release (tag $(VERSION), title v$(VERSION)) from RELEASE_NOTES.md's v$(VERSION) section,"; \
		echo "ending with **Full Changelog**: https://github.com/pambrose/common-utils/compare/$$prev...$(VERSION)"; \
		echo "  gh release create $(VERSION) --verify-tag --title v$(VERSION) --notes-file <notes>"; \
	fi

# Tags HEAD as VERSION, as the earlier releases' lightweight tags are, and pushes the tag. make release runs it once
# VERSION is published. It first fetches the tag from GitHub, where creating the GitHub release first makes it; a tag
# already on HEAD is pushed again, which changes nothing, and one on another commit fails, as release-check does.
release-tag: _require-version ## Tag HEAD as VERSION and push the tag (make release runs it after publishing)
	@git fetch -q origin "refs/tags/$(VERSION):refs/tags/$(VERSION)" 2>/dev/null; \
	tagged=$$(git rev-parse -q --verify "refs/tags/$(VERSION)^{commit}"); \
	if [ -z "$$tagged" ]; then \
		git tag "$(VERSION)" || exit 1; \
	elif [ "$$tagged" != "$$(git rev-parse HEAD)" ]; then \
		echo "ERROR: tag $(VERSION) points at another commit" >&2; exit 1; \
	fi; \
	git push --quiet origin "refs/tags/$(VERSION)" \
		&& echo "Tagged $$(git rev-parse --short HEAD) as $(VERSION) and pushed the tag"

# Gradle's documented upgrade procedure: the first run rewrites
# gradle-wrapper.properties using the *old* wrapper jar; the second run
# regenerates the wrapper itself with the new version. Both pass the
# distribution's published SHA-256, so distributionSha256Sum is kept and the
# wrapper verifies every download.
upgrade-wrapper: _require-gradle-version ## Re-run the Gradle wrapper task at the pinned version
	@sum="$$(curl -fsSL https://services.gradle.org/distributions/gradle-$(GRADLE_VERSION)-bin.zip.sha256)" \
		|| { echo "ERROR: Could not fetch the SHA-256 for gradle $(GRADLE_VERSION)" >&2; exit 1; }; \
	./gradlew wrapper --gradle-version=$(GRADLE_VERSION) --distribution-type=bin --gradle-distribution-sha256-sum=$$sum && \
	./gradlew wrapper --gradle-version=$(GRADLE_VERSION) --distribution-type=bin --gradle-distribution-sha256-sum=$$sum

# Fails, naming the keychain entry and never its value, when the signing key's passphrase is missing or empty: the
# signing key must have one
_check-gpg-env:
	@if [ -z "$$GPG_SIGNING_KEY_ID" ]; then \
		echo "ERROR: GPG_SIGNING_KEY_ID is not set" >&2; exit 1; \
	fi
	@if ! gpg --list-secret-keys "$$GPG_SIGNING_KEY_ID" >/dev/null 2>&1; then \
		echo "ERROR: no GPG secret key found for GPG_SIGNING_KEY_ID=$$GPG_SIGNING_KEY_ID" >&2; exit 1; \
	fi
	@if [ -z "$$(security find-generic-password -a 'gpg-signing' -s 'gradle-signing-password' -w 2>/dev/null)" ]; then \
		echo "ERROR: keychain entry 'gradle-signing-password' (account 'gpg-signing') is missing or empty; add it with: security add-generic-password -a gpg-signing -s gradle-signing-password -U -w" >&2; \
		exit 1; \
	fi

# Fails, naming the keychain entry and never its value, when either of Maven Central's credentials is missing or empty
_check-maven-central-env:
	@for entry in maven-central-username maven-central-password; do \
		if [ -z "$$(security find-generic-password -a 'maven-central' -s "$$entry" -w 2>/dev/null)" ]; then \
			echo "ERROR: keychain entry '$$entry' (account 'maven-central') is missing or empty; add it with: security add-generic-password -a maven-central -s $$entry -U -w" >&2; \
			exit 1; \
		fi; \
	done

_require-version:
	@[ -n "$(VERSION)" ] || { echo "ERROR: Could not determine project version from gradle.properties" >&2; exit 1; }

_require-gradle-version:
	@[ -n "$(GRADLE_VERSION)" ] || { echo "ERROR: Could not determine gradle version from gradle/libs.versions.toml" >&2; exit 1; }
