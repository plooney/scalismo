package org.statismo.stk.core.image

import scala.language.implicitConversions
import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers
import org.statismo.stk.core.image.Interpolation._
import org.statismo.stk.core.geometry.Point.implicits._
import org.statismo.stk.core.geometry.Vector.implicits._
import org.statismo.stk.core.geometry.Index.implicits._
import org.scalatest.PrivateMethodTester
import org.statismo.stk.core.io.ImageIO
import java.io.File
import org.statismo.stk.core.geometry._

class InterpolationTest extends FunSpec with ShouldMatchers with PrivateMethodTester {

  implicit def doubleToFloat(d: Double) = d.toFloat

  org.statismo.stk.core.initialize()
  describe("A 1D Interpolation with 0rd order bspline") {

    it("interpolates the values for origin 2.3 and spacing 1.5") {
      val domain = DiscreteImageDomain[_1D](2.3f, 1.5f, 7)
      val discreteImage = DiscreteScalarImage1D(domain, Array[Float](1.4, 2.1, 7.5, 9.0, 8.0, 0.0, 2.1))
      val continuousImg = interpolate(discreteImage, 0)
      for ((pt, idx) <- discreteImage.domain.points.zipWithIndex) {
        continuousImg(pt) should be(discreteImage(idx) plusOrMinus 0.0001f)
      }
    }
  }

  describe("A 1D Interpolation with 1th order bspline") {

    it("interpolates the values for origin 2.3 and spacing 1.5") {
      val domain = DiscreteImageDomain[_1D](2.3f, 1.5f, 7)
      val discreteImage = DiscreteScalarImage1D[Float](domain, Array[Float](1.4, 2.1, 7.5, 9, 8, 0, 2.1))
      val continuousImg = interpolate(discreteImage, 1)
      for ((pt, idx) <- discreteImage.domain.points.zipWithIndex) {
        continuousImg(pt) should be(discreteImage(idx) plusOrMinus 0.0001f)
      }
    }

    it("interpolates the values for origin 0 and spacing 1") {
      val domain = DiscreteImageDomain[_1D](0f, 1, 5)
      val discreteImage = DiscreteScalarImage1D(domain, Array(3.0, 2.0, 1.5, 1.0, 0.0))
      val continuousImg = interpolate(discreteImage, 0)
      for ((pt, idx) <- discreteImage.domain.points.zipWithIndex) {
        assert(continuousImg(pt) === discreteImage(idx))
      }
    }

    describe("A 1D Interpolation with 3rd order bspline") {

      it("Derivative of interpolated Sine function is the Cosine") {
        val domain = DiscreteImageDomain[_1D](-2.0f, 0.01f, 400)

        val discreteSinImage = DiscreteScalarImage1D(domain, domain.points.map(x => math.sin(x * math.Pi)).toArray)
        val interpolatedSinImage = interpolate(discreteSinImage, 3)
        val derivativeImage = interpolatedSinImage.differentiate.get

        val discreteCosImage = DiscreteScalarImage1D(domain, domain.points.map(x => math.Pi * math.cos(x * math.Pi)).toArray)

        for ((pt, idx) <- domain.points.zipWithIndex.filter(x => math.abs(x._1) < 1.90)) {
          derivativeImage(pt)(0).toDouble should be(discreteCosImage(idx) plusOrMinus 0.0001f)
        }
      }
    }
  }

