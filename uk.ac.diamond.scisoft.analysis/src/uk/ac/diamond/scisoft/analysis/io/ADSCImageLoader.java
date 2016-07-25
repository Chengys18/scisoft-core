/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.io;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Vector;

import javax.vecmath.Matrix3d;
import javax.vecmath.Vector3d;

import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.dataset.ILazyDataset;
import org.eclipse.dawnsci.analysis.api.dataset.SliceND;
import org.eclipse.dawnsci.analysis.api.diffraction.DetectorProperties;
import org.eclipse.dawnsci.analysis.api.diffraction.DiffractionCrystalEnvironment;
import org.eclipse.dawnsci.analysis.api.io.IDataHolder;
import org.eclipse.dawnsci.analysis.api.io.ScanFileHolderException;
import org.eclipse.dawnsci.analysis.api.monitor.IMonitor;
import org.eclipse.dawnsci.analysis.dataset.impl.AbstractDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetFactory;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetUtils;
import org.eclipse.dawnsci.analysis.dataset.impl.IntegerDataset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Class to load ADSC images
 */
public class ADSCImageLoader extends AbstractFileLoader {
	private static final String ADSC_IMAGE_NAME = "ADSC Image";

	private static final Logger logger = LoggerFactory.getLogger(ADSCImageLoader.class);

	private HashMap<String, String>      metadataMap    = new HashMap<String, String>();
	private Vector<String> extraHeaders;
	DetectorProperties detectorProperties;
	DiffractionCrystalEnvironment diffractionCrystalEnvironment;
	private boolean keepBitWidth = false;

	private static final String DATE_FORMAT = "EEE MMM dd HH:mm:ss yyyy";


	/**
	 * @return true if loader keeps bit width of pixels
	 */
	public boolean isKeepBitWidth() {
		return keepBitWidth;
	}

	/**
	 * set loader to keep bit width of pixels
	 * 
	 * @param keepBitWidth
	 */
	public void setKeepBitWidth(boolean keepBitWidth) {
		this.keepBitWidth = keepBitWidth;
	}

	public ADSCImageLoader() {
		
	}

	/**
	 * @param FileName
	 */
	public ADSCImageLoader(String FileName) {
		this(FileName, false);
	}

	/**
	 * @param FileName
	 * @param keepBitWidth
	 *            true if loader keeps bit width of pixels
	 */
	public ADSCImageLoader(String FileName, boolean keepBitWidth) {
		setFile(FileName);
		this.keepBitWidth = keepBitWidth;
	}

	@Override
	protected void clearMetadata() {
		metadata = null;
		metadataMap.clear();
	}
	
