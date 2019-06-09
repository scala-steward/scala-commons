package com.avsystem.commons
package jiop

import scala.collection.convert.{AsJavaExtensions, AsScalaExtensions}

trait JavaInterop extends AnyRef
  with JBasicUtils
  with JCollectionUtils
  with AsJavaExtensions
  with AsScalaExtensions

object JavaInterop extends JavaInterop
