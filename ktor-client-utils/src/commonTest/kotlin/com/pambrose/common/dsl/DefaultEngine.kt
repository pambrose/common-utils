package com.pambrose.common.dsl

/**
 * Whether `HttpClient {}` finds an engine on this platform, which the client-creating paths of [KtorDsl] need.
 *
 * The module declares no engine. The JVM tests add CIO, and ktor-client-core falls back to its bundled Js engine
 * on js and wasmJs, but Kotlin/Native has no fallback, so those paths throw there (see `KtorDslNativeTests`).
 */
internal expect val hasDefaultEngine: Boolean
