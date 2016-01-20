/*-
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.fitting.functions;

import org.eclipse.dawnsci.analysis.api.fitting.functions.IFunction;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetUtils;
import org.eclipse.dawnsci.analysis.dataset.impl.DoubleDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.Maths;


/**
 * Class which contains all the information about a particular function which is made up out of several other
 * functions
 */
public class CompositeFunction extends Add {

	/**
	 * This constructor is simply to start an empty composite function.
	 */
	public CompositeFunction() {
		super();
	}

	/**
	 * Create a set of datasets each containing the composite function and its constituent
	 * parts evaluated over the values
	 * 
	 * @param values
	 *            datasets containing all the values to evaluate the function at
	 * @return an array of datasets
	 */
	public DoubleDataset[] display(DoubleDataset... values) {
		if (values == null || values.length == 0) {
			
		}
		int noOfFunctions = getNoOfFunctions();
		
		DoubleDataset[] outputs = new DoubleDataset[noOfFunctions + 1];

		outputs[0] = calculateValues(values);
		outputs[0].setName("Composite function");

		// now add the data for each bit in turn
		int j = 1;
		for (IFunction f : functions) {
			outputs[j] = (DoubleDataset) DatasetUtils.cast(f.calculateValues(values), Dataset.FLOAT64);
			outputs[j++].setName(f.getName());
		}

		return outputs;
	}

	/**
	 * Create a set of datasets each containing the composite function and its constituent
	 * parts evaluated over the values
	 * 
	 * @param XValues
	 *            A dataset containing all the X values to calculate the data at
	 * @param DataValues
	 *            The data that is being fitted too, for visual help.
	 * @return an array of datasets 
	 */
	public DoubleDataset[] display(DoubleDataset XValues, DoubleDataset DataValues) {
		int noOfFunctions = getNoOfFunctions();

		DoubleDataset[] outputs = new DoubleDataset[noOfFunctions + 4];

		outputs[0] = new DoubleDataset(DataValues);

		// now add the data
		outputs[1] = calculateValues(XValues);
		outputs[1].setName("Composite function");

		// now add the errors to the graph, this should provide a good view to
		// how good the fit is quite nicely.
		outputs[2] = (DoubleDataset) Maths.subtract(outputs[1], DataValues);
		outputs[2].setName("Error Value");
		double offset = DataValues.min().doubleValue() - ((DataValues.max().doubleValue() - DataValues.min().doubleValue()) / 5.0);
		outputs[2].isubtract(offset);

		outputs[3] = new DoubleDataset(XValues.getShapeRef());
		outputs[3].setName("Error Offset");
		outputs[3].fill(offset);

		// now add the data for each bit in turn
		int j = 4;
		for (IFunction f : functions) {
			outputs[j] = (DoubleDataset) DatasetUtils.cast(f.calculateValues(XValues), Dataset.FLOAT64);
			outputs[j++].setName(f.getName());
		}

		return outputs;

	}

	/**
	 * Attempts to cast and return the function at i as a Peak
	 * @param i
	 * @return IPeak
	 * @throws ClassCastException
	 */
	public IPeak getPeak(int i) {
		return (IPeak) getFunction(i);
	}

	@Override
	public CompositeFunction copy() throws Exception {
		return (CompositeFunction)super.copy();
	}
}
