/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.diffraction.powder;

import java.util.Arrays;

import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.DatasetUtils;
import org.eclipse.january.dataset.DoubleDataset;
import org.eclipse.january.dataset.FloatDataset;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.january.dataset.IndexIterator;
import org.eclipse.january.dataset.IntegerDataset;
import org.eclipse.january.dataset.Maths;

/**
 * This is terrible, it's like just completely copy pasted from the Pixel integration classes. Blame Jake, blame Gareth.
 * 
 * Do it better please.
 */
public class MapTo2DUtils {

	public static Dataset remap2Dto2DNonSplitting(Dataset original, Dataset xO, Dataset yO, double[] xRange, int xNumber, double[] yRange, int yNumber) {
		
		double shift = (xRange[1]- xRange[0])/(2*xNumber);
		DoubleDataset xBins =  (DoubleDataset) DatasetFactory.createLinearSpace(xRange[0]-shift, xRange[1]+shift, xNumber + 1, Dataset.FLOAT64);
		shift = (yRange[1]- yRange[0])/(2*yNumber);
		DoubleDataset yBins =  (DoubleDataset) DatasetFactory.createLinearSpace(yRange[0]-shift, yRange[1]+shift, yNumber + 1, Dataset.FLOAT64);
		
		final double[] edgesQ = xBins.getData();
		final double loQ = edgesQ[0];
		final double hiQ = edgesQ[xNumber];
		final double spanQ = (hiQ - loQ)/xNumber;

		final double[] edgesChi = yBins.getData();
		final double loChi = edgesChi[0];
		final double hiChi = edgesChi[yNumber];
		final double spanChi = (hiChi - loChi)/yNumber;
		
		IntegerDataset histo = (IntegerDataset)DatasetFactory.zeros(new int[]{yNumber,xNumber}, Dataset.INT32);
		FloatDataset intensity = (FloatDataset)DatasetFactory.zeros(new int[]{yNumber,xNumber},Dataset.FLOAT32);

		Dataset a = DatasetUtils.convertToDataset(xO);
		Dataset b = DatasetUtils.convertToDataset(original);
		Dataset c = DatasetUtils.convertToDataset(yO);
		
		IndexIterator iter = a.getIterator();
		
		while (iter.hasNext()) {

			final double valq = a.getElementDoubleAbs(iter.index);
			final double sig = b.getElementDoubleAbs(iter.index);
			final double chi = c.getElementDoubleAbs(iter.index);

			if (valq < loQ || valq > hiQ) {
				continue;
			}

			if (chi < loChi || chi > hiChi) {
				continue;
			}

			int qPos = (int) ((valq-loQ)/spanQ);
			int chiPos = (int) ((chi-loChi)/spanChi);

			if(qPos<xNumber && chiPos<yNumber){
				int cNum = histo.get(chiPos,qPos);
				float cIn = intensity.get(chiPos,qPos);
				histo.set(cNum+1, chiPos,qPos);
				intensity.set(cIn+sig, chiPos,qPos);
			}
		}
		
		intensity.idivide(histo);
		DatasetUtils.makeFinite(intensity);
		
		return intensity;
		
	}
	
