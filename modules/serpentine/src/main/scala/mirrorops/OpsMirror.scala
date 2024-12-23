package mirrorops

import quoted.*

import scala.util.chaining.given
import scala.annotation.implicitNotFound

@implicitNotFound(
  "No OpsMirror could be generated.\nDiagnose any issues by calling OpsMirror.reify[T] directly"
)
sealed trait OpsMirror:
  type Metadata <: Tuple
  type MirroredType
  type MirroredLabel
  type MirroredOperations <: Tuple
  type MirroredOperationLabels <: Tuple
end OpsMirror

sealed trait Meta

sealed trait VoidType

open class MetaAnnotation extends scala.annotation.RefiningAnnotation

sealed trait Operation:
  type Metadata <: Tuple
  type InputTypes <: Tuple
  type InputLabels <: Tuple
  type InputMetadatas <: Tuple
  type OutputType
end Operation

object OpsMirror:
  type Of[T] = OpsMirror { type MirroredType = T }

  transparent inline given reify[T]: Of[T] = ${ reifyImpl[T] }

  case class Metadata(base: List[Expr[Any]], inputs: List[List[Expr[Any]]])

  def typesFromTuple[Ts: Type](using Quotes): List[Type[?]] =
    Type.of[Ts] match
      case '[t *: ts]    => Type.of[t] :: typesFromTuple[ts]
      case '[EmptyTuple] => Nil

  def stringsFromTuple[Ts: Type](using Quotes): List[String] =
    typesFromTuple[Ts].map:
      case '[t] => stringFromType[t]

  def stringFromType[T: Type](using Quotes): String =
    import quotes.reflect.*
    TypeRepr.of[T] match
      case ConstantType(StringConstant(label)) => label
      case _                                   =>
        report.errorAndAbort(
          s"expected a constant string, got ${TypeRepr.of[T]}"
        )
    end match
  end stringFromType

  def typesToTuple(list: List[Type[?]])(using Quotes): Type[?] =
    val empty: Type[? <: Tuple] = Type.of[EmptyTuple]
    list.foldRight(empty)({ case ('[t], '[acc]) =>
      Type.of[t *: (acc & Tuple)]
    })
  end typesToTuple

  def metadata[Op: Type](using Quotes): Metadata =
    import quotes.reflect.*

    def extractMetass[Metadatas: Type]: List[List[Expr[Any]]] =
      typesFromTuple[Metadatas].map:
        case '[m] => extractMetas[m]

    def extractMetas[Metadata: Type]: List[Expr[Any]] =
      typesFromTuple[Metadata].map:
        case '[m] =>
          TypeRepr.of[m] match
            case AnnotatedType(_, annot) =>
              annot.asExpr
            case tpe                     =>
              report.errorAndAbort(s"got the metadata element ${tpe.show}")

    Type.of[Op] match
      case '[Operation {
            type Metadata       = metadata
            type InputMetadatas = inputMetadatas
          }] =>
        Metadata(extractMetas[metadata], extractMetass[inputMetadatas])
      case _ => report.errorAndAbort("expected an Operation with Metadata.")
    end match
  end metadata

  private def reifyImpl[T: Type](using Quotes): Expr[Of[T]] =
    import quotes.reflect.*

    val tpe    = TypeRepr.of[T]
    val cls    = tpe.classSymbol.get
    val decls  = cls.declaredMethods
    val labels = decls.map(m => ConstantType(StringConstant(m.name)))

    def isMeta(annot: Term): Boolean =
      annot.tpe <:< TypeRepr.of[MetaAnnotation]

    def encodeMeta(annot: Term): Type[?] =
      AnnotatedType(TypeRepr.of[Meta], annot).asType

    val annots = cls.annotations.filter(isMeta).map(encodeMeta)

    val ops       = decls.map(method =>
      val annots = method.annotations.filter(isMeta).map(encodeMeta)

      val meta = typesToTuple(annots)

      val (inputTypes, inputLabels, inputMetas, output) =
        tpe.memberType(method) match
          case ByNameType(res)                        =>
            val output = res.asType
            (Nil, Nil, Nil, output)
          case MethodType(paramNames, paramTpes, res) =>
            val inputTypes  = paramTpes.map(_.asType)
            val inputLabels =
              paramNames.map(l => ConstantType(StringConstant(l)).asType)
            val inputMetas  = method.paramSymss.head.map: s =>
              typesToTuple(s.annotations.filter(isMeta).map(encodeMeta))
            val output      = res match
              case _: MethodType =>
                report.errorAndAbort(
                  s"curried method ${method.name} is not supported"
                )
              case _: PolyType   =>
                report.errorAndAbort(
                  s"curried method ${method.name} is not supported"
                )
              case _             => res.asType
            (inputTypes, inputLabels, inputMetas, output)
          case _: PolyType                            =>
            report.errorAndAbort(
              s"generic method ${method.name} is not supported"
            )

      val inTup = typesToTuple(inputTypes)
      val inLab = typesToTuple(inputLabels)
      val inMet = typesToTuple(inputMetas)

      (meta, inTup, inLab, inMet, output) match
        case ('[m], '[i], '[l], '[iM], '[o]) =>
          Type.of[
            Operation {
              type Metadata       = m
              type InputTypes     = i
              type InputLabels    = l
              type InputMetadatas = iM
              type OutputType     = o
            }
          ]
      end match
    )
    val clsMeta   = typesToTuple(annots)
    val opsTup    = typesToTuple(ops.toList)
    val labelsTup = typesToTuple(labels.map(_.asType))
    val name      = ConstantType(StringConstant(cls.name)).asType
    (clsMeta, opsTup, labelsTup, name) match
      case ('[meta], '[ops], '[labels], '[label]) =>
        '{
          (new OpsMirror:
            type Metadata                = meta & Tuple
            type MirroredType            = T
            type MirroredLabel           = label
            type MirroredOperations      = ops & Tuple
            type MirroredOperationLabels = labels & Tuple
          ): OpsMirror.Of[T] {
            type MirroredLabel           = label
            type MirroredOperations      = ops & Tuple
            type MirroredOperationLabels = labels & Tuple
          }
        }
    end match
  end reifyImpl
end OpsMirror
