/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.dataset.function;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.dawnsci.analysis.dataset.impl.function.DatasetToDatasetFunction;
import org.eclipse.january.dataset.Comparisons;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.DatasetUtils;
import org.eclipse.january.dataset.DoubleDataset;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.january.dataset.IndexIterator;
import org.eclipse.january.dataset.IntegerDataset;
import org.eclipse.january.dataset.Maths;

/**
 * Find histogram of each dataset and return pairs of 1D integer dataset of bin counts
 * and 1D double dataset of bin edges (including rightmost edge).
 * <p>
 * By default, outliers are ignored.
 */
public class Histogram implements DatasetToDatasetFunction {
	private int nbins;
	private boolean ignoreOutliers = true;
	private Double min = null;
	private Double max = null;
	private DoubleDataset bins = null;
	private boolean useEqualSpanBins = true;

	/**
	 * Constructor of the Histogram
	 * @param numBins number of bins
	 */
	public Histogram(int numBins)
	{
		nbins = numBins;
		ignoreOutliers = true;
	}
	
	/**
	 * Constructor of the Histogram
	 * @param numBins number of bins
	 * @param lower minimum value of histogram range
	 * @param upper maximum value of histogram range
	 */
	public Histogram(int numBins, double lower, double upper)
	{
		this(numBins);
		min = lower;
		max = upper;
		if (min > max) {
			throw new IllegalArgumentException("Given lower bound was higher than upper bound");
		}

		bins = (DoubleDataset) DatasetFactory.createLinearSpace(min, max, nbins + 1, Dataset.FLOAT64);
	}

	/**
	 * Constructor of the Histogram
	 * @param numBins number of bins
	 * @param lower minimum value of histogram range
	 * @param upper maximum value of histogram range
	 * @param ignore if true, outliers will be ignored
	 */
	public Histogram(int numBins, double lower, double upper, boolean ignore)
	{
		this(numBins, lower, upper);
		ignoreOutliers = ignore;
	}	

	/**
	 * Constructor of the Histogram, ignoring outliers
	 * @param edges bin edges including rightmost edge
	 */
	public Histogram(IDataset edges) {
		this(edges, true);
	}

	/**
	 * Constructor of the Histogram
	 * @param edges bin edges including rightmost edge
	 * @param ignore if true, outliers will be ignored
	 */
	public Histogram(IDataset edges, boolean ignore)
	{
		if (edges.getRank() != 1) {
			throw new IllegalArgumentException("Bin edges should be given as 1D dataset");
		}

		bins = (DoubleDataset) DatasetUtils.cast(DatasetUtils.convertToDataset(edges), Dataset.FLOAT64);

		// check for increasing order
		Dataset sorted = DatasetUtils.sort(bins);
		if (!Comparisons.allTrue(Comparisons.almostEqualTo(bins, sorted, 1e-8, 1e-8))) {
			throw new IllegalArgumentException("Bin edges should be given in increasing order");
		}

		// check for equal spans
		Dataset diff = Maths.difference(bins, 2, 0);
		useEqualSpanBins = Comparisons.allTrue(Comparisons.almostEqualTo(diff, 0, 1e-8, 1e-8));

		nbins = edges.getSize() - 1;
		ignoreOutliers = ignore;
	}	

	/**
	 * @param datasets input datasets
	 * @return a list of 1D datasets which are histograms
	 */
	@Override
	public List<Dataset> value(IDataset... datasets) {
		if (datasets.length == 0)
			return null;

		List<Dataset> result = new ArrayList<Dataset>();

		if (useEqualSpanBins) {
			for (IDataset ds : datasets) {
				if (bins == null) {
					bins = (DoubleDataset) DatasetFactory.createLinearSpace(ds.min().doubleValue(), ds.max().doubleValue(), nbins + 1, Dataset.FLOAT64);
				}
				final double[] edges = bins.getData();
				final double lo = edges[0];
				final double hi = edges[nbins];
				final double span = (hi - lo)/nbins;

				IntegerDataset histo = DatasetFactory.zeros(IntegerDataset.class, nbins);
				final int[] h = histo.getData();
				if (span <= 0) {
					h[0] = ds.getSize();
					result.add(histo);
					result.add(bins);
					continue;
				}

				Dataset a = DatasetUtils.convertToDataset(ds);
				IndexIterator iter = a.getIterator();

				while (iter.hasNext()) {
					final double val = a.getElementDoubleAbs(iter.index);
					if (val < lo) {
						if (ignoreOutliers)
							continue;
						h[0]++;
					} else if (val >= hi) {
						if (val > hi && ignoreOutliers)
							continue;
						h[nbins-1]++;
					} else {
						if(((int) ((val-lo)/span))<h.length)
							h[(int) ((val-lo)/span)]++;
					}
				}
				result.add(histo);
				result.add(bins);
			}
		} else {
			for (IDataset ds : datasets) {
				if (bins == null) {
					bins = (DoubleDataset) DatasetFactory.createLinearSpace(ds.min().doubleValue(), ds.max().doubleValue(), nbins + 1, Dataset.FLOAT64);
				}
				final double[] edges = bins.getData();
				final double lo = edges[0];
				final double hi = edges[nbins];

				IntegerDataset histo = DatasetFactory.zeros(IntegerDataset.class, nbins);
				final int[] h = histo.getData();
				if (lo >= hi) {
					h[0] = ds.getSize();
					result.add(histo);
					result.add(bins);
					continue;
				}

				Dataset a = DatasetUtils.convertToDataset(ds);
				IndexIterator iter = a.getIterator();

				while (iter.hasNext()) {
					final double val = a.getElementDoubleAbs(iter.index);
					if (val < lo) {
						if (ignoreOutliers)
							continue;
						h[0]++;
					} else if (val >= hi) {
						if (val > hi && ignoreOutliers)
							continue;
						h[nbins-1]++;
					} else {
						// search for correct bin
						final int b = Arrays.binarySearch(edges, val);
						if (b >= 0) {
							h[b]++; // check for special case where rightmost edge is caught
						} else {
							h[-b - 2]++;
						}
					}
				}
				result.add(histo);
				result.add(bins);
			}
		}
		return result;
	}

	/**
	 * Set minimum and maximum edges of histogram bins
	 * @param min
	 * @param max
	 */
	public void setMinMax(double min, double max) {
		this.min = min;
		this.max = max;
		bins = (DoubleDataset) DatasetFactory.createLinearSpace(min, max, nbins + 1, Dataset.FLOAT64);		
	}

	/**
	 * Set histogram's outliers handling
	 * @param b if true, then ignore values that lie outside minimum and maximum bin edges
	 */
	public void setIgnoreOutliers(boolean b) {
		ignoreOutliers = b;
	}
	
}
