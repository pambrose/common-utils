.PHONY: default help clean stop build lint detekt detekt-baseline refresh tests tree depends versioncheck kdocs \
	coverage coverage-html coverage-xml coverage-log coverage-verify coverage-open coverage-packages coverage-clean \
	check-gpg-env publish-local publish-local-snapshot publish-snapshot publish-maven-central upgrade-wrapper

VERSION := $(shell sed -n 's/^version=\(.*\)/\1/p' gradle.properties)

ifeq ($(strip $(VERSION)),)
$(error Could not determine project version from gradle.properties)
endif

GRADLE_VERSION := $(shell sed -n 's/^gradle = "\(.*\)"/\1/p' gradle/libs.versions.toml)

ifeq ($(strip $(GRADLE_VERSION)),)
$(error Could not determine gradle version from gradle/libs.versions.toml)
endif

default: versioncheck

help: ## Show available make targets
	@awk 'BEGIN { FS = ":.*## "; printf "Usage: make <target>\n\nTargets:\n" } \
		/^[a-zA-Z0-9_-]+:.*## / { printf "  %-22s %s\n", $$1, $$2 }' $(MAKEFILE_LIST)

clean: ## Run gradle clean
	./gradlew clean

stop: ## Stop the Gradle daemon
	./gradlew --stop

build: ## Build without running tests
	./gradlew build -xtest

lint: detekt ## Run Kotlinter and Detekt
	./gradlew lintKotlinMain lintKotlinTest

detekt: ## Run Detekt static analysis
	./gradlew detekt

detekt-baseline: ## Generate or update the Detekt baseline
	./gradlew detektBaseline

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

coverage-packages: coverage-xml ## Print per-package coverage table from the XML report
	@python3 scripts/coverage-packages.py

coverage-clean: ## Clean Kover outputs and previous test results
	./gradlew cleanAllTests
	rm -rf build/reports/kover build/kover

refresh: ## Refresh dependencies and re-run dependencyUpdates
	./gradlew --refresh-dependencies dependencyUpdates --no-configuration-cache

tests: ## Run lint and tests (./gradlew --rerun-tasks check)
	./gradlew --rerun-tasks check

tree: ## Show dependency tree (quiet)
	./gradlew -q dependencies

depends: ## Show dependency tree (verbose)
	./gradlew dependencies

versioncheck: ## Check for dependency updates (default target)
	./gradlew dependencyUpdates --no-configuration-cache --no-parallel

kdocs: ## Generate Dokka HTML documentation
	./gradlew :dokkaGenerate

publish-local: ## Install to local Maven repo
	./gradlew publishToMavenLocal

publish-local-snapshot: ## Install a -SNAPSHOT build to local Maven repo
	./gradlew -PoverrideVersion=$(VERSION)-SNAPSHOT publishToMavenLocal

GPG_ENV = \
	ORG_GRADLE_PROJECT_signingInMemoryKey="$$(gpg --armor --export-secret-keys $$GPG_SIGNING_KEY_ID)" \
	ORG_GRADLE_PROJECT_signingInMemoryKeyId="$$GPG_SIGNING_KEY_ID" \
	ORG_GRADLE_PROJECT_signingInMemoryKeyPassword=$$(security find-generic-password -a "gpg-signing" -s "gradle-signing-password" -w)

check-gpg-env: ## Verify GPG signing environment is configured
	@if [ -z "$$GPG_SIGNING_KEY_ID" ]; then \
		echo "Error: GPG_SIGNING_KEY_ID is not set" >&2; exit 1; \
	fi
	@if ! gpg --list-secret-keys "$$GPG_SIGNING_KEY_ID" >/dev/null 2>&1; then \
		echo "Error: no GPG secret key found for GPG_SIGNING_KEY_ID=$$GPG_SIGNING_KEY_ID" >&2; exit 1; \
	fi
	@if ! security find-generic-password -a "gpg-signing" -s "gradle-signing-password" -w >/dev/null 2>&1; then \
		echo "Error: keychain entry 'gradle-signing-password' (account 'gpg-signing') not found" >&2; exit 1; \
	fi

publish-snapshot: check-gpg-env ## Publish a -SNAPSHOT to Maven Central
	$(GPG_ENV) ./gradlew -PoverrideVersion=$(VERSION)-SNAPSHOT publishToMavenCentral

publish-maven-central: check-gpg-env ## Publish and release to Maven Central
	$(GPG_ENV) ./gradlew publishAndReleaseToMavenCentral

upgrade-wrapper: ## Re-run the Gradle wrapper task at the pinned version
	./gradlew wrapper --gradle-version=$(GRADLE_VERSION) --distribution-type=bin
