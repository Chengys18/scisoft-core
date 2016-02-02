/*-
 * Copyright 2015 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.xpdf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.dataset.ILazyDataset;
import org.eclipse.dawnsci.analysis.api.metadata.IDiffractionMetadata;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DoubleDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.Maths;
import org.eclipse.dawnsci.analysis.dataset.impl.Signal;
import org.eclipse.dawnsci.analysis.dataset.operations.AbstractOperationBase;

import uk.ac.diamond.scisoft.analysis.diffraction.powder.IPixelIntegrationCache;
import uk.ac.diamond.scisoft.analysis.diffraction.powder.PixelIntegration;
import uk.ac.diamond.scisoft.analysis.diffraction.powder.PixelIntegrationBean;
import uk.ac.diamond.scisoft.analysis.diffraction.powder.PixelIntegrationCache;
import uk.ac.diamond.scisoft.analysis.roi.XAxis;

/**
 * A class for holding the information to calibrate the XPDF data.
 * @author Timothy Spain timothy.spain@diamond.ac.uk
 * @since 2015-09-11
 */
public class XPDFCalibration {

	private LinkedList<Double> calibrationConstants;
	private Double calibrationConstant0;
	private XPDFQSquaredIntegrator qSquaredIntegrator;
	private double selfScatteringDenominator;
	private Dataset multipleScatteringCorrection;
	private double nSampleIlluminatedAtoms;
	private ArrayList<Dataset> backgroundSubtracted;
	private XPDFAbsorptionMaps absorptionMaps;
	private XPDFCoordinates coords;
	private XPDFDetector tect;
	private XPDFBeamData beamData;
	private Dataset sampleFluorescence;
	private double fluorescenceScale;
	private Dataset sampleSelfScattering;
	
	private boolean doFluorescence;
	
	/**
	 * Empty constructor.
	 */
	public XPDFCalibration() {
		calibrationConstants = new LinkedList<Double>();
		qSquaredIntegrator = null;
		selfScatteringDenominator = 1.0;
		multipleScatteringCorrection = null;
		nSampleIlluminatedAtoms = 1.0; // there must be at least one
		backgroundSubtracted = new ArrayList<Dataset>();
		doFluorescence = true;
	}
	
	/**
	 * Copy constructor.
	 * @param inCal
	 * 				calibration object to be copied.
	 */
	public XPDFCalibration(XPDFCalibration inCal) {
		if (inCal.calibrationConstants != null) {
			this.calibrationConstants = new LinkedList<Double>();
			for (double c3 : inCal.calibrationConstants)
				this.calibrationConstants.add(c3);
		}
		this.qSquaredIntegrator = (inCal.qSquaredIntegrator != null) ? inCal.qSquaredIntegrator : null;
		this.selfScatteringDenominator = inCal.selfScatteringDenominator;
		this.multipleScatteringCorrection = (inCal.multipleScatteringCorrection != null) ? new DoubleDataset(inCal.multipleScatteringCorrection) : null;
		this.nSampleIlluminatedAtoms = inCal.nSampleIlluminatedAtoms;
		if (inCal.backgroundSubtracted != null) {
			this.backgroundSubtracted = new ArrayList<Dataset>();
			for (Dataset data : inCal.backgroundSubtracted) {
				this.backgroundSubtracted.add(data);
			}
		}
		this.tect = (inCal.tect != null) ? new XPDFDetector(inCal.tect): null;
		this.beamData = (inCal.beamData != null) ? new XPDFBeamData(inCal.beamData) : null;
		this.sampleFluorescence = (inCal.sampleFluorescence != null) ? inCal.sampleFluorescence.clone() : null;
		this.fluorescenceScale = inCal.fluorescenceScale;
		this.doFluorescence = inCal.doFluorescence;
	}

	/**
	 * Gets the most recent calibration constant from the list.
	 * @return the most recent calibration constant.
	 */
	public double getCalibrationConstant() {
		return calibrationConstants.getLast();
	}

