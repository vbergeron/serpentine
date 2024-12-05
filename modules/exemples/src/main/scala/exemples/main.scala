package exemples

import serpentine.PythonBindings

object Exemples derives PythonBindings:

  @scalanative.unsafe.exported
  def multiply(a: Int, b: Int): Int = a * b

  @scalanative.unsafe.exported
  def add(a: Int, b: Int): Int = a + b
