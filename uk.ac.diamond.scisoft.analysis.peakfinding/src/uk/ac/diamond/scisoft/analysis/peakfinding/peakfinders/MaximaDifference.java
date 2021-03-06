/*-
 * Copyright 2015 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.peakfinding.peakfinders;

import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.DoubleDataset;
import org.eclipse.january.dataset.IDataset;


/**
 * Method 1 in: 
 * "Simple Algorithm Peak Detection Time Series, Palshikar (Tata Research)"
 * 
 * Compute the average of the maxima of the signed difference of a point and
 * it's windowSize left and right neighbours. Smaller values of windowSize are
 * appropriate for narrower peaks.
 */
public class MaximaDifference extends AbstractSignificanceFilter {
	
	private final static String NAME = "Maxima Difference";
	
	public MaximaDifference() {
		super();
		//Change the windowSize default here.
	}
	
	@Override
	protected void setName() {
		this.name = NAME;
	}
	
	@Override
	public double calculateSignificance(int position, int windowSize, IDataset yData) {
		double posVal = yData.getDouble(position);
		
		//Calculate the differences between the position & each point across
		//the two windows. N.B. left & right diffs are in opposite directions. 
		IDataset leftDiffs = DatasetFactory.zeros(DoubleDataset.class, windowSize);
		IDataset rightDiffs = DatasetFactory.zeros(DoubleDataset.class, windowSize);
		for(int i = 0; i < windowSize; i++) {
			leftDiffs.set(posVal-yData.getDouble(position-i-1), i);
			rightDiffs.set(posVal-yData.getDouble(position+i+1), i);
		}
		
		//Calculate the average of the maximum of the differences (i.e. significance)
		double sig = (leftDiffs.max().doubleValue() + rightDiffs.max().doubleValue())/2; 
		
		return sig;
	}
}