	/**
	 * Sets the initial calibration constant to be iterated.
	 * <p>
	 * Sets the initial calibration constant. If there is already an array,
	 * then a new Array is created.
	 * @param calibrationConstant
	 * 							the initial value of the calibration constant.
	 */
	public void initializeCalibrationConstant(double calibrationConstant) {
//		if (this.calibrationConstants == null || !this.calibrationConstants.isEmpty())
//			this.calibrationConstants = new LinkedList<Double>();
//		this.calibrationConstants.add(calibrationConstant);
		calibrationConstant0 = calibrationConstant;
	}

	/**
	 * Sets up the q² integrator class to use in the calculation of the constants
	 * @param qSquaredIntegrator
	 * 							a properly constructed q² integrator class to
	 * 							be used to integrate scattering in the sample.
	 */
	public void setqSquaredIntegrator(XPDFQSquaredIntegrator qSquaredIntegrator) {
		this.qSquaredIntegrator = new XPDFQSquaredIntegrator(qSquaredIntegrator);
	}

	/**
	 * Calculates the denominator used in calculating the calibration constant.
	 * <p> 
	 * Difference of the Krogh-Moe sum and integral of Thomson self-scattering
	 * for the sample, used as the denominator of the updating factor of the
	 * calibration constant.
	 * @param sample
	 * 				Sample for which the properties should be calculated.
	 * @param coordinates
	 * 					Angles and beam energy at which the properties should be calculated.
	 */
	public void setSelfScatteringDenominatorFromSample(XPDFTargetComponent sample, XPDFCoordinates coordinates) {
		selfScatteringDenominator = qSquaredIntegrator.ThomsonIntegral(sample.getSelfScattering(coordinates))
				- sample.getKroghMoeSum();
	}

	/**
	 * Sets a Dataset for the multiple scattering correction.
	 * <p>
	 * Set the multiple scattering correction. Presently takes a zero Dataset
	 * of the same shape as the angle arrays. 
	 * @param multipleScatteringCorrection
	 * 									A zero Dataset.
	 */
	public void setMultipleScatteringCorrection(Dataset multipleScatteringCorrection) {
		this.multipleScatteringCorrection = new DoubleDataset(multipleScatteringCorrection);
	}

	/**
	 * Sets the number of atoms illuminated in the sample.
	 * @param nSampleIlluminatedAtoms
	 * 								the number of atoms illuminated in the sample.
	 */
	public void setSampleIlluminatedAtoms(double nSampleIlluminatedAtoms) {
		this.nSampleIlluminatedAtoms = nSampleIlluminatedAtoms;
	}

	/**
	 * Sets the list of target component traces with their backgrounds subtracted.
	 * @param backgroundSubtracted
	 * 							A List of Datasets containing the ordered sample and containers.
	 */
	public void setBackgroundSubtracted(List<Dataset> backgroundSubtracted) {
		this.backgroundSubtracted = new ArrayList<Dataset>();
		for (Dataset data : backgroundSubtracted)
			this.backgroundSubtracted.add(data);
	}

	/**
	 * Sets the absorption maps.
	 * <p>
	 * Set the absorption maps object that holds the maps between the target
	 * components stored in the list of background subtracted traces. The
	 * ordinals used in the list and used to index the maps. 
	 * @param absorptionMaps
	 * 						The absorption maps in their holding class.
	 */
	public void setAbsorptionMaps(XPDFAbsorptionMaps absorptionMaps) {
		this.absorptionMaps = new XPDFAbsorptionMaps(absorptionMaps);
	}
	
	public void setCoordinates(XPDFCoordinates inCoords) {
		this.coords = inCoords;
	}
	
	public void setDetector(XPDFDetector inTect) {
		this.tect = inTect;
	}
	
	public void setBeamData(XPDFBeamData inBeam) {
		this.beamData = inBeam;
	}
	
	public void setSampleFluorescence(Dataset sampleFluor) {
		this.sampleFluorescence = sampleFluor;
	}
	
