/*-
 * Copyright 2016 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.processing.operations.externaldata;

import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;

import uk.ac.diamond.scisoft.analysis.processing.operations.ErrorPropagationUtils;


// Does not work in the Operations menu for a reason I cannot fathom. Use PlusExternalDataOperation instead. --TCS
public class AddExternalDataOperation extends
		OperateOnExternalDataAbstractOperation<ExternalDataModel, OperationData> {

	@Override
	public String getId() {
		return "uk.ac.diamond.scisoft.analysis.processing.operations.externaldata.AddExternalDataOperation";
	}

	@Override
	protected Dataset doMathematics(Dataset a, double b) {
		return a;//ErrorPropagationUtils.addWithUncertainty(a, b);
	}

}