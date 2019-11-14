default: versioncheck

clean:
	./gradlew clean

compile:
	./gradlew build -xtest

publish:
	./gradlew publish

tests:
	./gradlew check

versioncheck:
	./gradlew dependencyUpdates