	public void setSelfScattering(XPDFTargetComponent sample) {
		this.sampleSelfScattering = sample.getSelfScattering(coords);
	}
	
	/**
	 * Iterates the calibration constant for five iterations.
	 * <p>
	 * Perform the iterations to converge the calibration constant of the data.
	 * The steps performed are:
	 * <ul>
	 * <li>divide by the old calibration constant
	 * <li>apply the multiple scattering correction
	 * <li>apply the calibration constant
	 * <li>divide by the number of atoms contributing to the signal to produce the absorption corrected data
	 * <li>calculate the ratio of the new to old calibration constants
	 * </ul>
	 * @return the absorption corrected data
	 */
	private Dataset iterate(List<Dataset> fluorescenceCorrected, boolean propagateErrors) {
		// Detector transmission correction
		List<Dataset> deTran = new ArrayList<Dataset>();
		for (Dataset componentTrace : fluorescenceCorrected) {
			Dataset deTranData = tect.applyTransmissionCorrection(componentTrace, coords.getTwoTheta(), beamData.getBeamEnergy());
			// Error propagation
			if (propagateErrors && componentTrace.getError() != null)
				deTranData.setError(tect.applyTransmissionCorrection(componentTrace.getError(), coords.getTwoTheta(), beamData.getBeamEnergy()));
			deTran.add(deTranData);
		}
		
		// Divide by the calibration constant and subtract the multiple scattering correction
		List<Dataset> calCon = new ArrayList<Dataset>();
		for (Dataset componentTrace : deTran) {
			Dataset calConData = Maths.divide(componentTrace, calibrationConstants.getLast()); 
			// Error propagation
			if (propagateErrors && componentTrace.getError() != null)
				calConData.setError(Maths.divide(componentTrace.getError(), calibrationConstants.getLast()));
			// Set the data
			calCon.add(calConData);
		}
		
		// Mulcor should be a LinkedList, so that we can get the last element simply
		List<Dataset> mulCor = new ArrayList<Dataset>();
		if (multipleScatteringCorrection != null) {
			for (Dataset componentTrace : calCon) {
				Dataset mulCorData = Maths.subtract(componentTrace, multipleScatteringCorrection);
				// Error propagation: no change if the multiple scattering correction is taken as exact
				if (propagateErrors && componentTrace.getError() != null)
					mulCorData.setError(componentTrace.getError());
				mulCor.add(mulCorData);
			}
		} else {
			for (Dataset componentTrace : calCon) {
				Dataset mulCorData = Maths.subtract(componentTrace, 0);
				//Error propagation
				if (propagateErrors && componentTrace.getError() != null)
					mulCorData.setError(componentTrace.getError());
				mulCor.add(mulCorData);
			}
		}
		Dataset absCor = applyCalibrationConstant(mulCor);

		absCor.idivide(nSampleIlluminatedAtoms);
		if (propagateErrors && absCor.getError() != null)
			absCor.getError().idivide(nSampleIlluminatedAtoms);

		Dataset absCorP = applyPolarizationConstant(absCor);
		
		// Integrate
		double numerator = qSquaredIntegrator.ThomsonIntegral(absCorP);
		// Divide by denominator
		double aMultiplier = numerator/selfScatteringDenominator;
		// Make the new calibration constant
		double updatedCalibration = aMultiplier * calibrationConstants.getLast();
		// Add to the list
		calibrationConstants.add(updatedCalibration);

		return absCorP;
	}

