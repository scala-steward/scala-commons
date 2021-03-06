package com.avsystem.commons
package rpc.akka.serialization

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}

import org.openjdk.jmh.annotations.{Benchmark, BenchmarkMode, Fork, Measurement, Mode, Scope, State, Warmup}
import org.openjdk.jmh.infra.Blackhole

/**
  * @author Wojciech Milewski
  */
@Warmup(iterations = 5)
@Measurement(iterations = 20)
@Fork(1)
@BenchmarkMode(Array(Mode.Throughput))
@State(Scope.Thread)
class JavaSerializationBenchmark {

  val something = Something(42, Nested(4 :: 8 :: 15 :: 16 :: 23 :: 42 :: Nil, 0), "lol")
  val array = {
    val baos = new ByteArrayOutputStream()
    val o = new ObjectOutputStream(baos)

    o.writeObject(something)
    o.close()

    baos.toByteArray
  }

  @Benchmark
  def byteStringOutput(): Something = {
    val baos = new ByteArrayOutputStream()
    val o = new ObjectOutputStream(baos)

    o.writeObject(something)
    o.close()

    val array = baos.toByteArray

    new ObjectInputStream(new ByteArrayInputStream(array)).readObject().asInstanceOf[Something]
  }

  @Benchmark
  def writeTest(): Array[Byte] = {
    val baos = new ByteArrayOutputStream()
    val o = new ObjectOutputStream(baos)

    o.writeObject(something)
    o.close()

    baos.toByteArray
  }

  @Benchmark
  def readTest(): Something = {
    new ObjectInputStream(new ByteArrayInputStream(array)).readObject().asInstanceOf[Something]
  }
}
