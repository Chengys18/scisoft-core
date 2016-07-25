/*-
 * Copyright 2015 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.peakfinding.peakfinders;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.DoubleDataset;
import org.eclipse.january.dataset.IDataset;

/**
 * Contains routines for filtering out points in the significance function
 * which might be peaks.
 * 
 * Originally specified in:
 * "Simple Algorithm Peak Detection Time Series, Palshikar (Tata Research)"
 */
public abstract class AbstractSignificanceFilter extends AbstractPeakFinder {
	
	/**
	 * Set's values of parameters needed for filtering significance function.
	 * @throws Exception 
	 */
	public AbstractSignificanceFilter() {
		super();
		try {
			initialiseParameter("windowSize", true, 50);
			initialiseParameter("nrStdDevs", true, 3);
		} catch (Exception e) {
			System.out.println(e);
			logger.error("Problem initialising "+this.getName()+" peak finder: e");
		}
	}
	
	/**
	 * Calculates the significance function specified in "Simple Algorithm Peak
	 * Detection Time Series, Palshikar (Tata Research)". The significance of 
	 * each point of a 1D dataset is calculated.
	 * @param position Index of current point 
	 * @param windowSize Number of points left and right to consider
	 * @param yData The dataset
	 * @return The significance of the current point (i.e. it's peakiness)
	 */
	protected abstract double calculateSignificance(int position, int windowSize, IDataset yData);
	
	@Override
	public Map<Integer, Double> findPeaks(IDataset xData, IDataset yData, Integer nPeaks) {
		//Put our peak finding parameters into more accessible variables
		Integer nrStdDevs;
		Integer windowSize;
		try {
			nrStdDevs = (Integer)getParameterValue("nrStdDevs");
			windowSize = (Integer)getParameterValue("windowSize");
		} catch(Exception e) {
			logger.error("Could not find specified peak finding parameters");
			return null;
		}
		
		//Calculate the significance function for this data & its mean & SD
		int nrPoints = yData.getSize();
		Dataset significance = DatasetFactory.zeros(DoubleDataset.class, yData.getShape());
		for (int i = windowSize; i <= (nrPoints-windowSize-1); i++) {
			double posSig = calculateSignificance(i, windowSize, yData);
			significance.set(posSig, i);
		}
		
		//Filter out significance values less than n*SD
		Double sigMean = (Double)significance.mean();
		Double sigStdDev = (Double)significance.stdDeviation();
		Map<Integer, Double> peakPosnsSigs = new TreeMap<Integer, Double>();
		
		for (int i = 0; i < significance.getSize(); i++) {
			Double currSig = significance.getDouble(i);
			if ((currSig > 0) && ((currSig-sigMean) > nrStdDevs * sigStdDev)) {
				peakPosnsSigs.put(i, currSig);
			}
		}
		
		//Remove significant points less than one windowSize apart
		Set<Integer> peakIndices = new TreeSet<Integer>(peakPosnsSigs.keySet());
		Iterator<Integer> peakIndIter = peakIndices.iterator(); 
		int currInd = peakIndIter.next().intValue();
		int nextInd;
		while (peakIndIter.hasNext()) {
			nextInd = peakIndIter.next().intValue();
			if (Math.abs(currInd - nextInd) <= windowSize) {
				peakPosnsSigs.remove(currInd);
			}
			currInd = nextInd;
		}
		return peakPosnsSigs;
	}
}
