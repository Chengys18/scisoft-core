/*-
 * Copyright 2015 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.xpdf;

import java.util.Arrays;
import java.util.List;

import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DoubleDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.IndexIterator;
import org.eclipse.dawnsci.analysis.dataset.impl.Maths;

/**
 * The class for cylindrical components of the experimental target. This class
 * is <code>public</code> because it needs to be visible in the
 * uk...xpdf.operations package
 * @author Timothy Spain timothy.spain@diamond.ac.uk
 * @since 2015-09-11
 *
 */
public class XPDFComponentCylinder extends XPDFComponentGeometry {

	/**
	 * Empty constructor
	 */
	public XPDFComponentCylinder() {
		super();
	}

	/**
	 * Copy constructor from another cylinder.
	 * @param inCyl
	 * 			cylinder to be copied
	 */
	public XPDFComponentCylinder(XPDFComponentCylinder inCyl) {
		super(inCyl);
	}

	/**
	 * Copy constructor from another geometric object.
	 * @param inGeom
	 * 				geometry to be copied
	 */
	public XPDFComponentCylinder(XPDFComponentGeometry inGeom) {
		super(inGeom);
	}

	/**
	 * Clone method.
	 */
	@Override
	protected XPDFComponentGeometry clone() {
		return new XPDFComponentCylinder(this);
	}

	/**
	 * Returns the shape of this cylinder.
	 */
	@Override
	public String getShape() {
		return "cylinder";
	}

	/**
	 * Calculates the illuminated volume.
	 * <p>
	 * Return the illuminated volume of this cylinder, given the beam data. The
	 *  beam is assumed to be centred on the cylinder. 
	 */
	@Override
	public double getIlluminatedVolume(XPDFBeamData beamData) {
		// Mathematics to match DK's python version. Never mind the
		// four-dimensional volume

		double h_2 = beamData.getBeamHeight() / 2;
		double illuminatedHeight = Math.PI;

		if (rOuter >= h_2)
			illuminatedHeight -= 2 * Math.cos(h_2 / rOuter) + h_2
					* Math.sqrt(h_2 * h_2 + rOuter * rOuter);

		return illuminatedHeight * beamData.getBeamWidth() * rOuter * rOuter;
	}

	/**
	 * Returns the path length upstream of the given points.
	 */
	@Override
	public Dataset getUpstreamPathLength(Dataset x, Dataset y, Dataset z) {
		// Thickness of the cylinder at x
		return thicknessAtDistanceFromRadius(x, z);
	}

	/**
	 * Returns the path length downstream of the given points.
	 */
	@Override
	public Dataset getDownstreamPathLength(Dataset x, Dataset y, Dataset z,
			double gamma, double delta) {
		return getDownstreamPathLengthExplicit(x, y, z, gamma, delta);
//		return getDownstreamPathLengthImplicit(x, y, z, gamma, delta);
	}

//	private Dataset getDownstreamPathLengthImplicit(Dataset x, Dataset y, Dataset z,
//			double gamma, double delta) {
//		// The capillary is held vertically, such that γ=π/2 is along it. 
//		Dataset d =
//				Maths.subtract(
//						Maths.multiply(x, Math.cos(delta)),
//						Maths.multiply(z, -Math.sin(delta))
//						);
//		Dataset w = 
//				Maths.add(
//						Maths.multiply(x, Math.sin(delta)),
//						Maths.multiply(z, -Math.cos(delta))
//						);
//
//		// Don't forget the secant factor
//		return Maths.divide(
//				thicknessAtDistanceFromRadius(d, w), Math.cos(gamma));
//		
//	}
	
	
	private Dataset getDownstreamPathLengthExplicit(Dataset xSet, Dataset ySet, Dataset zSet,
			double gamma, double delta) {
		double cd = Math.cos(delta), sd = Math.sin(delta);
		double cgamma = Math.cos(gamma);
		DoubleDataset lambda = new DoubleDataset(xSet);
		IndexIterator iter = xSet.getIterator();
		
		while(iter.hasNext()) {
			double x = xSet.getElementDoubleAbs(iter.index);
			double z = zSet.getElementDoubleAbs(iter.index);
			double d = x*cd - z*-sd;
			double w = x*sd + z*-cd;
			lambda.setAbs(iter.index, thicknessAtDistanceFromRadiusScalar(d, w)/cgamma);
		}
		return lambda;
	}

		
	/**
	 * Calculates the absorption correction map when attenuatorGeometry is attenuating.
	 */
	@Override
	public Dataset calculateAbsorptionCorrections(Dataset gamma, Dataset delta,
			XPDFComponentGeometry attenuatorGeometry, double attenuationCoefficient,
			XPDFBeamData beamData,
			boolean doUpstreamAbsorption, boolean doDownstreamAbsorption) {
		
		// Grid size for the high resolution data
		int nXHigh = delta.getShape()[0];
		int nYHigh = delta.getShape()[1];

		int[] nXYLow = new int[2];
		
		restrictGridSize(4096, nXHigh, nYHigh, nXYLow);
		
		int nXLow = nXYLow[0];
		int nYLow = nXYLow[1];

		// Down sampling of the angular coordinates for faster calculations
		Dataset gammaDown = XPDFRegrid.two(gamma, nXLow, nYLow);
		Dataset deltaDown = XPDFRegrid.two(delta, nXLow, nYLow);
		
		Dataset absorption = calculateAbsorptionFluorescence(gammaDown, deltaDown,
				Arrays.asList(new XPDFComponentGeometry[] {attenuatorGeometry}),
				Arrays.asList(new Double[] {attenuationCoefficient}), Arrays.asList(new Double[] {attenuationCoefficient}),
				beamData,
				doUpstreamAbsorption, doDownstreamAbsorption, true);
		
		// Upsample the absorption back to the original resolution and return
		return XPDFRegrid.two(absorption, nXHigh, nYHigh);
		}

