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
`rediss://...` for TLS. It defaults to the `REDIS_URL` environment variable, or `redis://user:none@localhost:6379`
when that is unset. The user is sent with `AUTH` only when it is a real one: not blank, `default` or `user`, with
a password other than `none`.

### Short-lived Clients

The `withRedis` family creates a client, runs the block, and closes the client. When the connection fails,
`withRedis` passes `null` to the block, while `withNonNullRedis` skips the block and returns `null`.

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

| Parameter | System property | Default |
|-----------|-----------------|---------|
| `maxPoolSize` | `redis.maxPoolSize` | 10 |
| `maxIdleSize` | `redis.maxIdleSize` | 5 |
| `minIdleSize` | `redis.minIdleSize` | 1 |
| `maxWaitSecs` | `redis.maxWaitSecs` | 1 |

The `withRedisPool` family pings the client first. When that fails, it logs the error, including the stack trace
when `printStackTrace = true`, and then passes `null` to the block or returns `null`.

### Scanning Keys

```kotlin
import com.pambrose.common.redis.RedisUtils.scanKeys

client.scanKeys("user:*").forEach { key -> println(key) }
```

`count`, which defaults to 100, is a hint for how many keys each `SCAN` call returns.

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
- Constants `REDIS_MAX_POOL_SIZE`, `REDIS_MAX_IDLE_SIZE`, `REDIS_MIN_IDLE_SIZE`, `REDIS_MAX_WAIT_SECS`

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
