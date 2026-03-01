# Jetty Upgrade: 10.0.26 → 11.0.26

## Overview

Upgraded Eclipse Jetty from 10.0.26 to 11.0.26. Jetty 11 is architecturally identical to Jetty 10
but migrates from the `javax.servlet` namespace to `jakarta.servlet`, aligning with the Jakarta EE transition.

## Dependency Changes (gradle/libs.versions.toml)

| Dependency                               | Before           | After                                                   |
|------------------------------------------|------------------|---------------------------------------------------------|
| `org.eclipse.jetty:jetty-server`         | 10.0.26          | 11.0.26                                                 |
| `org.eclipse.jetty:jetty-servlet`        | 10.0.26          | 11.0.26                                                 |
| `io.prometheus:simpleclient_servlet`     | 0.16.0           | replaced by `simpleclient_servlet_jakarta` 0.16.0       |
| `io.dropwizard.metrics:metrics-servlets` | 4.2.38 (via BOM) | replaced by `metrics-jakarta-servlets` 4.2.38 (via BOM) |

The Prometheus and Dropwizard servlet artifacts were switched to their Jakarta-compatible variants,
which are covered by the same BOMs at the same versions.

## Source Changes

### javax.servlet → jakarta.servlet (4 files)

All `javax.servlet` imports were replaced with `jakarta.servlet`:

- `jetty-utils/src/main/kotlin/.../servlet/LambdaServlet.kt`
- `jetty-utils/src/main/kotlin/.../servlet/VersionServlet.kt`
- `service-utils/src/main/kotlin/.../service/ServletGroup.kt`
- `service-utils/src/test/kotlin/.../service/ServletGroupTests.kt`

### Package renames from Jakarta-migrated libraries (2 files)

The Jakarta variants of Prometheus and Dropwizard use different Java packages:

- `service-utils/src/main/kotlin/.../service/GenericService.kt`
  - `com.codahale.metrics.servlets.HealthCheckServlet` → `io.dropwizard.metrics.servlets.HealthCheckServlet`
  - `com.codahale.metrics.servlets.PingServlet` → `io.dropwizard.metrics.servlets.PingServlet`
  - `com.codahale.metrics.servlets.ThreadDumpServlet` → `io.dropwizard.metrics.servlets.ThreadDumpServlet`
- `service-utils/src/main/kotlin/.../service/MetricsService.kt`
  - `io.prometheus.client.exporter.MetricsServlet` → `io.prometheus.client.servlet.jakarta.exporter.MetricsServlet`

### No changes needed

The Jetty API imports (`org.eclipse.jetty.server.Server`, `org.eclipse.jetty.servlet.ServletContextHandler`,
`org.eclipse.jetty.servlet.ServletHolder`) are unchanged between Jetty 10 and 11. The `JettyDsl.kt` DSL wrapper
and all handler/server wiring code required no modifications.
