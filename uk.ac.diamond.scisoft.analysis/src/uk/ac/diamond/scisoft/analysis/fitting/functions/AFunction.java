/*-
 * Copyright 2011 Diamond Light Source Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.ac.diamond.scisoft.analysis.fitting.functions;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.Comparisons;
import uk.ac.diamond.scisoft.analysis.dataset.DatasetUtils;
import uk.ac.diamond.scisoft.analysis.dataset.DoubleDataset;
import uk.ac.diamond.scisoft.analysis.dataset.IDataset;
import uk.ac.diamond.scisoft.analysis.monitor.IMonitor;

/**
 * Class which is the fundamentals for any function which is to be used in a composite function. If the isPeak value is
 * specified as true, then the first parameter must be that peak's position
 */
public abstract class AFunction implements IFunction, Serializable {

	/**
	 * Setup the logging facilities
	 */
	private static transient final Logger logger = LoggerFactory.getLogger(AFunction.class);

	/**
	 * The array of parameters which specify all the variables in the minimisation problem
	 */
	protected IParameter[] parameters;

	/**
	 * The name of the function, a description more than anything else.
	 */
	protected String name = "default";

	/**
	 * The description of the function
	 */
	protected String description = "default";

	protected boolean dirty = true;

	protected IMonitor monitor = null;

	/**
	 * Constructor which is given a set of parameters to begin with.
	 * 
	 * @param params
	 *            An array of parameters
	 */
	public AFunction(IParameter... params) {
		if (params != null)
			fillParameters(params);
	}

	protected void fillParameters(IParameter... params) {
		parameters = new IParameter[params.length];
		for (int i = 0; i < params.length; i++) {
			IParameter p = params[i];
			parameters[i] = new Parameter(p);
		}
	}

	/**
	 * @param function
	 * @param parameter
	 * @return index of parameter or -1 if parameter is not in function
	 */
	public static int indexOfParameter(IFunction function, IParameter parameter) {
		if (function == null || parameter == null)
			return -1;

		if (function instanceof AFunction)
			return ((AFunction) function).indexOfParameter(parameter);

		for (int j = 0, jmax = function.getNoOfParameters(); j < jmax; j++) {
			if (parameter == function.getParameter(j)) {
				return j;
			}
		}
		return -1;
	}

