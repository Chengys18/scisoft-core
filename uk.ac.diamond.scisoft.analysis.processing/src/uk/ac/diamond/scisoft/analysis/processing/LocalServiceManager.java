/*-
 * Copyright 2016 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.processing;

import org.eclipse.dawnsci.analysis.api.io.ILoaderService;
import org.eclipse.dawnsci.analysis.api.persistence.IPersistenceService;
import org.eclipse.dawnsci.analysis.api.processing.IOperationService;

public class LocalServiceManager {
	
	private static ILoaderService lservice;
	private static IOperationService oservice;
	private static IPersistenceService pservice;

	public void setLoaderService(ILoaderService s) {
		lservice = s;
	}

	public static ILoaderService getLoaderService() {
		return lservice;
	}
	
	public static IOperationService getOperationService() {
		return oservice;
	}

	public void setOperationService(IOperationService s) {
		oservice = s;
	}

	public static IPersistenceService getPersistenceService() {
		return pservice;
	}

	public void setPersistenceService(IPersistenceService p) {
		pservice = p;
	}
}
