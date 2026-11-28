@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.script

import com.pambrose.common.script.ScriptUtils.bindings
import com.pambrose.common.script.ScriptUtils.engineBindings
import com.pambrose.common.script.ScriptUtils.globalBindings
import com.pambrose.common.script.ScriptUtils.resetContext
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import javax.script.ScriptContext
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager
import javax.script.SimpleBindings
import javax.script.SimpleScriptContext

class ScriptUtilsTests : StringSpec() {
  init {
    accessorTests()
    resetContextTests()
    releaseTests()
  }

  private fun accessorTests() {
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
  }

  private fun resetContextTests() {
    "resetContext creates a fresh context" {
      val oldBindings = SimpleBindings(mutableMapOf<String, Any>("key" to 1))
      val engine = mockk<ScriptEngine>(relaxed = true)
      every { engine.createBindings() } returns SimpleBindings()
      every { engine.getBindings(ScriptContext.ENGINE_SCOPE) } returns oldBindings

      engine.resetContext()

      verify { engine.createBindings() }
      verify { engine.context = any() }
      oldBindings.isEmpty() shouldBe true
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

  private fun releaseTests() {
    // The Kotlin and Jython engines keep the context of their last evaluation until the next one, so the bindings a
    // reset replaced stayed reachable through it.
    "resetContext empties the engine bindings it replaces" {
      val engine = FakeScriptEngineFactory().scriptEngine as FakeScriptEngine
      engine.resetContext()
      engine.put("key", "value")
      engine.eval("key") shouldBe "value"
      val previous = engine.lastContext.shouldNotBeNull()

      engine.resetContext()

      previous.getBindings(ScriptContext.ENGINE_SCOPE).isEmpty() shouldBe true
      (engine.context === previous) shouldBe false
    }

    // ScriptEngineManager gives every engine it creates the same global bindings, so they must survive a reset.
    "resetContext leaves the replaced global bindings alone" {
      val manager = ScriptEngineManager(FakeScriptEngineFactory::class.java.classLoader)
      manager.put("shared", 1)
      val engine = manager.getEngineByExtension(FAKE_EXTENSION).shouldNotBeNull()
      (engine.globalBindings === manager.bindings) shouldBe true

      engine.resetContext()

      manager.bindings["shared"] shouldBe 1
      (engine.globalBindings === manager.bindings) shouldBe false
    }
  }
}
