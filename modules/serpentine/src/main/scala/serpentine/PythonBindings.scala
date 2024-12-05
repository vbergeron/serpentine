package serpentine

import mirrorops.OpsMirror

sealed trait PythonBindings[A]

object PythonBindings:

  sealed class Impl[A]() extends PythonBindings[A]

  inline def derived[A](using mirror: OpsMirror.Of[A]): PythonBindings[A] =
    ${ PythonBindingsMacros.derived[A]('mirror) }
