package Chainsaw.DSP.interleave

import Chainsaw._
import Chainsaw.matlabIO._

object Refs {

  def matIntrlv[T](input: Array[T], row: Int, col: Int) = eng.feval[Array[T]]("matintrlv", input, Array(row), Array(col))


}
