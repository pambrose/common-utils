# Change: Review Codebase for Bugs and Add Tests

## Why

The common-utils library spans 20+ modules providing utilities for various frameworks. A systematic code review will
identify potential bugs, edge cases, and areas lacking test coverage. Adding comprehensive tests improves reliability
and prevents regressions as the library evolves.

## What Changes

- Systematic review of all modules for common bug patterns:
  - Null safety issues
  - Resource leaks (unclosed streams, connections)
  - Concurrency bugs (race conditions, improper synchronization)
  - Error handling gaps
  - Edge case handling (empty collections, boundary values)
- Create or expand test suites for each module
- Document any bugs found and their fixes
- Improve test coverage across the library

## Impact

- Affected specs: code-quality (new capability spec)
- Affected code: All modules in the library
  - core-utils
  - dropwizard-utils
  - email-utils
  - exposed-utils
  - grpc-utils
  - guava-utils
  - json-utils
  - jetty-utils
  - ktor-client-utils
  - ktor-server-utils
  - prometheus-utils
  - recaptcha-utils
  - redis-utils
  - script-utils-common
  - script-utils-python
  - script-utils-java
  - script-utils-kotlin
  - service-utils
  - zipkin-utils

## Success Criteria

- All identified bugs are fixed with corresponding regression tests
- Each module has at least basic test coverage for public APIs
- All tests pass: `./gradlew test`
- No new linting violations: `make lint`
