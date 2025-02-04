package Chainsaw.Communication.viterbi

import spinal.core._
import spinal.core.sim._
import spinal.lib._
import spinal.lib.fsm._

import Chainsaw._
import Chainsaw.matlabIO._

/** Viterbi Decoder using Hamming(Hard) and has a fixed length of input
 *
 * @param trellis
 * @param lengthMax
 */
case class ViterbiHard(trellis: Trellis[Int], lengthMax: Int, temp: Int) extends Component {

  // submodules
  val forward = ViterbiForwarding(trellis) // ACS
  val backward = ViterbiBackwarding(trellis) // TB

  // I/O
  val dataIn: Flow[Fragment[Bits]] = slave Flow Fragment(HardType(forward.dataIn.fragment))
  val dataOut: Flow[Fragment[Bits]] = master Flow Fragment(HardType(backward.dataOut.fragment))

  val addrWidth = log2Up(lengthMax * 2)

  // storage(stack)
  val recordStack = Mem(HardType(forward.dataOut.fragment), lengthMax * 2)

  // input -> forward
  dataIn >> forward.dataIn

  // forward -> stack
  val stackCounter = Counter(lengthMax * 2, inc = forward.dataOut.valid) // push counter for the stack
  recordStack(stackCounter) := forward.dataOut.fragment

  // data reading
  val dataReady = Delay(forward.dataOut.valid, lengthMax, init = False) // TODO: improve the implementation
  val stackCountDown = Reg(UInt(log2Up(lengthMax * 2) bits)) // TODO: optimize the width
  when(dataReady)(stackCountDown := stackCountDown - 1) // pop counter for the stack
  when(forward.dataOut.last)(stackCountDown := stackCounter) // forward last means backward starts

  // stack -> backward
  backward.stateStart := temp // FIXME: should be from a common minTree
  backward.dataIn.valid := RegNext(dataReady, init = False) // latency for read sync
  backward.dataIn.last := Delay(forward.dataOut.last, lengthMax + 1, init = False) // 1 for read sync
  backward.dataIn.fragment := recordStack.readSync(stackCountDown) // TODO: readSync and using BRAM

  // backward -> output
  backward.dataOut >> dataOut

  def latency = 2 + // forwarding
    lengthMax + // writing
    1 // reading

}

object ViterbiHard {
  def main(args: Array[String]): Unit = {
    VivadoSynth(ViterbiHard(Refs.getTestData._1, 128, 0))
  }
}
