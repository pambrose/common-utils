VERSION := $(shell grep -E '^[[:space:]]*version[[:space:]]*=' build.gradle.kts | head -1 | sed 's/.*"\(.*\)"/\1/')

.PHONY: default clean stop compile build lint refresh tests tree depends versioncheck kdocs coverage coverage-xml \
	check-gpg-env publish-local publish-local-snapshot publish-snapshot publish-maven-central upgrade-wrapper

default: versioncheck

clean:
	./gradlew clean

stop:
	./gradlew --stop

compile:
	./gradlew build -xtest

build: compile

lint:
	./gradlew lintKotlinMain lintKotlinTest

refresh:
	./gradlew --refresh-dependencies dependencyUpdates --no-configuration-cache

tests:
	./gradlew --rerun-tasks check

tree:
	./gradlew -q dependencies

depends:
	./gradlew dependencies

versioncheck:
	./gradlew dependencyUpdates --no-configuration-cache

kdocs:
	./gradlew :dokkaGenerate

coverage:
	./gradlew koverHtmlReport

coverage-xml:
	./gradlew koverXmlReport

publish-local:
	./gradlew publishToMavenLocal

publish-local-snapshot:
	./gradlew -PoverrideVersion=$(VERSION)-SNAPSHOT publishToMavenLocal

GPG_ENV = \
	ORG_GRADLE_PROJECT_signingInMemoryKey="$$(gpg --armor --export-secret-keys $$GPG_SIGNING_KEY_ID)" \
	ORG_GRADLE_PROJECT_signingInMemoryKeyPassword=$$(security find-generic-password -a "gpg-signing" -s "gradle-signing-password" -w)

check-gpg-env:
	@if [ -z "$$GPG_SIGNING_KEY_ID" ]; then \
		echo "Error: GPG_SIGNING_KEY_ID is not set" >&2; exit 1; \
	fi
	@if ! gpg --list-secret-keys "$$GPG_SIGNING_KEY_ID" >/dev/null 2>&1; then \
		echo "Error: no GPG secret key found for GPG_SIGNING_KEY_ID=$$GPG_SIGNING_KEY_ID" >&2; exit 1; \
	fi
	@if ! security find-generic-password -a "gpg-signing" -s "gradle-signing-password" -w >/dev/null 2>&1; then \
		echo "Error: keychain entry 'gradle-signing-password' (account 'gpg-signing') not found" >&2; exit 1; \
	fi

publish-snapshot: check-gpg-env
	$(GPG_ENV) ./gradlew -PoverrideVersion=$(VERSION)-SNAPSHOT publishToMavenCentral

publish-maven-central: check-gpg-env
	$(GPG_ENV) ./gradlew publishAndReleaseToMavenCentral

upgrade-wrapper:
	./gradlew wrapper --gradle-version=9.5.0 --distribution-type=bin
