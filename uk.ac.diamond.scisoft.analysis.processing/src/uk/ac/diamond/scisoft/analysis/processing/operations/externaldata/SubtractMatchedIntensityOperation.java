/*-
 * Copyright 2018 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */


package uk.ac.diamond.scisoft.analysis.processing.operations.externaldata;


// Imports from java
import java.util.List;

//Imports from org.eclipse.dawnsci
import org.eclipse.dawnsci.analysis.api.metadata.IDiffractionMetadata;
import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.dataset.operations.AbstractOperation;

// Imports from org.eclipse.january
import org.eclipse.january.DatasetException;
import org.eclipse.january.IMonitor;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.DatasetUtils;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.january.dataset.ILazyDataset;
import org.eclipse.january.dataset.Maths;
import org.eclipse.january.dataset.Slice;

// Imports from uk.ac.diamond
import uk.ac.diamond.scisoft.analysis.diffraction.powder.IPixelIntegrationCache;
import uk.ac.diamond.scisoft.analysis.diffraction.powder.PixelIntegration;
import uk.ac.diamond.scisoft.analysis.diffraction.powder.PixelIntegrationBean;
import uk.ac.diamond.scisoft.analysis.diffraction.powder.PixelIntegrationCache;
import uk.ac.diamond.scisoft.analysis.processing.operations.ErrorPropagationUtils;
import uk.ac.diamond.scisoft.analysis.processing.operations.saxs.UsaxsTwoThetaToQOperation;
import uk.ac.diamond.scisoft.analysis.roi.XAxis;

// Imports from org.slf4j
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class SubtractMatchedIntensityOperation extends AbstractOperation<SubtractMatchedIntensityModel, OperationData> {
	
	
	// First, set up a logger
	@SuppressWarnings("unused")
	private static final Logger logger = LoggerFactory.getLogger(UsaxsTwoThetaToQOperation.class);
	
	
	// Some things to hold on to
	protected volatile IPixelIntegrationCache cache;
	protected IDiffractionMetadata metadata;
	
	
	@Override
	public String getId() {
		return "uk.ac.diamond.scisoft.analysis.processing.operations.externaldata.SubtractMatchedIntensityOperation";
	}
	
	
	@Override
	public OperationRank getInputRank() {
		return OperationRank.TWO;
	}
	
	
	@Override
	public OperationRank getOutputRank() {
		return OperationRank.TWO;
	}
	
	
	protected OperationData process(IDataset input, IMonitor monitor) throws OperationException {
		// First let's set up the subtract with processing plugin to format the external data
		SubtractWithProcessing subtractWithProcessing = new SubtractWithProcessing();
		subtractWithProcessing.setModel(model);
		
		// Now let's fetch both datasets
		Dataset processedExternalData = subtractWithProcessing.getData(input).squeeze();
		Dataset processedInternalData = DatasetUtils.convertToDataset(input);
		
		// Now let's reduce these datasets
		IDiffractionMetadata diffractionMetadata = getFirstDiffractionMetadata(input);
		List<Dataset> reducedExternalData = getReducedData(processedExternalData, diffractionMetadata);
		List<Dataset> reducedInternalData = getReducedData(processedInternalData, diffractionMetadata);
		
		double[] qRange = model.getQScalingRange();
		
		// Then find the index values corresponding to the range we want to work on
		int[] externalIndexValues = findMinMaxIndices(qRange, reducedExternalData.get(0));
		int[] internalIndexValues = findMinMaxIndices(qRange, reducedInternalData.get(0));
		
		// Get the slice of reduced data that this corresponds to
		Dataset reducedExternalDataSlice = reducedExternalData.get(1).getSlice(new Slice(externalIndexValues[0], externalIndexValues[1], 1));
		Dataset reducedInternalDataSlice = reducedInternalData.get(1).getSlice(new Slice(internalIndexValues[0], internalIndexValues[1], 1));
		
		// First divide a by b in order to work out the difference factor and then take the mean for good stats
		Dataset differenceFactorDataset = Maths.divide(reducedInternalDataSlice, reducedExternalDataSlice);
		Dataset meanDifferenceFactor = DatasetFactory.createFromObject(differenceFactorDataset.mean(null), 1);

		// Then do the multiplication and subtraction
		Dataset subtractedFrame = ErrorPropagationUtils.subtractWithUncertainty(ErrorPropagationUtils.multiplyWithUncertainty(processedExternalData, meanDifferenceFactor), processedInternalData);
		copyMetadata(input, subtractedFrame);
		
		// Before returning the result
		return new OperationData(subtractedFrame);
	}
	
	
	private List<Dataset> getReducedData(IDataset input, IDiffractionMetadata md) {
		if (md == null) throw new OperationException(this, "No detector geometry information!");
		
		if (metadata == null) {
			metadata = md;
			cache = null;
		} else {
			boolean dee = metadata.getDiffractionCrystalEnvironment().equals(md.getDiffractionCrystalEnvironment());
			boolean dpe = metadata.getDetector2DProperties().equals(md.getDetector2DProperties());
			
			if (!dpe || !dee) {
				metadata = md;
				cache = null;
			}
		}
		
		ILazyDataset mask = getFirstMask(input);
		IDataset m = null;
		if (mask != null) {
			try {
				m = mask.getSlice().squeeze();
			} catch (DatasetException e) {
				throw new OperationException(this, e);
			}
		}
		
		IPixelIntegrationCache lcache = getCache(metadata, input.getShape());
		
		List<Dataset> reducedDataset = PixelIntegration.integrate(input,m,lcache);
		
		return reducedDataset;
	}
	
	
	protected IPixelIntegrationCache getCache(IDiffractionMetadata md, int[] shape) {
		IPixelIntegrationCache lcache = cache;
		if (lcache == null) {
			synchronized(this) {
				lcache = cache;
				if (lcache == null) {
					PixelIntegrationBean bean = new PixelIntegrationBean();
					bean.setUsePixelSplitting(false);
					bean.setxAxis(XAxis.Q);
					bean.setAzimuthalIntegration(true);
					bean.setTo1D(true);
					bean.setShape(shape);
					bean.setSanitise(true);
					cache = lcache = new PixelIntegrationCache(metadata, bean);
				}
			}
		}
		return lcache;
	}
	
	
	private int[] findMinMaxIndices(double[] roiValues, Dataset xAxis) {
		// Create some placeholders
		int startIndex = 0;
		int endIndex = 0;
		
		// Assuming that we've been given some values
		if (roiValues == null) {
			startIndex = 0;
			endIndex = xAxis.getSize();
		} // Go and find them!
		else {
			// Just to make sure the indexing is right, lowest number first
			if (roiValues[0] < roiValues[1]) {
				startIndex = DatasetUtils.findIndexGreaterThanOrEqualTo(xAxis, roiValues[0]);
				endIndex = DatasetUtils.findIndexGreaterThanOrEqualTo(xAxis, roiValues[1]);	
			} // Or we handle for this
			else {
				startIndex = DatasetUtils.findIndexGreaterThanOrEqualTo(xAxis, roiValues[1]);
				endIndex = DatasetUtils.findIndexGreaterThanOrEqualTo(xAxis, roiValues[0]);
			}
		}
		return new int[] {startIndex, endIndex};
	}
}