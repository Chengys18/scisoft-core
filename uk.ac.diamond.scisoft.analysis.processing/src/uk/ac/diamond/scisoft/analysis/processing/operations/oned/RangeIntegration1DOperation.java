package uk.ac.diamond.scisoft.analysis.processing.operations.oned;

import java.io.Serializable;
import java.util.Arrays;

import org.eclipse.dawnsci.analysis.api.processing.Atomic;
import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.dataset.operations.AbstractOperation;
import org.eclipse.dawnsci.analysis.dataset.roi.ROISliceUtils;
import org.eclipse.january.DatasetException;
import org.eclipse.january.IMonitor;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.january.dataset.ILazyDataset;
import org.eclipse.january.dataset.IntegerDataset;

@Atomic
public class RangeIntegration1DOperation extends AbstractOperation<RangeIntegration1DModel, OperationData> {

	@Override
	public String getId() {
		return "uk.ac.diamond.scisoft.analysis.processing.operations.RangeIntegration1DOperation";
	}

	@Override
	public OperationRank getInputRank() {
		return OperationRank.ONE;
	}

	@Override
	public OperationRank getOutputRank() {
		return OperationRank.ONE;
	}
	
	protected OperationData process(IDataset input, IMonitor monitor) throws OperationException {
		
		ILazyDataset[] firstAxes = getFirstAxes(input);
		IDataset axis = null;
		
		if (firstAxes == null || firstAxes[0] == null) {
			axis = DatasetFactory.createRange(IntegerDataset.class, input.getSize());
		} else {
			try {
				axis = firstAxes[0].getSlice();
			} catch (DatasetException e) {
				throw new OperationException(this, e);
			}
		}
		
		double[] integrationRange = model.getIntegrationRange();
		int[] indexes = new int[2];
		if (integrationRange != null) {
			integrationRange = integrationRange.clone();
			Arrays.sort(integrationRange);
			indexes = new int[2];
			indexes[0]= ROISliceUtils.findPositionOfClosestValueInAxis(axis, integrationRange[0]);
			indexes[1]= ROISliceUtils.findPositionOfClosestValueInAxis(axis, integrationRange[1]);
			Arrays.sort(indexes);
		} else {
			indexes[0] = 0;
			indexes[1] = axis.getSize()-1;
		}
		
		
		double out = 0;
		
		for (int i = indexes[0]; i < indexes[1]; i++) {
			
			double val = (input.getDouble(i)+input.getDouble(i+1))/2;
			double tmp = Math.abs((axis.getDouble(i+1)-axis.getDouble(i)));
			val = val * tmp;
			
			out += val;
			
		}
		
		if (model.isSubtractBaseline()) {
			
			double val = (input.getDouble(indexes[0]) + input.getDouble(indexes[1]))/2;
			double tmp = Math.abs((axis.getDouble(indexes[1])-axis.getDouble(indexes[0])));
			val = val * tmp;
			out -= val;
		}
		
		Dataset aux = DatasetFactory.createFromObject(new double[]{out});
		aux.setName("integrated");
		aux.squeeze();
		
		return new OperationData(input,new Serializable[]{aux});
	}

	
}
