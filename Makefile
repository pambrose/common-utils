VERSION := $(shell grep 'extra\["versionStr"\]' build.gradle.kts | sed 's/.*"\(.*\)"/\1/')
JITPACK_BUILD_LOG := https://jitpack.io/com/github/pambrose/common-utils/$(VERSION)/build.log
JITPACK_API_URL := https://jitpack.io/api/builds/com.github.pambrose/common-utils/$(VERSION)

default: versioncheck

clean:
	./gradlew clean

stop:
	./gradlew --stop

compile:
	./gradlew build -xtest

build: compile

refresh:
	./gradlew --refresh-dependencies dependencyUpdates

publish:
	./gradlew publish

publishLocal:
	./gradlew publishToMavenLocal

tests:
	./gradlew --rerun-tasks check

reports:
	./gradlew koverMergedHtmlReport

tree:
	./gradlew -q dependencies

depends:
	./gradlew dependencies

lint:
	./gradlew lintKotlinMain
	./gradlew lintKotlinTest

trigger-jitpack:
	until curl -s "${JITPACK_BUILD_LOG}" | grep -qv "not found"; do \
		echo "Waiting for JitPack..."; \
		sleep 10; \
	done
	echo "JitPack build complete for version ${VERSION}"

view-jitpack:
	curl -s "${JITPACK_BUILD_LOG}"
	curl -s "${JITPACK_API_URL}" | jq

versioncheck:
	./gradlew dependencyUpdates --no-configuration-cache

upgrade-wrapper:
	./gradlew wrapper --gradle-version=9.4.0 --distribution-type=bin
