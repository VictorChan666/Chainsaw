package Chainsaw.DSP.rings

import scala.collection.JavaConversions._
import cc.redberry.rings
import cc.redberry.rings.bigint.BigInteger
import cc.redberry.rings.scaladsl.GaloisField64._
import rings.scaladsl._

object ringsExamples extends App {

  import rings.poly.PolynomialMethods._
  import syntax._

  // polynomial ring is specified by a ring and a list of variables
  UnivariateRing(Z, "x") // univariate polynomial, coefficients are integers
  MultivariateRing(Z, Array("x", "y", "z")) // multivariate polynomial, coefficients are integers
  MultivariateRing(Q, Array("x", "y", "z")) // multivariate polynomial, coefficients are rationals

  UnivariateRingZp64(3, "x") // // univariate polynomial, built on Z/3
  MultivariateRingZp64(3, Array("x", "y", "z")) // multivariate polynomial, built on Z/3

  MultivariateRing(Zp(Z(2).pow(107) - 1), Array("x", "y", "z")) // multivariate polynomial, built on Z/2^107-1

  // when p is prime, Zp is isomorphism of GF(p) ?

  // Galios filed can be specified by p^m
  val GF7_10 = GF(7, 10, "x")
  // in rings, elements of GF(p^m) are represented as a polynomial on Z/p
  println(asFiniteField(GF7_10).iterator().toSeq(10))
  // or, it can be generated by an irreducible polynomial
  GF(UnivariateRingZp64(7, "z")("1 + 3*z + z^2 + z^3"), "z")

  // Frac means field of fractions
  Frac(UnivariateRing(Z, "x"))
  Frac(MultivariateRingZp64(19, Array("x", "y", "z")))

  // find the generator of GF(p)
  def getGenerator(p:Int) = {
    val field = Zp(p)
    def isGenerator(candidate:Int) = {
      val want: Array[IntZ] = field.iterator().toArray.tail // all the elements except 0
      val get: Seq[BigInteger] = want.map(i => field.pow(candidate, i)).sorted // powers of the candidate
      want.zip(get).forall{ case (z, integer) => z.intValue() == integer.intValue()} // check whether get is a permutation of want
    }
    field.iterator().toArray.dropWhile(i => !isGenerator(i.intValue())).head // run isGenerator until we find one
  }

}
