/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.io;

import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.dataset.ILazyDataset;
import org.eclipse.dawnsci.analysis.api.dataset.Slice;
import org.eclipse.dawnsci.analysis.api.io.IDataHolder;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import uk.ac.diamond.scisoft.analysis.IOTestUtils;

/**
 */
public class MRCImageStackLoaderTest {
	static String TestFileFolder;

	static String testfile;

	@BeforeClass
	static public void setUpClass() {
		TestFileFolder = IOTestUtils.getGDALargeTestFilesLocation();
		TestFileFolder += "MRCImageStackLoaderTest/";
		testfile = TestFileFolder + "May10_15.48.32.mrc";
	}

	@Test
	public void testLoaderFactory() throws Exception {
		IDataHolder dh = LoaderFactory.getData(testfile, null);
		if (dh == null || dh.getNames().length < 1)
			throw new Exception();

		Assert.assertTrue(dh.getName(0).contains(AbstractFileLoader.STACK_NAME));

		ILazyDataset image = dh.getLazyDataset(0);
		checkImage(image);
	}

	/**
	 * Test Loading
	 * 
	 * @throws Exception
	 *             if the test fails
	 */
	@Test
	public void testLoadFile() throws Exception {
		DataHolder dh = new MRCImageStackLoader(testfile).loadFile();

		ILazyDataset image = dh.getLazyDataset(0);
		checkImage(image);
	}

	private void checkImage(ILazyDataset image) throws Exception { // 3838, 3710, 40
		Assert.assertEquals(3, image.getRank());
		Assert.assertEquals(40, image.getShape()[0]);
		Assert.assertEquals(3710, image.getShape()[1]);
		Assert.assertEquals(3838, image.getShape()[2]);

		IDataset subImage;
		subImage = image.getSlice(new Slice(1), null, null);
		Assert.assertEquals(3, subImage.getRank());
		Assert.assertEquals(1, subImage.getShape()[0]);
		Assert.assertEquals(3710, subImage.getShape()[1]);
		Assert.assertEquals(3838, subImage.getShape()[2]);

		subImage = image.getSlice((Slice) null, new Slice(1), null);
		Assert.assertEquals(40, subImage.getShape()[0]);
		Assert.assertEquals(1, subImage.getShape()[1]);
		Assert.assertEquals(3838, subImage.getShape()[2]);

		subImage = image.getSlice((Slice) null, null, new Slice(1));
		Assert.assertEquals(40, subImage.getShape()[0]);
		Assert.assertEquals(3710, subImage.getShape()[1]);
		Assert.assertEquals(1, subImage.getShape()[2]);

		subImage = image.getSlice(new Slice(1), null, new Slice(null, null, 2));
		Assert.assertEquals(1, subImage.getShape()[0]);
		Assert.assertEquals(3710, subImage.getShape()[1]);
		Assert.assertEquals(3838/2, subImage.getShape()[2]);
	}

	@Test
	public void testLoadFile2() throws Exception {
		DataHolder dh = new MRCImageStackLoader(TestFileFolder + "FoilHole_18118581_Data_18120607_18120608_20150618_1751_frames.mrc").loadFile();

		ILazyDataset image = dh.getLazyDataset(0);
		Assert.assertEquals(3, image.getRank());
		Assert.assertEquals(8, image.getShape()[0]);
		Assert.assertEquals(4096, image.getShape()[1]);
		Assert.assertEquals(4096, image.getShape()[2]);

		IDataset subImage;
		subImage = image.getSlice(new Slice(1), null, null);
		Assert.assertEquals(3, subImage.getRank());
		Assert.assertEquals(1, subImage.getShape()[0]);
		Assert.assertEquals(4096, subImage.getShape()[1]);
		Assert.assertEquals(4096, subImage.getShape()[2]);
	}

}
