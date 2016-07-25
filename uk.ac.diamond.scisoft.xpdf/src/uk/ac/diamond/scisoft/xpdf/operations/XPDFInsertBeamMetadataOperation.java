/*-
 * Copyright 2015 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.xpdf.operations;

import org.eclipse.dawnsci.analysis.api.dataset.DatasetException;
import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
//import org.eclipse.dawnsci.analysis.api.io.ILoaderService;
import org.eclipse.dawnsci.analysis.api.monitor.IMonitor;
import org.eclipse.dawnsci.analysis.api.processing.Atomic;
import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetUtils;

import uk.ac.diamond.scisoft.analysis.processing.operations.utils.ProcessingUtils;
import uk.ac.diamond.scisoft.xpdf.XPDFBeamData;
import uk.ac.diamond.scisoft.xpdf.XPDFBeamTrace;
import uk.ac.diamond.scisoft.xpdf.XPDFMetadataImpl;
import uk.ac.diamond.scisoft.xpdf.metadata.XPDFMetadata;

/**
 * Insert the beam metadata into the XPDF metadata.
 * @author Timothy Spain timothy.spain@diamond.ac.uk
 * @since 2015-09-14
 *
 */
@Atomic
public class XPDFInsertBeamMetadataOperation extends XPDFInsertXMetadataOperation<XPDFInsertBeamMetadataModel, OperationData> {

	protected OperationData process(IDataset input, IMonitor monitor) throws OperationException {
		// Beam geometry metadata
		XPDFBeamData beamMetadata = new XPDFBeamData();
		
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
		if (model.getDataset().length() <= 0) throw new OperationException(this, "Undefined dataset");
		Dataset bgTrace;
		try {
			bgTrace = DatasetUtils.sliceAndConvertLazyDataset(ProcessingUtils.getLazyDataset(this, xyFilePath, model.getDataset()));
		} catch (DatasetException e) {
			throw new OperationException(this, e);
		}
		// the beam background shouldn't have extraneous dimensions
		bgTrace.squeezeEnds();
		checkDataAndAuxillaryDataMatch(input, bgTrace);
		
		try {
			if (model.getErrorDataset().length() <= 0) throw new OperationException(this, "Undefined error dataset");
			Dataset bgErrors;
			try {
				bgErrors = DatasetUtils.sliceAndConvertLazyDataset(ProcessingUtils.getLazyDataset(this, model.getErrorFilePath(), model.getErrorDataset()));
			} catch (DatasetException e) {
				throw new OperationException(this, e);
			}
			if (bgErrors != null)
				checkDataAndAuxillaryDataMatch(bgTrace, bgErrors);
				bgTrace.setError(bgErrors);
		} catch (OperationException e) {
			// catch and ignore; add no errors to the Dataset.
		}
		
		XPDFBeamTrace bgMetadata = new XPDFBeamTrace();
		bgMetadata.setCountingTime(model.getCountingTime());
		bgMetadata.setMonitorRelativeFlux(model.getMonitorRelativeFlux());
		bgMetadata.setTrace(bgTrace);
		// Assumes the axis is the same as the experimental data, if present.
		if (input.getFirstMetadata(XPDFMetadata.class) != null && 
				input.getFirstMetadata(XPDFMetadata.class).getSampleTrace() != null )
			bgMetadata.setAxisAngle(input.getFirstMetadata(XPDFMetadata.class).getSampleTrace().isAxisAngle());

		
		XPDFMetadataImpl theXPDFMetadata = getAndRemoveXPDFMetadata(input);
		theXPDFMetadata.setBeamData(beamMetadata);
		theXPDFMetadata.setEmptyTrace(bgMetadata);
		input.setMetadata(theXPDFMetadata);
		
		return new OperationData(input);
	}
	
	@Override
	public String getId() {
		return "uk.ac.diamond.scisoft.xpdf.operations.XPDFInsertBeamMetadataOperation";
	}

	@Override
	public OperationRank getInputRank() {
		return OperationRank.ANY;
	}

	@Override
	public OperationRank getOutputRank() {
		return OperationRank.SAME;
	}

}
