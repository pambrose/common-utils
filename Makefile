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

refresh:
	./gradlew --refresh-dependencies dependencyUpdates

versioncheck:
	./gradlew dependencyUpdates