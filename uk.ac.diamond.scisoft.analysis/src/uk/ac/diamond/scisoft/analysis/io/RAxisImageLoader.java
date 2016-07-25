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
import java.io.Serializable;
import java.util.HashMap;

import javax.vecmath.Vector3d;

import org.eclipse.dawnsci.analysis.api.diffraction.DetectorProperties;
import org.eclipse.dawnsci.analysis.api.diffraction.DiffractionCrystalEnvironment;
import org.eclipse.dawnsci.analysis.api.io.ScanFileHolderException;
import org.eclipse.january.dataset.AbstractDataset;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.DatasetUtils;
import org.eclipse.january.dataset.ILazyDataset;
import org.eclipse.january.dataset.IntegerDataset;

/**
 * Class to load Rigaku images. Class returns a DataHolder that is called from the ScanFileHolder class.
 */
public class RAxisImageLoader extends AbstractFileLoader implements Serializable {

	private HashMap<String, Serializable> metadataMap = new HashMap<String, Serializable>();
	private HashMap<String, Serializable> GDAMetadata = new HashMap<String, Serializable>();
	private boolean keepBitWidth = false;

	private static final String DATA_NAME = "RAxis Image";

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

	public RAxisImageLoader() {

	}

	/**
	 * @param FileName
	 */
	public RAxisImageLoader(String FileName) {
		this(FileName, false);
	}

	/**
	 * @param FileName
	 * @param keepBitWidth
	 *            true if loader keeps bit width of pixels
	 */
	public RAxisImageLoader(String FileName, boolean keepBitWidth) {
		setFile(FileName);
		this.keepBitWidth = keepBitWidth;
	}

	@Override
	public void clearMetadata() {
		metadata = null;
		metadataMap.clear();
		GDAMetadata.clear();
	}

