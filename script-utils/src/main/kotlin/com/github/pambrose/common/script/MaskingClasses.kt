package com.github.pambrose.common.script

import javax.script.ScriptException

class System {
  companion object {
    fun exit(status: Int) {
      throw ScriptException("Illegal call to System.exit()")
    }
  }
}