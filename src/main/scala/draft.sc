import spinal.core._
import spinal.core.sim._
import spinal.lib._
import spinal.sim._
import spinal.lib.fsm._

import Chainsaw._
import Chainsaw.Real

class divide extends Component{
  val input = in Bits(8 bits)
  val divide = Array(1,1,2,2,2)
  val start = divide.indices.map(i => divide.take(i).sum)
  val end = start.tail :+ divide.sum
  val output = out(Vec(divide.indices.map(i => input.asBools.slice(start(i), end(i)).asBits())))
}

val divide = Array(1,1,2,2,2)
divide.indices.map(i => divide.take(i).sum)

GenRTL(new divide)
