VERSION := $(shell grep -E '^[[:space:]]*version[[:space:]]*=' build.gradle.kts | head -1 | sed 's/.*"\(.*\)"/\1/')

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
	./gradlew --refresh-dependencies dependencyUpdates

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

publish-local:
	./gradlew publishToMavenLocal

publish-local-snapshot:
	./gradlew -PoverrideVersion=$(VERSION)-SNAPSHOT publishToMavenLocal

GPG_ENV = \
	ORG_GRADLE_PROJECT_signingInMemoryKey="$$(gpg --armor --export-secret-keys $$GPG_SIGNING_KEY_ID)" \
	ORG_GRADLE_PROJECT_signingInMemoryKeyPassword=$$(security find-generic-password -a "gpg-signing" -s "gradle-signing-password" -w)

publish-snapshot:
	$(GPG_ENV) ./gradlew -PoverrideVersion=$(VERSION)-SNAPSHOT publishToMavenCentral

publish-maven-central:
	$(GPG_ENV) ./gradlew publishAndReleaseToMavenCentral

upgrade-wrapper:
	./gradlew wrapper --gradle-version=9.4.1 --distribution-type=bin
