/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.plotserver;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import uk.ac.diamond.scisoft.analysis.PlotServer;

abstract public class AbstractPlotServer implements PlotServer {

	private Map<String, GuiBean> guiStore;
	private Map<String, DataBean> dataStore;
	private final boolean removeOnGet;

	public AbstractPlotServer() {
		this(false);
	}

	public AbstractPlotServer(boolean removeOnGet) {
		super();
		guiStore = new HashMap<String, GuiBean>();
		dataStore = new HashMap<String, DataBean>();
		this.removeOnGet = removeOnGet;
		
	}

	@Override
	public DataBean getData(String guiName) throws Exception {
		return removeOnGet ? dataStore.remove(guiName):  dataStore.get(guiName);
	}

	@Override
	public void setData(String guiName, DataBean data) throws Exception {
		if (data == null) {
			dataStore.remove(guiName);
			return;
		}
		GuiBean gb = data.getGuiParameters();
		Serializable value = gb == null ? null : gb.get(GuiParameters.PLOTOPERATION);

		// if it's a duplicate key and a PLOTOP_ADD we need to add the datasets to the old bean
		if (GuiParameters.PLOTOP_ADD.equals(value) && dataStore.containsKey(guiName)) {
			dataStore.get(guiName).addData(data);
		} else {
			dataStore.put(guiName, data);
		}

		// update plot mode
		GuiPlotMode mode = data.getGuiPlotMode();
		GuiBean bean = guiStore.get(guiName);
		if (bean == null) {
			bean = new GuiBean();
			guiStore.put(guiName, bean);
		}
		bean.put(GuiParameters.PLOTMODE, mode);
	}

	@Override
	public GuiBean getGuiState(String guiName) throws Exception {
		if (removeOnGet) {
			return guiStore.remove(guiName);
		}

		GuiBean bean = guiStore.get(guiName);
		if (bean == null) {
			bean = new GuiBean();
			guiStore.put(guiName, bean);
		}
		return bean;
	}

	@Override
	public void updateGui(String guiName, GuiBean guiData) throws Exception {
		guiData.remove(GuiParameters.QUIET_UPDATE);
		guiStore.put(guiName, guiData);
	}

	@Override
	public String[] getGuiNames() throws Exception {
		Set<String> names = new HashSet<String>();
		names.addAll(guiStore.keySet());
		names.addAll(dataStore.keySet());
		return names.toArray(new String[names.size()]);
	}
	
	/**
	 * Retrieve the Gui Store. Use with caution and understanding.
	 * @return guiStore
	 */
	protected Map<String, GuiBean> getGuiStore() {
		return guiStore;
	}

	/**
	 * Retrieve the Data Store. Use with caution and understanding.
	 * @return dataStore
	 */
	protected Map<String, DataBean> getDataStore() {
		return dataStore;
	}
	
}
