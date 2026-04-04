@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.script

import com.pambrose.common.script.ScriptUtils.engineBindings
import com.pambrose.common.script.ScriptUtils.globalBindings
import com.pambrose.common.script.ScriptUtils.resetContext
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import javax.script.Bindings
import javax.script.ScriptContext
import javax.script.ScriptEngine
import javax.script.SimpleBindings

class ScriptUtilsTests : StringSpec() {
  init {
    "engineBindings returns ENGINE_SCOPE bindings" {
      val expectedBindings = SimpleBindings()
      val engine = mockk<ScriptEngine>()
      every { engine.getBindings(ScriptContext.ENGINE_SCOPE) } returns expectedBindings

      (engine.engineBindings === expectedBindings) shouldBe true
      verify { engine.getBindings(ScriptContext.ENGINE_SCOPE) }
    }

    "globalBindings returns GLOBAL_SCOPE bindings" {
      val expectedBindings = SimpleBindings()
      val engine = mockk<ScriptEngine>()
      every { engine.getBindings(ScriptContext.GLOBAL_SCOPE) } returns expectedBindings

      (engine.globalBindings === expectedBindings) shouldBe true
      verify { engine.getBindings(ScriptContext.GLOBAL_SCOPE) }
    }

    "resetContext creates a fresh context" {
      val engine = mockk<ScriptEngine>(relaxed = true)
      every { engine.createBindings() } returns SimpleBindings()

      engine.resetContext()

      verify { engine.createBindings() }
      verify { engine.context = any() }
    }

    "setting a binding in engine scope is accessible" {
      val bindings = SimpleBindings()
      val engine = mockk<ScriptEngine>()
      every { engine.getBindings(ScriptContext.ENGINE_SCOPE) } returns bindings

      bindings["testKey"] = "testValue"
      engine.engineBindings["testKey"] shouldBe "testValue"
    }

    "resetContext clears previously set bindings" {
      val engine = mockk<ScriptEngine>(relaxed = true)
      val freshBindings = SimpleBindings()
      every { engine.createBindings() } returns freshBindings

      engine.resetContext()

      verify(exactly = 1) { engine.context = any() }
      val capturedContext = mutableListOf<ScriptContext>()
      verify { engine.context = capture(capturedContext) }
      val newContext = capturedContext.first()
      newContext.shouldBeInstanceOf<javax.script.SimpleScriptContext>()
      newContext.getBindings(ScriptContext.ENGINE_SCOPE) shouldNotBe null
    }
  }
}
