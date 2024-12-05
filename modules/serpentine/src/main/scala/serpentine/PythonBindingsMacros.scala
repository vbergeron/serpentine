package serpentine

import quoted.*
import mirrorops.*
import java.nio.file.*

object PythonBindingsMacros {

  /*
  void 	Unit
  bool 	unsafe.CBool
  char 	unsafe.CChar
  signed char 	unsafe.CSignedChar
  unsigned char 	unsafe.CUnsignedChar[^1]
  short 	unsafe.CShort
  unsigned short 	unsafe.CUnsignedShort[^2]
  int 	unsafe.CInt
  long int 	unsafe.CLongInt
  unsigned int 	unsafe.CUnsignedInt[^3]
  unsigned long int 	unsafe.CUnsignedLongInt[^4]
  long 	unsafe.CLong
  unsigned long 	unsafe.CUnsignedLong[^5]
  long long 	unsafe.CLongLong
  unsigned long long 	unsafe.CUnsignedLongLong[^6]
  size_t 	unsafe.CSize
  ssize_t 	unsafe.CSSize
  ptrdiff_t 	unsafe.CPtrDiff[^7]
  wchar_t 	unsafe.CWideChar
  char16_t 	unsafe.CChar16
  char32_t 	unsafe.CChar32
  float 	unsafe.CFloat
  double 	unsafe.CDouble
   */

  def intoPythonType[A](t: Type[A])(using Quotes): CType =
    t match
      case '[Int]                        => CType.Int
      case '[Float]                      => CType.Float
      case '[Double]                     => CType.Double
      case '[scalanative.unsafe.CString] => CType.String
      case '[Boolean]                    => CType.Bool
      case '[t]                          =>
        quotes.reflect.report.errorAndAbort(s"Unsupported type: ${Type.show[t]}")

  def findSuitableDirectory(using Quotes): Path =
    import quotes.reflect.*
    val position = Position.ofMacroExpansion

    @annotation.tailrec
    def findHome(path: Path): Option[Path] =
      val parent = Option(path.getParent())
      if path.endsWith("src")
      then parent
      else
        parent match
          case Some(path) => findHome(path)
          case None       => None

    position.sourceFile.getJPath.flatMap(findHome).getOrElse(Paths.get("."))

  def derived[A](mirror: Expr[OpsMirror.Of[A]])(using Quotes, Type[A]): Expr[PythonBindings[A]] =
    import quotes.reflect.*
    mirror match
      case '{
            $m: OpsMirror.Of[A] {
              type MirroredOperations      = ops
              type MirroredOperationLabels = labels
            }
          } =>
        val labels = OpsMirror.stringsFromTuple[labels]

        val funcs = OpsMirror.typesFromTuple[ops].map { case '[op] =>
          Type.of[op] match
            case '[Operation {
                  type InputLabels = labels
                  type InputTypes  = itypes
                  type OutputType  = otype

                  type Metadata = meta
                }] =>
              report.info(s"meta: ${OpsMirror
                  .typesFromTuple[meta]
                  .map:
                    case '[t] => TypeRepr.of[t].show
                }")
              val labels = OpsMirror.stringsFromTuple[labels]
              val itypes = OpsMirror.typesFromTuple[itypes].map { case '[t] =>
                intoPythonType(Type.of[t])
              }

              val inputs = (labels zip itypes).map: (label, itype) =>
                PythonInput(label, itype)

              val output = intoPythonType(Type.of[otype])

              (inputs, output)
        }

        val functions =
          (labels zip funcs).map:
            case (name, (inputs, output)) =>
              PythonFunction(name, inputs, output)

        val libname =
          Type.show[A].split('.').filterNot(_ == "type").last.toLowerCase()

        val library = PythonLibrary(libname, functions)

        val path = findSuitableDirectory.resolve(s"$libname.py")
        report.info(s"Bindings located: $path")
        Files.writeString(path, library.code)

        '{ PythonBindings.Impl[A]() }

}
