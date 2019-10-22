default: versioncheck

clean:
	./gradlew clean

publish:
	./gradlew publish

versioncheck:
	./gradlew dependencyUpdates