	/**
	 * Returns smaller grid axes, based on the lengths of the originals and the maximum grid size.
	 * @param maxGrid
	 * 				maximum number of grid points to use.
	 * @param nXHigh
	 * 				original length of the x axis (dimension 0)
	 * @param nYHigh
	 * 				original length of the y axis (dimension 1)
	 * @param nXYLow
	 * 				return the values of the axis lengths using a 2 element int
	 * 				array, since Java has no pass by reference
	 */
	private void restrictGridSize(int maxGrid, int nXHigh, int nYHigh, int[] nXYLow) {
		int nXLow, nYLow;
		// Grid size for the low resolution calculations
		if (nXHigh*nYHigh < maxGrid) {
			nXLow = nXHigh;
			nYLow = nYHigh;
		} else {
			// Sort the axes
			int smallerDim, largerDim;
			boolean isXSmaller = nXHigh < nYHigh;
			if (isXSmaller) {
				smallerDim = nXHigh;
				largerDim = nYHigh;
			} else {
				smallerDim = nYHigh;
				largerDim = nXHigh;
			}

			// Deal with one axis being rather short
			if (smallerDim <= 2) {
				largerDim = (largerDim*smallerDim > maxGrid) ? maxGrid/smallerDim : largerDim;
			} else {
				double scale = maxGrid/(1.0*smallerDim*largerDim);
				smallerDim = (int) Math.ceil(Math.sqrt(scale) * smallerDim);
				smallerDim = (smallerDim < 2) ? 2 : smallerDim;
				largerDim = maxGrid/smallerDim;
			}
			
			// Unsort the axes
			if (isXSmaller) {
				nXLow = smallerDim;
				nYLow = largerDim;
			} else {
				nXLow = largerDim;
				nYLow = smallerDim;
			}
		
		}
		nXYLow[0] = nXLow;
		nXYLow[1] = nYLow;
	}
	
	/**
	 * For a circle, returns the chord distance from the -z boundary along the
	 * line that passes p from the centre of the circle.
	 * @param p
	 * 			Distance the line passes from the centre of the circle.
	 * @param z
	 * 			z coordinate of the desired point. 
	 * @return the Dataset of the path length for all the points provided.
	 */
	private Dataset thicknessAtDistanceFromRadius(Dataset p, Dataset z) {
		return thicknessAtDistanceFromRadiusExplicit(p, z);
	}
	
//	private Dataset thicknessAtDistanceFromRadiusImplicit(Dataset p, Dataset z) {
//		// Given a distance from the radius vector, calculate the path length
//		// parallel to the radius
//
//		Dataset zOuter = Maths.sqrt(
//				Maths.subtract(
//						rOuter*rOuter,
//						Maths.square(Maths.minimum(Maths.abs(p), rOuter))
//						)
//				);
//		Dataset zInner = Maths.sqrt(
//				Maths.subtract(
//						rInner*rInner,
//						Maths.square(Maths.minimum(Maths.abs(p), rInner))
//						)
//				);
//		
//		Dataset l = Maths.add(Maths.add(Maths.add(
//				Maths.maximum(Maths.multiply(z,  -1), zOuter), 
//				Maths.minimum(z, zOuter)),
//				Maths.minimum(z, Maths.multiply(zInner, -1))), 
//				Maths.maximum(Maths.multiply(z, -1), Maths.multiply(zInner, -1)));
//		
//		return Maths.abs(l);
//	}
	