	/**
	 * Eliminates the scattered radiation from all containers from the data.
	 * <p>
	 * Subtract the scattered radiation from each container from the multiple-
	 * scattering corrected data.
	 * @param mulCor
	 * 				list of the radiation scattered from the full target and
	 * 				each container, ordered innermost outwards, with the sample
	 * 				as the first element. 
	 * @return the absorption corrected sample data.
	 */
	private Dataset applyCalibrationConstant(List<Dataset> mulCor) {
		// Use size and indexing, rather than any fancy-pants iterators or such
		int nComponents = mulCor.size();
		
		// need to retain the original data for the next time around the loop,
		// so copy to absorptionTemporary, an ArrayList.
		List<Dataset> absorptionTemporary = new ArrayList<Dataset>();
		for (Dataset data : mulCor) {
			Dataset absTempData = new DoubleDataset(data);
			if (data.getError() != null)
				absTempData.setError(data.getError());
			absorptionTemporary.add(absTempData);
		}
		
		// The objects are ordered outwards; 0 is the sample, nComponents-1 the
		// outermost container
		// The scatterers loop over all containers, but not the sample
		for (int iScatterer = nComponents-1; iScatterer > 0; iScatterer--){
			// The innermost absorber here considered. Does not include the
			// Scatterer, but does include the sample 
			for (int iInnermostAbsorber = iScatterer - 1; iInnermostAbsorber >= 0; iInnermostAbsorber--) {
				// A product of absorption maps from (but excluding) the
				// scatterer, to (and including) the innermost absorber
				// Set the initial term of the partial product to be the absorber
				Dataset subsetAbsorptionCorrection = this.absorptionMaps.getAbsorptionMap(iScatterer, iInnermostAbsorber);
				for (int iAbsorber = iInnermostAbsorber+1; iAbsorber < iScatterer; iAbsorber++)
					subsetAbsorptionCorrection = Maths.multiply(subsetAbsorptionCorrection, this.absorptionMaps.getAbsorptionMap(iScatterer, iAbsorber));
//					subsetAbsorptionCorrection.imultiply(this.absorptionMaps.getAbsorptionMap(iScatterer, iAbsorber));
				// Subtract the scattered radiation, corrected for absorption, from the attenuator's radiation
				absorptionTemporary.get(iInnermostAbsorber).isubtract(
//						Maths.divide(
						Maths.multiply(
								absorptionTemporary.get(iScatterer),
//								subsetAbsorptionCorrection.reshape(subsetAbsorptionCorrection.getSize())));
								subsetAbsorptionCorrection.squeeze()));

				// Error propagation. If either is present, then set an error on the result. Non-present errors are taken as zero (exact).
				if (absorptionTemporary.get(iInnermostAbsorber).getError() != null ||
						absorptionTemporary.get(iScatterer).getError() != null) {
					Dataset innerError = (absorptionTemporary.get(iInnermostAbsorber).getError() != null) ?
							absorptionTemporary.get(iInnermostAbsorber).getError() :
								DoubleDataset.zeros(absorptionTemporary.get(iInnermostAbsorber));
					Dataset scatterError = (absorptionTemporary.get(iScatterer).getError() != null) ?
//							Maths.divide(
							Maths.multiply(
									absorptionTemporary.get(iScatterer).getError(),
									subsetAbsorptionCorrection.reshape(subsetAbsorptionCorrection.getSize())) :
//									subsetAbsorptionCorrection.squeeze()) :
								DoubleDataset.zeros(absorptionTemporary.get(iScatterer));
					absorptionTemporary.get(iInnermostAbsorber).setError(Maths.sqrt(Maths.add(Maths.square(innerError), Maths.square(scatterError))));
				}
				
			}
		}
		
		// start with sample self-absorption
		Dataset absorptionCorrection = this.absorptionMaps.getAbsorptionMap(0, 0);
		for (int iAbsorber = 1; iAbsorber < nComponents; iAbsorber++)
			absorptionCorrection = Maths.multiply(absorptionCorrection, absorptionMaps.getAbsorptionMap(0, iAbsorber));
		
//		Dataset absCor = Maths.divide(absorptionTemporary.get(0), absorptionCorrection.reshape(absorptionCorrection.getSize()));
		Dataset absCor = Maths.divide(absorptionTemporary.get(0), absorptionCorrection.getSliceView().squeeze());
		// Error propagation
		if (absorptionTemporary.get(0).getError() != null) {
//			absCor.setError(Maths.divide(absorptionTemporary.get(0).getError(), absorptionCorrection.reshape(absorptionCorrection.getSize())));
			absCor.setError(Maths.divide(absorptionTemporary.get(0).getError(), absorptionCorrection.getSliceView().squeeze()));
		}
		
		return absCor;
	}

