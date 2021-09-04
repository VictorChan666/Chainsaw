package FTN

import Chainsaw._
import spinal.core._
import spinal.lib._

case class Tx() extends Component {

  val pF = pFNonIter
  val dataIn = slave Flow Fragment(Bits(pF bits))

  val convencFTN = ConvencFTN(convencConfig, pF)
  val interleaverFTN = InterleaverFTN(params.InterleaveRow, params.InterleaveCol, pF * convencConfig.m)
  val qammodFTN = QammodFTN(iter = false)
  val IfftFTN = FftFTN(iter = false, inverse = true)

  val dataOut = out(cloneOf(IfftFTN.dataOut))

  dataIn >> convencFTN.dataIn
  convencFTN.dataOut >> interleaverFTN.dataIn
  interleaverFTN.dataOut >> qammodFTN.dataIn

  qammodFTN.dataOut >> IfftFTN.dataIn
  //  val hermitian = qammodFTN.dataOut.fragment.tail.reverse.map(_.conj)
  //  val symmetric = (qammodFTN.dataOut.fragment :+ ComplexNumber(0.0, 0.0, fixedType)) ++ hermitian
  //  IfftFTN.dataIn.fragment := Vec(symmetric)
  //  IfftFTN.dataIn.valid := qammodFTN.dataOut.valid
  //  IfftFTN.dataIn.last := qammodFTN.dataOut.last

  IfftFTN.dataOut >> dataOut

  def latency = convencFTN.latency + interleaverFTN.core.latency + qammodFTN.latency + IfftFTN.latency
}

object Tx {
  def main(args: Array[String]): Unit = {
    GenRTL(Tx())
    VivadoSynth(Tx())
  }
}


