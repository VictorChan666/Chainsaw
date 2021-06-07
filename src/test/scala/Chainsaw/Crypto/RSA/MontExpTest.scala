package Chainsaw.Crypto.RSA

import Chainsaw.{DSPRand, GenRTL, _}
import org.scalatest.funsuite.AnyFunSuite
import spinal.core.sim.{SimConfig, simTime, _}

import scala.collection.mutable.ArrayBuffer

class MontExpTest extends AnyFunSuite {
  test("testMontExp") {

    val lN = 512

    // print the padded number in hex form
    def printPadded(name: String, value: BigInt, n: Int): Unit = {
      val hex =
        value.toString(2).padToLeft(n, '0')
          .grouped(4).toArray.map(BigInt(_, 2).toString(16))
          .mkString("")
      println(s"$name = $hex")
    }

    GenRTL(new MontExp(lN))
    val ref = new RSARef(lN)
    val algo = new RSAAlgo(lN)

    val N = BigInt(ref.getModulus)

    val exponent = ref.getPrivateValue
    val exponentLength = ref.getPrivateValue.bitLength()
    // pad to right as the hardware design requires
    val paddedExponent = BigInt(exponent.toString(2).padTo(lN, '0'), 2)

    val rhoSquare = algo.getRhoSquare(N)
    val omega = algo.getOmega(N)

    val inputValue = BigInt(ref.getPrivateValue) - DSPRand.nextInt(10000)
    val aMont = algo.montMul(inputValue, rhoSquare, N)
    val result = algo.montExp(inputValue, exponent, N)
    val record = algo.montExpWithRecord(inputValue, exponent, N)

    val toPrint = Map(
      "N" -> (N, lN),
      "omega" -> (omega, lN), "rhoSquare" -> (rhoSquare, lN),
      "aMont" -> (aMont, lN), "result" -> (result, lN)
    )
    toPrint.foreach { case (str, tuple) => printPadded(str, tuple._1, tuple._2) }

    SimConfig.withWave.compile(
      new MontExp(lN) {

        fsm.isPRE.simPublic()
        fsm.isPOST.simPublic()
        fsm.isINIT.simPublic()
        fsm.isBOOT.simPublic()
        innerCounter.value.simPublic()
        montRedcRet.simPublic()
      })
      .doSim { dut =>
        import dut._
        dut.clockDomain.forkStimulus(2)

        // poking to input
        dut.input.N #= N
        dut.input.exponent #= paddedExponent
        dut.input.exponentLength #= exponentLength

        dut.input.omega #= omega
        dut.input.RhoSquare #= algo.getRhoSquare(N)

        dut.input.value #= inputValue

        val yourRecord = ArrayBuffer[BigInt]()

        val start = ArrayBuffer[Long]()
        val end = ArrayBuffer[Long]()
        var dutResult = BigInt(0)
        // simulation and monitoring

        val cyclesForExponent = exponent.toString(2).tail.map(_.asDigit + 1).sum * 3

        (0 until cyclesForExponent + 50).foreach { _ =>

          def count = innerCounter.value.toInt

          dut.clockDomain.waitSampling()
          if (dut.fsm.isPRE.toBoolean && count == 2) start += (simTime + 2)
          if (dut.fsm.isPOST.toBoolean && count == 0) end += simTime
          if (dut.innerCounter.value.toInt == 0
            && !dut.fsm.isINIT.toBoolean && !dut.fsm.isBOOT.toBoolean)
            yourRecord += dut.montRedcRet.toBigInt
          if (fsm.isPOST.toBoolean && count == 3)
            dutResult = montRedcRet.toBigInt
        }

        if (ChainsawDebug) {
          yourRecord.zip(record).foreach { case (int, int1) =>
            printPadded("yours ", int, lN)
            printPadded("golden", int1, lN)
          }
          printPadded("your result   ", dutResult, lN)
          printPadded("golden result ", result, lN)
        }
        else assertResult(result)(dutResult) // result assertion

        println(s"cycles for exponent should be ${exponent.toString(2).tail.map(_.asDigit + 1).sum * 3}")
        println(s"cycles actually comsumed: ${(end(0) - start(0)) / 2}")
      }
  }
}