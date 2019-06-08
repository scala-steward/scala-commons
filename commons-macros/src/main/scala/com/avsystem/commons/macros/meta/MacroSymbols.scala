package com.avsystem.commons
package macros.meta

import com.avsystem.commons.macros.MacroCommons
import com.avsystem.commons.macros.misc.{Fail, FailMsg, Ok, Res}

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

private[commons] trait MacroSymbols extends MacroCommons {

  import c.universe._

  final def RpcPackage = q"$CommonsPkg.rpc"
  final def MetaPackage = q"$CommonsPkg.meta"
  final def RpcUtils = q"$RpcPackage.RpcUtils"
  final def OptionLikeCls = tq"$MetaPackage.OptionLike"
  final def CanBuildFromCls = tq"$CollectionPkg.generic.CanBuildFrom"
  final lazy val RpcArityAT: Type = staticType(tq"$MetaPackage.SymbolArity")
  final lazy val SingleArityAT: Type = staticType(tq"$MetaPackage.single")
  final lazy val OptionalArityAT: Type = staticType(tq"$MetaPackage.optional")
  final lazy val MultiArityAT: Type = staticType(tq"$MetaPackage.multi")
  final lazy val CompositeAT: Type = staticType(tq"$MetaPackage.composite")
  final lazy val AuxiliaryAT: Type = staticType(tq"$MetaPackage.auxiliary")
  final lazy val AnnotatedAT: Type = staticType(tq"$MetaPackage.annotated[_]")
  final lazy val TaggedAT: Type = staticType(tq"$RpcPackage.tagged[_]")
  final lazy val UnmatchedAT: Type = staticType(tq"$RpcPackage.unmatched")
  final lazy val UnmatchedParamAT: Type = staticType(tq"$RpcPackage.unmatchedParam[_]")
  final lazy val ParamTagAT: Type = staticType(tq"$RpcPackage.paramTag[_]")
  final lazy val CaseTagAT: Type = staticType(tq"$RpcPackage.caseTag[_]")
  final lazy val RpcTagAT: Type = staticType(tq"$RpcPackage.RpcTag")
  final lazy val WhenUntaggedArg: Symbol = TaggedAT.member(TermName("whenUntagged"))
  final lazy val UnmatchedErrorArg: Symbol = UnmatchedAT.member(TermName("error"))
  final lazy val UnmatchedParamErrorArg: Symbol = UnmatchedParamAT.member(TermName("error"))

  def primaryConstructor(ownerType: Type, ownerParam: Option[MacroSymbol]): Symbol =
    primaryConstructorOf(ownerType, ownerParam.fold("")(p => s"${p.problemStr}:\n"))

  sealed trait Arity {
    def annotStr: String
    def verbatimByDefault: Boolean
  }
  object Arity {
    trait Single extends Arity {
      final def annotStr = "@single"
    }
    trait Optional extends Arity {
      final def annotStr = "@optional"
    }
    trait Multi extends Arity {
      final def annotStr = "@multi"
    }
  }

  sealed abstract class ParamArity(val verbatimByDefault: Boolean) extends Arity {
    def collectedType: Type
  }
  object ParamArity {
    def apply(param: ArityParam): ParamArity = {
      val annot = param.annot(RpcArityAT)
      val at = annot.fold(SingleArityAT)(_.tpe)
      if (at <:< SingleArityAT) ParamArity.Single(param.actualType)
      else if (at <:< OptionalArityAT) {
        val optionLikeType = param.optionLike.actualType
        val valueMember = optionLikeType.member(TypeName("Value"))
        if (valueMember.isAbstract)
          param.reportProblem("could not determine actual value of optional parameter type")
        else
          ParamArity.Optional(valueMember.typeSignatureIn(optionLikeType))
      }
      else if ((param.allowListedMulti || param.allowNamedMulti) && at <:< MultiArityAT) {
        if (param.allowNamedMulti && param.actualType <:< StringPFTpe)
          Multi(param.actualType.baseType(PartialFunctionClass).typeArgs(1), named = true)
        else if (param.allowListedMulti && param.actualType <:< BIterableTpe)
          Multi(param.actualType.baseType(BIterableClass).typeArgs.head, named = false)
        else if (param.allowNamedMulti && param.allowListedMulti)
          param.reportProblem(s"@multi ${param.shortDescription} must be a PartialFunction of String " +
            s"(for by-name mapping) or Iterable (for sequence)")
        else if (param.allowListedMulti)
          param.reportProblem(s"@multi ${param.shortDescription} must be an Iterable")
        else
          param.reportProblem(s"@multi ${param.shortDescription} must be a PartialFunction of String")
      }
      else param.reportProblem(s"forbidden RPC arity annotation: $at")
    }

    case class Single(collectedType: Type) extends ParamArity(true) with Arity.Single
    case class Optional(collectedType: Type) extends ParamArity(true) with Arity.Optional
    case class Multi(collectedType: Type, named: Boolean) extends ParamArity(false) with Arity.Multi
  }

  sealed abstract class MethodArity(val verbatimByDefault: Boolean) extends Arity
  object MethodArity {
    def fromAnnotation(method: MacroMethod): MethodArity = {
      val at = method.annot(RpcArityAT).fold(SingleArityAT)(_.tpe)
      if (at <:< SingleArityAT) Single
      else if (at <:< OptionalArityAT) Optional
      else if (at <:< MultiArityAT) Multi
      else method.reportProblem(s"unrecognized RPC method arity annotation: $at")
    }

    case object Single extends MethodArity(true) with Arity.Single
    case object Optional extends MethodArity(true) with Arity.Optional
    case object Multi extends MethodArity(false) with Arity.Multi
  }

  abstract class MacroSymbol {
    type NameType <: Name

    def symbol: Symbol
    def pos: Position = symbol.pos
    def seenFrom: Type
    def shortDescription: String
    def description: String
    def problemStr: String = s"problem with $description"

    def reportProblem(msg: String, detailPos: Position = NoPosition): Nothing =
      abortAt(s"$problemStr: $msg", if (detailPos != NoPosition) detailPos else pos)

    def annot(tpe: Type, fallback: List[Tree] = Nil): Option[Annot] =
      findAnnotation(symbol, tpe, seenFrom, withInherited = true, fallback)

    def annots(tpe: Type, fallback: List[Tree] = Nil): List[Annot] =
      allAnnotations(symbol, tpe, seenFrom, withInherited = true, fallback)

    def infer(tpt: Tree): CachedImplicit =
      infer(getType(tpt))

    def infer(
      tpe: Type,
      typeParams: List[Symbol] = Nil,
      availableImplicits: List[Type] = Nil,
      forSym: MacroSymbol = this,
      clue: String = ""
    ): CachedImplicit =
      inferCachedImplicit(tpe, ErrorCtx(s"${forSym.problemStr}:\n$clue", forSym.pos), typeParams, availableImplicits)

    protected def asName(name: Name): NameType

    val name: NameType = asName(symbol.name)
    val safeName: NameType = c.freshName(name)
    val nameStr: String = name.decodedName.toString
    val encodedNameStr: String = name.encodedName.toString

    override def equals(other: Any): Boolean = other match {
      case rpcSym: MacroSymbol => symbol == rpcSym.symbol
      case _ => false
    }
    override def hashCode: Int = symbol.hashCode
    override def toString: String = symbol.toString
  }

  abstract class MacroTermSymbol extends MacroSymbol {
    type NameType = TermName

    protected def asName(name: Name): TermName = name.toTermName
  }

  abstract class MacroTypeSymbol extends MacroSymbol {
    type NameType = TypeName

    protected def asName(name: Name): TypeName = name.toTypeName
  }

  abstract class MacroMethod extends MacroTermSymbol {
    def ownerType: Type
    def seenFrom: Type = ownerType

    if (!symbol.isMethod) {
      abortAt(s"problem with member $nameStr of type $ownerType: it must be a method (def)", pos)
    }

    val sig: Type = symbol.typeSignatureIn(ownerType)
    def typeParams: List[MacroTypeParam]
    def paramLists: List[List[MacroParam]]
    val resultType: Type = sig.finalResultType

    def argLists: List[List[Tree]] = paramLists.map(_.map(_.argToPass))
    def typeParamDecls: List[Tree] = typeParams.map(_.typeParamDecl)
    def paramDecls: List[List[Tree]] = paramLists.map(_.map(_.paramDecl))
  }

  abstract class MacroTypeParam extends MacroTypeSymbol {
    def typeParamDecl: Tree

    val instanceName: TermName = safeName.toTermName
  }

  abstract class MacroParam extends MacroTermSymbol {
    val actualType: Type = actualParamType(symbol)
    def collectedType: Type = actualType

    def localValueDecl(body: Tree): Tree =
      if (symbol.asTerm.isByNameParam)
        q"def $safeName = $body"
      else
        q"val $safeName = $body"

    def paramDecl: Tree = {
      val implicitFlag = if (symbol.isImplicit) Flag.IMPLICIT else NoFlags
      ValDef(Modifiers(Flag.PARAM | implicitFlag), safeName, TypeTree(symbol.typeSignature), EmptyTree)
    }

    def argToPass: Tree =
      if (isRepeated(symbol)) q"$safeName: _*" else q"$safeName"
  }

  trait AritySymbol extends MacroSymbol {
    def arity: Arity

    // @unchecked because "The outer reference in this type test cannot be checked at runtime"
    // Srsly scalac, from static types it should be obvious that outer references are the same
    def matchName(shortDescr: String, name: String): Res[Unit] = arity match {
      case _: Arity.Single@unchecked | _: Arity.Optional@unchecked =>
        if (name == nameStr) Ok(())
        else Fail
      case _ => Ok(())
    }
  }

  trait FilteringSymbol extends MacroSymbol {
    lazy val requiredAnnots: List[Type] =
      annots(AnnotatedAT).map(_.tpe.dealias.typeArgs.head)

    lazy val unmatchedError: Option[String] =
      annot(UnmatchedAT).map(_.findArg[String](UnmatchedErrorArg))

    def matchFilters(realSymbol: MatchedSymbol): Res[Unit] =
      Res.traverse(requiredAnnots) { annotTpe =>
        if (realSymbol.annot(annotTpe).nonEmpty) Ok(())
        else Fail
      }.map(_ => ())
  }

  case class BaseTagSpec(baseTagTpe: Type, fallbackTag: FallbackTag)
  object BaseTagSpec {
    def apply(a: Annot): BaseTagSpec = {
      val tagType = a.tpe.dealias.typeArgs.head
      val defaultTagArg = a.tpe.member(TermName("defaultTag"))
      val fallbackTag = FallbackTag(a.findArg[Tree](defaultTagArg, EmptyTree))
      BaseTagSpec(tagType, fallbackTag)
    }
  }

  case class RequiredTag(baseTagTpe: Type, tagTpe: Type, fallbackTag: FallbackTag)

  case class FallbackTag(annotTree: Tree) {
    def isEmpty: Boolean = annotTree == EmptyTree
    def asList: List[Tree] = List(annotTree).filter(_ != EmptyTree)
    def orElse(other: FallbackTag): FallbackTag = FallbackTag(annotTree orElse other.annotTree)
  }
  object FallbackTag {
    final val Empty = FallbackTag(EmptyTree)
  }

  trait TagSpecifyingSymbol extends MacroSymbol {
    def tagSpecifyingOwner: Option[TagSpecifyingSymbol]

    private val tagSpecsCache = new mutable.HashMap[TypeKey, List[BaseTagSpec]]

    def tagSpecs(baseTagAnnot: Type): List[BaseTagSpec] =
      tagSpecsCache.getOrElseUpdate(TypeKey(baseTagAnnot),
        annots(baseTagAnnot).map(BaseTagSpec.apply) ++ tagSpecifyingOwner.map(_.tagSpecs(baseTagAnnot)).getOrElse(Nil))
  }

  trait TagMatchingSymbol extends MacroSymbol with FilteringSymbol {
    def baseTagSpecs: List[BaseTagSpec]

    def tagAnnot(tpe: Type): Option[Annot] =
      annot(tpe)

    lazy val requiredTags: List[RequiredTag] = {
      val result = baseTagSpecs.map { case BaseTagSpec(baseTagTpe, fallbackTag) =>
        val taggedAnnot = annot(getType(tq"$RpcPackage.tagged[_ <: $baseTagTpe]"))
        val requiredTagType = taggedAnnot.fold(baseTagTpe)(_.tpe.typeArgs.head)
        val whenUntagged = FallbackTag(taggedAnnot.map(_.findArg[Tree](WhenUntaggedArg, EmptyTree)).getOrElse(EmptyTree))
        RequiredTag(baseTagTpe, requiredTagType, whenUntagged orElse fallbackTag)
      }
      annots(TaggedAT).foreach { ann =>
        val requiredTagTpe = ann.tpe.typeArgs.head
        if (!result.exists(_.tagTpe =:= requiredTagTpe)) {
          reportProblem(s"annotation $ann does not have corresponding @paramTag/@methodTag/@caseTag set up")
        }
      }
      result
    }

    def matchTagsAndFilters(matched: MatchedSymbol): Res[matched.Self] = for {
      fallbackTagsUsed <- Res.traverse(requiredTags) {
        case RequiredTag(baseTagTpe, requiredTag, whenUntagged) =>
          val annotTagTpeOpt = matched.annot(baseTagTpe).map(_.tpe)
          val fallbackTagUsed = if (annotTagTpeOpt.isEmpty) whenUntagged else FallbackTag.Empty
          val realTagTpe = annotTagTpeOpt.getOrElse(NoType) orElse fallbackTagUsed.annotTree.tpe orElse baseTagTpe

          if (realTagTpe <:< requiredTag) Ok(fallbackTagUsed)
          else Fail
      }
      newMatched = matched.addFallbackTags(fallbackTagsUsed)
      _ <- matchFilters(newMatched)
    } yield newMatched
  }

  trait ArityParam extends MacroParam with AritySymbol {
    def allowNamedMulti: Boolean
    def allowListedMulti: Boolean
    def allowFail: Boolean

    lazy val arity: ParamArity = ParamArity(this)

    override def collectedType: Type = arity.collectedType

    lazy val optionLike: CachedImplicit = infer(tq"$OptionLikeCls[$actualType]")

    lazy val canBuildFrom: CachedImplicit = arity match {
      case _: ParamArity.Multi if allowNamedMulti && actualType <:< StringPFTpe =>
        infer(tq"$CanBuildFromCls[$NothingCls,($StringCls,${arity.collectedType}),$actualType]")
      case _: ParamArity.Multi =>
        infer(tq"$CanBuildFromCls[$NothingCls,${arity.collectedType},$actualType]")
      case _ => abort(s"(bug) CanBuildFrom computed for non-multi $shortDescription")
    }

    def mkOptional[T: Liftable](opt: Option[T]): Tree =
      opt.map(t => q"${optionLike.name}.some($t)").getOrElse(q"${optionLike.name}.none")

    def mkMulti[T: Liftable](elements: List[T]): Tree =
      if (elements.isEmpty)
        q"$RpcUtils.createEmpty(${canBuildFrom.name})"
      else {
        val builderName = c.freshName(TermName("builder"))
        q"""
          val $builderName = $RpcUtils.createBuilder(${canBuildFrom.name}, ${elements.size})
          ..${elements.map(t => q"$builderName += $t")}
          $builderName.result()
        """
      }
  }

  trait MatchedSymbol {
    type Self >: this.type <: MatchedSymbol

    def real: MacroSymbol
    def rawName: String
    def indexInRaw: Int
    def fallbackTagsUsed: List[FallbackTag]
    def addFallbackTags(fallbackTags: List[FallbackTag]): Self
    def typeParamsInContext: List[MacroTypeParam]

    def annot(tpe: Type): Option[Annot] =
      real.annot(tpe, fallbackTagsUsed.flatMap(_.asList))

    def annots(tpe: Type): List[Annot] =
      real.annots(tpe, fallbackTagsUsed.flatMap(_.asList))
  }

  trait SelfMatchedSymbol extends MacroSymbol with MatchedSymbol {
    type Self = this.type

    def real: MacroSymbol = this
    def indexInRaw: Int = 0
    def rawName: String = nameStr
    def typeParamsInContext: List[MacroTypeParam] = Nil
    def fallbackTagsUsed: List[FallbackTag] = Nil
    def addFallbackTags(fallbackTagsUsed: List[FallbackTag]): Self = this
  }

  def collectParamMappings[Real, Raw, M](
    reals: List[Real],
    raws: List[Raw],
    allowIncomplete: Boolean
  )(
    createMapping: (Raw, ParamsParser[Real]) => Res[M],
    unmatchedMsg: Real => String
  ): Res[List[M]] = {

    val parser = new ParamsParser(reals)
    Res.traverse(raws)(createMapping(_, parser)).flatMap { result =>
      parser.remaining.headOption match {
        case _ if allowIncomplete => Ok(result)
        case None => Ok(result)
        case Some(firstUnmatched) => FailMsg(unmatchedMsg(firstUnmatched))
      }
    }
  }

  class ParamsParser[Real](reals: Seq[Real]) {

    import scala.collection.JavaConverters._

    private val realParams = new java.util.LinkedList[Real]
    realParams.addAll(reals.asJava)

    def remaining: Seq[Real] = realParams.asScala

    def extractSingle[M](consume: Boolean, matcher: Real => Option[Res[M]], unmatchedError: String): Res[M] = {
      val it = realParams.listIterator()
      @tailrec def loop(): Res[M] =
        if (it.hasNext) {
          val real = it.next()
          matcher(real) match {
            case Some(res) =>
              if (consume) {
                it.remove()
              }
              res
            case None =>
              loop()
          }
        } else FailMsg(unmatchedError)
      loop()
    }

    def extractOptional[M](consume: Boolean, matcher: Real => Option[Res[M]]): Option[M] = {
      val it = realParams.listIterator()
      @tailrec def loop(): Option[M] =
        if (it.hasNext) {
          val real = it.next()
          matcher(real) match {
            case Some(Ok(res)) =>
              if (consume) {
                it.remove()
              }
              Some(res)
            case Some(_) => None
            case None => loop()
          }
        } else None
      loop()
    }

    def extractMulti[M](consume: Boolean, matcher: (Real, Int) => Option[Res[M]]): Res[List[M]] = {
      val it = realParams.listIterator()
      @tailrec def loop(result: ListBuffer[M]): Res[List[M]] =
        if (it.hasNext) {
          val real = it.next()
          matcher(real, result.size) match {
            case Some(res) =>
              if (consume) {
                it.remove()
              }
              res match {
                case Ok(value) =>
                  result += value
                  loop(result)
                case fail: FailMsg =>
                  fail
                case Fail =>
                  Fail
              }
            case None => loop(result)
          }
        } else Ok(result.result())
      loop(new ListBuffer[M])
    }

    def findFirst[M](matcher: Real => Option[M]): Option[M] = {
      val it = realParams.listIterator()
      @tailrec def loop(): Option[M] =
        if (it.hasNext) {
          val real = it.next()
          matcher(real) match {
            case Some(m) => Some(m)
            case None => loop()
          }
        } else None
      loop()
    }
  }
}
