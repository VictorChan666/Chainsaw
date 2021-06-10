package Chainsaw.Crypto.RSA

import Chainsaw.{DSPDUTTiming, TimingInfo}
import spinal.core.{RegNext, SInt, UInt, Vec, in, out, _}
import spinal.core._
import spinal.core.sim._
import spinal.lib._
import spinal.sim._
import spinal.lib.fsm._

import Chainsaw._
import Chainsaw.Real

class BigAdd(n: Int, val latency: Int) extends DSPDUTTiming[Vec[UInt], UInt] {
  override val input: Vec[UInt] = in Vec(UInt(n bits), 2)
  override val output: UInt = out(Delay(input(0) +^ input(1), latency))
  override val timing: TimingInfo = TimingInfo(1, 1, 0, 1)
}

class BigSub(n: Int, val latency: Int) extends DSPDUTTiming[Vec[SInt], SInt] {
  override val input: Vec[SInt] = in Vec(SInt(n bits), 2)
  override val output: SInt = out(Delay(input(0) - input(1), latency))
  override val timing: TimingInfo = TimingInfo(1, 1, 0, 1)
}

class BigAddSub(n: Int, val latency: Int) extends DSPDUTTiming[Vec[UInt], UInt] {
  val input: Vec[UInt] = in Vec(UInt(n bits), 2)
  val isAdd = in Bool()
  val ret = Mux(isAdd, input(0) +^ input(1), (input(0) - input(1)).resized) // TODO: reconsider the sub part
  val output: UInt = out(Delay(ret, latency))
  override val timing: TimingInfo = TimingInfo(1,1,latency,1)
}

class BigMult(n: Int, val latency: Int) extends DSPDUTTiming[Vec[UInt], UInt] {
  override val input: Vec[UInt] = in Vec(UInt(n bits), 2)
  override val output: UInt = out(Delay(input(0) * input(1), latency))
  override val timing: TimingInfo = TimingInfo(1, 1, latency, 1)
}

class BigAddMod(n: Int, m: Int) extends DSPDUTTiming[Vec[UInt], UInt] {
  override val input: Vec[UInt] = in Vec(UInt(n bits), 2)
  val sum = input(0) +^ input(1)
  override val output: UInt = out(sum(m - 1 downto 0))
  override val timing: TimingInfo = TimingInfo(1, 1, 0, 1)
}

class BigMultMod(n: Int, m: Int) extends DSPDUTTiming[Vec[UInt], UInt] {
  override val input: Vec[UInt] = in Vec(UInt(n bits), 2)
  val prod = input(0) * input(1)
  val ret = prod(m - 1 downto 0)
  override val output: UInt = out(RegNext(ret))
  override val timing: TimingInfo = TimingInfo(1, 1, 1, 1)
}
