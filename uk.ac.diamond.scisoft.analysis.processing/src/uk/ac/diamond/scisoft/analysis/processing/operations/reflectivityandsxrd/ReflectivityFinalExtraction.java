/*-
 * Copyright 2016 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.processing.operations.reflectivityandsxrd;

import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.dataset.operations.AbstractOperation;
import org.eclipse.january.IMonitor;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.DatasetUtils;
import org.eclipse.january.dataset.IDataset;

public class ReflectivityFinalExtraction extends AbstractOperation<Polynomial2DReflectivityModel, OperationData> {
	
	/*EXtracts summed intensities and fhkls
	 * 
	 */
	
	@Override
	public String getId() {
		return "uk.ac.diamond.scisoft.analysis.processing.operations.reflectivityandsxrd.ReflectivityFinalExtraction";
	}
	
	@Override
	public OperationRank getInputRank() {
		return OperationRank.TWO ;
		}
	
	@Override
	public OperationRank getOutputRank() {
		return OperationRank.TWO ;	
		}
	
	@Override
	protected OperationData process(IDataset input, IMonitor monitor) {
		
		
		
		Dataset inputSum = DatasetFactory.createFromObject((DatasetUtils.cast(input, Dataset.FLOAT64)).sum());
		
	
		return new OperationData(input, inputSum);
		
	}
}

//TEST1