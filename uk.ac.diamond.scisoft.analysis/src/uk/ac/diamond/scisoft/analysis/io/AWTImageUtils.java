/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.io;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferUShort;
import java.awt.image.PixelInterleavedSampleModel;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.SinglePixelPackedSampleModel;
import java.awt.image.WritableRaster;
import java.util.HashMap;
import java.util.Map;

import javax.media.jai.DataBufferFloat;
import javax.media.jai.PlanarImage;
import javax.media.jai.RasterFactory;
import javax.media.jai.TiledImage;

import org.eclipse.january.dataset.ByteDataset;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.DatasetUtils;
import org.eclipse.january.dataset.DoubleDataset;
import org.eclipse.january.dataset.FloatDataset;
import org.eclipse.january.dataset.IndexIterator;
import org.eclipse.january.dataset.IntegerDataset;
import org.eclipse.january.dataset.RGBDataset;
import org.eclipse.january.dataset.ShortDataset;
import org.eclipse.january.metadata.IMetadata;
import org.eclipse.january.metadata.Metadata;

/**
 * Helper methods to convert to/from AWT images and datasets
 */
public class AWTImageUtils {
	private AWTImageUtils() {
	}

	/**
	 * Create datasets from a Raster
	 * @param r raster
	 * @param data array to output datasets
	 * @param clazz dataset interface
	 */
	public static void createDatasets(Raster r, Dataset[] data, Class <? extends Dataset> clazz) {
		final int bands = data.length;
		final int height = r.getHeight();
		final int width = r.getWidth();
		Dataset tmp;

		for (int i = 0; i < bands; i++) {
			if (FloatDataset.class.isAssignableFrom(clazz)) {
				tmp =  DatasetFactory.createFromObject(r.getSamples(0, 0, width, height, i, (float[]) null), height, width);
			} else if (DoubleDataset.class.isAssignableFrom(clazz)) {
				tmp = DatasetFactory.createFromObject(r.getSamples(0, 0, width, height, i, (double[]) null), height, width);
			} else if (IntegerDataset.class.isAssignableFrom(clazz)) {
				tmp = DatasetFactory.createFromObject(r.getSamples(0, 0, width, height, i, (int[]) null), height, width);
			} else {
				tmp = DatasetFactory.createFromObject(clazz, r.getSamples(0, 0, width, height, i, (int[]) null), height, width);
			}
			data[i] = tmp;
		}
	}

	/**
	 * Get datasets from an image
	 * @param image
	 * @param keepBitWidth if true, then use signed primitives of same bit width for possibly unsigned data
	 * @return array of datasets
	 */
	static public Dataset[] makeDatasets(final BufferedImage image, boolean keepBitWidth) {
		// make raster from buffered image
		final Raster ras = image.getData();
		final SampleModel sm = ras.getSampleModel();
		int dbType = reduceDataBufferType(sm);
		Class<? extends Dataset> clazz = getInterfaceFromDataBufferType(dbType, keepBitWidth);

		final int bands = ras.getNumBands();
		Dataset[] data = new Dataset[bands];

		createDatasets(ras, data, clazz);

		if (dbType == DataBuffer.TYPE_USHORT && !keepBitWidth) {
			for (int i = 0; i < bands; i++) {
				tagIntForShortDataset(data[i]);
			}
		}
		return data;
	}

	private static void tagIntForShortDataset(Dataset ret) {
		final Map<String,String> map = new HashMap<String, String>(1);
		map.put("unsigned.short.data", "true");
		IMetadata metadata = new Metadata();
		metadata.initialize(map);
		ret.setMetadata(metadata);
	}

