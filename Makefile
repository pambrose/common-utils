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
	curl -s "https://jitpack.io/com/github/common-utils/common-utils/2.5.4/build.log"

view-build:
	curl -s "https://jitpack.io/api/builds/com.github.common-utils/common-utils/2.5.4" | python3 -m json.tool

versioncheck:
	./gradlew dependencyUpdates

upgrade-wrapper:
	./gradlew wrapper --gradle-version=9.2.0 --distribution-type=bin
