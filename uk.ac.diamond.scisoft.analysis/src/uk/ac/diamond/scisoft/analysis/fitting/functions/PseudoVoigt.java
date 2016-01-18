/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.fitting.functions;

import java.util.List;

import org.eclipse.dawnsci.analysis.api.fitting.functions.IParameter;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetUtils;
import org.eclipse.dawnsci.analysis.dataset.impl.DoubleDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.Maths;


/**
 * PseudoVoigt Class
 */
public class PseudoVoigt extends APeak {
	private static final String NAME = "PseudoVoigt";
	private static final String DESC = "A pseudo Voigt function defined by a linear mixture of Lorentzian and Gaussian functions"
			+ "\nwhere l_fhwm is Lorentzian full-width at half-maximum, g_fhwm is the Gaussian full-width,"
			+ "\narea is total area and mix is 0 for a pure Gaussian and 1 for a pure Lorentzian." + PEAK_DESC;
	private static final String[] LOCAL_PARAM_NAMES = new String[] { "posn", "l_fwhm", "area", "g_fwhm", "mix" };
	private static final double[] PARAMS = new double[] { 0, 0, 0, 0, 0 };

	public PseudoVoigt() {
		this(PARAMS[0], PARAMS[1], PARAMS[2], PARAMS[3], PARAMS[4]);
	}

	/**
	 * Note, now (20131204) this constructor has a different order
	 * 
	 * @param position
	 * @param lorentzianFWHM
	 * @param area
	 * @param gaussianFWHM
	 * @param mix
	 */
	public PseudoVoigt(double position, double lorentzianFWHM, double area, double gaussianFWHM, double mix) {
		super(PARAMS.length);
		setParameterValues(position, lorentzianFWHM, area, gaussianFWHM, mix);

		getParameter(FWHM).setLowerLimit(0.0);
		getParameter(FWHMG).setLowerLimit(0.0);
		getParameter(MIX).setLowerLimit(0.0);
		getParameter(MIX).setUpperLimit(1.0);
	}

	public PseudoVoigt(double[] params) {
		super(PARAMS.length);
		setParameterValues(params);

		getParameter(FWHM).setLowerLimit(0.0);
		getParameter(FWHMG).setLowerLimit(0.0);
		getParameter(MIX).setLowerLimit(0.0);
		getParameter(MIX).setUpperLimit(1.0);
	}

	/**
	 * Note, now (20131204) this constructor has a different order
	 * Initialise with set parameters
	 * @param params Position, LorentzianFWHM, Area, GaussianFWHM, Mix(0-1)
	 */
	public PseudoVoigt(IParameter... params) {
		super(PARAMS.length);
		setParameters(params);

		getParameter(FWHM).setLowerLimit(0.0);
		getParameter(FWHMG).setLowerLimit(0.0);
		getParameter(MIX).setLowerLimit(0.0);
		getParameter(MIX).setUpperLimit(1.0);
	}

	private static final int FWHMG = AREA + 1;
	private static final int MIX = FWHMG + 1;

	public PseudoVoigt(IdentifiedPeak peakParameters) {
		super(PARAMS.length);

		IParameter p;
		p = getParameter(POSN);
		double range = peakParameters.getMaxXVal()-peakParameters.getMinXVal();
		p.setValue(peakParameters.getPos());
		p.setLimits(peakParameters.getMinXVal(), peakParameters.getMaxXVal());

		// Lorentzian FWHM
		p = getParameter(FWHM);
		p.setLimits(0, range);
		double width = peakParameters.getFWHM();
		p.setValue(width);

		// Area
		// better fitting is generally found if sigma expands into the peak.
		p = getParameter(AREA);
		p.setLimits(0, peakParameters.getHeight()*range*4);
		p.setValue(peakParameters.getArea()/2);
		
		// Gaussian FWHM
		p = getParameter(FWHMG);
		p.setLimits(0, range);
		p.setValue(width);
		
		// Mix
		p = getParameter(MIX);
		p.setValue(0.5);
		p.setLimits(0.0, 1.0);
	}

	/**
	 * @param minPos
	 * @param maxPos
	 * @param maxFWHM
	 * @param maxArea
	 */
	public PseudoVoigt(double minPos, double maxPos, double maxFWHM, double maxArea) {
		super(PARAMS.length);

		internalSetPeakParameters(minPos, maxPos, maxFWHM, maxArea);

		IParameter p;
		// Gaussian FWHM
		p = getParameter(FWHMG);
		p.setLimits(0.0, maxFWHM);
		p.setValue(maxFWHM / 5.0);
		
		// Mix
		p = getParameter(MIX);
		p.setValue(0.5);
		p.setLimits(0.0, 1.0);
	}

	@Override
	protected void setNames() {
		setNames(NAME, DESC, LOCAL_PARAM_NAMES);
	}

	@Override
	public void setParameterValues(double... params) {
		super.setParameterValues(params);
		if (params.length > FWHM && params.length <= FWHMG) {
			double width = params[FWHM];
			double mix = 2 * getParameterValue(MIX);
			getParameter(FWHM).setValue(width * mix);
			getParameter(FWHMG).setValue(width * (2 - mix));
		}
	}

	private static final double CONST_A = Math.sqrt(Math.log(2.));
	private static final double CONST_B = Math.sqrt(Math.PI / Math.log(2.));

	private transient double pos, halfwg, halfwl, mixing;

	@Override
	protected void calcCachedParameters() {
		pos = getParameter(POSN).getValue();
		halfwl = getParameterValue(FWHM) / 2.0;
		halfwg = getParameterValue(FWHMG) / 2.0;
		mixing = getParameter(MIX).getValue();

		height = getParameterValue(AREA) / (halfwl * Math.PI * mixing +
				halfwg * CONST_B * (1 - mixing));

		setDirty(false);
	}

	@Override
	public double val(double... values) {
		if (isDirty())
			calcCachedParameters();

		double delta = values[0] - pos;
		// Lorentzian part
		double dist = delta / halfwl;
		double ex = mixing / (dist * dist + 1);
		// Gaussian part
		double arg = CONST_A * delta / halfwg;
		ex += (1 - mixing) * Math.exp(- arg * arg);

		return ex * height;
	}

	@Override
	public void fillWithValues(DoubleDataset data, CoordinatesIterator it) {
		if (isDirty())
			calcCachedParameters();

		it.reset();
		double[] coords = it.getCoordinates();
		int i = 0;
		double[] buffer = data.getData();
		while (it.hasNext()) {
			double delta = coords[0] - pos;
			// Lorentzian part
			double dist = delta / halfwl;
			double ex = mixing / (dist * dist + 1);
			// Gaussian part
			double arg = CONST_A * delta / halfwg;
			ex += (1 - mixing) * Math.exp(- arg * arg);

			buffer[i++] = height * ex;
		}
	}

	@Override
	public double getFWHM() {
		if (isDirty())
			calcCachedParameters();

		double w = 2*(halfwl + halfwg);
		Dataset x = DatasetUtils.linSpace(pos - w, pos + w, 200, Dataset.FLOAT64);
		DoubleDataset data = calculateValues(x);
		data = (DoubleDataset) Maths.abs(data);
		List<Double> crossings = DatasetUtils.crossings(x, data, height / 2);
		if (crossings.size() < 2)
			return Double.NaN;

		double width = crossings.get(1).doubleValue() - crossings.get(0).doubleValue();
		return width;
	}
}
