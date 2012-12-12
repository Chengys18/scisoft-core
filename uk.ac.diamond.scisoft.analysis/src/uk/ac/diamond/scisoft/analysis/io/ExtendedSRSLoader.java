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

package uk.ac.diamond.scisoft.analysis.io;

import gda.analysis.io.ScanFileHolderException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.ILazyDataset;
import uk.ac.diamond.scisoft.analysis.dataset.LazyDataset;
import uk.ac.gda.monitor.IMonitor;

/**
 * This class loads an SRS data file and also images from a Dectris Pilatus detector 
 * <p>
 * <b>Note</b>: the metadata from this loader is left as strings
 */
public class ExtendedSRSLoader extends SRSLoader implements ISliceLoader {

	private static final String PILATUS_DIR = "pilatus100k"; // sub-directory for 100k images 
	private static final String PATH_DATASET = "path";       // path dataset name
	private static final String PILATUS_KEY = "pilatus100k_path_template"; // metadata key for template string
	private static final String DATA_NAME = "Pilatus";

	public ExtendedSRSLoader(String filename) {
		super(filename);
	}

	private void appendPilatusData(DataHolder currentDataHolder, IMonitor mon) {

		ImageStackLoader loader=null;

		// now we need to try to load in the the pilatus data
		if (currentDataHolder.contains(PATH_DATASET)) {
			AbstractDataset paths = currentDataHolder.getDataset(PATH_DATASET);
			if (!textMetadata.containsKey(PILATUS_KEY)) {
				// TODO FIXME BODGE bodged format v1
				loader = getImageStackV1(paths, mon);
			} else {
			    // TODO FIXME BODGE bodged format v2
				loader = getImageStackV2(paths, mon);
			}
		}

		if (loader!=null) {
			LazyDataset lazyDataset = new LazyDataset(DATA_NAME, loader.dtype, loader.getShape(), loader);
			currentDataHolder.addDataset(lazyDataset.getName(), lazyDataset);
			datasetNames.add(lazyDataset.getName());
			dataShapes.put(lazyDataset.getName(), lazyDataset.getShape());
		}
	}
	
	private ImageStackLoader getImageStackV1(AbstractDataset paths, IMonitor mon) {
		
		List<String> files = new ArrayList<String>();
		// build a list of all the pathnames
		File base = new File(fileName);
		File path = base.getParentFile();
		path = new File(base.getParentFile(), PILATUS_DIR);

		for (int i = 0; i < paths.getShape()[0]; i++) {
			File file = new File(path, String.format("test%d.tif", paths.getInt(i)));
			if(!file.exists()) file = new File(path, String.format("p%d.tif", paths.getInt(i)));
			
			// finally add the item.
			files.add(file.getAbsolutePath());
		}

		try {
			return new ImageStackLoader(files, mon);
		} catch (Exception e) {
			logger.warn("Could not create ImageStackLoader, not populating pilatus stack");
			return null;
		}

	}

	private ImageStackLoader getImageStackV2(AbstractDataset paths, IMonitor mon) {
		
		ArrayList<String> files = new ArrayList<String>();
		final String pilPath = textMetadata.get(PILATUS_KEY);
		
		final File file = new File(fileName);
		final File dir  = file.getParentFile();
		
		// Only works with 1D set which is likely ok, we are a very specific format here.
		for (int index=0;index<paths.getSize();++index) {
			final String subPath = String.format(pilPath, paths.getInt(index));
			final String imgPath = new File(dir, subPath).getAbsolutePath();
			files.add(imgPath);
		}	
		
		try {
			return new ImageStackLoader(files, mon);
		} catch (Exception e) {
			logger.warn("Could not create ImageStackLoader, not populating Pilatus image stack");
			return null;
		}
	}

	@Override
	public DataHolder loadFile(IMonitor mon) throws ScanFileHolderException {
		// load all the standard data in
		DataHolder data = super.loadFile(mon);

		appendPilatusData(data, mon);
		if (loadMetadata) {
			createMetadata();
			data.setMetadata(getMetaData());
		}
		return data;
	}
	
	@Override
	public void loadMetaData(IMonitor mon) throws Exception {
        super.loadMetaData(mon);
        
        // Cannot do this if decorator, this means that the I16 data folder would parse all
        // the ascii files in the whole directory!!
        
		if (textMetadata.containsKey(PILATUS_KEY)) {
			
			if (!datasetNames.contains(PATH_DATASET)) return;
			/**
			 * IMPORTANT DO NOT PARSE WHOLE FILE HERE! It will break the decorators!
			 */
			datasetNames.add(DATA_NAME);
			createMetadata();
		}
	}

	/**
	 * Slices the stack of images
	 */
	@Override
	public AbstractDataset slice(SliceObject bean, IMonitor mon) throws Exception {
		
		/**
		 * Not ideal have to parse SRS file once for each slice.
		 * The LoaderFactory caches slices which helps a little. 
		 */
		this.fileName = bean.getPath();
		final DataHolder dh = loadFile(mon);
		ILazyDataset imageStack = dh.getLazyDataset(DATA_NAME);
		// ImageStackLoader does load the AbstractDataset at this point
		return (AbstractDataset) imageStack.getSlice(bean.getSliceStart(), bean.getSliceStop(), bean.getSliceStep());
	}

}
