default: versioncheck

clean:
	./gradlew clean

compile:
	./gradlew build -xtest

build: compile

publish:
	./gradlew publish

tests:
	./gradlew --rerun-tasks check

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
	./gradlew wrapper --gradle-version=6.8.3 --distribution-type=bin