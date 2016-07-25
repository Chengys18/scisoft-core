/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package uk.ac.diamond.scisoft.analysis.processing.operations.mask;

import java.util.Arrays;

import org.eclipse.dawnsci.analysis.api.processing.Atomic;
import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.dataset.operations.AbstractOperation;
import org.eclipse.january.IMonitor;
import org.eclipse.january.dataset.BooleanDataset;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.DatasetUtils;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.january.dataset.IndexIterator;
import org.eclipse.january.metadata.MaskMetadata;
import org.eclipse.january.metadata.internal.MaskMetadataImpl;

@Atomic
public class ThresholdMask extends AbstractOperation<ThresholdMaskModel, OperationData> {

	@Override
    public String getName() {
		return "Threshold Mask";
	}

	@Override
	public String getId() {
		return "uk.ac.diamond.scisoft.analysis.processing.operations.thresholdMask";
	}

	@Override
	protected OperationData process(IDataset input, IMonitor monitor) throws OperationException {

		Dataset mask = DatasetUtils.convertToDataset(getFirstMask(input));
		Dataset in = DatasetUtils.convertToDataset(input);
		
		if (mask == null) {
			mask = DatasetFactory.ones(BooleanDataset.class, input.getShape());
		} else {
			mask = mask.getSlice();
		}
		
		if (!Arrays.equals(input.getShape(), mask.getShape())) {
			throw new OperationException(this, "Mask is incorrect shape!");
		}


		Double upper  = (Double)model.getUpper();
		if (upper==null) upper = Double.MAX_VALUE;

		Double lower  = (Double)model.getLower();
		if (lower==null) lower = -Double.MAX_VALUE;

		IndexIterator it = mask.getIterator();

		while (it.hasNext()) {
			double val = in.getElementDoubleAbs(it.index);
			if (val>upper || val<lower) {
				mask.setObjectAbs(it.index,false);
			}
		}

		MaskMetadata mm = new MaskMetadataImpl(mask);
		input.setMetadata(mm);

		return new OperationData(input);


	}

	@Override
	public OperationRank getInputRank() {
		return OperationRank.TWO;
	}

	@Override
	public OperationRank getOutputRank() {
		return OperationRank.TWO;
	}

}
