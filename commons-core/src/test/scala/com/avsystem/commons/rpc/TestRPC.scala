package com.avsystem.commons
package rpc

import com.avsystem.commons.rpc.DummyRPC._
import com.avsystem.commons.serialization.{HasGenCodec, whenAbsent}
import com.github.ghik.silencer.silent

case class Record(i: Int, fuu: String)
object Record extends HasGenCodec[Record]

trait InnerRPC {
  def proc(): Unit

  def func(arg: Int): Future[String]

  def moreInner(name: String): InnerRPC

  def indirectRecursion(): TestRPC
}
object InnerRPC extends RPCCompanion[InnerRPC]

trait TestRPC {
  def defaultNum: Int = 42

  @silent("side-effecting nullary methods")
  def handle: Unit

  def handleMore(): Unit

  def doStuff(lol: Int, fuu: String = "pisiont")(implicit cos: Option[Boolean]): Unit

  @rpcName("doStuffBoolean")
  def doStuff(yes: Boolean): Future[String]

  @rpcName("doStuffInt")
  def doStuff(@whenAbsent(defaultNum) num: Int): Unit

  def takeCC(r: Record = Record(-1, "_")): Unit

  def srslyDude(): Unit

  def innerRpc(name: String): InnerRPC

  def generallyDoStuff[T](list: List[T])(implicit @encodingDependency tag: Tag[T]): Future[Option[T]]
}

@silent("side-effecting nullary methods")
object TestRPC extends RPCCompanion[TestRPC] {
  def rpcImpl(onInvocation: (RawInvocation, Option[Any]) => Any): TestRPC = new TestRPC { outer =>
    private def onProcedure(methodName: String, args: List[String]): Unit =
      onInvocation(RawInvocation(methodName, args), None)

    private def onCall[T](methodName: String, args: List[String], result: T): Future[T] = {
      onInvocation(RawInvocation(methodName, args), Some(result))
      Future.successful(result)
    }

    private def onGet[T](methodName: String, args: List[String], result: T): T = {
      onInvocation(RawInvocation(methodName, args), None)
      result
    }

    def handleMore(): Unit =
      onProcedure("handleMore", Nil)

    def doStuff(lol: Int, fuu: String)(implicit cos: Option[Boolean]): Unit =
      onProcedure("doStuff", List(write(lol), write(fuu), write(cos)))

    def doStuff(yes: Boolean): Future[String] =
      onCall("doStuffBoolean", List(write(yes)), "doStuffResult")

    def doStuff(num: Int): Unit =
      onProcedure("doStuffInt", List(write(num)))

    def handle: Unit =
      onProcedure("handle", Nil)

    def takeCC(r: Record): Unit =
      onProcedure("takeCC", List(write(r)))

    def srslyDude(): Unit =
      onProcedure("srslyDude", Nil)

    def innerRpc(name: String): InnerRPC = {
      onGet("innerRpc", List(write(name)), new InnerRPC {
        def func(arg: Int): Future[String] =
          onCall("innerRpc.func", List(write(arg)), "innerRpc.funcResult")

        def proc(): Unit =
          onProcedure("innerRpc.proc", Nil)

        def moreInner(name: String): InnerRPC =
          this

        def indirectRecursion(): TestRPC =
          outer
      })
    }

    def generallyDoStuff[T](list: List[T])(implicit tag: Tag[T]): Future[Option[T]] =
      onCall("generallyDoStuff", List(write(tag), write(list)), list.headOption)
  }
}