	/**
	 * @param parameter
	 * @return index of parameter or -1 if parameter is not in function
	 */
	protected int indexOfParameter(IParameter parameter) {
		for (int i = 0; i < parameters.length; i++) {
			if (parameter == parameters[i]) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Constructor which takes a list of parameter values as its starting configuration
	 * 
	 * @param params
	 *            An array of starting parameter values as doubles.
	 */
	public AFunction(double... params) {
		if (params != null)
			fillParameters(params);
	}

	protected void fillParameters(double... params) {
		parameters = new Parameter[params.length];
		for (int i = 0; i < params.length; i++) {
			parameters[i] = new Parameter(params[i]);
		}
	}

	/**
	 * Constructor which simply generates the parameters but uninitialised
	 * 
	 * @param numberOfParameters
	 */
	public AFunction(int numberOfParameters) {
		parameters = new Parameter[numberOfParameters];
		for (int i = 0; i < numberOfParameters; i++) {
			parameters[i] = new Parameter();
		}
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void setName(String newName) {
		name = newName;
	}

	@Override
	public String getDescription() {
		return description;
	}

	@Override
	public void setDescription(String newDescription) {
		description = newDescription;
	}

	@Override
	public IParameter getParameter(int index) {
		return parameters[index];
	}

	@Override
	public IParameter[] getParameters() {
		IParameter[] params = new IParameter[parameters.length];
		for (int i = 0; i < parameters.length; i++) {
			params[i] = parameters[i];
		}
		return params;
	}

	@Override
	public int getNoOfParameters() {
		return parameters.length;
	}

	@Override
	public double getParameterValue(int index) {
		return parameters[index].getValue();
	}

	@Override
	final public double[] getParameterValues() {
		int n = getNoOfParameters();
		double[] result = new double[n];
		for (int j = 0; j < n; j++) {
			result[j] = getParameterValue(j);
		}
		return result;
	}

	@Override
	public void setParameter(int index, IParameter parameter) {
		if (indexOfParameter(parameter) == index)
			return;

		parameters[index] = parameter;
		dirty = true;
	}

	@Override
	public void setParameterValues(double... params) {
		int nparams = Math.min(params.length, parameters.length);

		for (int j = 0; j < nparams; j++) {
			parameters[j].setValue(params[j]);
		}
		dirty = true;
	}

	@Override
	public String toString() {
		StringBuffer out = new StringBuffer();
		int n = getNoOfParameters();
		out.append(String.format("'%s' has %d parameters:\n", name, n));
		for (int i = 0; i < n; i++) {
			IParameter p = getParameter(i);
			out.append(String.format("%d) %s = %g in range [%g, %g]\n", i, p.getName(), p.getValue(),
					p.getLowerLimit(), p.getUpperLimit()));
		}
		return out.toString();
	}

	@Override
	@Deprecated
	public double partialDeriv(int index, double... values) {
		return partialDeriv(getParameter(index), values);
	}

	/**
	 * This implementation is a numerical approximation. Overriding methods should check
	 * for duplicated parameters before doing any calculation and either cope with this
	 * or use this numerical approximation
	 */
	@Override
	public double partialDeriv(IParameter parameter, double... values) {
		if (indexOfParameter(parameter) < 0)
			return 0;

		return calcNumericalDerivative(A_TOLERANCE, R_TOLERANCE, parameter, values);
	}

	/**
	 * @param param
	 * @return true if there is more than one occurrence of given parameter in function
	 */
	protected boolean isDuplicated(IParameter param) {
		int c = 0;
		int n = getNoOfParameters();
		for (int i = 0; i < n; i++) {
			if (getParameter(i) == param) {
				c++;
				return c > 1;
			}
		}

		return false;
	}

	private final static double DELTA = 1/256.; // initial value
	private final static double DELTA_FACTOR = 0.25;

	protected final static double A_TOLERANCE = 1e-9; // absolute tolerance
	protected final static double R_TOLERANCE = 1e-9; // relative tolerance

	/**
	 * @param abs
	 * @param rel
	 * @param param
	 * @param values
	 * @return partial derivative up to tolerances
	 */
	protected double calcNumericalDerivative(double abs, double rel, IParameter param, double... values) {
		double delta = DELTA;
		double previous = numericalDerivative(delta, param, values);
		double aprevious = Math.abs(previous);
		double current = 0;
		double acurrent = 0;

		while (delta > Double.MIN_NORMAL) {
			delta *= DELTA_FACTOR;
			current = numericalDerivative(delta, param, values);
			acurrent = Math.abs(current);
			if (Math.abs(current - previous) <= abs + rel*Math.max(acurrent, aprevious))
				break;
			previous = current;
			aprevious = acurrent;
		}

		return current;
	}

	/**
	 * Calculate partial derivative. This is a numerical approximation.
	 * @param param
	 * @param values
	 * @return partial derivative
	 */
	private double numericalDerivative(double delta, IParameter param, double... values) {
		double v = param.getValue();
		double dv = delta * (v != 0 ? v : 1);

		param.setValue(v - dv);
		dirty = true;
		double minval = val(values);
		param.setValue(v + dv);
		dirty = true;
		double maxval = val(values);
		param.setValue(v);
		dirty = true;
		return (maxval - minval) / (2. * dv);
	}

	@Override
	public DoubleDataset makeDataset(IDataset... values) {
		return calculateValues(values);
	}

	/**
	 * @param coords
	 * @return a coordinate iterator
	 */
	final public CoordinatesIterator getIterator(IDataset... coords) {
		if (coords == null || coords.length == 0) {
			logger.error("No coordinates given to evaluate function");
			throw new IllegalArgumentException("No coordinates given to evaluate function");
		}

		CoordinatesIterator it;
		int[] shape = coords[0].getShape();
		if (coords.length == 1) {
			it = coords[0].getElementsPerItem() == 1 ? new DatasetsIterator(coords) : new CoordinateDatasetIterator(coords[0]);
		} else {
			boolean same = true;
			for (int i = 1; i < shape.length; i++) {
				if (!Arrays.equals(shape, coords[i].getShape())) {
					same = false;
					break;
				}
			}
			if (same && shape.length == 1) // override for 1D datasets
				same = false;

			it = same ? new DatasetsIterator(coords) : new HypergridIterator(coords);
		}
		return it;
	}

	@Override
	final public DoubleDataset calculateValues(IDataset... coords) {
		CoordinatesIterator it = getIterator(coords);
		DoubleDataset result = new DoubleDataset(it.getShape());
		fillWithValues(result, it);
		result.setName(name);
		return result;
	}

	@Override
	public DoubleDataset calculatePartialDerivativeValues(IParameter parameter, IDataset... coords) {
		CoordinatesIterator it = getIterator(coords);
		DoubleDataset result = new DoubleDataset(it.getShape());
		if (indexOfParameter(parameter) >= 0)
			internalFillWithPartialDerivativeValues(parameter, result, it);
		result.setName(name);
		return result;
	}

	private void internalFillWithPartialDerivativeValues(IParameter parameter, DoubleDataset data, CoordinatesIterator it) {
		if (isDuplicated(parameter)) {
			calcNumericalDerivativeDataset(A_TOLERANCE, R_TOLERANCE, parameter, data, it);
		} else {
			fillWithPartialDerivativeValues(parameter, data, it);
		}
	}

	/**
	 * Fill dataset with values
	 * @param data
	 * @param it
	 */
	abstract public void fillWithValues(DoubleDataset data, CoordinatesIterator it);

	/**
	 * Fill dataset with partial derivatives
	 * <p>
	 * This implementation is a numerical approximation.
	 * <p>
	 * Note that is called only if there are no duplicated parameters otherwise,
	 * a numerical approximation is used. To change this behaviour, override
	 * {@link #calculatePartialDerivativeValues(IParameter, IDataset...)}
	 * @param parameter
	 * @param data
	 * @param it
	 */
	public void fillWithPartialDerivativeValues(IParameter parameter, DoubleDataset data, CoordinatesIterator it) {
		calcNumericalDerivativeDataset(A_TOLERANCE, R_TOLERANCE, parameter, data, it);
	}

	/**
	 * Calculate partial derivatives up to tolerances
	 * @param abs
	 * @param rel
	 * @param param
	 * @param data
	 * @param it
	 */
	protected void calcNumericalDerivativeDataset(double abs, double rel, IParameter param, DoubleDataset data, CoordinatesIterator it) {
		DoubleDataset previous = new DoubleDataset(it.getShape());
		double delta = DELTA;
		fillWithNumericalDerivativeDataset(delta, param, previous, it);
		DoubleDataset current = new DoubleDataset(it.getShape());

		while (delta > Double.MIN_NORMAL) {
			delta *= DELTA_FACTOR;
			fillWithNumericalDerivativeDataset(delta, param, current, it);
			it.reset();
			if (Comparisons.allCloseTo(previous, current, rel, abs))
				break;

			DoubleDataset temp = previous;
			previous = current;
			current = temp;
		}
		data.fill(current);
	}

	/**
	 * Calculate partial derivative. This is a numerical approximation.
	 * @param delta
	 * @param param
	 * @param data
	 * @param it
	 */
	private void fillWithNumericalDerivativeDataset(double delta, IParameter param, DoubleDataset data, CoordinatesIterator it) {
		double v = param.getValue();
		double dv = delta * (v != 0 ? v : 1);

		param.setValue(v + dv);
		dirty = true;
		fillWithValues(data, it);
		it.reset();
		param.setValue(v - dv);
		dirty = true;
		DoubleDataset temp = new DoubleDataset(it.getShape());
		fillWithValues(temp, it);
		data.isubtract(temp);
		data.imultiply(0.5/dv);
		param.setValue(v);
		dirty = true;
	}

	/**
	 * @return true if any parameters have changed
	 */
	public boolean isDirty() {
		return dirty;
	}

	@Override
	public void setDirty(boolean isDirty) {
		dirty = isDirty;
	}

	@Override
	public double weightedResidual(boolean allValues, IDataset weight, IDataset data, IDataset... values) {
		double residual = 0;
		if (allValues) {
			DoubleDataset ddata = (DoubleDataset) DatasetUtils.convertToAbstractDataset(data).cast(AbstractDataset.FLOAT64);
			if (weight == null) {
				residual = ddata.residual(calculateValues(values));
			} else {
				residual = ddata.residual(calculateValues(values), DatasetUtils.convertToAbstractDataset(weight), false);
			}
		} else {
			// stochastic sampling of coords;
//			int NUMBER_OF_SAMPLES = 100;
			//TODO
			logger.error("Stochastic sampling has not been implemented yet");
			throw new UnsupportedOperationException("Stochastic sampling has not been implemented yet");
		}

		if (monitor != null) {
			monitor.worked(1);
			if (monitor.isCancelled()) {
				throw new IllegalMonitorStateException("Monitor cancelled");
			}
		}

		return residual;
	}

	@Override
	public double residual(boolean allValues, IDataset data, IDataset... values) {
		return weightedResidual(allValues, null, data, values);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (dirty ? 1231 : 1237);
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + Arrays.hashCode(parameters);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AFunction other = (AFunction) obj;
		if (dirty != other.dirty)
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (!Arrays.equals(parameters, other.parameters))
			return false;
		return true;
	}

	@Override
	public AFunction copy() throws Exception {
		Constructor<? extends AFunction> c = getClass().getConstructor();

		IParameter[] localParameters = getParameters();
		
		AFunction function =  c.newInstance();
		function.fillParameters(localParameters);
		return function;
	}

	@Override
	public IMonitor getMonitor() {
		return monitor;
	}

	@Override
	public void setMonitor(IMonitor monitor) {
		this.monitor = monitor;
	}
}
