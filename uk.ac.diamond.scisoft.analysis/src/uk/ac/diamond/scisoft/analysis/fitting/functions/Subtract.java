/*-
 * Copyright 2013 Diamond Light Source Ltd.
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

import uk.ac.diamond.scisoft.analysis.dataset.DoubleDataset;

/**
 * Subtract two functions
 */
public class Subtract extends ABinaryOperator implements IOperator {
	private static final String NAME = "Subtract";
	private static final String DESC = "Subtract one function from another";

	public Subtract() {
		super();
		name = NAME;
		description = DESC;
	}


	@Override
	public double val(double... values) {
		
		double y = fa == null ? 0 : fa.val(values);
		y -= fb == null ? 0 : fb.val(values);

		return y;
	}

	@Override
	public void fillWithValues(DoubleDataset data, CoordinatesIterator it) {
		if (fa != null) {
			if (fa instanceof AFunction) {
				((AFunction) fa).fillWithValues(data, it);
				it.reset();
			} else {
				data.fill(fa.calculateValues(it.getValues()));
			}
		}

		if (fb != null) {
			if (fb instanceof AFunction) {
				DoubleDataset temp = new DoubleDataset(it.getShape());
				((AFunction) fb).fillWithValues(temp, it);
				it.reset();
				data.isubtract(temp);
			} else {
				data.isubtract(fb.calculateValues(it.getValues()));
			}
		}
	}

	@Override
	public double partialDeriv(int index, double... values) throws IndexOutOfBoundsException {
		IParameter p = getParameter(index);
		double d = fa == null ? 0 : fa.partialDeriv(p, values);

		if (fb != null) {
			d -= fb.partialDeriv(p, values);
		}

		return d;
	}

	@Override
	public void fillWithPartialDerivativeValues(IParameter param, DoubleDataset data, CoordinatesIterator it) {
		if (fa != null && indexOfParameter(fa, param) >= 0) {
			if (fa instanceof AFunction) {
				((AFunction) fa).fillWithPartialDerivativeValues(param, data, it);
				it.reset();
			} else {
				data.iadd(fa.calculatePartialDerivativeValues(param, it.getValues()));
			}
		}

		if (fb != null && indexOfParameter(fb, param) >= 0) {
			if (fb instanceof AFunction) {
				DoubleDataset temp = new DoubleDataset(it.getShape());
				((AFunction) fb).fillWithPartialDerivativeValues(param, temp, it);
				data.isubtract(temp);
			} else {
				data.isubtract(fb.calculatePartialDerivativeValues(param, it.getValues()));
			}
		}
	}
}