	/**
	 * Removes the effect of polarization on the data. 
	 * @param absCor
	 * 				The data uncorrected for the effects of polarization
	 * @return the data corrected for the effects of polarization.
	 */
	private Dataset applyPolarizationConstant(Dataset absCor) {
		final double sineFudge = 0.99;
		Dataset polCor = 
				Maths.multiply(
						0.5,
						Maths.add(
								1,
								Maths.subtract(
										Maths.square(Maths.cos(coords.getTwoTheta())),
										Maths.multiply(0.5*sineFudge, Maths.square(Maths.sin(coords.getTwoTheta())))
										)
								)
						);
								
		Dataset absCorP = Maths.multiply(absCor, polCor); 
		
		return absCorP; 
	}

	public Dataset calibrate(int nIterations) {
		if (doFluorescence)
				calibrateFluorescence(nIterations);
		Dataset absCor = iterateCalibrate(nIterations, true);
		System.err.println("Fluorescence scale = " + fluorescenceScale);
		System.err.println("Calibration constant = " + calibrationConstants.getLast());
		return absCor;
	}
	
	private Dataset iterateCalibrate(int nIterations, boolean propagateErrors) {
		// TODO: Solid angle correction do nothing
		List<Dataset> solAng = new ArrayList<Dataset>();
		for (Dataset targetComponent : backgroundSubtracted) {
			Dataset cosTwoTheta = Maths.cos(coords.getTwoTheta());
			Dataset solAngData = Maths.multiply(Maths.multiply(cosTwoTheta, cosTwoTheta), Maths.multiply(targetComponent, cosTwoTheta));
//			Dataset solAngData = Maths.multiply(1.0, targetComponent);
			if (propagateErrors && targetComponent.getError() != null)
				solAngData.setError(targetComponent.getError());
			solAng.add(solAngData);
		}
		
		// fluorescence correction
		List<Dataset> fluorescenceCorrected = new ArrayList<Dataset>();
//		for (Dataset targetComponent : backgroundSubtracted) {
		// Only correct fluorescence in the sample itself
		Dataset targetComponent = backgroundSubtracted.get(0);

		if (doFluorescence) {
//			Dataset fluorescenceCorrectedData = Maths.subtract(targetComponent, Maths.multiply(fluorescenceScale, sampleFluorescence.reshape(targetComponent.getSize())));
					Dataset fluorescenceCorrectedData = Maths.subtract(targetComponent, Maths.multiply(fluorescenceScale, sampleFluorescence.squeeze()));
			if (propagateErrors && targetComponent.getError() != null)
				if (sampleFluorescence.getError() != null)
					fluorescenceCorrectedData.setError(
							Maths.sqrt(
									Maths.add(
											Maths.square(targetComponent.getError()),
											Maths.square(sampleFluorescence.getError())
											)
									)
							);
				else
					fluorescenceCorrectedData.setError(targetComponent.getError());
			fluorescenceCorrected.add(fluorescenceCorrectedData);
		} else {
			fluorescenceCorrected.add(targetComponent);
		}
		for (int iContainer = 1; iContainer < backgroundSubtracted.size(); iContainer++)
			fluorescenceCorrected.add(backgroundSubtracted.get(iContainer));
		
		Dataset absCor = null;
		// Initialize the list of calibration constants with the predefined initial value.
		calibrationConstants = new LinkedList<Double>(Arrays.asList(new Double[] {calibrationConstant0}));
		for (int i = 0; i < nIterations; i++)
			absCor = this.iterate(fluorescenceCorrected, propagateErrors);
		return absCor;
	}
	
