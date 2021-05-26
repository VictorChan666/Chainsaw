package FTN

import Chainsaw.{DSPSimTiming, _}
import breeze.numerics.{abs, pow}
import com.mathworks.matlab.types.Complex
import org.scalatest.funsuite.AnyFunSuite
import spinal.core.sim._
import spinal.core.{Real, Vec}

class FFTSim(N: Int, inverse: Boolean) extends FFTDUT(N, inverse) with DSPSimTiming[Vec[Real], Vec[Real], Array[Complex], Array[Complex]] {
  override def poke(testCase: Array[Complex], input: Vec[Real]): Unit = {
    testCase.indices.foreach { i =>
      input(2 * i) #= testCase(i).real
      input(2 * i + 1) #= testCase(i).imag
    }
  }

  override def peek(output: Vec[Real]): Array[Complex] =
    (0 until output.length / 2).map(i => new Complex(output(2 * i).toDouble, output(2 * i + 1).toDouble)).toArray

  /** The function that takes the testCase and return the ground truth
   *
   * @param testCase testCase
   * @return testResult
   */
  override def referenceModel(testCase: Array[Complex]): Array[Complex] = fft.referenceModel(testCase)

  /** Define the conditions by which you regard ref and dut as the same
   *
   * @param refResult - golden truth generated by the reference model
   * @param dutResult - output generated by DUT
   * @return
   */
  override def isValid(refResult: Array[Complex], dutResult: Array[Complex]): Boolean = {
    val refDoubles = refResult.flatMap(complex => Array(complex.real, complex.imag))
    val dutDoubles = dutResult.flatMap(complex => Array(complex.real, complex.imag))
    output.map(_.error).zip(refDoubles.zip(dutDoubles))
      .forall { case (err, pair) => abs(pair._1 - pair._2) <= pow(2, 0) }
  }

  /** Message String to log when !isValid(refResult, dutResult)
   *
   * @param testCase  - testCase corresponding to the result
   * @param refResult - golden truth generated by the reference model
   * @param dutResult - output generated by DUT
   */
  override def messageWhenInvalid(testCase: Array[Complex], refResult: Array[Complex], dutResult: Array[Complex]): String = {
    val refDoubles = refResult.flatMap(complex => Array(complex.real, complex.imag))
    val dutDoubles = dutResult.flatMap(complex => Array(complex.real, complex.imag))
    s"golden: ${refDoubles.map(_.toString.take(8)).mkString(" ")}\nyours : ${dutDoubles.map(_.toString.take(8)) mkString (" ")}"
  }

  override def messageWhenValid(testCase: Array[Complex], refResult: Array[Complex], dutResult: Array[Complex]): String =
    messageWhenInvalid(testCase, refResult, dutResult)
}

class testFFT extends AnyFunSuite {
  test("testFFT") {
    ChainsawDebug = true
    ChainsawExpLowerBound = -16
    val testFFTLength = 8
    //    SimConfig.withWave.compile(new FFTSim(testFFTLength, inverse = false)).doSim { dut =>
    //      dut.sim()
    //      (0 until 20).foreach(_ => dut.insertTestCase((0 until testFFTLength).map(_ => new Complex(DSPRand.nextDouble(), DSPRand.nextDouble())).toArray))
    //      dut.simDone()
    //    }
    //    printlnGreen(s"FFT test at length $testFFTLength passed")
    SimConfig.withWave.compile(new FFTSim(testFFTLength, inverse = true)).doSim { dut =>
      dut.sim()
      (0 until 20).foreach(_ => dut.insertTestCase((0 until testFFTLength).map(_ => new Complex(DSPRand.nextDouble() * 0.99, DSPRand.nextDouble() * 0.99)).toArray))
      dut.simDone()
    }
  }
}