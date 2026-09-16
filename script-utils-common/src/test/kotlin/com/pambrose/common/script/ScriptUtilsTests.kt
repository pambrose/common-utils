@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.script

import com.pambrose.common.script.ScriptUtils.bindings
import com.pambrose.common.script.ScriptUtils.engineBindings
import com.pambrose.common.script.ScriptUtils.globalBindings
import com.pambrose.common.script.ScriptUtils.resetContext
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import javax.script.ScriptContext
import javax.script.ScriptEngine
import javax.script.SimpleBindings
import javax.script.SimpleScriptContext

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

    "bindings defaults to the engine scope and reads the engine's own bindings" {
      val engine = FakeScriptEngineFactory().scriptEngine
      engine.resetContext()
      engine.put("engineKey", "engineValue")
      engine.bindings()?.get("engineKey") shouldBe "engineValue"
      engine.engineBindings["engineKey"] shouldBe "engineValue"

      engine.globalBindings.shouldNotBeNull()["globalKey"] = "globalValue"
      engine.context.getAttributesScope("globalKey") shouldBe ScriptContext.GLOBAL_SCOPE
      engine.eval("globalKey") shouldBe "globalValue"
    }

    "resetContext replaces the bindings in both scopes" {
      val engine = FakeScriptEngineFactory().scriptEngine
      engine.resetContext()
      val oldGlobals = engine.globalBindings.shouldNotBeNull()
      engine.engineBindings["engineKey"] = 1
      oldGlobals["globalKey"] = 2

      engine.resetContext()

      engine.context.shouldBeInstanceOf<SimpleScriptContext>()
      engine.engineBindings.isEmpty() shouldBe true
      engine.globalBindings.shouldNotBeNull().isEmpty() shouldBe true
      (engine.globalBindings === oldGlobals) shouldBe false
      engine.context.getAttributesScope("engineKey") shouldBe -1
      engine.context.getAttributesScope("globalKey") shouldBe -1
    }

    "resetContext with nullGlobalContext leaves the global scope null" {
      val engine = FakeScriptEngineFactory().scriptEngine
      engine.resetContext(nullGlobalContext = true)
      engine.getBindings(ScriptContext.GLOBAL_SCOPE) shouldBe null
      // Reading the null global scope used to throw a NullPointerException.
      engine.globalBindings shouldBe null
      engine.bindings(ScriptContext.GLOBAL_SCOPE) shouldBe null
      engine.engineBindings.isEmpty() shouldBe true
    }
  }
}
