package uk.ac.diamond.scisoft.xpdf.operations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.monitor.IMonitor;
import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetUtils;
import org.eclipse.dawnsci.analysis.dataset.impl.DoubleDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.Maths;
import org.eclipse.dawnsci.analysis.dataset.operations.AbstractOperation;

import uk.ac.diamond.scisoft.xpdf.XPDFCalibration;
import uk.ac.diamond.scisoft.xpdf.XPDFCoordinates;
import uk.ac.diamond.scisoft.xpdf.XPDFQSquaredIntegrator;
import uk.ac.diamond.scisoft.xpdf.XPDFTargetComponent;
import uk.ac.diamond.scisoft.xpdf.metadata.XPDFMetadata;

/**
 * Iterate the calibration constant for the XPDF data.
 * @author Timothy Spain (rkl37156) timothy.spain@diamond.ac.uk
 * @since 2015-09-14
 *
 */
public class XPDFIterateCalibrationConstantOperation extends
		AbstractOperation<XPDFIterateCalibrationConstantModel, OperationData> {

	protected OperationData process(IDataset input, IMonitor monitor)
			throws OperationException {

		// The real XPDFIterateCalibrationConstantOperation starts here
		
		XPDFCalibration theCalibration = new XPDFCalibration();
		
		int nIterations = model.getnIterations();
		// The initial value of the calibration constant is 20
		theCalibration.setInitialCalibrationConstant(20.0);
		
		Dataset absCor = null;
		
		XPDFMetadata theXPDFMetadata = null;
		// Get the metadata
		theXPDFMetadata = input.getFirstMetadata(XPDFMetadata.class);
		if (theXPDFMetadata == null) throw new OperationException(this, "XPDFMetadata not found.");
		
		// Sort the containers if requested
		if (model.isSortContainers()) {
			theXPDFMetadata.reorderContainers(orderContainers(theXPDFMetadata.getContainers()));
		}
		List<Dataset> backgroundSubtracted = new ArrayList<Dataset>();
		// The 0th element is the sample
		backgroundSubtracted.add((Dataset) input);
		// Add the containers in order, innermost to outermost
		for (XPDFTargetComponent container : theXPDFMetadata.getContainers()) {
			backgroundSubtracted.add(container.getBackgroundSubtractedTrace());
		}
		theCalibration.setBackgroundSubtracted(backgroundSubtracted);
	
		theCalibration.setSampleIlluminatedAtoms(theXPDFMetadata.getSampleIlluminatedAtoms());
		
		// Get 2θ, the axis variable
		Dataset twoTheta = Maths.toRadians(DatasetUtils.convertToDataset(AbstractOperation.getFirstAxes(input)[0]));
		XPDFCoordinates coordinates = new XPDFCoordinates();
		coordinates.setTwoTheta(twoTheta);
		coordinates.setBeamData(theXPDFMetadata.getBeam());
		
		// Set up the q² integrator class
		theCalibration.setqSquaredIntegrator(new XPDFQSquaredIntegrator(coordinates));//twoTheta, theXPDFMetadata.getBeam()));
		
		theCalibration.setSelfScatteringDenominatorFromSample(theXPDFMetadata.getSample(), coordinates);
		
		theCalibration.setAbsorptionMaps(theXPDFMetadata.getAbsorptionMaps(twoTheta.reshape(twoTheta.getSize(), 1), DoubleDataset.zeros(twoTheta.reshape(twoTheta.getSize(), 1))));
		
		for (int i = 0; i < nIterations; i++) 
			absCor = theCalibration.iterate();
		
		copyMetadata(input, absCor);
		
		return new OperationData(absCor);
	}
	
	/**
	 * Orders the list of containers.
	 * <p>
	 * Given a list of container XPDFTargetComponents, orders them by their
	 * larger distance (external radius). Matches with the logic of the python
	 * version.
	 * @param containers
	 * 					the list of containers to order
	 * @return a map keyed by the position in the new list, with a value of the
	 * position in the old list.
	 */
	static private Map<Integer, Integer> orderContainers(
			List<XPDFTargetComponent> containers) {
		List<Double> outerRadii = new ArrayList<Double>();
		// Populate a list of outer radii of the containers
		for (XPDFTargetComponent aContainer : containers) {
			outerRadii.add(aContainer.getForm().getGeom().getDistances()[1]);
		}
		// Java offers no way of getting the sorted indices from a Collection, 
		// so we have to do it ourselves
		List<Integer> indices = new ArrayList<Integer>();
		for (int i = 0; i<outerRadii.size(); i++) {
			indices.add(i, i);
		}
		indices.sort(new Comparator<Integer>() {
			@Override public int compare(final Integer i1, final Integer i2) {
				return Double.compare(outerRadii.get(i1), outerRadii.get(i2));
			}
		});
		Map<Integer, Integer> newOrder = new HashMap<Integer, Integer>();
		for (int i = 0; i < indices.size(); i++) {
			newOrder.put(i, indices.get(i));
		}

		return newOrder;
	}


	@Override
	public String getId() {
		return "uk.ac.diamond.scisoft.xpdf.operations.XPDFIterateCalibrationConstantOperation";
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
