package uk.ac.diamond.scisoft.analysis.processing.operations;

import java.util.ArrayList;
import java.util.List;

import uk.ac.diamond.scisoft.analysis.dataset.BooleanDataset;
import uk.ac.diamond.scisoft.analysis.dataset.Dataset;
import uk.ac.diamond.scisoft.analysis.dataset.IDataset;
import uk.ac.diamond.scisoft.analysis.dataset.PositionIterator;
import uk.ac.diamond.scisoft.analysis.dataset.Slice;
import uk.ac.diamond.scisoft.analysis.metadata.MaskMetadata;
import uk.ac.diamond.scisoft.analysis.monitor.IMonitor;
import uk.ac.diamond.scisoft.analysis.processing.AbstractOperation;
import uk.ac.diamond.scisoft.analysis.processing.OperationData;
import uk.ac.diamond.scisoft.analysis.processing.OperationException;
import uk.ac.diamond.scisoft.analysis.processing.OperationRank;
import uk.ac.diamond.scisoft.analysis.processing.model.IOperationModel;

public class ThresholdMask extends AbstractOperation {

	@Override
    public String getName() {
		return "Threshold Mask";
	}

	@Override
	public String getId() {
		return " uk.ac.diamond.scisoft.analysis.processing.operations.thresholdMask";
	}

	@Override
	public OperationData execute(IDataset slice, IMonitor monitor) throws OperationException {
		
		Dataset data = (Dataset)slice;
		IDataset mask = null;
		try {
			MaskMetadata maskMetadata = ((MaskMetadata)data.getMetadata(MaskMetadata.class));
			mask = maskMetadata!=null
				 ? maskMetadata.getMask().getSlice((Slice[])null)
				 : BooleanDataset.ones(slice.getShape());
				 
		} catch (Exception e) {
			throw new OperationException(this, e);
		}
		
		if (!isCompatible(slice.getShape(), mask.getShape())) {
			throw new OperationException(this, "Mask is incorrect shape!");
		}
		
		try {
			Double upper  = (Double)model.get("Upper");
			Double lower  = (Double)model.get("Lower");
			
			// TODO A fork/join or Java8 lambda would do this operation faster...
			PositionIterator it = new PositionIterator(mask.getShape());
			while (it.hasNext()) {
								
				int[] pos = it.getPos();
				if (slice.getDouble(pos)>upper || slice.getDouble(pos)<lower) {
					mask.set(false, pos);
				}
			}
			monitor.worked(1);
			
			return new OperationData(data);

		} catch (Exception ne) {
			throw new OperationException(this, ne);
		}
	}

	protected static boolean isCompatible(final int[] ashape, final int[] bshape) {

		List<Integer> alist = new ArrayList<Integer>();

		for (int a : ashape) {
			if (a > 1) alist.add(a);
		}

		final int imax = alist.size();
		int i = 0;
		for (int b : bshape) {
			if (b == 1)
				continue;
			if (i >= imax || b != alist.get(i++))
				return false;
		}

		return i == imax;
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
