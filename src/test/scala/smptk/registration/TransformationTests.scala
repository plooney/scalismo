package smptk.registration

import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers
import java.nio.ByteBuffer
import java.io.File
import java.io.IOException
import smptk.image._
import smptk.image.Image._
import smptk.io.ImageIO
import breeze.linalg.DenseVector

import smptk.geometry._
import smptk.geometry.implicits._

import smptk.image.Utils
import smptk.io.MeshIO

class TransformationTests extends FunSpec with ShouldMatchers {
  smptk.initialize()
  
  describe("A scaling in 2D") {
    val ss = ScalingSpace2D()
    val params = DenseVector(3.0)
    val scale = ss(params)
    val pt = Point2D(2.0, 1.0)
    val scaledPt = scale(pt)
    it("Scales a point correctly") {
      (scaledPt(0)) should be(6.0 plusOrMinus 0.0001)
      (scaledPt(1)) should be(3.0 plusOrMinus 0.0001)
    }

    it("Can be inverted") {
      val identitiyTransform = (ss.inverseTransform(params).get) compose scale
      (identitiyTransform(pt)(0) should be(pt(0) plusOrMinus 0.00001))
      (identitiyTransform(pt)(1) should be(pt(1) plusOrMinus 0.00001))
    }
  }

  describe("A Rotation in 2D") {
    val center = Point2D(2.0, 3.5)
    val rs = RotationSpace2D(center)
    val phi = scala.math.Pi / 2
    val rotationParams = rs.rotationParametersToParameterVector(phi.toFloat)
    val rotate = rs(rotationParams)
    val pt = Point2D(2.0, 2.0)
    val rotatedPt = rotate(pt)
    it("Rotates a point correctly") {
      (rotatedPt(0)) should be(3.5 plusOrMinus 0.0001)
      (rotatedPt(1)) should be(3.5 plusOrMinus 0.0001)
    }

    it("can be inverted") {
      val identitiyTransform = (rs.inverseTransform(rotationParams).get) compose rotate
      (identitiyTransform(pt)(0) should be(pt(0) plusOrMinus 0.00001))
      (identitiyTransform(pt)(1) should be(pt(1) plusOrMinus 0.00001))
    }

  }

  describe("A translation in 2D") {
    it("translates an image") {
      val testImgUrl = getClass().getResource("/lena.h5").getPath()
      val discreteImage = ImageIO.read2DScalarImage[Short](new File(testImgUrl)).get
      val continuousImage = Interpolation.interpolate(discreteImage, 3)

      val translation = TranslationSpace2D()(DenseVector[Double](10, 0))
      val translatedImg = continuousImage.compose(translation)
      val resampledImage = Resample.sample[Short](translatedImg, discreteImage.domain, 0)
      ImageIO.writeImage(resampledImage, new File("/tmp/resampled.h5"))

    }

    describe("composed with a rotation") {

      val ts = TranslationSpace2D()
      val center = Point2D(2.0, 3.5)
      val rs = RotationSpace2D(center)

      val productSpace = ts.product(rs)

      it("can be composed with a rotation 2D") {
        assert(productSpace.parametersDimensionality === ts.parametersDimensionality + rs.parametersDimensionality)
      }

      val transParams = DenseVector(1.0, 1.5)
      val translate = ts(transParams)

      val phi = scala.math.Pi / 2
      val rotationParams = rs.rotationParametersToParameterVector(phi.toFloat)
      val rotate = rs(rotationParams)

      val pt = Point2D(2.0, 2.0)
      val rotatedPt = rotate(pt)

      val translatedRotatedPt = translate(rotatedPt)

      val productParams = DenseVector.vertcat(transParams, rotationParams)
      val productTransform = productSpace(productParams)

      it("correctly transforms a point") {
        assert(productTransform(pt) === translatedRotatedPt)
      }
      val productDerivative = (x: Point[TwoD]) =>
        breeze.linalg.DenseMatrix.horzcat(
          ts.takeDerivativeWRTParameters(transParams)(x),
          (rs.takeDerivativeWRTParameters(rotationParams)(x)))
      it("differentiates correctly with regard to parameters") {
        assert(productSpace.takeDerivativeWRTParameters(productParams)(pt) === productDerivative(pt))
      }
      it("differenetiates correctly the parametrized transforms") {
        assert(productTransform.takeDerivative(pt) === translate.takeDerivative(rotate(pt)) * rotate.takeDerivative(pt))
      }

      it("can be inverted") {
        val identitiyTransform = (productSpace.inverseTransform(productParams).get) compose productTransform
        (identitiyTransform(pt)(0) should be(pt(0) plusOrMinus 0.00001f))
        (identitiyTransform(pt)(1) should be(pt(1) plusOrMinus 0.00001f))
      }
    }

    it("translates a 1D image") {
      val domain = DiscreteImageDomain1D(-50.0, 1.0, 100)
      val continuousImage = ContinuousScalarImage1D(domain, (x: Point[OneD]) => x * x, Some((x: Point[OneD]) => DenseVector(2.0 * x)))

      val translation = TranslationSpace1D()(DenseVector[Double](10))
      val translatedImg = continuousImage.compose(translation)

      assert(translatedImg(-10) === 0)
    }

  }

  describe("In 3D") {

    val path = getClass().getResource("/3dimage.h5").getPath()
    val discreteImage = ImageIO.read3DScalarImage[Short](new File(path)).get
    val continuousImage = Interpolation.interpolate(discreteImage, 0)

    it("translation forth and back of a real dataset yields the same image") {

      val parameterVector = DenseVector[Double](75.0, 50.0, 25.0)
      val translation = TranslationSpace3D()(parameterVector)
      val inverseTransform = TranslationSpace3D().inverseTransform(parameterVector).get
      val translatedForthBackImg = continuousImage.compose(translation).compose(inverseTransform)


      for (p <- discreteImage.domain.points.filter(translatedForthBackImg.isDefinedAt)) assert(translatedForthBackImg(p) === continuousImage(p))
    }

    it("rotation forth and back of a real dataset yields the same image") {

      val parameterVector = DenseVector[Double](2.0 * Math.PI, 2.0 * Math.PI, 2.0 * Math.PI)
      val origin = discreteImage.domain.origin
      val extent = discreteImage.domain.extent
      val center = ((extent - origin) * 0.5).toPoint

      val rotation = RotationSpace3D(center)(parameterVector)

      // val inverseRotation =  RotationSpace3D(center).inverseTransform(parameterVector).get

      val rotatedImage = continuousImage.compose(rotation)


      for (p <- discreteImage.domain.points.filter(rotatedImage.isDefinedAt)) assert(rotatedImage(p) === continuousImage(p))
    }

    it("rotation works on meshes") {

      val path = getClass().getResource("/facemesh.h5").getPath
      val mesh = MeshIO.readHDF5(new File(path)).get
       
      val region = mesh.boundingBox     
      val origin = region.origin
      val extent = region.extent
      val center = ((extent - origin) * 0.5).toPoint

      val parameterVector = DenseVector[Double](Math.PI, Math.PI, Math.PI)
      
      val rotation = RotationSpace3D(center)(parameterVector)
    }

  }

  describe("A Transformation space") {

  }

}
