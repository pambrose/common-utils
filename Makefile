default: versioncheck

clean:
	./gradlew clean

stop:
	./gradlew --stop

compile:
	./gradlew build -xtest

build: compile

publish:
	./gradlew publish

tests:
	./gradlew --rerun-tasks check

reports:
	./gradlew koverMergedHtmlReport

tree:
	./gradlew -q dependencies

depends:
	./gradlew dependencies

refresh:
	./gradlew --refresh-dependencies dependencyUpdates

lint:
	./gradlew lintKotlinMain
	./gradlew lintKotlinTest

versioncheck:
	./gradlew dependencyUpdates

upgrade-wrapper:
	./gradlew wrapper --gradle-version=8.8 --distribution-type=bin