	@Override
	public DataHolder loadFile() throws ScanFileHolderException {

		DataHolder output = new DataHolder();
		// opens the file and reads the header information
		RandomAccessFile raf = null;
		try {
			raf = new RandomAccessFile(fileName, "r");
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
		processingMetadata(raf);
		int[] shape = { toInt("nSlow"), toInt("nFast")};
		double st = toDouble("phistart");
		double[] origin = createGDAMetadata(shape[1], shape[0], st);
		createMetadata(origin, shape[1], shape[0], st);

		try {

			ILazyDataset data;
			if (loadLazily) {
				data = createLazyDataset(DEF_IMAGE_NAME, DATA_NAME, -1, shape, new RAxisImageLoader(fileName));
			} else {
				data = readDataset(shape, raf);
				data.setName(DEF_IMAGE_NAME);
			}

			output.addDataset(DATA_NAME, data);
			if (loadMetadata) {
				data.setMetadata(getMetadata());
				output.setMetadata(data.getMetadata());
			}
		} catch (Exception e) {
			throw new ScanFileHolderException("There was a problem reading the RAxis image", e);
		} finally {
			try {
				raf.close();
			} catch (IOException e) {
				throw new ScanFileHolderException("Problem closing RAxis file", e);
			}
		}

		return output;
	}

	private ILazyDataset readDataset(int[] shape, RandomAccessFile raf) throws IOException {
		byte[] read = new byte[shape[0] * shape[1] * 2];
		
		raf.read(read); // read in all the data at once for speed.

		// and put it into the dataset
		AbstractDataset data = DatasetFactory.zeros(IntegerDataset.class, shape);
		int[] databuf = ((IntegerDataset) data).getData();
		int amax = Integer.MIN_VALUE;
		int amin = Integer.MAX_VALUE;
		int hash = 0;
		for (int i = 0, j = 0; i < databuf.length; i++) {
			int value = Utils.beInt(read[j++], read[j++]);
			hash = hash * 19 + value;
			databuf[i] = value;
			if (value > amax) {
				amax = value;
			}
			if (value < amin) {
				amin = value;
			}
	
		}
		if (keepBitWidth || amax < (1 << 15)) {
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

		return data;
	}

	/**
	 * processing all Metadata between { and } tags at the top of the file, put it into a key-value pair map if they are
	 * in "key=value" format. remove ; from line ending
	 * 
	 * @param raf
	 * @throws ScanFileHolderException
	 */
	private void processingMetadata(RandomAccessFile raf) throws ScanFileHolderException {
		// handling metadata in the file header
		try {
			byte[] b = new byte[10];
			raf.readFully(b);
			metadataMap.put("Device", new String(b).trim());
			// this is a really crude way of figuring out if the binary files
			// is from a RAxis IP
			if (!new String(b).trim().contains("RAXIS"))
				throw new ScanFileHolderException("This image does not appear to be an RAxis image");

			b = new byte[10];
			raf.readFully(b);
			metadataMap.put("Version", new String(b).trim());

			b = new byte[20];
			raf.readFully(b);
			metadataMap.put("Crystal", new String(b).trim());

			b = new byte[12];
			raf.readFully(b);
			metadataMap.put("Crystal system", new String(b).trim());

			raf.skipBytes(24); // this is supposed to be a b c al be ga

			b = new byte[12];
			raf.readFully(b);
			metadataMap.put("SpaceGroup", new String(b).trim());

			metadataMap.put("mosaic1", raf.readFloat());

			b = new byte[80];
			raf.readFully(b);
			metadataMap.put("memo", new String(b).trim());

			b = new byte[84];
			raf.readFully(b);
			metadataMap.put("reserve1", new String(b).trim());

			b = new byte[12];
			raf.readFully(b);
			metadataMap.put("date", new String(b).trim());

			b = new byte[20];
			raf.readFully(b);
			metadataMap.put("operatorname", new String(b).trim());

			b = new byte[4];
			raf.readFully(b);
			metadataMap.put("target", new String(b).trim());

			// ('wavelength',4,'!f'),
			metadataMap.put("wavelength", raf.readFloat());

			// ('monotype',20,'s'),
			b = new byte[20];
			raf.readFully(b);
			metadataMap.put("monotype", new String(b).trim());

			// ('mono2theta',4,'!f'),
			metadataMap.put("mono2theta", raf.readFloat());

			// ('collimator',20,'s'),
			b = new byte[20];
			raf.readFully(b);
			metadataMap.put("collimator", new String(b).trim());

			// ('filter',4,'s'),
			b = new byte[4];
			raf.readFully(b);
			metadataMap.put("filter", new String(b).trim());

			// ('distance',4,'!f'),
			metadataMap.put("distance", raf.readFloat());

			// ('Kv',4,'!f'),
			metadataMap.put("Kv", raf.readFloat());

			// ('mA',4,'!f'),
			metadataMap.put("mA", raf.readFloat());

			// ('focus',12,'s'),
			b = new byte[12];
			raf.readFully(b);
			metadataMap.put("focus", new String(b).trim());

			// ('Xmemo',80,'s'),
			b = new byte[80];
			raf.readFully(b);
			metadataMap.put("Xmemo", new String(b).trim());

			// ('cyl',4,'!i'),
			metadataMap.put("cyl", raf.readInt());

			// (None,60),
			raf.skipBytes(60);

			// ('Spindle',4,'s'), # Crystal mount axis closest to spindle axis
			b = new byte[4];
			raf.readFully(b);
			metadataMap.put("Spindle", new String(b).trim());

			// ('Xray_axis',4,'s'), # Crystal mount axis closest to beam axis
			b = new byte[4];
			raf.readFully(b);
			metadataMap.put("Xray_axis", new String(b).trim());

			// ('phidatum',4,'!f'),
			metadataMap.put("phidatum", raf.readFloat());

			// ('phistart',4,'!f'),
			metadataMap.put("phistart", raf.readFloat());

			// ('phiend',4,'!f'),
			metadataMap.put("phiend", raf.readFloat());

			// ('noscillations',4,'!i'),
			metadataMap.put("noscillations", raf.readInt());

			// ('minutes',4,'!f'), # Exposure time in minutes?
			metadataMap.put("minutes", raf.readFloat());

			// ('beampixels_x',4,'!f'),
			metadataMap.put("beampixels_x", raf.readFloat());

			// ('beampixels_y',4,'!f'), # Direct beam position in pixels
			metadataMap.put("beampixels_y", raf.readFloat());

			// ('omega',4,'!f'),
			metadataMap.put("omega", raf.readFloat());

			// ('chi',4,'!f'),
			metadataMap.put("chi", raf.readFloat());

			// ('twotheta',4,'!f'),
			metadataMap.put("twotheta", raf.readFloat());
			// ('Mu',4,'!f'), # Spindle inclination angle?

			metadataMap.put("Mu", raf.readFloat());
			// ('ScanTemplate',204,'s'), # This space is now used for storing the scan
			// # templates information
			b = new byte[204];
			raf.readFully(b);
			metadataMap.put("ScanTemplate", new String(b).trim());

			// ('nFast',4,'!i'),
			metadataMap.put("nFast", raf.readInt());

			// ('nSlow',4,'!i'), # Number of fast, slow pixels
			metadataMap.put("nSlow", raf.readInt());

			// ('sizeFast',4,'!f'),
			metadataMap.put("sizeFast", raf.readFloat());

			// ('sizeSlow',4,'!f'), # Size of fast, slow direction in mm
			metadataMap.put("sizeSlow", raf.readFloat());

			// ('record_length',4,'!i'), # Record length in bytes
			metadataMap.put("record_length", raf.readInt());

			// ('number_records',4,'!i'), # number of records
			metadataMap.put("number_records", raf.readInt());

			// ('Read_start',4,'!i'), # For partial reads, 1st read line
			metadataMap.put("Read_start", raf.readInt());

			// ('IP_num',4,'!i'), # Which imaging plate 1, 2 ?
			metadataMap.put("IP_num", raf.readInt());

			// ('Ratio',4,'!f'), # Output ratio for high value pixels
			metadataMap.put("Ratio", raf.readFloat());

			// ('Fading_start',4,'!f'), # Fading time to start of read
			metadataMap.put("Fading_start", raf.readFloat());

			// ('Fading_end',4,'!f'), # Fading time to end of read
			metadataMap.put("Fading_end", raf.readFloat());

			// ('computer',10,'s'), # Type of computer "IRIS", "VAX", "SUN", etc
			b = new byte[10];
			raf.readFully(b);
			metadataMap.put("computer", new String(b).trim());

			// ('plate_type',10,'s'), # Type of IP
			b = new byte[10];
			raf.readFully(b);
			metadataMap.put("plate_type", new String(b).trim());

			// ('Dr',4,'!i'),
			metadataMap.put("Dr", raf.readInt());

			// ('Dx',4,'!i'),
			metadataMap.put("Dx", raf.readInt());

			// ('Dz',4,'!i'), # IP scanning codes??
			metadataMap.put("Dz", raf.readInt());

			// ('PixShiftOdd',4,'!f'), # Pixel shift to odd lines
			metadataMap.put("PixShiftOdd", raf.readFloat());

			// ('IntRatioOdd',4,'!f'), # Intensity ratio to odd lines
			metadataMap.put("IntRatioOdd", raf.readFloat());

			// ('MagicNum',4,'!i'), # Magic number to indicate next values are legit
			metadataMap.put("MagicNum'", raf.readInt());

			// ('NumGonAxes',4,'!i'), # Number of goniometer axes
			metadataMap.put("NumGonAxes'", raf.readInt());

			// ('a5x3fGonVecs',60,'!fffffffffffffff'),# Goniometer axis vectors
			float[] fa = { raf.readFloat(), raf.readFloat(), raf.readFloat(), raf.readFloat(), raf.readFloat(),
					raf.readFloat(), raf.readFloat(), raf.readFloat(), raf.readFloat(), raf.readFloat(),
					raf.readFloat(), raf.readFloat(), raf.readFloat(), raf.readFloat(), raf.readFloat(), };
			metadataMap.put("a5x3fGonVecs", fa);

			// ('a5fGonStart',20,'!fffff'),# Start angles for each of 5 axes
			float[] fb = { raf.readFloat(), raf.readFloat(), raf.readFloat(), raf.readFloat(), raf.readFloat() };
			metadataMap.put("a5x3fGonStart", fb);

			// ('a5fGonEnd',20,'!fffff'), # End angles for each of 5 axes
			float[] fc = { raf.readFloat(), raf.readFloat(), raf.readFloat(), raf.readFloat(), raf.readFloat() };
			metadataMap.put("a5fGonEnd", fc);

			// ('a5fGonOffset',20,'!fffff'),# Offset values for each of 5 axes
			float[] fd = { raf.readFloat(), raf.readFloat(), raf.readFloat(), raf.readFloat(), raf.readFloat() };
			metadataMap.put("a5fGonOffset", fd);

			// ('ScanAxisNum',4,'!i'), # Which axis is the scan axis?
			metadataMap.put("ScanAxisNum", raf.readInt());

			// ('AxesNames',40,'s'), # Names of the axes (space or comma separated?)'''
			b = new byte[10];
			raf.readFully(b);
			metadataMap.put("ScanAxisNum", new String(b).trim());

			raf.skipBytes((int) (toInt("record_length") - raf.getFilePointer()));

		} catch (IOException e) {
			throw new ScanFileHolderException("There was a problem parsing the RAxis header information", e);
		}
	}

	private int toInt(String key) {
		return (Integer) metadataMap.get(key);
	}

	private double toDouble(String key) {
		return ((Float) metadataMap.get(key)).doubleValue();
	}

	private double[] createGDAMetadata(int nx, int ny, double st) throws ScanFileHolderException {
		try {

			// NXGeometery:NXtranslation

			double x = nx - (nx - toDouble("beampixels_x")) * toDouble("sizeFast");
			double y = ny - (ny - toDouble("beampixels_y")) * toDouble("sizeSlow");
			double[] detectorOrigin = { x, y, toDouble("distance") };
			GDAMetadata.put("NXdetector:NXgeometry:NXtranslation", detectorOrigin);
			GDAMetadata.put("NXdetector:NXgeometry:NXtranslation@units", "milli*meter");

			// NXGeometery:NXOrientation
			double[] directionCosine = { 1, 0, 0, 0, 1, 0 }; // to form identity matrix as no header data
			GDAMetadata.put("NXdetector:NXgeometry:NXorientation", directionCosine);
			// NXGeometery:XShape (shape from origin (+x, +y, +z,0, 0, 0) > x,y,0,0,0,0)
			double[] detectorShape = { nx * toDouble("sizeFast"),
					ny * toDouble("sizeSlow"), 0, 0, 0, 0 };
			GDAMetadata.put("NXdetector:NXgeometry:NXshape", detectorShape);
			GDAMetadata.put("NXdetector:NXgeometry:NXshape@units", "milli*metre");

			// NXGeometery:NXFloat
			double[] pixelSize = { toDouble("sizeFast"), toDouble("sizeSlow") };
			GDAMetadata.put("NXdetector:x_pixel_size", pixelSize[0]);
			GDAMetadata.put("NXdetector:x_pixel_size@units", "milli*metre");
			GDAMetadata.put("NXdetector:y_pixel_size", pixelSize[1]);
			GDAMetadata.put("NXdetector:y_pixel_size@units", "milli*metre");
			// "NXmonochromator:wavelength"
			GDAMetadata.put("NXmonochromator:wavelength", toDouble("wavelength"));
			GDAMetadata.put("NXmonochromator:wavelength@units", "Angstrom");

			// oscillation range
			GDAMetadata.put("NXsample:rotation_start", st);
			GDAMetadata.put("NXsample:rotation_start@units", "degree");
			GDAMetadata.put("NXsample:rotation_range", toDouble("phiend") - st);
			GDAMetadata.put("NXsample:rotation_range@units", "degree");

			// Exposure time
			GDAMetadata.put("NXsample:exposure_time", toDouble("minutes") * 60);
			GDAMetadata.put("NXsample:exposure_time@units", "seconds");
			return detectorOrigin;
		} catch (Exception e) {
			throw new ScanFileHolderException("There was a problem creating the GDA metatdata", e);
		}
	}

	private void createMetadata(double[] detectorOrigin, int nx, int ny, double st) {
		DetectorProperties detProps = new DetectorProperties(new Vector3d(detectorOrigin), ny, nx, toDouble("sizeSlow"), toDouble("sizeFast"), null);
		DiffractionCrystalEnvironment diffEnv = new DiffractionCrystalEnvironment(toDouble("wavelength"), st, toDouble("phiend"), toDouble("minutes") * 60);

		metadata = new DiffractionMetadata(fileName, detProps, diffEnv);
		HashMap<String, Serializable> md = new HashMap<String, Serializable>();
		md.putAll(metadataMap);
		md.putAll(GDAMetadata);
		metadata.setMetadata(md);
		metadata.addDataInfo(DATA_NAME, ny, nx);
	}
}
