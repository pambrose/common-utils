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

# 6.9.1
upgrade-wrapper:
	./gradlew wrapper --gradle-version=7.3.1 --distribution-type=bin