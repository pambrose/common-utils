default: versioncheck

clean:
	./gradlew clean

compile:
	./gradlew build -x test

publish:
	./gradlew publish

tests:
	./gradlew check

versioncheck:
	./gradlew dependencyUpdates