	@Override
	public DataHolder loadFile() throws ScanFileHolderException {

		DataHolder output = new DataHolder();
		// opens the file and reads the header information
		RandomAccessFile raf = null;
		try {

			raf = new RandomAccessFile(fileName, "r");
			processingMetadata(raf);

		} catch (FileNotFoundException fnf) {
			throw new ScanFileHolderException("File not found", fnf);
		} catch (ScanFileHolderException se) {
			logger.warn("Ignoring problem with metadata in {}", fileName);
		} catch (Exception e) {
			try {
				if (raf != null)
					raf.close();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
			throw new ScanFileHolderException("There was a problem loading or reading metadata", e);
		}
		// Opens the file and reads the byte information and parsed them to doubles.
		try {
			int height = getInteger("SIZE1");
			int width = getInteger("SIZE2");
			final int pointer = getInteger("HEADER_BYTES");

			int[] shape = { height, width };
			ILazyDataset data;
			if (loadLazily) {
				data = createLazyDataset(DEF_IMAGE_NAME, Dataset.INT32, shape, new LazyLoaderStub() {
					@Override
					public IDataset getDataset(IMonitor mon, SliceND slice) throws IOException {
						try {
							Dataset tmp = loadDataset(fileName, slice.getSourceShape(), pointer, keepBitWidth);
							return tmp == null ? null : tmp.getSliceView(slice);
						} catch (ScanFileHolderException e) {
							throw new IOException(e);
						}
					}
				});
			} else {
				raf.seek(pointer);
				data = createDataset(raf, shape, keepBitWidth);
			}
			output.addDataset(ADSC_IMAGE_NAME, data);
			if (loadMetadata) {
				data.setMetadata(getMetadata());
				output.setMetadata(data.getMetadata());
			}
		} catch (Exception e) {
			throw new ScanFileHolderException("There was a problem reading the ADSC image", e);
		} finally {
			try {
				raf.close();
			} catch (IOException e) {
				throw new ScanFileHolderException("Problem closing ADSC file", e);
			}
		}

		return output;
	}

	private static Dataset loadDataset(String fileName, int[] shape, int pointer, boolean keepBitWidth) throws ScanFileHolderException {
		IDataHolder holder = LoaderFactory.fetchData(fileName, false);
		if (holder != null) {
			return DatasetUtils.convertToDataset(holder.getDataset(0));
		}
		RandomAccessFile raf = null;
		try {

			raf = new RandomAccessFile(fileName, "r");
			raf.seek(pointer);

			Dataset data = createDataset(raf, shape, keepBitWidth);
			holder = new DataHolder();
			holder.setLoaderClass(ADSCImageLoader.class);
			holder.setFilePath(fileName);
			holder.addDataset(ADSC_IMAGE_NAME, data);
			LoaderFactory.cacheData(holder);
			return data;
		} catch (FileNotFoundException fnf) {
			throw new ScanFileHolderException("File not found", fnf);
		} catch (Exception e) {
			try {
				if (raf != null)
					raf.close();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
			throw new ScanFileHolderException("There was a problem loading or reading metadata", e);
		}
	}

	private static Dataset createDataset(RandomAccessFile raf, int[] shape, boolean keepBitWidth) throws IOException {
		AbstractDataset data;

		// read in all the data at once for speed.

		byte[] read = new byte[shape[0] * shape[1] * 2];
		raf.read(read);

		// and put it into the dataset
		data = DatasetFactory.zeros(IntegerDataset.class, shape);
		int[] databuf = ((IntegerDataset) data).getData();
		int amax = Integer.MIN_VALUE;
		int amin = Integer.MAX_VALUE;
		int hash = 0;
		for (int i = 0, j = 0; i < databuf.length; i++, j += 2) {
			int value = Utils.leInt(read[j], read[j + 1]);
			hash = (hash * 19 + value);
			databuf[i] = value;
			if (value > amax) {
				amax = value;
			}
			if (value < amin) {
				amin = value;
			}
		}

		if (keepBitWidth||amax < (1 << 15)) {
				data = (AbstractDataset) DatasetUtils.cast(data, Dataset.INT16);
		}

		hash = hash*19 + data.getDType()*17 + data.getElementsPerItem();
		int rank = shape.length;
		for (int i = 0; i < rank; i++) {
			hash = hash*17 + shape[i];
		}
		data.setStoredValue(AbstractDataset.STORE_MAX, amax);
		data.setStoredValue(AbstractDataset.STORE_MIN, amin);
		data.setStoredValue(AbstractDataset.STORE_HASH, hash);

		data.setName(DEF_IMAGE_NAME);
		return data;
	}

	/**
	 * processing all Metadata between { and } tags at the top of the file, put it into a key-value pair map if they are
	 * in "key=value" format. remove ; from line ending
	 * 
	 * @param in
	 * @throws ScanFileHolderException
	 */
	private void processingMetadata(RandomAccessFile in) throws ScanFileHolderException {
		// handling metadata in the file header
		try {
			byte firstChar = in.readByte();
			in.seek(0);
			if (firstChar != '{')
				throw new ScanFileHolderException("This is not a valid ADSC image");
			String line = in.readLine();

			// an updated header reader which ignores all of the header.
			while (!line.contains("{"))
				line = in.readLine();

			while (true) {
				line = in.readLine();
				if (line.contains("}")) {// stop at end of header
					double[] detectorOrigin = { getDouble("BEAM_CENTER_Y"), getDouble("BEAM_CENTER_X"),
							getDouble("DISTANCE") };
//					createGDAMetadata(detectorOrigin);
					createMetadata(detectorOrigin);
					return;
				} else if (line.contains("=")) {
					String[] keyvalue = line.split("=");
					metadataMap.put(keyvalue[0], keyvalue[1].substring(0, keyvalue[1].length() - 1));
				} else {
					extraHeaders.add(line);
				}
			}
		} catch (IOException e) {
			throw new ScanFileHolderException("There was a problem parsing the ADSC header information", e);
		}
	}

//	private void createGDAMetadata(double[] detectorOrigin) throws ScanFileHolderException {
		// NXGeometry:NXtranslation
//		double pixelsize = getDouble("PIXEL_SIZE");
//		double x = getInteger("SIZE1") * pixelsize;
//		double y = getInteger("SIZE2") * pixelsize;
		// bodge since bean centre of diamond ADSC detectors are in a DIFFERENT REFERENCE FRAME!!!!!!!!
//		double[] detectorOrigin = { x - getDouble("BEAM_CENTER_X"), y - getDouble("BEAM_CENTER_Y"),
//				getDouble("DISTANCE") };
//		GDAMetadata.put("NXdetector:NXgeometry:NXtranslation", detectorOrigin);
//		GDAMetadata.put("NXdetector:NXgeometry:NXtranslation@units", "milli*meter");
//
//		// NXGeometery:NXOrientation
//		double[] directionCosine = { 1, 0, 0, 0, 1, 0 }; // to form identity matrix as no header data
//		GDAMetadata.put("NXdetector:NXgeometry:NXorientation", directionCosine);
//		// NXGeometery:XShape (shape from origin (+x, +y, +z,0, 0, 0) > x,y,0,0,0,0)
//		double[] detectorShape = { getDouble("SIZE1") * getDouble("PIXEL_SIZE"),
//				getDouble("SIZE2") * getDouble("PIXEL_SIZE"), 0, 0, 0, 0 };
//		GDAMetadata.put("NXdetector:NXgeometry:NXshape", detectorShape);
//		GDAMetadata.put("NXdetector:NXgeometry:NXshape@units", "milli*metre");
//
//		// NXGeometery:NXFloat
//		double[] pixelSize = { getDouble("PIXEL_SIZE"), getDouble("PIXEL_SIZE") };
//		GDAMetadata.put("NXdetector:x_pixel_size", pixelSize[0]);
//		GDAMetadata.put("NXdetector:x_pixel_size@units", "milli*metre");
//		GDAMetadata.put("NXdetector:y_pixel_size", pixelSize[1]);
//		GDAMetadata.put("NXdetector:y_pixel_size@units", "milli*metre");
//		// "NXmonochromator:wavelength"
//		GDAMetadata.put("NXmonochromator:wavelength", getDouble("WAVELENGTH"));
//		GDAMetadata.put("NXmonochromator:wavelength@units", "Angstrom");
//
//		// oscillation range
//		GDAMetadata.put("NXsample:rotation_start", getDouble("OSC_START"));
//		GDAMetadata.put("NXsample:rotation_start@units", "degree");
//		GDAMetadata.put("NXsample:rotation_range", getDouble("OSC_RANGE"));
//		GDAMetadata.put("NXsample:rotation_range@units", "degree");
//
//		// Exposure time
//		GDAMetadata.put("NXsample:exposure_time", getDouble("TIME"));
//		GDAMetadata.put("NXsample:exposure_time@units", "seconds");
//	}

	private void createMetadata(double[] detectorOrigin) throws ScanFileHolderException {
		// This is new metadata
		Matrix3d identityMatrix = new Matrix3d();
		identityMatrix.setIdentity();
		detectorProperties = new DetectorProperties(new Vector3d(detectorOrigin), getInteger("SIZE1"),
				getInteger("SIZE2"), getDouble("PIXEL_SIZE"), getDouble("PIXEL_SIZE"), identityMatrix);

		diffractionCrystalEnvironment = new DiffractionCrystalEnvironment(getDouble("WAVELENGTH"),
				getDouble("OSC_START"), getDouble("OSC_RANGE"), getDouble("TIME"));

		DiffractionMetadata diffMetadata = new DiffractionMetadata(fileName, detectorProperties, diffractionCrystalEnvironment);
		metadata = diffMetadata;
		diffMetadata.addDataInfo(ADSC_IMAGE_NAME, getInteger("SIZE1"), getInteger("SIZE2"));
		diffMetadata.setMetadata(metadataMap);
		SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
		try {
			Date date = sdf.parse(metadataMap.get("DATE"));
			diffMetadata.setCreationDate(date);
		} catch (ParseException e) {
			throw new ScanFileHolderException("Could not parse the date from the header", e);
		}
	}

	private int getInteger(String key) throws ScanFileHolderException {
		try {
			return Integer.parseInt(getMetadataValue(key).trim());
		} catch (NumberFormatException e) {
			throw new ScanFileHolderException("There was a problem parsing integer value from string",e);
		}
	}

	private double getDouble(String key) throws ScanFileHolderException {
		try {
			return Double.parseDouble(getMetadataValue(key));
		} catch (NumberFormatException e) {
			throw new ScanFileHolderException("There was a problem parsing double value from string",e);
		}
	}

	private String getMetadataValue(String key) throws ScanFileHolderException {
		String v = metadataMap.get(key);
		if (v == null)
			throw new ScanFileHolderException("The keyword " + key + " was not found in the ADSC Header");
		return v;
	}
}
