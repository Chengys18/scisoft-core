/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */ 
package uk.ac.diamond.scisoft.analysis.io;

import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.dataset.ILazyDataset;
import org.eclipse.dawnsci.analysis.api.io.IDataHolder;
import org.eclipse.dawnsci.analysis.api.io.IFileLoader;
import org.eclipse.dawnsci.analysis.api.io.ILoaderService;
import org.eclipse.dawnsci.analysis.api.metadata.AxesMetadata;
import org.eclipse.dawnsci.analysis.api.metadata.IDiffractionMetadata;
import org.eclipse.dawnsci.analysis.api.metadata.IMetadata;
import org.eclipse.dawnsci.analysis.api.monitor.IMonitor;
import org.eclipse.dawnsci.analysis.dataset.metadata.AxesMetadataImpl;

/**
 * Provides a class which will use any loaders available to load a particular file
 * 
 * TODO FIXME This class should be moved to a proper OSGI service.
 * 
 * @author gerring
 *
 */
public class LoaderServiceImpl implements ILoaderService {

	static {
		System.out.println("Starting loader service");
	}
	public LoaderServiceImpl() {
		// Important do nothing here, OSGI may start the service more than once.
	}
	
	@Override
	public IDataHolder getData(String filePath, final IMonitor monitor) throws Exception {
		return getData(filePath, false, monitor);
	}

	@Override
	public IDataHolder getData(String filePath, boolean lazily, IMonitor monitor) throws Exception {
	    IMonitor mon = monitor!=null ? monitor : new IMonitor.Stub(); 
		return LoaderFactory.getData(filePath, true, false, lazily, mon);
	}

	@Override
	public IDataset getDataset(String filePath, final IMonitor monitor) throws Exception {
	    try {
		    final URL uri = new URL(filePath);
		    filePath = uri.getPath();
		} catch (Throwable ignored) {
		    // We try the file path anyway
		}
	    
	    IMonitor mon = monitor!=null ? monitor : new IMonitor.Stub(); 
		final IDataHolder dh  = LoaderFactory.getData(filePath, mon);
		return dh!=null ? dh.getDataset(0) : null;
	}

	@Override
	public IDataset getDataset(final String path, final String datasetName, final IMonitor monitor) throws Exception {
	    
	    IMonitor mon = monitor!=null ? monitor : new IMonitor.Stub(); 
		return LoaderFactory.getDataSet(path, datasetName, mon);
	}
	
	@Override
	public IMetadata getMetadata(final String filePath, final IMonitor monitor) throws Exception {
				
	    IMonitor mon = monitor!=null ? monitor : new IMonitor.Stub(); 
		return LoaderFactory.getMetadata(filePath, mon);
	}

	
	private IDiffractionMetadata lockedDiffractionMetaData;

	@Override
	public IDiffractionMetadata getLockedDiffractionMetaData() {
		return lockedDiffractionMetaData;
	}

	@Override
	public IDiffractionMetadata setLockedDiffractionMetaData(IDiffractionMetadata diffMetaData) {
		IDiffractionMetadata old = lockedDiffractionMetaData;
		lockedDiffractionMetaData= diffMetaData;
		LoaderFactory.setLockedMetaData(lockedDiffractionMetaData); // The locking can change meta of original data.
		return old;
	}

	@Override
	public Collection<String> getSupportedExtensions() {
		return LoaderFactory.getSupportedExtensions();
	}

	@Override
	public void clearSoftReferenceCache() {
		LoaderFactory.clear();
	}
	@Override
	public void clearSoftReferenceCache(String filePath) {
		LoaderFactory.clear(filePath);
	}
	

	@Override
	public Matcher getStackMatcher(String name) {
		
		int posExt = name.lastIndexOf(".");
		if (posExt>-1) {
			String regexp = LoaderFactory.getStackExpression();
			String ext = name.substring(posExt + 1);
    		Pattern pattern = Pattern.compile(regexp+"\\.("+ext+")");
    		return pattern.matcher(name);
		}
		return null;
	}
	
	@Override
	public AxesMetadata getAxesMetadata(ILazyDataset parent, String path, Map<Integer, String> axesNames, boolean lazy) throws Exception {

		AxesMetadataImpl axMeta = null;
		int rank = parent.getRank();
		int[] shape = parent.getShape();
			axMeta = new AxesMetadataImpl(rank);
			if (axesNames == null) return axMeta;
			for (Integer key : axesNames.keySet()) {
				String axesName = axesNames.get(key);
				IDataHolder dataHolder = getData(path, null);
				ILazyDataset lazyDataset = dataHolder.getLazyDataset(axesName);
				if (lazyDataset == parent) throw new IllegalArgumentException("Axes metadata should not contain original dataset!");
				if (lazyDataset!= null) {

					if (lazyDataset.getName() == null || lazyDataset.getName().isEmpty()) {
						lazyDataset.setName(axesName);
					}

					int axRank = lazyDataset.getRank();
					if (axRank == rank || axRank == 1)	{
						lazyDataset = lazyDataset.getSliceView();
						lazyDataset.clearMetadata(AxesMetadata.class);
						axMeta.setAxis(key-1, lazy ? lazyDataset : lazyDataset.getSlice());
					} else {

						int[] axShape = lazyDataset.getShape();
						int[] newShape = new int[rank];
						Arrays.fill(newShape, 1);

						int[] idx = new int[axRank];
						Arrays.fill(idx, -1);
						Boolean[] found = new Boolean[axRank];
						Arrays.fill(found, false);
						int max = rank;

						for (int i = axRank-1; i >= 0; i--) {
							int id = axShape[i];
							updateShape(i, max, shape, id, idx, found);

						}
						
						boolean allFound = !Arrays.asList(found).contains(false);
						
						if (!allFound) {
							throw new IllegalArgumentException("Axes shape not compatible!");
						}

						for (int i = 0; i < axRank; i++) {
							newShape[idx[i]] = axShape[i];
						}

						lazyDataset = lazyDataset.getSliceView();
						lazyDataset.clearMetadata(AxesMetadata.class);
						lazyDataset.setShape(newShape);
					}
					axMeta.setAxis(key-1, lazy ? lazyDataset : lazyDataset.getSlice());
				}
				else {
					axMeta.setAxis(key-1, new ILazyDataset[1]);
				}
			}

			return axMeta;
	}
	
	private boolean updateShape(int i, int max, int[] shape, int id, int[] idx, Boolean[] found){
		
		int[] idxc = idx.clone();
		Arrays.sort(idxc);
		
		for (int j = max -1 ; j >= 0; j--) {

			if (id == shape[j] && Arrays.binarySearch(idxc, j) < 0) {
				idx[i] = j;
				found[i] = true;
				max = j;
				return true;
			}

		}
		
		return false;
	}

	@Override
	public Class<? extends IFileLoader> getLoaderClass(String extension) {
		return LoaderFactory.getLoaderClass(extension);
	}
}
