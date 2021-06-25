package Chainsaw.Crypto.RSA

import Chainsaw._
import cc.redberry.rings.scaladsl._
import org.scalatest.funsuite.AnyFunSuite
import spinal.core.sim._

import scala.collection.mutable.ArrayBuffer

case class MontExpTestCase(modeId: Int, changeKey: Boolean = false)

class MontExpSystolicTest extends AnyFunSuite {

  test("testMontExpSystolicHardwareWithROM") {

    val testSizes = Seq(512, 1024, 2048, 3072, 4096)
    val testWordSize = 32
    GenRTL(new MontExpSystolic(MontConfig(lMs = testSizes, parallel = true))) // for a quick semantic test
    //    VivadoSynth(new MontExpSystolic(MontConfig(lMs = testSizes, parallel = true), testRadixSquare, testModulus, testExponent, testExponentLength, testInputs))
    // design parameters that are determined by the user

    SimConfig.withWave.compile(new MontExpSystolic(MontConfig(lMs = testSizes, parallel = true))).doSim { dut =>
      import dut._
      import dut.config._
      
      def runTestCases(montExpTestCases: Seq[MontExpTestCase]) = {
        montExpTestCases.foreach { testcase =>
          // preparing data
          val currentTestSize = lMs(testcase.modeId)
          val currentInstanceNumber = instanceNumber(testcase.modeId)

          val ref = new RSARef(currentTestSize)
          val testModulus = BigInt(ref.getModulus)
          val testExponent = BigInt(ref.getPublicValue)
          println(s"exponent = ${testExponent.toString(2)}")
          val testRadix = BigInt(1) << (testModulus.bitLength + 2)
          val testRadixSquare = BigInt(Zp(testModulus)(testRadix * testRadix).toByteArray)
          val testExponentLength = testExponent.bitLength
          val testInputs = (0 until currentInstanceNumber).map(_ => BigInt(ref.getPrivateValue) / DSPRand.nextInt(10000) - DSPRand.nextInt(10000))
          // get words
          val testExponentWords = toWords(BigInt(testExponent.toString(2).reverse, 2), testWordSize, currentTestSize / testWordSize)
          val testRadixSquareWords = toWords(testRadixSquare, testWordSize, currentTestSize / testWordSize)
          val testModulusWords = toWords(testModulus, testWordSize, currentTestSize / testWordSize)
          val testInputsWords = testInputs.map(input => toWords(input, testWordSize, currentTestSize / testWordSize))
          // get golden
          val goldens = testInputs.map(MontAlgos.Arch1ME(_, testExponent, testModulus, testWordSize, print = false))
          printlnGreen(s"goldens >= M exists: ${goldens.exists(_ >= testModulus)}")

          def startAMontExp(): Unit = {
            io.start #= true
            io.mode #= BigInt(1) << testcase.modeId
            io.exponentLengthIn #= testExponentLength
            io.keyReset #= testcase.changeKey
            clockDomain.waitSampling()
          }

          def unsetStart() = {
            io.start #= false
            io.mode #= BigInt(0)
            io.exponentLengthIn #= 0
            io.keyReset #= false
          }

          def runForOnce(modeId: Int, changeSecretKey: Boolean) = {
            val dutResults = Seq.fill(currentInstanceNumber)(ArrayBuffer[BigInt]())
            // monitors
            def montMulResultMonitor() = if (montMult.io.valids(0).toBoolean) dutResults.zip(io.dataOuts).foreach { case (buffer, signal) => buffer += signal.toBigInt }
            def montExpResultMonitor() = if (io.valids(0).toBoolean) dutResults.zip(io.dataOuts).foreach { case (buffer, signal) => buffer += signal.toBigInt }

            val runtime = config.IIs(modeId) * testExponent.toString(2).map(_.asDigit + 1).sum + 200
            val starterIds = (0 until parallelFactor).filter(_ % groupPerInstance(modeId) == 0)

            startAMontExp()

            (0 until wordPerInstance(modeId)).foreach { i => // STATE = INIT, feed
              if (i == 0) unsetStart()
              io.modulusWordIn #= testModulusWords(i)
              io.radixSquareWordIn #= testRadixSquareWords(i)
              io.exponentWordIn #= testExponentWords(i)
              starterIds.zipWithIndex.foreach { case (starter, inputId) => io.xWordIns(starter) #= testInputsWords(inputId)(i) }
              clockDomain.waitSampling()
            }

            (0 until runtime).foreach { _ => // STATE = RUN, run automatically, monitoring
              montExpResultMonitor()
              clockDomain.waitSampling()
            }

            // output
            printlnYellow(s"test of mode $modeId, which run ${parallelFactor / groupPerInstance(modeId)} instance of size ${lMs(modeId)}")
            println("X0     : " + toWordsHexString(testInputs(0), testWordSize, 16))
            println("M      : " + toWordsHexString(testModulus, testWordSize, 16))
            println("rSquare: " + toWordsHexString(testRadixSquare, testWordSize, 16))
            goldens.indices.foreach { i =>
              val goldenString = toWordsHexString(goldens(i), testWordSize, lMs(modeId) / w)
              val dutString = dutResults(i).init.map(_.toString(16).padToLeft(32 / 4, '0')).mkString(" ") + " "
              println(s"golden result$i        : $goldenString")
              println(s"dut result$i           : $dutString")
              assertResult(goldenString)(dutString)
            }
          }

          runForOnce(modeId = 0, true)
        }
      }

      io.start #= false
      io.mode #= BigInt(0)
      clockDomain.forkStimulus(2)
      clockDomain.waitSampling()

      val testCases = Seq(MontExpTestCase(0, true))
      runTestCases(testCases)

    }
  }
}