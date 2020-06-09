package com.avsystem.commons
package rpc.akka

import java.util.concurrent.atomic.AtomicLong

import akka.actor.{ActorPath, ActorSystem}
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * @author Wojciech Milewski
  */
abstract class AkkaRPCFrameworkTest(
  serverSystem: ActorSystem, clientSystem: ActorSystem, serverSystemPath: Option[String] = None
) extends AnyFlatSpec with RPCFrameworkTest with ProcedureRPCTest with FunctionRPCTest with GetterRPCTest with ObservableRPCTest
  with BeforeAndAfterAll {

  private def mock[T: ClassTag]: T =
    Mockito.mock(classTag[T].runtimeClass.asInstanceOf[Class[T]])

  /**
    * Servers as identifier supplier for each test case to allow tests parallelization.
    */
  private val idCounter = new AtomicLong()

  override def fixture(testCode: Fixture => Any): Unit = {
    val id: Long = idCounter.getAndIncrement()
    val serverActorName = s"rpcServerActor$id"

    val testRpcMock = mock[TestRPC]
    val innerRpcMock = mock[InnerRPC]
    val serverActor = {
      implicit val system = serverSystem
      AkkaRPCFramework.serverActor[TestRPC](testRpcMock, AkkaRPCServerConfig(actorName = serverActorName))
    }
    val rpc = {
      implicit val system = clientSystem
      AkkaRPCFramework.client[TestRPC](AkkaRPCClientConfig(serverPath = serverSystemPath.fold(serverActor.path)(
        serverPath => ActorPath.fromString(s"$serverPath/user/$serverActorName")))
      )
    }

    try {
      testCode(Fixture(rpc = rpc, mockRpc = testRpcMock, mockInnerRpc = innerRpcMock))
    } finally {
      serverSystem.stop(serverActor)
    }
  }

  override def noConnectionFixture(testCode: Fixture => Any): Unit = {
    implicit val system = clientSystem
    val mockRpc = mock[TestRPC]
    val mockInnerRpc = mock[InnerRPC]
    val rpc = AkkaRPCFramework.client[TestRPC](AkkaRPCClientConfig(
      functionCallTimeout = callTimeout,
      observableMessageTimeout = callTimeout,
      serverPath = serverSystemPath.fold(ActorPath.fromString("akka://user/thisactorshouldnotexists"))(serverPath => ActorPath.fromString(s"$serverPath/user/thisactorshouldnotExist)"))))
    testCode(Fixture(rpc = rpc, mockRpc = mockRpc, mockInnerRpc = mockInnerRpc))
  }


  override protected def beforeAll(): Unit = {
    super.beforeAll()
    /*
     * Kind of warmup of Akka systems.
     * First RPC request can be very slow due to connection establishment, done especially by Akka Remote.
     */
    fixture { f =>
      when(f.mockRpc.echoAsString(0)).thenReturn(Future.successful(""))
      Await.ready(f.rpc.echoAsString(0), 1.second)
    }
  }
  override protected def afterAll(): Unit = {
    super.afterAll()
    serverSystem.terminate()
    clientSystem.terminate()
    Await.ready(serverSystem.whenTerminated, 30.seconds)
    Await.ready(clientSystem.whenTerminated, 30.seconds)
  }

}