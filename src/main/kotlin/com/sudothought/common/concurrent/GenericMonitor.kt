/*
 *
 *  Copyright © 2019 Paul Ambrose (pambrose@mac.com)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 */

@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.sudothought.common.concurrent

import com.google.common.util.concurrent.Monitor
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.time.Duration
import kotlin.time.MonoClock
import kotlin.time.seconds

typealias MonitorAction = () -> Boolean

abstract class GenericMonitor {

    protected val monitor = Monitor()

    private val trueValueGuard =
        object : Monitor.Guard(monitor) {
            override fun isSatisfied() = monitorSatisfied
        }

    private val falseValueGuard =
        object : Monitor.Guard(monitor) {
            override fun isSatisfied() = !monitorSatisfied
        }

    abstract val monitorSatisfied: Boolean

    fun waitUntilTrue() =
        try {
            monitor.enterWhenUninterruptibly(trueValueGuard)
        } finally {
            monitor.leave()
        }

    @Throws(InterruptedException::class)
    fun waitUntilTrueWithInterruption() =
        try {
            monitor.enterWhen(trueValueGuard)
        } finally {
            if (monitor.isOccupiedByCurrentThread)
                monitor.leave()
        }

    fun waitUntilTrue(waitTime: Duration): Boolean {
        var satisfied = false
        try {
            satisfied = monitor.enterWhenUninterruptibly(trueValueGuard, waitTime.toLongMilliseconds(), MILLISECONDS)
        } finally {
            if (satisfied)
                monitor.leave()
        }
        return satisfied
    }

    @Throws(InterruptedException::class)
    fun waitUntilTrueWithInterruption(waitTime: Duration): Boolean {
        var satisfied = false
        try {
            satisfied = monitor.enterWhen(trueValueGuard, waitTime.toLongMilliseconds(), MILLISECONDS)
        } finally {
            if (satisfied)
                monitor.leave()
        }
        return satisfied
    }

    fun waitUntilFalse() =
        try {
            monitor.enterWhenUninterruptibly(falseValueGuard)
        } finally {
            monitor.leave()
        }

    fun waitUntilFalse(waitTime: Duration): Boolean {
        var satisfied = false
        try {
            satisfied = monitor.enterWhenUninterruptibly(falseValueGuard, waitTime.toLongMilliseconds(), MILLISECONDS)
        } finally {
            if (satisfied)
                monitor.leave()
        }
        return satisfied
    }

    fun waitUntilTrue(timeout: Duration, block: MonitorAction) =
        waitUntilTrue(timeout, (-1).seconds, block)

    fun waitUntilTrue(timeout: Duration, maxWait: Duration, block: MonitorAction?): Boolean {
        val start = MonoClock.markNow()
        while (true) {
            when {
                waitUntilTrue(timeout) -> return true
                maxWait > 0.seconds && start.elapsedNow() >= maxWait -> return false
                else ->
                    block?.also {
                        val continueToWait = it.invoke()
                        if (!continueToWait)
                            return false
                    }
            }
        }
    }

    @Throws(InterruptedException::class)
    fun waitUntilTrueWithInterruption(timeout: Duration, block: MonitorAction) =
        waitUntilTrueWithInterruption(timeout, (-1).seconds, block)

    @Throws(InterruptedException::class)
    fun waitUntilTrueWithInterruption(timeout: Duration, maxWait: Duration, block: MonitorAction?): Boolean {
        val start = MonoClock.markNow()
        while (true) {
            when {
                waitUntilTrueWithInterruption(timeout) -> return true
                maxWait > 0.seconds && start.elapsedNow() >= maxWait -> return false
                else ->
                    block?.also {
                        val continueToWait = it.invoke()
                        if (!continueToWait)
                            return false
                    }
            }
        }
    }

    fun waitUntilFalse(timeout: Duration, block: MonitorAction) =
        waitUntilFalse(timeout, (-1).seconds, block)

    fun waitUntilFalse(timeout: Duration, maxWait: Duration, block: MonitorAction?): Boolean {
        val start = MonoClock.markNow()
        while (true) {
            when {
                waitUntilFalse(timeout) -> return true
                maxWait > 0.seconds && start.elapsedNow() >= maxWait -> return false
                else ->
                    block?.also {
                        val continueToWait = it.invoke()
                        if (!continueToWait)
                            return false
                    }
            }
        }
    }

    fun waitUntil(value: Boolean, waitTime: Duration) =
        if (value) waitUntilTrue(waitTime) else waitUntilFalse(waitTime)

    fun waitUntil(value: Boolean) = if (value) waitUntilTrue() else waitUntilFalse()
}