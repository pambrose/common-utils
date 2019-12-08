package com.github.pambrose.common.util

import java.lang.reflect.ParameterizedType
import kotlin.reflect.full.isSubclassOf

val Any.typeParameterCount: Int
  get() =
    when {
      this is Array<*> -> 1  // Must come first in evaluation
      !javaClass.genericSuperclass.javaClass.kotlin.isSubclassOf(ParameterizedType::class) -> 0
      else -> (javaClass.genericSuperclass as ParameterizedType).actualTypeArguments.size
    }
