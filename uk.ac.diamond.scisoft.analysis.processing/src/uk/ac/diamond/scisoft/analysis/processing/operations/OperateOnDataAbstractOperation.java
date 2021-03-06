/*-
 * Copyright 2016 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.processing.operations;

import java.util.Arrays;

import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.dataset.operations.AbstractOperation;
import org.eclipse.dawnsci.analysis.dataset.slicer.SliceFromSeriesMetadata;
import org.eclipse.january.DatasetException;
import org.eclipse.january.IMonitor;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetUtils;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.january.dataset.ILazyDataset;
import org.eclipse.january.dataset.ShapeUtils;

import uk.ac.diamond.scisoft.analysis.processing.operations.internaldata.InternalDataModel;
import uk.ac.diamond.scisoft.analysis.processing.operations.utils.ProcessingUtils;

public abstract class OperateOnDataAbstractOperation<T extends InternalDataModel> extends AbstractOperation<T, OperationData> {
	
	@Override
	public final OperationRank getInputRank() {
		return OperationRank.ANY;
	}

	@Override
	public final OperationRank getOutputRank() {
		return OperationRank.SAME;
	}
	
	@Override
	protected OperationData process(IDataset input, IMonitor monitor) throws OperationException {
		
		// Shamelessly ripped from the old MultiplyExternalDataOperation class
		SliceFromSeriesMetadata ssm = getSliceSeriesMetadata(input);
		
		Dataset inputData = DatasetUtils.convertToDataset(input);
		
		String dataPath = getFilePath(input);
		
		ILazyDataset lz = ProcessingUtils.getLazyDataset(this, dataPath, model.getDatasetName());
		IDataset val = null;

		try {
			if (ShapeUtils.squeezeShape(lz.getShape(), false).length == 0) {
				// scalar lz
				val = lz.getSlice();
			} else {
				// vector lz
				val = ssm.getMatchingSlice(lz);
			}
		} catch (DatasetException e) {
			throw new OperationException(this, e);
		}

		// If a matching val was not found, throw
		if (val == null) throw new OperationException(this, "Dataset " + model.getDatasetName() + " " + Arrays.toString(lz.getShape()) + 
				" not a compatable shape with " + Arrays.toString(ssm.getParent().getShape()));
		val.squeeze();

		// A non-scalar val is an error at this point
		if (val.getRank() != 0) throw new OperationException(this, "External data shape invalid");

		Dataset output = doMathematics(inputData, DatasetUtils.convertToDataset(val));
		// copy metadata, except for the error metadata
		copyMetadata(input, output);
		
		return new OperationData(output);
		}
	
	protected abstract Dataset doMathematics(Dataset a, Dataset b); 
	
	protected abstract String getFilePath(IDataset input);
	
}
