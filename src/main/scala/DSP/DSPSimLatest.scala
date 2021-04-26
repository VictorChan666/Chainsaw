package DSP

import spinal.core._
import spinal.core.sim._
import spinal.lib._

import scala.collection.mutable

//trait DSPSim extends DSPGen {
trait DSPSimLatest[inputType <: Data, outputType <: Data, testCaseType, testResultType] extends Component {

  //  type inputType <: Data
  //  type outputType <: Data
  val input: Flow[inputType]
  val output: Flow[outputType]

  //  type testCaseType
  //  type testResultType
  val testCases = mutable.Queue[testCaseType]()
  val lastCase = mutable.Queue[testCaseType]()
  val refResults = mutable.Queue[testResultType]()
  val dutResults = mutable.Queue[testResultType]()

  def insertTestCase(testCase: testCaseType) = testCases.enqueue(testCase)

  var trueCase = 0
  var totalCase = 0
  var log = mutable.Queue[String]()
  var validLog = mutable.Queue[String]()

  val timing: TimingInfo

  def poke(testCase: testCaseType, input: inputType)

  def peek(output: outputType): testResultType

  def simInit(): Unit = {
    clockDomain.forkStimulus(2)
    input.valid #= false
    clockDomain.waitSampling(10)
  }

  // waitSampling should not accept 0
  def simDone() = {
    clockDomain.waitSampling(10)
    val protect = timing.initiationInterval - timing.latency - timing.outputInterval
    while (refResults.nonEmpty || dutResults.nonEmpty) clockDomain.waitSampling(if (protect > 0) protect else 1)
    (trueCase, totalCase, log, validLog)
  }

  /** The function that takes the testCase and return the ground truth
   *
   * @param testCase
   * @return
   */
  def referenceModel(testCase: testCaseType): testResultType

  /** Define the conditon by which you regard ref and dut as the same
   *
   * @param refResult - golden truth generated by the reference model
   * @param dutResult - output generated by DUT
   * @return
   */
  def isValid(refResult: testResultType, dutResult: testResultType): Boolean

  /** Message String to show when !isValid(refResult, dutResult)
   *
   * @param refResult - golden truth generated by the reference model
   * @param dutResult - output generated by DUT
   * @return
   */
  def messageWhenInvalid(testCase: testCaseType, refResult: testResultType, dutResult: testResultType): String

  def messageWhenValid(testCase: testCaseType, refResult: testResultType, dutResult: testResultType): String

  /** Define when and how the testCase is passed to DUT and reference model
   *
   * @example
   */
  def driver(): Unit = {
    val drv = fork {
      while (true) {
        if (testCases.nonEmpty) {
          val testCase = testCases.dequeue()
          lastCase.enqueue(testCase)
          val refResult = referenceModel(testCase)
          refResults.enqueue(refResult)
          input.valid #= true
          poke(testCase, input.payload)
          input.valid #= false
          clockDomain.waitSampling(timing.initiationInterval - timing.inputInterval)
        }
        else clockDomain.waitSampling()
      }
    }
  }

  /**
   *
   */
  def monitor() = {
    val mon = fork {
      while (true) {
        if (output.valid.toBoolean) {
          val dutResult = peek(output.payload)
          dutResults.enqueue(dutResult)
        }
        else clockDomain.waitSampling()
      }
    }
  }

  /** Compare ref and dut results, do assertion under test mode, and print the results under debug mode
   *
   */
  def scoreBoard(): Unit = {
    val score = fork {
      while (true) {
        if (refResults.nonEmpty && dutResults.nonEmpty) {
          val refResult = refResults.dequeue()
          val dutResult = dutResults.dequeue()
          val testCase = lastCase.dequeue()
          if (!isValid(refResult, dutResult)) log += messageWhenInvalid(testCase, refResult, dutResult)
          else {
            trueCase += 1
            validLog += messageWhenValid(testCase, refResult, dutResult)
          }
          totalCase += 1
          assert(isValid(refResult, dutResult) || debug, messageWhenInvalid(testCase, refResult, dutResult))
        }
        clockDomain.waitSampling()
      }
    }
  }

  def sim(): Unit = {
    simInit()
    driver()
    monitor()
    scoreBoard()
  }
}