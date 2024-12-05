package serpentine

enum CType:
  case Int
  case Float
  case String
  case Bool

  def ctype: String = this match
    case CType.Int    => "ctypes.c_int"
    case CType.Float  => "ctypes.c_float"
    case CType.String => "ctypes.c_char_p"
    case CType.Bool   => "ctypes.c_bool"

  def ptype: String = this match
    case CType.Int    => "int"
    case CType.Float  => "float"
    case CType.String => "str"
    case CType.Bool   => "bool"

final case class PythonInput(label: String, t: CType):
  def sigOf: String = s"$label: ${t.ptype}"

  def codeOf: String = t match
    case serpentine.CType.Int    => label
    case serpentine.CType.Float  => label
    case serpentine.CType.String => s"$label.encode('utf-8')"
    case CType.Bool              => label

final case class PythonFunction(
    name: String,
    inputs: List[PythonInput],
    output: CType
):
  def sigOf: String =
    s"""|lib.$name.argtypes = [${inputs.map(_.t.ctype).mkString(", ")}]
        |lib.$name.restype  = ${output.ctype}
        |""".stripMargin

  def codeOf: String =
    s"""|def $name(${inputs.map(_.sigOf).mkString(", ")}) -> ${output.ptype}:
        |    return lib.$name(${inputs.map(_.codeOf).mkString(", ")})
        |""".stripMargin

final case class PythonLibrary(name: String, funcs: List[PythonFunction]):
  def code: String =
    s"""|import ctypes
        |import platform
        |
        |system = platform.system()
        |if system == "Windows":
        |    libname = "${name}.dll"
        |elif system == "Darwin":
        |    libname = "lib${name}.dylib"
        |elif system == "Linux":
        |    libname = "lib${name}.so"
        |else:  
        |    raise ValueError(f"Unsupported platform: {system}")
        |
        |lib = ctypes.CDLL(libname)
        |
        |${funcs.map(_.sigOf).mkString("\n")}
        |
        |${funcs.map(_.codeOf).mkString("\n")}
        |""".stripMargin
