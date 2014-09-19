/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */ 
package uk.ac.diamond.scisoft.analysis.osgi;

import java.net.URL;
import java.util.Collection;

import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.io.IDataHolder;
import org.eclipse.dawnsci.analysis.api.io.ILoaderService;
import org.eclipse.dawnsci.analysis.api.metadata.IDiffractionMetadata;
import org.eclipse.dawnsci.analysis.api.metadata.IMetadata;
import org.eclipse.dawnsci.analysis.api.monitor.IMonitor;
import org.eclipse.ui.services.AbstractServiceFactory;
import org.eclipse.ui.services.IServiceLocator;

import uk.ac.diamond.scisoft.analysis.io.LoaderFactory;

/**
 * Provides a class which will use any loaders available to load a particular file
 * 
 * TODO FIXME This class should be moved to a proper OSGI service.
 * 
 * @author gerring
 *
 */
public class LoaderServiceImpl extends AbstractServiceFactory implements ILoaderService {

	static {
		System.out.println("Starting loader service");
	}
	public LoaderServiceImpl() {
		// Important do nothing here, OSGI may start the service more than once.
	}
	
	@Override
	public IDataHolder getData(String filePath, final IMonitor monitor) throws Throwable {
	    IMonitor mon = monitor!=null ? monitor : new IMonitor.Stub(); 
		return LoaderFactory.getData(filePath, mon);
	}

	
	@Override
	public IDataset getDataset(String filePath, final IMonitor monitor) throws Throwable {
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
	public IDataset getDataset(final String path, final String datasetName, final IMonitor monitor) throws Throwable {
	    
	    IMonitor mon = monitor!=null ? monitor : new IMonitor.Stub(); 
		return LoaderFactory.getDataSet(path, datasetName, mon);
	}
	
	@Override
	public IMetadata getMetadata(final String filePath, final IMonitor monitor) throws Exception {
				
	    IMonitor mon = monitor!=null ? monitor : new IMonitor.Stub(); 
		return LoaderFactory.getMetadata(filePath, mon);
	}



	@Override
	public Object create(@SuppressWarnings("rawtypes") Class serviceInterface, 
			             IServiceLocator parentLocator,
			             IServiceLocator locator) {
		
        if (serviceInterface==ILoaderService.class) {
        	return new LoaderServiceImpl();
        }
		return null;
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
}