  describe("A 2D interpolation  Spline") {

    describe("of degree 0") {

      it("Has coefficients equal to the image samples") {

        val domain = DiscreteImageDomain[_2D]((1.0f, 0.0f), (0.5f, 1.0f), (2, 3))
        val discreteImage = DiscreteScalarImage2D[Float](domain, Array(1.4f, 2.1f, 7.5f, 9f, 8f, 0f))
        val coeffs = Interpolation.determineCoefficients(0, discreteImage)
        for (idx <- 0 until discreteImage.domain.points.size) {
          coeffs(idx) should be(discreteImage(idx) plusOrMinus 0.0001f)
        }
      }

      it("Interpolates the values for a simple domain") {
        val domain = DiscreteImageDomain[_2D]((0.0f, 0.0f), (1.0f, 1.0f), (2, 3))
        val discreteImage = DiscreteScalarImage2D(domain, Array(1f, 2f, 3f, 4f, 5f, 6f))

        val continuousImg = interpolate(discreteImage, 0)

        for ((pt, idx) <- discreteImage.domain.points.zipWithIndex) {
          continuousImg(pt) should be(discreteImage(idx) plusOrMinus 0.0001f)
        }
      }

      it("Interpolates the values for origin (2,3) and spacing (1.5, 2.3)") {
        val domain = DiscreteImageDomain[_2D]((2.0f, 3.0f), (1.5f, 0.1f), (2, 3))
        val discreteImage = DiscreteScalarImage2D(domain, Array(1.4f, 2.1f, 7.5f, 9f, 8f, 0f))

        val continuousImg = interpolate(discreteImage, 0)

        for ((pt, idx) <- discreteImage.domain.points.zipWithIndex) {
          continuousImg(pt) should be(discreteImage(idx) plusOrMinus 0.0001f)
        }
      }
    }
    describe(" of degree 3") {
      it("Interpolates the values for origin (2,3) and spacing (1.5, 2.3)") {
        val domain = DiscreteImageDomain[_2D]((2.0f, 3.0f), (1.5f, 1.3f), (10, 10))
        val discreteImage = DiscreteScalarImage2D(domain, domain.points.map(x => x(0)).toArray)

        val continuousImg = interpolate(discreteImage, 3)

        for ((pt, idx) <- discreteImage.domain.points.zipWithIndex) {
          continuousImg(pt) should be(discreteImage(idx) plusOrMinus 0.0001f)
        }
      }

      it("Interpolates the values correctly for a test dataset") {
        val testImgUrl = getClass.getResource("/lena256.h5").getPath
        val discreteFixedImage = ImageIO.read2DScalarImage[Short](new File(testImgUrl)).get
        val interpolatedImage = Interpolation.interpolate(discreteFixedImage, 2)

        for ((p, i) <- discreteFixedImage.domain.points.zipWithIndex) {
          interpolatedImage(p).toShort should be(discreteFixedImage(i) plusOrMinus 30)
        }
      }

      it("Derivative of interpolated function is correct") {
        val domain = DiscreteImageDomain[_2D]((-2.0f, -2.0f), (0.01f, 0.01f), (400, 400))

        val discreteFImage = DiscreteScalarImage2D(domain, domain.points.map(x => x(0) * x(0) + x(1) * x(1)).toArray)
        val interpolatedFImage = interpolate(discreteFImage, 3)
        val derivativeImage = interpolatedFImage.differentiate.get

        for ((pt, idx) <- domain.points.zipWithIndex.filter(x => math.abs(x._1(0)) < 1.90 && math.abs(x._1(1)) < 1.90)) {
          derivativeImage(pt)(0) should be((2 * pt(0)) plusOrMinus 0.001f)
          derivativeImage(pt)(1) should be((2 * pt(1)) plusOrMinus 0.001f)
        }
      }
    }
  }
  describe("A 3D interpolation  Spline") {
    describe("of degree 0") {

      it("Has coefficients equal to the image samples") {

        val domain = DiscreteImageDomain[_3D]((2.0f, 3.0f, 0.0f), (1.5f, 1.3f, 2.0f), (2, 3, 2))
        val discreteImage = DiscreteScalarImage3D[Float](domain, Array(1.4f, 2.1f, 7.5f, 9f, 8f, 0f, 1.4f, 2.1f, 7.5f, 9f, 8f, 0f))

        for (idx <- 0 until discreteImage.domain.points.size) {
          val coeffs = Interpolation.determineCoefficients(0, discreteImage)
          coeffs(idx) should be(discreteImage(idx) plusOrMinus 0.0001f)
        }
      }

      it("Interpolates the values for origin (2,3,0) and spacing (1.5, 1.3, 2)") {
        val domain = DiscreteImageDomain[_3D]((2.0f, 3.0f, 0.0f), (1.5f, 1.3f, 2.0f), (2, 3, 2))
        val discreteImage = DiscreteScalarImage3D[Float](domain, Array(1.4f, 2.1f, 7.5f, 9f, 8f, 0f, 1.4f, 2.1f, 7.5f, 9f, 8f, 0f))

        val continuousImg = interpolate(discreteImage, 0)

        for ((pt, idx) <- discreteImage.domain.points.zipWithIndex) {
          continuousImg(pt) should be(discreteImage(idx) plusOrMinus 0.0001f)
        }
      }
    }

    describe(" of degree 1") {
      it("Interpolates the values for origin (2,3,0) and spacing (1.5, 1.3, 2)") {
        val domain = DiscreteImageDomain[_3D]((2.0f, 3.0f, 0.0f), (1.5f, 1.3f, 2.0f), (2, 3, 2))
        val discreteImage = DiscreteScalarImage3D[Float](domain, Array(1.4f, 2.1f, 7.5f, 9f, 8f, 0f, 1.4f, 2.1f, 7.5f, 9f, 8f, 0f))

        val continuousImg = interpolate(discreteImage, 1)

        for ((pt, idx) <- discreteImage.domain.points.zipWithIndex) {
          continuousImg(pt) should be(discreteImage(idx) plusOrMinus 0.0001f)
        }
      }
    }

    describe(" of degree 3") {
      it("Interpolates the values for origin (2,3,0) and spacing (1.5, 1.3, 2)") {
        val domain = DiscreteImageDomain[_3D]((2.0f, 3.0f, 0.0f), (1.5f, 1.3f, 2.0f), (10, 10, 10))
        val discreteImage = DiscreteScalarImage3D[Float](domain, domain.points.map(x => x(0)).toArray)

        val continuousImg = interpolate(discreteImage, 3)

        for ((pt, idx) <- discreteImage.domain.points.zipWithIndex) {
          continuousImg(pt) should be(discreteImage(idx) plusOrMinus 0.0001f)
        }
      }

      it("Derivative of interpolated function is correct") {
        val domain = DiscreteImageDomain[_3D]((-2.0f, -2.0f, -2.0f), (0.1f, 0.1f, 0.1f), (40, 40, 40))

        val discreteFImage = DiscreteScalarImage3D(domain, domain.points.map(x => x(0) * x(0) + x(1) * x(1) + x(2) * x(2)).toArray)
        val interpolatedFImage = interpolate(discreteFImage, 3)
        val derivativeImage = interpolatedFImage.differentiate.get

        for ((pt, idx) <- domain.points.zipWithIndex.filter(x => math.abs(x._1(0)) < 1.0 && math.abs(x._1(1)) < 1.0 && math.abs(x._1(2)) < 1.0)) {
          derivativeImage(pt)(0) should be((2 * pt(0)) plusOrMinus 0.0001)
          derivativeImage(pt)(1) should be((2 * pt(1)) plusOrMinus 0.0001)
          derivativeImage(pt)(2) should be((2 * pt(2)) plusOrMinus 0.0001)
        }
      }

      it("Interpolates a real dataset correctly") {
        val path = getClass.getResource("/3dimage.h5").getPath
        val discreteImage = ImageIO.read3DScalarImage[Short](new File(path)).get
        val continuousImage = Interpolation.interpolate(discreteImage, 1)

        for ((p, i) <- discreteImage.domain.points.zipWithIndex.filter(p => p._2 % 100 == 0))
          discreteImage.values(i) should be(continuousImage(p).toShort plusOrMinus 1.toShort)
      }
    }
  }
}
