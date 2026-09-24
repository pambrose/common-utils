# Redis Utils

Helpers for the Jedis client: create pooled or short-lived `RedisClient`s from a Redis URL, run code against them
without handling connection failures yourself, and scan keys lazily.

## Features

### Short-lived Clients

- **`withRedis { }` / `withNonNullRedis { }`**: create a client, run a block, and close the client
- **`withSuspendingRedis { }` / `withSuspendingNonNullRedis { }`**: the same for suspending blocks

### Pooled Clients

- **`newRedisClient(...)`**: a pooled `RedisClient` configured from a URL and pool settings
- **`withRedisPool { }` / `withNonNullRedisPool { }`**: ping an existing client, then run a block
- **`withSuspendingRedisPool { }` / `withSuspendingNonNullRedisPool { }`**: the same for suspending blocks

### Keys

- **`scanKeys(pattern)`**: a lazy `Sequence` of matching keys, built on `SCAN`

## Usage Examples

### Connection URLs

Every function that creates a client takes a Redis URL such as `redis://user:password@host:port`, or
`rediss://...` for TLS, matched case-insensitively. It defaults to the `REDIS_URL` environment variable, or
`redis://user:none@localhost:6379` when that is unset.

The whole URL is read, not just the host and port:

| URL part      | Effect                            |
|---------------|-----------------------------------|
| `/3` path     | selects database 3                |
| `?protocol=3` | selects the RESP protocol version |
| userinfo      | sent with `AUTH`                  |

The password is sent only when it is a real one: a blank password and the placeholder `none` both count as no
password. The user is sent only alongside a real password, and only when the name itself is a real one — not
blank, `default` or `user`.

A URL without a port connects to 6379. A URL that can never work throws `IllegalArgumentException` from every
function that takes one, before any block runs; it is a configuration error, not a connection failure, so it does
not take the `null` path described below. That covers a malformed URL, one with no host (such as
`localhost:6379`, which parses as the scheme `localhost`), a non-numeric database index and an unknown
`protocol`. The message of a malformed-URL exception leaves out the URL, which may hold a password.

### Short-lived Clients

The `withRedis` family creates a client, pings the server to verify the connection, runs the block, and closes
the client. When the connection fails, `withRedis` passes `null` to the block, while `withNonNullRedis` skips the
block and returns `null`. Building a Jedis client does not report an unreachable server, so the ping is what
makes that promise hold for an unreachable server, a pool that cannot lend a connection, or a rejected password. A client that fails
the ping is closed rather than handed to the block.

```kotlin
import com.pambrose.common.redis.RedisUtils.withNonNullRedis
import com.pambrose.common.redis.RedisUtils.withRedis

val visits = withRedis { redis -> redis?.incr("visits") ?: -1L }

val name: String? = withNonNullRedis("redis://localhost:6379") { redis -> redis.get("name") }
```

### Pooled Clients

```kotlin
import com.pambrose.common.redis.RedisUtils.newRedisClient
import com.pambrose.common.redis.RedisUtils.withNonNullRedisPool

val client = newRedisClient(maxPoolSize = 20)

client.withNonNullRedisPool { redis -> redis.set("status", "ready") }
```

Each pool setting defaults to a system property, and then to a fixed value:

| Parameter     | System property     | Default |
|---------------|---------------------|---------|
| `maxPoolSize` | `redis.maxPoolSize` | 10      |
| `maxIdleSize` | `redis.maxIdleSize` | 5       |
| `minIdleSize` | `redis.minIdleSize` | 1       |
| `maxWaitSecs` | `redis.maxWaitSecs` | 1       |

`maxPoolSize` must be positive, or `UNLIMITED_POOL_SIZE` (-1) for no limit; 0 would create a pool that can never
lend a connection. `maxIdleSize` also accepts -1 for no limit. A `minIdleSize` above `maxIdleSize` is lowered to it
by commons-pool2, and a warning is logged. Pooled connections are validated when they are borrowed and while they sit idle, but not when
they are returned, which would add a second round-trip to every command.

The `withRedisPool` family pings the client first. When that fails — including pool exhaustion and authentication
failures, which arrive as a plain `JedisException` — it logs the error with the exception's message, including the
stack trace when `printStackTrace = true`, and then passes `null` to the block or returns `null`.

The suspending variants connect, ping and close on `Dispatchers.IO`, since each of those is a blocking Jedis call
that can take the full 2-second timeout when Redis is down. The block itself runs in the caller's context, so
Jedis calls inside it still block their thread: wrap them in `withContext(Dispatchers.IO)` when calling from a
limited dispatcher such as `Dispatchers.Default` or a Ktor event loop.

### Scanning Keys

```kotlin
import com.pambrose.common.redis.RedisUtils.scanKeys

client.scanKeys("user:*").forEach { key -> println(key) }
```

`count`, which defaults to 100, is a hint for how many keys each `SCAN` call returns.

`scanKeys` is for a standalone server. On a cluster client (`RedisClusterClient`) Jedis sends `SCAN` to one node:
it rejects a pattern without a `{hash-tag}`, and with one it returns only that tag's slot. Use Jedis'
`scanIteration` to scan a whole cluster.

## API Reference

### `RedisUtils`

- `newRedisClient(redisUrl, maxPoolSize, maxIdleSize, minIdleSize, maxWaitSecs): RedisClient`
- `fun <T> withRedis(redisUrl, printStackTrace = false, block: (RedisClient?) -> T): T`
- `fun <T> withNonNullRedis(redisUrl, printStackTrace = false, block: (RedisClient) -> T): T?`
- `suspend fun <T> withSuspendingRedis(...)` and `withSuspendingNonNullRedis(...)`
- `fun <T> RedisClient.withRedisPool(printStackTrace = false, block: (RedisClient?) -> T): T`
- `fun <T> RedisClient.withNonNullRedisPool(printStackTrace = false, block: (RedisClient) -> T): T?`
- `suspend fun <T> RedisClient.withSuspendingRedisPool(...)` and `withSuspendingNonNullRedisPool(...)`
- `fun UnifiedJedis.scanKeys(pattern: String, count: Int = 100): Sequence<String>`
- `class RedisInfo(uri, user, password)` — the parsed URL, with `includeUserInAuth`
- Constants `REDIS_MAX_POOL_SIZE`, `REDIS_MAX_IDLE_SIZE`, `REDIS_MIN_IDLE_SIZE`, `REDIS_MAX_WAIT_SECS`,
  `UNLIMITED_POOL_SIZE`

## Dependencies

This module depends on:

- Kotlin Standard Library
- core-utils
- Jedis (`redis.clients:jedis`)

## Installation

[![Maven Central](https://img.shields.io/maven-central/v/com.pambrose.common-utils/redis-utils)](https://central.sonatype.com/artifact/com.pambrose.common-utils/redis-utils)

### Gradle

```kotlin
dependencies {
  implementation("com.pambrose.common-utils:redis-utils:LATEST_VERSION")
}
```

### Maven

```xml
<dependency>
  <groupId>com.pambrose.common-utils</groupId>
  <artifactId>redis-utils</artifactId>
  <version>LATEST_VERSION</version>
</dependency>
```

## License

Licensed under the Apache License, Version 2.0.
