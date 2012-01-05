/*
 * Copyright 2011 Diamond Light Source Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.ac.diamond.scisoft.analysis.dataset;

import gda.analysis.io.ScanFileHolderException;

import java.io.Serializable;

import uk.ac.diamond.scisoft.analysis.io.IMetaData;
import uk.ac.gda.monitor.IMonitor;

/**
 * This interface defines the lazy parts of a dataset. A dataset is a N-dimensional array of items
 * where N can be zero to represent a "scalar" or single-valued dataset. A scalar dataset has zero
 * rank and an empty array for shape.
 */
public interface ILazyDataset extends Serializable, IMetadataProvider {
	/**
	 * @return Class of element
	 */
	public Class<?> elementClass();

	/**
	 * The dataset's name. This can be useful for labelling an axis, etc.
	 * 
	 * @return name
	 */
	public String getName();

	/**
	 * Set the name of the dataset
	 * 
	 * @param name
	 */
	public void setName(String name);

	/**
	 * The size of the dataset is the number of items in the array
	 * (not including reserved space)
	 * 
	 * @return number of data items
	 */
	public int getSize();

	/**
	 * The shape (or array of lengths for each dimension) of the dataset can be empty for "scalar"
	 * datasets
	 * 
	 * @return Shape of dataset
	 */
	public int[] getShape();

	/**
	 * Set a compatible shape for dataset. A shape is compatible if it has the capacity to contain
	 * the same number of items
	 * 
	 * @param shape
	 */
	public void setShape(final int... shape);

	/**
	 * The rank (or number of dimensions/indices) of the dataset can be zero for a "scalar"
	 * (single-valued) dataset 
	 * @return rank
	 */
	public int getRank();

	/**
	 * Remove dimensions of 1 in shape of the dataset
	 */
	public ILazyDataset squeeze();

	/**
	 * Remove dimensions of 1 in shape of the dataset from end only if true
	 * 
	 * @param onlyFromEnd
	 */
	public ILazyDataset squeeze(boolean onlyFromEnd);

	/**
	 * Get a slice of the dataset. The returned dataset is a copied selection of items
	 * 
	 * @param start
	 *            specifies the starting indexes
	 * @param stop
	 *            specifies the stopping indexes
	 * @param step
	 *            specifies the steps in the slice
	 * @return The dataset of the sliced data
	 */
	public IDataset getSlice(final int[] start, final int[] stop, final int[] step);

	/**
	 * Get a slice of the dataset. The returned dataset is a copied selection of items
	 * 
	 * @param monitor
	 * @param start
	 *            specifies the starting indexes
	 * @param stop
	 *            specifies the stopping indexes
	 * @param step
	 *            specifies the steps in the slice
	 * @return The dataset of the sliced data
	 * @throws ScanFileHolderException 
	 */
	public IDataset getSlice(IMonitor monitor, final int[] start, final int[] stop,
			final int[] step) throws ScanFileHolderException;

	/**
	 * Get a slice of the dataset. The returned dataset is a copied selection of items
	 * 
	 * @param slice an array of slice objects (the array can be null or contain nulls)
	 * @return The dataset of the sliced data
	 */
	public IDataset getSlice(final Slice... slice);

	/**
	 * Get a slice of the dataset. The returned dataset is a copied selection of items
	 * 
	 * @param monitor
	 * @param slice an array of slice objects (the array can be null or contain nulls)
	 * @return The dataset of the sliced data
	 * @throws ScanFileHolderException 
	 */
	public IDataset getSlice(IMonitor monitor, final Slice... slice) throws ScanFileHolderException;

	/**
	 * Set metadata on the dataset
	 * 
	 * @param metadata
	 */
	public void setMetadata(IMetaData metadata);
}
