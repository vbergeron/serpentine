# Serpentine

## Python binding generation for Scala Native

Serpentine is a scala library that automatically generates a python module to use your shared library code. Generation is done at compile time, using a modified version of [ops-mirror](https://github.com/bishabosha/ops-mirror).

### Usage

Simply use `derives PythonBindings` on an object containing your `@exported` methods.

```scala
package exemples

import serpentine.*

object Exemples derives PythonBindings:

  @scalanative.unsafe.exported
  def multiply(a: Int, b: Int): Int = a * b

  @scalanative.unsafe.exported
  def add(a: Int, b: Int): Int = a + b
```

This will generate for you this `exemples.py` file (name extracted from the object name) in the parent folder of the nearest `src` directory, or simply at the top level if none is found.

```python
import ctypes
import platform

system = platform.system()
if system == "Windows":
    libname = "exemples.dll"
elif system == "Darwin":
    libname = "libexemples.dylib"
elif system == "Linux":
    libname = "libexemples.so"
else:  
    raise ValueError(f"Unsupported platform: {system}")

lib = ctypes.CDLL(libname)

lib.multiply.argtypes = [ctypes.c_int, ctypes.c_int]
lib.multiply.restype  = ctypes.c_int

lib.add.argtypes = [ctypes.c_int, ctypes.c_int]
lib.add.restype  = ctypes.c_int


def multiply(a: int, b: int) -> int:
    return lib.multiply(a, b)

def add(a: int, b: int) -> int:
    return lib.add(a, b)
```

As the bindings are generated at scala-compile time, they will evolve and change as you modify your code.