	public static Dataset remap2Dto2DSplitting(IDataset original, IDataset xO, IDataset yO, double[] xRange, int xNumber, double[] yRange, int yNumber) {
		
		double shift = (xRange[1]- xRange[0])/(2*xNumber);
		DoubleDataset xBins =  (DoubleDataset) DatasetFactory.createLinearSpace(xRange[0]-shift, xRange[1]+shift, xNumber + 1, Dataset.FLOAT64);
		shift = (yRange[1]- yRange[0])/(2*yNumber);
		DoubleDataset yBins =  (DoubleDataset) DatasetFactory.createLinearSpace(yRange[0]-shift, yRange[1]+shift, yNumber + 1, Dataset.FLOAT64);
		
		final double[] edgesQ = xBins.getData();
		final double loQ = edgesQ[0];
		final double hiQ = edgesQ[xNumber];
		final double spanQ = (hiQ - loQ)/xNumber;

		final double[] edgesChi = yBins.getData();
		final double loChi = edgesChi[0];
		final double hiChi = edgesChi[yNumber];
		final double spanChi = (hiChi - loChi)/yNumber;
		
		FloatDataset histo = (FloatDataset)DatasetFactory.zeros(new int[]{yNumber,xNumber}, Dataset.FLOAT32);
		FloatDataset intensity = (FloatDataset)DatasetFactory.zeros(new int[]{yNumber,xNumber},Dataset.FLOAT32);

		Dataset[] radialArray = getPixelRange(xO,1);
		Dataset[] azimuthalArray = getPixelRange(yO,0);
		Dataset b = DatasetUtils.convertToDataset(original);

		IndexIterator iter = b.getIterator();
		
		while (iter.hasNext()) {

//			posStop[0] = pos[0]+2;
//			posStop[1] = pos[1]+2;
//			Dataset qrange = a.getSlice(pos, posStop, null);
//			Dataset chirange = azimuthalArray.getSlice(pos, posStop, null);
			final double qMax = radialArray[1].getElementDoubleAbs(iter.index);
			final double qMin = radialArray[0].getElementDoubleAbs(iter.index);
			final double chiMax = azimuthalArray[1].getElementDoubleAbs(iter.index);
			final double chiMin = azimuthalArray[0].getElementDoubleAbs(iter.index);

			final double sig = b.getElementDoubleAbs(iter.index);

			if (qMax < loQ || qMin > hiQ) {
				continue;
			} 

			if (chiMax < loChi || chiMin > hiChi) {
				continue;
			} 

			//losing something here? is flooring (int cast best?)

			double minBinExactQ = (qMin-loQ)/spanQ;
			double maxBinExactQ = (qMax-loQ)/spanQ;
			int minBinQ = (int)minBinExactQ;
			int maxBinQ = (int)maxBinExactQ;

			double minBinExactChi = (chiMin-loChi)/spanChi;
			double maxBinExactChi = (chiMax-loChi)/spanChi;
			int minBinChi = (int)minBinExactChi;
			int maxBinChi = (int)maxBinExactChi;

			//FIXME potentially may need to deal with azimuthal arrays with discontinuities (+180/-180 degress etc)

			double iPerPixel = 1/((maxBinExactQ-minBinExactQ)*(maxBinExactChi-minBinExactChi));
			double minFracQ = 1-(minBinExactQ-minBinQ);
			double maxFracQ = maxBinExactQ-maxBinQ;
			double minFracChi = 1-(minBinExactChi-minBinChi);
			double maxFracChi = maxBinExactChi-maxBinChi;

			for (int i = minBinQ ; i <= maxBinQ; i++) {
				if (i < 0 || i >= xBins.getSize()-1) continue;
				for (int j = minBinChi; j <= maxBinChi; j++) {
					if (j < 0 || j >= yBins.getSize()-1) continue;

					int[] setPos = new int[]{j,i};
					double val = histo.get(setPos);

					double modify = 1;

					if (i == minBinQ && minBinQ != maxBinQ) modify *= minFracQ;
					if (i == maxBinQ && minBinQ != maxBinQ) modify *= maxFracQ;
					if (i == minBinChi && minBinChi != maxBinChi) modify *= minFracChi;
					if (i == maxBinChi && minBinChi != maxBinChi) modify *= maxFracChi;

					histo.set(val+iPerPixel*modify, setPos);
					double inVal = intensity.get(setPos);
					
					intensity.set(inVal+sig*iPerPixel*modify, setPos);
				}
			}
		}
		
		intensity.idivide(histo);
		DatasetUtils.makeFinite(intensity);
		
		return intensity;
	}
	
	private static Dataset[] getPixelRange(IDataset d, int dim) {
		
		int[] sli = new int[d.getRank()];
		Arrays.fill(sli, 1);
		sli[dim] = 0;
		Dataset dd = DatasetUtils.convertToDataset(d.getSlice(sli, null, null));
		
		Arrays.fill(sli, -1);
		sli[dim] = d.getShape()[dim];
		
		Dataset d0 = DatasetUtils.convertToDataset(d.getSlice(null, sli, null));
		
		dd.isubtract(d0);
		dd = Maths.abs(dd);
		
		int nd = dim == 0 ? 1 : 0;
		
			int[] start = new int[d.getRank()];
			int[] stop = dd.getShape();
			start[nd] = -1;
			IDataset pad = dd.getSlice(start,stop,null);
			dd = DatasetUtils.append(dd, pad, nd);

		dd.idivide(2);
		return new Dataset[]{Maths.subtract(d, dd), Maths.add(d, dd)};
	}
}
