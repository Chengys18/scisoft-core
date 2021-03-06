/*-
 * Copyright (c) 2015 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.applications;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.eclipse.dawnsci.analysis.api.io.ScanFileHolderException;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import uk.ac.diamond.scisoft.analysis.diffraction.MillerSpaceMapper;
import uk.ac.diamond.scisoft.analysis.diffraction.MillerSpaceMapper.MillerSpaceMapperBean;

/**
 * Used to execute the Miller Space mapper
 * 
 * Command line to run the application from a DAWN install
 * <pre>
 *    module load dawn/nightly; $DAWN_RELEASE_DIRECTORY/dawn -consoleLog -noSplash -application uk.ac.diamond.scisoft.applications.msmapper -data @none -bean JSONbean >> someLogFile.txt
 * </pre>
 * where JSONbean is a file containing the JSON representation of a {@link MillerSpaceMapperBean} 
 */
public class MillerSpaceMapperApplication implements IApplication {

	@Override
	public Object start(IApplicationContext context) throws Exception {
		Map<?,?> map = context.getArguments();
		String[] args = (String[]) map.get("application.args");

		
		return main(args);
	}

	@Override
	public void stop() {
		// do nothing
	}

	private static final String BEAN_PATH = "-bean";

	private Integer main(String[] args) {

		try {
			int n = args.length;
			int i = 0;
			String path = null;
			for (; i < n; i++) {
				String a = args[i];
				if (a.equals(BEAN_PATH)) {
					path  = args[i+1];
					break;
				}
			}
			if (path == null) {
				throw new IllegalArgumentException("There must be a bean argument in the command line arguments");
			}
			ObjectMapper mapper = new ObjectMapper();
			MillerSpaceMapperBean bean = mapper.readValue(new File(path), MillerSpaceMapperBean.class);

			MillerSpaceMapper msm = new MillerSpaceMapper(bean);
			msm.mapToVolumeFile();
			return IApplication.EXIT_OK;
		} catch (JsonParseException e) {
			e.printStackTrace();
			throw new IllegalArgumentException("Could not parse JSON file", e);
		} catch (JsonMappingException e) {
			e.printStackTrace();
			throw new IllegalArgumentException("Could not generate bean from JSON file", e);
		} catch (IOException e) {
			e.printStackTrace();
			throw new IllegalArgumentException("Could not read JSON file", e);
		} catch (ScanFileHolderException e) {
			e.printStackTrace();
			throw new IllegalArgumentException("Could not process volume", e);
		}
	}
}
