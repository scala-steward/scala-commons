package com.avsystem.commons
package macros

import com.avsystem.commons.misc.TypeString
import org.scalactic.source.Position
import org.scalatest.FunSuite

import scala.language.experimental.macros
import scala.language.higherKinds

object TypeStringTest {
  val x = "x"

  type A

  type StaticAlias = Int

  class OFuu {
    val z = "z"
    object bar {
      val q = "q"
    }
  }
  val fuu = new OFuu

  def defineTests[T: TypeString](suite: TypeStringTest): Unit = {
    type LocalAlias = Int

    import suite.testTypeString
    testTypeString[Int]("Int")
    testTypeString[LocalAlias]("Int")
    testTypeString[StaticAlias]("TypeStringTest.StaticAlias")
    testTypeString[Integer]("Integer")
    testTypeString[String => Int]("String => Int")
    testTypeString[T => Int](s"(${TypeString.of[T]}) => Int")
    testTypeString[String => Int => Double]("String => Int => Double")
    testTypeString[(String => Int) => Double]("(String => Int) => Double")
    testTypeString[() => Int]("() => Int")
    testTypeString[(String, Int)]("(String, Int)")
    testTypeString[A]("TypeStringTest.A")
    testTypeString[T](TypeString.of[T])
    testTypeString[List[T]](s"List[${TypeString.of[T]}]")
    testTypeString[TypeStringTest#B]("TypeStringTest#B")
    testTypeString[x.type]("TypeStringTest.x.type")
    testTypeString[fuu.bar.q.type]("TypeStringTest.fuu.bar.q.type")
    testTypeString[None.type]("None.type")
    testTypeString[this.type]("TypeStringTest.type")
    testTypeString[Set.type]("Set.type")
    testTypeString[List[Int]]("List[Int]")
    testTypeString[Set[_]]("Set[_]")
    testTypeString[Set[_ <: String]]("Set[_ <: String]")
    testTypeString[Set[T] forSome {type T <: List[T]}]("Set[T] forSome {type T <: List[T]}")
    testTypeString[Map[T, T] forSome {type T <: String}]("Map[T, T] forSome {type T <: String}")
    testTypeString[Map[K, V] forSome {type K; type V <: List[K]}]("Map[K, V] forSome {type K; type V <: List[K]}")
    testTypeString[fu.z.type forSome {val fu: OFuu}]("fu.z.type forSome {val fu: TypeStringTest.OFuu}")
    testTypeString[fu.z.type forSome {val fu: OFuu with Singleton}]("fu.z.type forSome {val fu: TypeStringTest.OFuu with Singleton}")
    testTypeString[fu.bar.q.type forSome {val fu: OFuu}]("fu.bar.q.type forSome {val fu: TypeStringTest.OFuu}")
    testTypeString[AnyRef with Serializable]("AnyRef with Serializable")
    testTypeString[AnyRef with Serializable {type A <: String}]("AnyRef with Serializable {type A <: String}")
    testTypeString[Any {type A = String}]("Any {type A = String}")
    testTypeString[Any {type A}]("Any {type A}")
    testTypeString[(Any {type A})#A]("(Any {type A})#A")
    testTypeString[(Any {type *})# *]("(Any {type *})# *")
    testTypeString[Any {type A >: Null <: String}]("Any {type A >: Null <: String}")
    testTypeString[Any {type A[+X] = Int}]("Any {type A[+X] = Int}")
    testTypeString[Any {type A[X] = List[X]}]("Any {type A[X] = List[X]}")
    testTypeString[Any {type A[+X <: Set[X]] = List[X]}]("Any {type A[+X <: Set[X]] = List[X]}")
    testTypeString[Any {type A[F[_] <: List[_]] = F[Int]}]("Any {type A[F[_] <: List[_]] = F[Int]}")
    testTypeString[Any {type A[M[_, _] <: Map[_, _]] = M[Int, String]}]("Any {type A[M[_, _] <: Map[_, _]] = M[Int, String]}")
    testTypeString[Any {type A[F[+X] <: List[X]] = F[Int]}]("Any {type A[F[+X] <: List[X]] = F[Int]}")
    testTypeString[Any {type A[F[_ <: String]] = F[String]}]("Any {type A[F[_ <: String]] = F[String]}")
    testTypeString[Any {type A[F[X <: List[X]]] = F[Nothing]}]("Any {type A[F[X <: List[X]]] = F[Nothing]}")
  }
}

class TypeStringTest extends FunSuite {

  def testTypeString[T: TypeString](expected: String)(implicit pos: Position): Unit =
    test(s"${pos.lineNumber}:$expected") {
      assert(TypeString.of[T].replaceAllLiterally("com.avsystem.commons.macros.", "") == expected)
    }

  TypeStringTest.defineTests[Double](this)

  type B
  val y = "y"

  class Fuu {
    val z = "z"
    object bar {
      val q = "q"
    }
  }
  val fuu = new Fuu

  import TypeStringTest.{A, x}

  testTypeString[Int]("Int")
  testTypeString[Integer]("Integer")
  testTypeString[TypeStringTest#B]("TypeStringTest#B")
  testTypeString[A]("TypeStringTest.A")
  //  testTypeString[B]("B")
  testTypeString[x.type]("TypeStringTest.x.type")
  //  testTypeString[y.type]("y.type")
  //  testTypeString[fuu.bar.q.type]("fuu.bar.q.type")
  testTypeString[None.type]("None.type")
  //  testTypeString[this.type]("this.type")
  testTypeString[List[Int]]("List[Int]")
  testTypeString[Set[_]]("Set[_]")
  testTypeString[Set[_ <: String]]("Set[_ <: String]")
  testTypeString[Map[T, T] forSome {type T <: String}]("Map[T, T] forSome {type T <: String}")
  //  testTypeString[fu.z.type forSome {val fu: Fuu}]("fu.z.type forSome {val fu: Fuu}")
  //  testTypeString[fu.z.type forSome {val fu: Fuu with Singleton}]("fu.z.type forSome {val fu: Fuu with Singleton}")
  //  testTypeString[fu.bar.q.type forSome {val fu: Fuu}]("fu.bar.q.type forSome {val fu: Fuu}")
  //  testTypeString[AnyRef with Serializable]("AnyRef with Serializable")

  UnrelatedTypeString.defineTests[String](this)
}

object UnrelatedTypeString {

  import TypeStringTest._

  def defineTests[T: TypeString](suite: TypeStringTest): Unit = {
    import suite.testTypeString
    testTypeString[Int]("Int")
    testTypeString[StaticAlias]("TypeStringTest.StaticAlias")
    testTypeString[Integer]("Integer")
    testTypeString[String => Int]("String => Int")
    testTypeString[String => Int => Double]("String => Int => Double")
    testTypeString[(String => Int) => Double]("(String => Int) => Double")
    testTypeString[() => Int]("() => Int")
    testTypeString[(String, Int)]("(String, Int)")
    testTypeString[A]("TypeStringTest.A")
    testTypeString[T](TypeString.of[T])
    testTypeString[List[T]](s"List[${TypeString.of[T]}]")
    testTypeString[TypeStringTest#B]("TypeStringTest#B")
    testTypeString[x.type]("TypeStringTest.x.type")
    testTypeString[fuu.bar.q.type]("TypeStringTest.fuu.bar.q.type")
    testTypeString[None.type]("None.type")
    testTypeString[this.type]("UnrelatedTypeString.type")
    testTypeString[Set.type]("Set.type")
    testTypeString[List[Int]]("List[Int]")
    testTypeString[Set[_]]("Set[_]")
    testTypeString[Set[_ <: String]]("Set[_ <: String]")
    testTypeString[Set[T] forSome {type T <: List[T]}]("Set[T] forSome {type T <: List[T]}")
    testTypeString[Map[T, T] forSome {type T <: String}]("Map[T, T] forSome {type T <: String}")
    testTypeString[Map[K, V] forSome {type K; type V <: List[K]}]("Map[K, V] forSome {type K; type V <: List[K]}")
    testTypeString[fu.z.type forSome {val fu: OFuu}]("fu.z.type forSome {val fu: TypeStringTest.OFuu}")
    testTypeString[fu.z.type forSome {val fu: OFuu with Singleton}]("fu.z.type forSome {val fu: TypeStringTest.OFuu with Singleton}")
    testTypeString[fu.bar.q.type forSome {val fu: OFuu}]("fu.bar.q.type forSome {val fu: TypeStringTest.OFuu}")
    testTypeString[AnyRef with Serializable]("AnyRef with Serializable")
    testTypeString[AnyRef with Serializable {type A <: String}]("AnyRef with Serializable {type A <: String}")
  }
}