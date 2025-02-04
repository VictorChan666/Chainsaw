package Chainsaw

import spinal.core._
import spinal.lib._

trait EasyFIFO[T <: Data] {
  def init(): Unit

  def push(data: T): Unit

  def pop(): T
}


class Stack[T <: Data](dataType: HardType[T], depth: Int) extends StreamStack(dataType, depth) {
  def init(): Unit = {
    io.push.valid := False
    io.pop.ready := False
    io.push.payload.assignFromBits(B(BigInt(0), io.push.payload.getBitsWidth bits)) // avoid latch
  }

  def push(data: T): Unit = {
    io.push.valid := True
    io.push.payload := data.resized
  }

  def pop(): T = {
    io.pop.ready := True
    io.pop.payload
  }

  def clear(): Unit = {
    io.flush := True
  }
}

object Stack {
  def apply[T <: Data](dataType: HardType[T], depth: Int): Stack[T] = {
    val ret = new Stack(dataType, depth)
    ret.init()
    ret
  }
}

class FIFO[T <: Data](dataType: HardType[T], depth: Int) extends StreamFifo(dataType, depth) with EasyFIFO[T] {
  def init(): Unit = {
    io.push.valid := False
    io.pop.ready := False
    io.push.payload.assignFromBits(B(BigInt(0), io.push.payload.getBitsWidth bits)) // avoid latch
  }

  def push(data: T): Unit = {
    io.push.allowOverride
    io.push.valid := True
    io.push.payload := data.resized
  }

  def pop(): T = {
    io.pop.allowOverride
    io.pop.ready := True
    io.pop.payload
  }
}

class FIFOLowLatency[T <: Data](dataType: HardType[T], depth: Int) extends StreamFifoLowLatency(dataType, depth, latency = 1) with EasyFIFO[T] {
  def init(): Unit = {
    io.push.valid := False
    io.pop.ready := False
    io.push.payload.assignFromBits(B(BigInt(0), io.push.payload.getBitsWidth bits)) // avoid latch
  }

  def push(data: T): Unit = {
    io.push.valid := True
    io.push.payload := data.resized
  }

  def pop(): T = {
    io.pop.ready := True
    io.pop.payload
  }
}

object FIFOLowLatency {
  def apply[T <: Data](dataType: HardType[T], depth: Int): FIFOLowLatency[T] = {
    val ret = new FIFOLowLatency(dataType, depth)
    ret.init()
    ret
  }
}

object FIFO {
  def apply[T <: Data](dataType: HardType[T], depth: Int): FIFO[T] = {
    val ret = new FIFO(dataType, depth)
    ret.init()
    ret
  }
}

class StreamStack[T <: Data](dataType: HardType[T], depth: Int) extends Component {
  // TODO: merge adders, improve performance
  require(depth >= 0)
  val io = new Bundle {
    val push = slave Stream (dataType)
    val pop = master Stream (dataType)
    val flush = in Bool() default (False)
    val occupancy = out UInt (log2Up(depth + 1) bits)
    val availability = out UInt (log2Up(depth + 1) bits)
  }

  // when depth = 0/1, stack behaves the same as a FIFO
  val bypass = (depth == 0) generate new Area {
    io.push >> io.pop
    io.occupancy := 0
    io.availability := 0
  }
  val oneStage = (depth == 1) generate new Area {
    io.push.m2sPipe(flush = io.flush) >> io.pop
    io.occupancy := U(io.pop.valid)
    io.availability := U(!io.pop.valid)
  }

  val logic = (depth > 1) generate new Area {
    val ram = Mem(dataType, depth)
    val ptrNext = UInt(log2Up(depth) bits)
    val ptr = RegNext(ptrNext, U(0)) // for pushing, starts at 0, popping starts at prt - 1

    val pushing = io.push.fire
    val popping = io.pop.fire
    val full = ptr === depth
    val empty = ptr === 0

    io.push.ready := !full
    io.pop.valid := !empty | io.push.fire

    val justWritten = RegNext(pushing, False)
    val temp = RegNextWhen(io.push.payload, pushing)

    when(pushing) {
      io.pop.payload := io.push.payload // bypass
    }.elsewhen(justWritten) {
      io.pop.payload := temp
    }.otherwise {
      io.pop.payload := ram.readSync(ptrNext - 1) // prefetch
    }

    ptrNext := ptr
    when(pushing && !popping) {
      ram(ptr) := io.push.payload // write synchronously
      ptrNext := ptr + 1
    }.elsewhen(popping && !pushing) {
      ptrNext := ptr - 1
    }

    io.occupancy := ptr
    io.availability := depth - ptr

    when(io.flush) {
      ptr.clearAll()
    }
  }
}

class multi(counts: Seq[BigInt], modeOH: Bits) extends Counter(start = 0, end = counts.max) {
  override val willOverflowIfInc = (value === MuxOH(modeOH, counts.map(count => U(count - 1, log2Up(counts.max) bits))))
}

class MultiCountCounter(counts: Seq[BigInt], modeOH: Bits) extends ImplicitArea[UInt] {
  val start = 0
  val end = counts.max - 1
  require(start <= end)
  val willIncrement = False.allowOverride
  val willClear = False.allowOverride

  def clear(): Unit = willClear := True
  def increment(): Unit = willIncrement := True

  val valueNext = UInt(log2Up(end + 1) bit)
  val value = RegNext(valueNext) init (start)
  val willOverflowIfInc = (value === MuxOH(modeOH, counts.map(count => U(count - 1, log2Up(counts.max) bits))))
  val willOverflow = willOverflowIfInc && willIncrement

  when(willOverflow) {
    valueNext := U(start)
  } otherwise {
    valueNext := (value + U(willIncrement)).resized
  }

  when(willClear) {
    valueNext := start
  }

  willOverflowIfInc.allowPruning
  willOverflow.allowPruning

  override def implicitValue: UInt = this.value

  /**
   * Convert this stream to a flow. It will send each value only once. It is "start inclusive, end exclusive".
   * This means that the current value will only be sent if the counter increments.
   */
  def toFlow(): Flow[UInt] = {
    val flow = Flow(value)
    flow.payload := value
    flow.valid := willIncrement
    flow
  }
}

// FIXME: when count is a power of 2, the Counter optimize it in a way that is no suitable for MultiCount
// solved
object MultiCountCounter {
  def apply(counts: Seq[BigInt], modeOH: Bits): MultiCountCounter = new MultiCountCounter(counts, modeOH)

  def apply(counts: Seq[BigInt], modeOH: Bits, inc: Bool): MultiCountCounter = {
    val counter = new MultiCountCounter(counts: Seq[BigInt], modeOH: Bits)
    counter.willIncrement := inc
    counter
  }
}