	private void calibrateFluorescence(int nIterations) {
		if (this.sampleFluorescence == null) return;
		
		final double minScale = 200, maxScale = 5000, nSteps = 20, stepScale = (maxScale-minScale)/nSteps;
		final double fractionOfRange = 1/5.0;
		double minimalScale = minScale;
		double minimalValue = Double.POSITIVE_INFINITY;
		
		// 2 D variables that do not need re-creating every time around the loop
		Dataset truncatedQ = DoubleDataset.createRange(8, 32, 1.6);
		
		// Get the mask from the background subtracted sample data
		ILazyDataset mask = AbstractOperationBase.getFirstMask(backgroundSubtracted.get(0));
		IDataset m = (mask != null) ? mask.getSlice().squeeze() : null;

		List<Double> fluorScales = new ArrayList<Double>(), fluorDiffs = new ArrayList<Double>();
		for (double scale = minScale; scale < maxScale; scale += stepScale)
			fluorScales.add(scale);
//		for (double scale = minScale; scale < maxScale; scale += stepScale) {
		for (double scale : fluorScales) {
			this.fluorescenceScale = scale;
			Dataset absCor = this.iterateCalibrate(nIterations, false);
			
			Dataset smoothed, truncatedSelfScattering = new DoubleDataset();
			final int smoothLength = (int) Math.floor(absCor.getSize()*fractionOfRange);
			
			// See how well the processed data matches the target. The output
			// of this step should be a smoothed version of absCor and the
			// self-scattering of the sample at the same abscissa values 
			if (absCor.getShape().length == 1) {
				// One dimensional version
				Dataset covolver = Maths.divide(DoubleDataset.ones(smoothLength), smoothLength);
				smoothed = Signal.convolveForOverlap(absCor, covolver, new int[] {0});
				truncatedSelfScattering = sampleSelfScattering.getSlice(new int[] {smoothLength/2}, new int[] {smoothed.getSize()+smoothLength/2}, new int[] {1});
				truncatedQ = coords.getQ().getSlice(new int[] {smoothLength/2}, new int[] {smoothed.getSize()+smoothLength/2}, new int[] {1});
			} else {
				// Set up the pixel integration information
				IPixelIntegrationCache lcache = getPICache(truncatedQ, AbstractOperationBase.getFirstDiffractionMetadata(backgroundSubtracted.get(0)), backgroundSubtracted.get(0).getShape());

				List<Dataset> out = PixelIntegration.integrate(absCor, m, lcache);
				smoothed = out.remove(1);

				out = PixelIntegration.integrate(sampleSelfScattering, m, lcache);
				truncatedSelfScattering = out.remove(1);
				
				//truncatedSelfScattering = InterpolatorUtils.remap1D(sampleSelfScattering, coords.getQ(), truncatedQ);
			}
			Dataset difference = Maths.subtract(smoothed, truncatedSelfScattering);
			double absSummedDifference = Math.abs((double) Maths.multiply(difference, truncatedQ).sum());

			fluorDiffs.add(absSummedDifference);
			
			if (absSummedDifference < minimalValue) {
				minimalScale = scale;
				minimalValue = absSummedDifference;
			}
			
		}
		this.fluorescenceScale = minimalScale;
		this.fluorescenceScale = 3640.0;
	}

	public void setDoFluorescence(boolean doIt) {
		doFluorescence = doIt;
	}
	
	private IPixelIntegrationCache getPICache(Dataset q, IDiffractionMetadata md, int[] shape) {
		PixelIntegrationBean pIBean = new PixelIntegrationBean();
		pIBean.setUsePixelSplitting(false);
		pIBean.setNumberOfBinsRadial(q.getSize());
		pIBean.setxAxis(XAxis.Q);
		pIBean.setRadialRange(new double[] {(double) q.min(), (double) q.max()});
		pIBean.setAzimuthalRange(null);
		pIBean.setTo1D(true);
		pIBean.setLog(false);
		pIBean.setShape(shape);
		
		return new PixelIntegrationCache(md, pIBean);
	}
}