	private Dataset thicknessAtDistanceFromRadiusExplicit(Dataset pSet, Dataset zSet) {
		DoubleDataset lSet = new DoubleDataset(zSet.getShape());
		IndexIterator iter = zSet.getIterator();
		
		while (iter.hasNext()) {
			lSet.setAbs(iter.index, thicknessAtDistanceFromRadiusScalar(
					pSet.getElementDoubleAbs(iter.index),
					zSet.getElementDoubleAbs(iter.index)));
		}
		
		return lSet;
	}

	private double thicknessAtDistanceFromRadiusScalar(double p, double z) {
		double outerMin = Math.min(Math.abs(p), rOuter);
		double zOuter = Math.sqrt( rOuter*rOuter - outerMin*outerMin );
		double innerMin = Math.min(Math.abs(p), rInner);
		double zInner = Math.sqrt( rInner*rInner -innerMin*innerMin );
		double l = Math.max(-z, zOuter) + Math.min(z, zOuter) + Math.min(z, -zInner) + Math.max(-z, -zInner);
		return Math.abs(l);		
	}

	@Override
	public Dataset calculateFluorescence(Dataset gamma, Dataset delta,
			List<XPDFComponentGeometry> attenuators,
			List<Double> attenuationsIn, List<Double> attenuationsOut,
			XPDFBeamData beamData,
			boolean doIncomingAbsorption, boolean doOutgoingAbsorption) {
		// Grid size for the high resolution data
		int nXHigh = delta.getShape()[0];
		int nYHigh = delta.getShape()[1];

		int[] nXYLow = new int[2];
		
//		restrictGridSize(4096, nXHigh, nYHigh, nXYLow);
		restrictGridSize(512, nXHigh, nYHigh, nXYLow);
		
		int nXLow = nXYLow[0];
		int nYLow = nXYLow[1];

		// Down sampling of the angular coordinates for faster calculations
		Dataset gammaDown = XPDFRegrid.two(gamma, nXLow, nYLow);
		Dataset deltaDown = XPDFRegrid.two(delta, nXLow, nYLow);

		Dataset fluorescence = calculateAbsorptionFluorescence(gammaDown, deltaDown,
						attenuators,
						attenuationsIn, attenuationsOut,
						beamData,
						doIncomingAbsorption, doOutgoingAbsorption, false);

		// Upsample the fluorescence back to the original resolution and return
		return XPDFRegrid.two(fluorescence, nXHigh, nYHigh);
		
	}
	
