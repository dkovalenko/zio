package scalaz.zio.lockfree

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole
import scalaz.zio.lockfree.ConcurrentQueueBenchmark.{ OfferCounters, PollCounters }

object ConcurrentQueueBenchmark {
  @AuxCounters
  @State(Scope.Thread)
  class PollCounters(var pollsFailed: Long, var pollsMade: Long) {
    def this() {
      this(0, 0)
    }
  }

  @AuxCounters
  @State(Scope.Thread)
  class OfferCounters(var offersFailed: Long, var offersMade: Long) {
    def this() {
      this(0, 0)
    }
  }
}

@State(Scope.Group)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 20, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
class ConcurrentQueueBenchmark {
  val DELAY_PRODUCER: Long = 32
  var DELAY_CONSUMER: Long = 16

  var Token: Int = 1

  @Param(Array("65536"))
  var qCapacity: Int = _

  @Param(Array("RingBuffer", "JucBlocking", "JucConcurrent", "JCTools"))
  var qType: String = _

  var q: LockFreeQueue[Int] = _

  def backoff(): Unit = {}

  @Setup(Level.Trial)
  def createQueue(): Unit = q = impls.queueByType(qType, qCapacity)

  @TearDown(Level.Iteration)
  def emptyQ(): Unit =
    q.synchronized {
      while (q.poll().isDefined) {}
    }

  @Benchmark
  @Group("Asymmetric")
  @GroupThreads(16)
  def offerAsymmetric(counters: OfferCounters): Unit = doOffer(counters)

  @Benchmark
  @Group("Asymmetric")
  @GroupThreads(8)
  def pollAsymmetric(counters: PollCounters): Unit = doPoll(counters)

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  def doOffer(counters: OfferCounters): Unit = {
    if (!q.offer(Token)) {
      counters.offersFailed += 1
      backoff()
    } else {
      counters.offersMade += 1
    }

    if (DELAY_PRODUCER != 0) {
      Blackhole.consumeCPU(DELAY_PRODUCER)
    }
  }

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  def doPoll(counters: PollCounters): Unit = {
    val e = q.poll()

    e match {
      case None =>
        counters.pollsFailed += 1
        backoff()

      case Some(_) =>
        counters.pollsMade += 1
    }

    if (DELAY_CONSUMER != 0) {
      Blackhole.consumeCPU(DELAY_CONSUMER)
    }
  }
}
