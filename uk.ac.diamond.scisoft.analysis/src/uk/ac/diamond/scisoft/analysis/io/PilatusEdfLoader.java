/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.io;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.dawnsci.analysis.api.io.ScanFileHolderException;
import org.eclipse.january.IMonitor;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.FloatDataset;
import org.eclipse.january.dataset.ILazyDataset;
import org.eclipse.january.dataset.IntegerDataset;
import org.eclipse.january.metadata.Metadata;

/**
 * This class should be used to load ESRF datafiles created by the Pilatus detector system
 * into the ScanFileHolder object. This has not been tested on general ESRF datafiles.
 * <p>
 * <b>Note</b>: the header data from this loader is left as strings
 */
public class PilatusEdfLoader extends AbstractFileLoader {

	private Map<String, String> textMetadata = new HashMap<String, String>();
	public static final String DATA_NAME = "EDF";

	/**
	 * @param fileName
	 */
	public PilatusEdfLoader(String fileName) {
		this.fileName = fileName;
	}

	@Override
	protected void clearMetadata() {
		metadata = null;
		textMetadata.clear();
	}

	@Override
	public DataHolder loadFile() throws ScanFileHolderException {
		return loadFile(null);
	}
	
	@Override
	public DataHolder loadFile(IMonitor mon) throws ScanFileHolderException {
		ILazyDataset data = null;
		final DataHolder output = new DataHolder();
		File f = null;
		FileInputStream fi = null;
		BufferedReader br = null;
		try {

			f = new File(fileName);
			fi = new FileInputStream(f);

			br = new BufferedReader(new FileReader(f));
			String line = br.readLine();
			if (line == null)
				throw new ScanFileHolderException("No lines found");

			// If the first line is not a { then we fail this loader.
			if (!line.trim().startsWith("{")) throw new ScanFileHolderException("EDF File should start with {"); 
			
			if (line.contains("{")) {
				
				// Read the meta data
				int index = readMetaData(br, line.length()+1, mon);
				
				// Now read the data
				int[] shape = new int[] { Integer.parseInt(textMetadata.get("Dim_2")),
						Integer.parseInt(textMetadata.get("Dim_1"))};
				String dataType = textMetadata.get("DataType");
				if (loadLazily) {
					// This does not provide support for 64-bit datatypes, which may be present in EDF files.
					data = createLazyDataset(DEF_IMAGE_NAME, DATA_NAME, dataType.equals("Float") ? Dataset.FLOAT32 : Dataset.INT32,
							shape, new PilatusEdfLoader(fileName));
				} else {
					boolean le = "LowByteFirst".equals(textMetadata.get("ByteOrder"));
					boolean signed = dataType.startsWith("Signed");
					// this EDF loader supports only a subset of all possible datatypes
					// an exception will be thrown when an unsupported datatype is encountered
					if (dataType.equals("Float") || dataType.equals("FloatValue")) {
						data = DatasetFactory.zeros(FloatDataset.class, shape);
						if (le) {
							Utils.readLeFloat(fi, (FloatDataset) data, index);
						}
						else {
							Utils.readBeFloat(fi, (FloatDataset) data, index);
						}
					} 
					else if (dataType.contains("Short")) {
						// 16 bit integers
						data = DatasetFactory.zeros(IntegerDataset.class, shape);
						if (le)
							Utils.readLeShort(fi, (IntegerDataset) data, index, signed);
						else
							Utils.readBeShort(fi, (IntegerDataset) data, index, signed);
					} 
					else if (dataType.contains("Long") || dataType.contains("Integer")) {
						// 32 bit integers, signed ONLY
						if (!signed) {
							throw new ScanFileHolderException("32-bit unsigned integers are currently not supported");
						}
						data = DatasetFactory.zeros(IntegerDataset.class, shape);
						if (le)
							Utils.readLeInt(fi, (IntegerDataset) data, index);
						else
							Utils.readBeInt(fi, (IntegerDataset) data, index);
					}
					else {
						// unsupported data type exception
						// expect this when datatype is byte, 64-bit integer or 64-bit float
						throw new ScanFileHolderException("Unknown EDF datatype " + dataType);
					}
					data.setName(DEF_IMAGE_NAME);
				}
			}
		} catch (Exception e) {
			throw new ScanFileHolderException("File failed to load " + fileName, e);
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					// do nothing
				}
			}
			if (fi != null) {
				try {
					fi.close();
				} catch (IOException ex) {
					// do nothing
				}
				fi = null;
			}
		}
		if (data != null) {
			output.addDataset(DATA_NAME, data);
			if (loadMetadata) {
				createMetadata();
				data.setMetadata(metadata);
				output.setMetadata(metadata);
			}
		}
		return output;
	}

	private void createMetadata() {
		metadata = new Metadata();
		metadata.initialize(textMetadata);
		metadata.setFilePath(fileName);
		metadata.addDataInfo(DATA_NAME, Integer.parseInt(textMetadata.get("Dim_2")),
				Integer.parseInt(textMetadata.get("Dim_1")));
	}

	private int readMetaData(final BufferedReader br, int index, final IMonitor mon) throws Exception {
		
		textMetadata.clear();
		while (true) {
			if (!monitorIncrement(mon)) {
				throw new ScanFileHolderException("Loader cancelled during reading!");
			}
			
			String line = br.readLine();
			if (line == null) {
				throw new ScanFileHolderException("No closing brace found");
			}
			index += line.length()+1;
			if (line.contains("}")) {
				break;
			}
			String[] keyvalue = line.split("=");
				
			if (keyvalue.length == 1) {
				textMetadata.put(keyvalue[0].trim(), "");
			} else {		
				int len = (keyvalue[1].endsWith(";")) ? keyvalue[1].length()-1 : keyvalue[1].length();
				String value = keyvalue[1].substring(0, len);
				textMetadata.put(keyvalue[0].trim(), value.trim());
			}
		}
		
		return index;
	}
}
