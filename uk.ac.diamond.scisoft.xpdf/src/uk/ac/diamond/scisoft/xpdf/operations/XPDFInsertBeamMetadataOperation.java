/*-
 * Copyright 2015 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.xpdf.operations;

import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
//import org.eclipse.dawnsci.analysis.api.io.ILoaderService;
import org.eclipse.dawnsci.analysis.api.monitor.IMonitor;
import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.dataset.impl.DoubleDataset;
import org.eclipse.dawnsci.analysis.dataset.operations.AbstractOperation;

import uk.ac.diamond.scisoft.analysis.processing.operations.utils.ProcessingUtils;
import uk.ac.diamond.scisoft.xpdf.metadata.XPDFBeamMetadataImpl;
import uk.ac.diamond.scisoft.xpdf.metadata.XPDFTraceMetadataImpl;


public class XPDFInsertBeamMetadataOperation extends AbstractOperation<XPDFInsertBeamMetadataModel, OperationData> {

	protected OperationData process(IDataset input, IMonitor monitor) throws OperationException {
		// Beam geometry metadata
		XPDFBeamMetadataImpl beamMetadata = new XPDFBeamMetadataImpl();
		
		// Get the properties of the beam
		beamMetadata.setBeamEnergy(model.getBeamEnergy());
		beamMetadata.setBeamHeight(model.getBeamHeight());
		beamMetadata.setBeamWidth(model.getBeamWidth());
		
		// Background trace metadata. The beam causes the background, after all
		String xyFilePath = "";
		try {
			xyFilePath = model.getFilePath();
		} catch (Exception e) {
			throw new OperationException(this, "Could not find " + xyFilePath);
		}
		// Load the background from the designated xy file
		IDataset bgTrace = ProcessingUtils.getLazyDataset(this, xyFilePath, "Column_2").getSlice();
		
		XPDFTraceMetadataImpl bgMetadata = new XPDFTraceMetadataImpl();
		bgMetadata.setCountingTime(model.getCountingTime());
		bgMetadata.setMonitorRelativeFlux(model.getMonitorRelativeFlux());
		bgMetadata.setTrace(bgTrace);
		
		beamMetadata.setTrace(bgMetadata);

		input.setMetadata(beamMetadata);
		
		return new OperationData(input);
	}
	
	@Override
	public String getId() {
		return "uk.ac.diamond.scisoft.xpdf.operations.XPDFInsertBeamMetadataOperation";
	}

	@Override
	public OperationRank getInputRank() {
		return OperationRank.ONE;
	}

	@Override
	public OperationRank getOutputRank() {
		return OperationRank.ONE;
	}

}
