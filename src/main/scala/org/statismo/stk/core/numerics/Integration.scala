package org.statismo.stk.core.numerics

import org.statismo.stk.core.geometry.Vector.VectorFactory
import org.statismo.stk.core.image._
import breeze.linalg.DenseVector
import org.statismo.stk.core.geometry._
import org.statismo.stk.core.image.DiscreteImageDomain
import org.statismo.stk.core.common.BoxedDomain
import org.statismo.stk.core.common.BoxedDomain1D
import org.statismo.stk.core.common.BoxedDomain2D

//trait Sampler[D <: Dim] {  
//  def sample(boxedRegion: BoxedDomain[D], numberOfPoints: Int): IndexedSeq[Point[D]]
//}

//trait UniformSampler[D <: Dim] extends Sampler[D]
//trait RandomSampler[D <: Dim] extends Sampler[D]
//
//case class UniformSampler1D extends UniformSampler[OneD] {
//  def sample(boxedRegion: BoxedDomain[OneD], numberOfPoints: Int = 300) = {
//    val step = (boxedRegion.extent(0) - boxedRegion.origin(0)) / numberOfPoints.toDouble
//    for (i <- 0 until numberOfPoints) yield Point1D(boxedRegion.origin(0) + i * step)
//  }
//
//}
//
//case class UniformSampler2D extends UniformSampler[TwoD] {
//  def sample(region: BoxedDomain[TwoD], numberOfPoints: Int = 300) = {
//    val nbPerDim = math.sqrt(numberOfPoints).floor.toInt
//    val step0 = (region.extent(0) - region.origin(0)) / nbPerDim
//    val step1 = (region.extent(1) - region.origin(1)) / nbPerDim
//    
//    for (i <- 0 until nbPerDim; j <- 0 until nbPerDim) yield Point2D(region.origin(0) + i * step0, region.origin(1) + j * step1)
//  }
//}
//
//case class UniformSampler3D extends UniformSampler[ThreeD] {
//  def sample(region: BoxedDomain[ThreeD], numberOfPoints: Int = 300) = {
//    val nbPerDim = math.cbrt(numberOfPoints).floor.toInt
//    val step0 = (region.extent(0) - region.origin(0)) / nbPerDim
//    val step1 = (region.extent(1) - region.origin(1)) / nbPerDim
//    val step2 = (region.extent(2) - region.origin(2)) / nbPerDim
//    
//    for (i <- 0 until nbPerDim; j <- 0 until nbPerDim; k <- 0 until nbPerDim) 
//      yield Point3D(region.origin(0) + i * step0, region.origin(1) + j * step1, region.origin(2) + k * step2)
//  }
//}
//
//
//
//case class UniformDistributionRandomSampler1D extends RandomSampler[OneD] {
//  def sample(region: BoxedDomain[OneD], numberOfPoints: Int = 300) = {
//    val distr = breeze.stats.distributions.Uniform(region.origin(0), region.extent(0))
//    (0 until numberOfPoints).map(i => Point1D(distr.draw))
//  }
//}
//
//case class UniformDistributionRandomSampler2D extends RandomSampler[TwoD] {
//  def sample(region: BoxedDomain[TwoD], numberOfPoints: Int = 300) = {
//    val distrDim1 = breeze.stats.distributions.Uniform(region.origin(0), region.extent(0))
//    val distrDim2 = breeze.stats.distributions.Uniform(region.origin(1), region.extent(1))
// 
//    (0 until numberOfPoints).map(i => Point2D(distrDim1.draw, distrDim2.draw))
//  }
//}
//
//case class UniformDistributionRandomSampler3D extends RandomSampler[ThreeD] {
//  def sample(region: BoxedDomain[ThreeD], numberOfPoints: Int = 300) = {
//    val distrDim1 = breeze.stats.distributions.Uniform(region.origin(0), region.extent(0))
//    val distrDim2 = breeze.stats.distributions.Uniform(region.origin(1), region.extent(1))
//    val distrDim3 = breeze.stats.distributions.Uniform(region.origin(2), region.extent(2))
//    
//    (0 until numberOfPoints).map(i => Point3D(distrDim1.draw, distrDim2.draw, distrDim3.draw))
//  }
//}
//
//
//case class SampleOnceSampler[D <: Dim](sampler: Sampler[D]) extends Sampler[D] {
// 
//  var points : IndexedSeq[Point[D]] = IndexedSeq()
//  def sample(boxedRegion: BoxedDomain[D], numberOfPoints: Int): IndexedSeq[Point[D]] = { 
//    
//    if(points.size == numberOfPoints) {
//      points 
//    }
//    else {
//      points = sampler.sample(boxedRegion, numberOfPoints)
//      points
//    }
//  }
//    
//  def sampledPoints = points
//}

case class IntegratorConfiguration[D <: Dim](sampler: Sampler[D])

case class Integrator[D <: Dim: ToInt : VectorFactory](configuration: IntegratorConfiguration[D]) {

  def sampler = configuration.sampler

  def integrateScalar(img: ContinuousScalarImage[D]): Float = {
    integrateScalar(img.liftPixelValue)
  }

  def integrateScalar(f: Function1[Point[D], Option[Float]]): Float = {
    val samples = configuration.sampler.sample

    val sum = samples.par.map { case (pt, p) => f(pt).getOrElse(0f) * 1f / p.toFloat }.sum

    sum / (sampler.numberOfPoints - 1).toFloat
  }

  def integrateVector(img: ContinuousVectorImage[D]): Vector[D] = {
    integrateVector(img.liftPixelValue)
  }

  def integrateVector(f: Function1[Point[D], Option[Vector[D]]]): Vector[D] = {
    val samples = configuration.sampler.sample

    val zeroVector = Vector.zeros[D]
    val sum = samples.par.map { case (pt, p) => f(pt).getOrElse(zeroVector) * (1f / p.toFloat) }.foldLeft(zeroVector)((a, b) => { a + b })
    sum * (1f / (sampler.numberOfPoints - 1).toFloat)
  }

  def integrateVector(f: Function1[Point[D], Option[DenseVector[Float]]], dimensionality: Int): DenseVector[Float] = {
    val samples = configuration.sampler.sample

    val zeroVector = DenseVector.zeros[Float](dimensionality)
    val sum = samples.par.map { case (pt, p) => f(pt).getOrElse(zeroVector) * (1f / p.toFloat) }.foldLeft(zeroVector)((a, b) => { a + b })
    sum * (1f / (sampler.numberOfPoints - 1).toFloat)
  }

}


