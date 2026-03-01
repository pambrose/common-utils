VERSION := $(shell grep 'extra\["versionStr"\]' build.gradle.kts | sed 's/.*"\(.*\)"/\1/')

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

trigger-build:
	curl -s "https://jitpack.io/com/github/common-utils/common-utils/$(VERSION)/build.log"

view-build:
	curl -s "https://jitpack.io/api/builds/com.github.common-utils/common-utils/$(VERSION)" | python3 -m json.tool

versioncheck:
	./gradlew dependencyUpdates

upgrade-wrapper:
	./gradlew wrapper --gradle-version=9.2.0 --distribution-type=bin
