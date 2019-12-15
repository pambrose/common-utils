package com.github.pambrose.common.script

import com.github.pambrose.common.script.KtsScript.Companion.SYSTEM_ERROR
import javax.script.ScriptException

class System {
  companion object {
    fun exit(status: Int) {
      throw ScriptException(SYSTEM_ERROR)
    }
  }
}