	private Dataset calculateAbsorptionFluorescence(Dataset gamma, Dataset delta,
			List<XPDFComponentGeometry> attenuators,
			List<Double> attenuationsIn, List<Double> attenuationsOut,
			XPDFBeamData beamData,
			boolean doIncomingAbsorption, boolean doOutgoingAbsorption,
			boolean illuminationNormalize) {
		double thickness = rOuter - rInner;
		
		// Account for the streamality of the (half?) cylinder
		double arc = 0.0, xiMin = 0.0, xiMax = 0.0;
		if (doIncomingAbsorption && doOutgoingAbsorption) {
			arc = 2*Math.PI;
			xiMin = -Math.PI;
			xiMax = Math.PI;
		} else {
			if (!(doIncomingAbsorption || doOutgoingAbsorption))
				return DoubleDataset.zeros(gamma);
			arc = Math.PI;
			if (doOutgoingAbsorption) {
				xiMin = -Math.PI/2;
				xiMax = Math.PI/2;
			} else if (doIncomingAbsorption) {
				xiMin = -3*Math.PI/2;
				xiMax = Math.PI/2;
			} else {
				; // You really shouldn't be here
			}
		}

		// Calculate the number of grid points in each dimension. The total
		// number should be gridSize, and the grid boxes should be roughly 
		// isotropic on the surface of the cylinder.
		double aspectRatio = (xiMax-xiMin)*rOuter/thickness;
		double log2RSteps = Math.round(Math.log(gridSize/aspectRatio)/2/Math.log(2.0));
		double rSteps = Math.pow(2.0, log2RSteps);
		double xiSteps = gridSize/rSteps;
		double dR = thickness/rSteps;
		double dXi = (arc)/xiSteps;
		
		Dataset r1D = DoubleDataset.createRange(rInner+dR/2, rOuter-dR/2+dR/1e6, dR);
		Dataset xi1D = DoubleDataset.createRange(xiMin+dXi/2, xiMax-dXi/2+dXi/1e6, dXi);
		

		// Expand the one dimensional coordinates to a two dimensional grid
		// TODO: Is this the best way to expand a Dataset?
		Dataset rCylinder = new DoubleDataset(r1D.getSize(), xi1D.getSize());
		Dataset xiCylinder = new DoubleDataset(r1D.getSize(), xi1D.getSize());
		for (int i = 0; i<rSteps; i++) {
			for (int k = 0; k<xiSteps; k++) {
				rCylinder.set(r1D.getDouble(i), i, k);
				xiCylinder.set(xi1D.getDouble(k), i, k);
			}
		}

		Dataset xPlate = Maths.multiply(rCylinder, Maths.sin(xiCylinder));
		Dataset yPlate = DoubleDataset.zeros(xPlate);
		Dataset zPlate = Maths.multiply(rCylinder, Maths.cos(xiCylinder));
		
		// Create a mask of the illuminated atoms in the cylinder.
		// TODO: There has to be a better way to make a mask Dataset
		Dataset illuminationPlate = DoubleDataset.ones(xPlate);
		for (int i=0; i<xPlate.getShape()[0]; i++){
			for (int k=0; k<xPlate.getShape()[1]; k++) {
				if (Math.abs(xPlate.getDouble(i, k)) > beamData.getBeamHeight()/2)
					illuminationPlate.set(0.0, i, k);
			}
		}
		
		Dataset illuminatedVolume = Maths.multiply(illuminationPlate, Maths.multiply(dR*dXi, rCylinder));
		// Loop over all detector angles
		IndexIterator iterAngle = gamma.getIterator();
		// total illuminated volume
		double totalIlluminatedVolume = 0;
		IndexIterator iterGrid = illuminatedVolume.getIterator();
		while(iterGrid.hasNext())
			totalIlluminatedVolume += illuminatedVolume.getElementDoubleAbs(iterGrid.index);

		Dataset volumeElement = Maths.multiply(dR*dXi, rCylinder);
		double totalVolume = (double) volumeElement.sum();

		double normalizationVolume = (illuminationNormalize) ? totalIlluminatedVolume : totalVolume;
		
		// For every direction, get the per-atom absorption of the radiation
		// scattered by this object, as attenuated by all the attenuating
		// objects.
		DoubleDataset attenuation = new DoubleDataset(gamma);
		
		// The amount of absorption experienced by photons reaching the grid
		// point from all considered attenuators
		Dataset inboundAttenuation = DoubleDataset.ones(rCylinder);
		
		// The upstream path length for each point is independent of scattering
		// angle.
		// Loop over all the attenuators in the List
		for (int iAttenuator = 0; iAttenuator < attenuators.size(); iAttenuator++) {
			Dataset upstreamPathLength;
			if (doIncomingAbsorption) {
				upstreamPathLength = attenuators.get(iAttenuator).getUpstreamPathLength(xPlate, yPlate, zPlate);
			} else {
				upstreamPathLength = DoubleDataset.zeros(xPlate);
			}

			double upstreamAttenuation = attenuationsIn.get(iAttenuator);

			// Total inbound attenuation for all objects
			inboundAttenuation.imultiply(Maths.exp(Maths.multiply(-upstreamAttenuation, upstreamPathLength)));
		}
		
		while(iterAngle.hasNext()) {
			//  Attenuation by all considered components, in the direction given by iterAngle at all grid points 
			Dataset outboundAttenuation = DoubleDataset.ones(rCylinder);
			for (int iAttenuator = 0; iAttenuator < attenuators.size(); iAttenuator++) {
				// Attenuation by this component, in the given direction, at all grid points
				Dataset downstreamPathLength;
				if (doOutgoingAbsorption)
					downstreamPathLength = attenuators.get(iAttenuator).getDownstreamPathLength(xPlate, yPlate, zPlate, gamma.getElementDoubleAbs(iterAngle.index), delta.getElementDoubleAbs(iterAngle.index));
				else
					downstreamPathLength = DoubleDataset.zeros(xPlate);

				double downstreamAttenuation = attenuationsOut.get(iAttenuator);
				outboundAttenuation.imultiply(Maths.exp(Maths.multiply(-downstreamAttenuation, downstreamPathLength)));
			}

			
			double illuminatedScattering = (double) Maths.multiply(illuminatedVolume, Maths.multiply(inboundAttenuation, outboundAttenuation)).sum();
//			iterGrid = outboundAttenuation.getIterator();
//			double illuminatedScattering = 0.0;
//			while (iterGrid.hasNext()) {
//				illuminatedScattering += illuminatedVolume.getElementDoubleAbs(iterGrid.index) *
//						inboundAttenuation.getElementDoubleAbs(iterGrid.index) * 
//						outboundAttenuation.getElementDoubleAbs(iterGrid.index);
//			}
			attenuation.setAbs(iterAngle.index, illuminatedScattering/normalizationVolume);
		}
		return attenuation;		
	}
	
}