	/**
	 * Get dataset interface from data buffer type
	 * @param dbtype
	 * @param keepBitWidth
	 * @return dataset interface
	 */
	private static Class<? extends Dataset> getInterfaceFromDataBufferType(final int dbtype, boolean keepBitWidth) {
		switch (dbtype) {
		case DataBuffer.TYPE_BYTE:
			return keepBitWidth ? ByteDataset.class : ShortDataset.class;
		case DataBuffer.TYPE_SHORT:
			return ShortDataset.class;
		case DataBuffer.TYPE_USHORT:
			return keepBitWidth ? ShortDataset.class : IntegerDataset.class;
		case DataBuffer.TYPE_INT:
			return IntegerDataset.class;
		case DataBuffer.TYPE_FLOAT:
			return FloatDataset.class;
		case DataBuffer.TYPE_DOUBLE:
			return DoubleDataset.class;
		}

		return null;
	}

	private static int reduceDataBufferType(final SampleModel sm) {
		int dbtype = sm.getDataType();
		final int bits = sm.getSampleSize(0);
		if (dbtype == DataBuffer.TYPE_INT) {
			if (bits <= 8) {
				dbtype = DataBuffer.TYPE_BYTE;
			} else if (bits <= 16) {
				dbtype = DataBuffer.TYPE_SHORT;
			}
		}
		if (dbtype == DataBuffer.TYPE_USHORT) {
			if (bits < 8) {
				dbtype = DataBuffer.TYPE_BYTE;
			} else if (bits < 16) {
				dbtype = DataBuffer.TYPE_SHORT;
			}
		}
		if (dbtype == DataBuffer.TYPE_SHORT) {
			if (bits <= 8) {
				dbtype = DataBuffer.TYPE_BYTE;
			}
		}
		return dbtype;
	}

	/**
	 * Get dataset interface from given sample model
	 * @param sm
	 * @param keepBitWidth
	 * @return dataset interface
	 */
	public static Class<? extends Dataset> getInterface(final SampleModel sm, boolean keepBitWidth) {
		return getInterfaceFromDataBufferType(reduceDataBufferType(sm), keepBitWidth);
	}

	/**
	 * Get image from a dataset
	 * @param data
	 * @param bits number of bits (<=16 for non-RGB datasets)
	 * @return buffered image
	 */
	static public BufferedImage makeBufferedImage(final Dataset data, final int bits) {
		final int[] shape = data.getShape();
		if (shape.length > 2) {
			throw new IllegalArgumentException("Rank of data must be less than or equal to two");
		}

		final int height = shape[0];
		final int width = shape.length == 1 ? 1 : shape[1]; // allow 1D datasets to be saved

		final int size = data.getSize();
		BufferedImage image = null;

		if (data instanceof RGBDataset) {
			RGBDataset rgbds = (RGBDataset) data;

			image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

			short maxv = rgbds.max().shortValue();
			final IndexIterator iter = rgbds.getIterator(true);
			final int[] pos = iter.getPos();
			final short[] rgbdata = rgbds.getData();
			if (maxv < 256) { // 888
				while (iter.hasNext()) {
					final int n = iter.index;
					final int rgb = ((rgbdata[n] & 0xff) << 16) | ((rgbdata[n + 1] & 0xff) << 8) | (rgbdata[n + 2] & 0xff);
					image.setRGB(pos[1], pos[0], rgb);
				}			
			} else {
				int shift = 0;
				while (maxv >= 256) {
					shift++;
					maxv >>= 2;
				}

				while (iter.hasNext()) {
					final int n = iter.index;
					final int rgb = (((rgbdata[n] >> shift) & 0xff) << 16) | (((rgbdata[n + 1] >> shift) & 0xff) << 8) | ((rgbdata[n + 2] >> shift) & 0xff);
					image.setRGB(pos[1], pos[0], rgb);
				}			
			}
		} else {
			DataBuffer buffer = null;
			SampleModel sampleModel = null;
			// reconcile data with output format

			// populate data buffer using sample model
			IntegerDataset tmp = DatasetUtils.cast(IntegerDataset.class, data);

			if (bits <= 8) {
				buffer = new DataBufferByte(size);
				sampleModel = new PixelInterleavedSampleModel(DataBuffer.TYPE_BYTE, width, height, 1, width,
						new int[] { 0 });
				image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);

				sampleModel.setPixels(0, 0, width, height, tmp.getData(), buffer);
			} else if (bits <= 16) {
				buffer = new DataBufferUShort(size);
				sampleModel = new PixelInterleavedSampleModel(DataBuffer.TYPE_USHORT, width, height, 1,
						width, new int[] { 0 });
				image = new BufferedImage(width, height, BufferedImage.TYPE_USHORT_GRAY);

				sampleModel.setPixels(0, 0, width, height, tmp.getData(), buffer);
			} else {
				throw new IllegalArgumentException("Number of bits must be less than or equal to 16");
			}

			WritableRaster wRas = Raster.createWritableRaster(sampleModel, buffer, null);
			image.setData(wRas);
		}

