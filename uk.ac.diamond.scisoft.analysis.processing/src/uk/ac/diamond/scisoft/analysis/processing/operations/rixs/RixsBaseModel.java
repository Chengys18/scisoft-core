/*-
 * Copyright 2017 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.processing.operations.rixs;

import org.eclipse.dawnsci.analysis.api.processing.model.AbstractOperationModel;
import org.eclipse.dawnsci.analysis.api.processing.model.OperationModelField;
import org.eclipse.dawnsci.analysis.api.roi.IRectangularROI;
import org.eclipse.dawnsci.analysis.dataset.roi.RectangularROI;

/**
 * Base model for processing RIXS images
 */
public class RixsBaseModel extends AbstractOperationModel {
	@OperationModelField(label = "Rectangle A", description = "Region to search for elastic peak")
	private IRectangularROI roiA = new RectangularROI(0, 1, 2048, 1600, 0);

	@OperationModelField(label = "Rectangle B", description = "Region to search for elastic peak")
	private IRectangularROI roiB = null;

	public enum ENERGY_DIRECTION {
		SLOW, // slowest pixel direction
		FAST, // fastest pixel direction
	}

	// orientation (given by energy direction)
	@OperationModelField(label = "Energy direction", description = "Principal direction in which the energy changes", hint = "Slow is vertical; fast is horizontal")
	private ENERGY_DIRECTION energyDirection = ENERGY_DIRECTION.SLOW; // in 2D image

	@OperationModelField(label = "Cutoff enable", description = "Use cutoff to remove pixels with high counts")
	private boolean useCutoff = false;

	@OperationModelField(label = "Cutoff for pixels", description = "Cutoff as multiple of single photon count", hint = "Check if peaks are clipped when cutoff is too low", enableif = "useCutoff == true", min = 1.0)
	private double cutoff = 5.0;

	@OperationModelField(label = "Clip spectra", description = "Clip spectra to avoid fall-off from slope correction", hint = "Set true when image background is not zero")
	private boolean clipSpectra = true;

	/**
	 * @return get first region of interest (can be null to signify the entire image)
	 */
	public IRectangularROI getRoiA() {
		return roiA;
	}

	public void setRoiA(IRectangularROI roi) {
		firePropertyChange("setRoiA", this.roiA, this.roiA = roi);
	}

	void internalSetRoiA(IRectangularROI roi) {
		this.roiA = roi;
	}

	/**
	 * @return get second region of interest (can be null to signify only use first ROI)
	 */
	public IRectangularROI getRoiB() {
		return roiB;
	}

	public void setRoiB(IRectangularROI roi) {
		firePropertyChange("setRoiB", this.roiB, this.roiB = roi);
	}

	void internalSetRoiB(IRectangularROI roi) {
		this.roiB = roi;
	}

	/**
	 * @return dimension of image in which energy changes
	 */
	public int getEnergyIndex() {
		return energyDirection.ordinal();
	}

	public void setEnergyIndex(int index) {
		// do nothing to make marshaller happy
	}

	public ENERGY_DIRECTION getEnergyDirection() {
		return energyDirection;
	}

	public void setEnergyDirection(ENERGY_DIRECTION energyDirection) {
		firePropertyChange("setEnergyIndex", this.energyDirection, this.energyDirection = energyDirection);
	}

	/**
	 * @return true if cutoff will be used
	 */
	public boolean isUseCutoff() {
		return useCutoff;
	}

	public void setUseCutoff(boolean useCutoff) {
		firePropertyChange("setUseCutoff", this.useCutoff, this.useCutoff = useCutoff);
	}

	/**
	 * @return upper threshold multiple of single photon (in detector count units) used to ignore pixels
	 */
	public double getCutoff() {
		return cutoff;
	}

	public void setCutoff(double cutoff) {
		firePropertyChange("setCutoff", this.cutoff, this.cutoff = cutoff);
	}

	/**
	 * @return true if spectra should be clipped to avoid fall-off from slope correction
	 */
	public boolean isClipSpectra() {
		return clipSpectra;
	}

	public void setClipSpectra(boolean clipSpectra) {
		firePropertyChange("setClipSpectra", this.clipSpectra, this.clipSpectra = clipSpectra);
	}
}
