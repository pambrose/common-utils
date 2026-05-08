.PHONY: default clean stop compile build lint detekt detekt-baseline refresh tests tree depends versioncheck kdocs \
	coverage coverage-xml check-gpg-env publish-local publish-local-snapshot publish-snapshot publish-maven-central \
	upgrade-wrapper

VERSION := $(shell sed -n 's/^version=\(.*\)/\1/p' gradle.properties)
GRADLE_VERSION := $(shell sed -n 's/^gradle = "\(.*\)"/\1/p' gradle/libs.versions.toml)

default: versioncheck

clean:
	./gradlew clean

stop:
	./gradlew --stop

compile:
	./gradlew build -xtest

build: compile

lint:
	./gradlew lintKotlinMain lintKotlinTest detekt

detekt-baseline:
	./gradlew detektBaseline

coverage: coverage-html coverage-xml

coverage-html:
	./gradlew koverHtmlReport

coverage-xml:
	./gradlew koverXmlReport

coverage-log:
	./gradlew koverLog

coverage-verify:
	./gradlew koverVerify

coverage-open: coverage-html
	open build/reports/kover/html/index.html

coverage-packages: coverage-xml
	@python3 -c "import xml.etree.ElementTree as ET; \
r = ET.parse('build/reports/kover/report.xml').getroot(); \
pkgs = []; \
[pkgs.append((p.get('name'), int(c.get('covered')), int(c.get('missed')))) \
 for p in r.findall('package') for c in p.findall('counter') if c.get('type') == 'INSTRUCTION']; \
pkgs.sort(key=lambda x: -x[2]); \
print(f\"{'package':<55} {'cov%':>6} {'covered':>9} {'missed':>9} {'total':>9}\"); \
[print(f'{n:<55} {(c/(c+m)*100 if c+m else 0):6.1f} {c:9d} {m:9d} {c+m:9d}') for n,c,m in pkgs]; \
tc=sum(p[1] for p in pkgs); tm=sum(p[2] for p in pkgs); \
print(f'\nOVERALL: {tc/(tc+tm)*100:.2f}% ({tc}/{tc+tm} instructions, {tm} missed)')"

coverage-clean:
	./gradlew cleanAllTests
	rm -rf build/reports/kover build/kover

refresh:
	./gradlew --refresh-dependencies dependencyUpdates --no-configuration-cache

tests:
	./gradlew --rerun-tasks check

tree:
	./gradlew -q dependencies

depends:
	./gradlew dependencies

versioncheck:
	./gradlew dependencyUpdates --no-configuration-cache --no-parallel

kdocs:
	./gradlew :dokkaGenerate

publish-local:
	./gradlew publishToMavenLocal

publish-local-snapshot:
	./gradlew -PoverrideVersion=$(VERSION)-SNAPSHOT publishToMavenLocal

GPG_ENV = \
	ORG_GRADLE_PROJECT_signingInMemoryKey="$$(gpg --armor --export-secret-keys $$GPG_SIGNING_KEY_ID)" \
	ORG_GRADLE_PROJECT_signingInMemoryKeyPassword=$$(security find-generic-password -a "gpg-signing" -s "gradle-signing-password" -w)

check-gpg-env:
	@if [ -z "$$GPG_SIGNING_KEY_ID" ]; then \
		echo "Error: GPG_SIGNING_KEY_ID is not set" >&2; exit 1; \
	fi
	@if ! gpg --list-secret-keys "$$GPG_SIGNING_KEY_ID" >/dev/null 2>&1; then \
		echo "Error: no GPG secret key found for GPG_SIGNING_KEY_ID=$$GPG_SIGNING_KEY_ID" >&2; exit 1; \
	fi
	@if ! security find-generic-password -a "gpg-signing" -s "gradle-signing-password" -w >/dev/null 2>&1; then \
		echo "Error: keychain entry 'gradle-signing-password' (account 'gpg-signing') not found" >&2; exit 1; \
	fi

publish-snapshot: check-gpg-env
	$(GPG_ENV) ./gradlew -PoverrideVersion=$(VERSION)-SNAPSHOT publishToMavenCentral

publish-maven-central: check-gpg-env
	$(GPG_ENV) ./gradlew publishAndReleaseToMavenCentral

upgrade-wrapper:
	./gradlew wrapper --gradle-version=$(GRADLE_VERSION) --distribution-type=bin