		return image;
	}

	/**
	 * Get image from a dataset
	 * @param data
	 * @param bits number of bits (> 32 for float image)
	 * @return tiled image
	 */
	static public TiledImage makeTiledImage(final Dataset data, final int bits) {
		final int[] shape = data.getShape();
		int height = shape[0];
		int width = shape.length == 1 ? 1 : shape[1]; // allow 1D datasets to be saved
		final int size = data.getSize();

		SampleModel sampleModel;
		DataBuffer buffer;

		if (data instanceof RGBDataset) {
			RGBDataset rgbds = (RGBDataset) data;

			buffer = new DataBufferInt(size);
			sampleModel = new SinglePixelPackedSampleModel(DataBuffer.TYPE_INT, width, height, new int[] { 0xff0000, 0x00ff00, 0x0000ff} );

			short maxv = rgbds.max().shortValue();
			final IndexIterator iter = rgbds.getIterator(true);
			final int[] pos = iter.getPos();
			final short[] rgbdata = rgbds.getData();

			if (maxv < 256) { // 888
				while (iter.hasNext()) {
					final int n = iter.index;
					final int rgb = ((rgbdata[n] & 0xff) << 16) | ((rgbdata[n + 1] & 0xff) << 8) | (rgbdata[n + 2] & 0xff);
					sampleModel.setSample(pos[1], pos[0], 0, rgb, buffer);
				}			
			} else {
				int shift = 0;
				while (maxv >= 256) {
					shift++;
					maxv >>= 2;
				}

				while (iter.hasNext()) {
					final int n = iter.index;
					final int rgb = (((rgbdata[n] >> shift) & 0xff) << 16) | (((rgbdata[n + 1] >> shift) & 0xff) << 8) | ((rgbdata[n + 2] >> shift) & 0xff);
					sampleModel.setSample(pos[1], pos[0], 0, rgb, buffer);
				}			
			}

		} else {
			// reconcile data with output format

			// populate data buffer using sample model
			if (bits <= 32) {
				buffer = new DataBufferInt(size);
				sampleModel = RasterFactory.createBandedSampleModel(DataBuffer.TYPE_INT,
								width, height, 1);

				IntegerDataset tmp = data.cast(IntegerDataset.class);
				sampleModel.setPixels(0, 0, width, height, tmp.getData(), buffer);
			} else { // Only TIFF supports floats (so far)
				buffer = new DataBufferFloat(size);
				sampleModel = RasterFactory.createBandedSampleModel(DataBuffer.TYPE_FLOAT,
								width, height, 1);

				FloatDataset ftmp = data.cast(FloatDataset.class);
				sampleModel.setPixels(0, 0, width, height, ftmp.getData(), buffer);
			}

		}

		WritableRaster wRas = Raster.createWritableRaster(sampleModel, buffer, null);
		ColorModel cm = PlanarImage.createColorModel(sampleModel);
		TiledImage timage = new TiledImage(0, 0, width, height, 0, 0, sampleModel, cm);
		timage.setData(wRas);
		return timage;
	}
	
}
