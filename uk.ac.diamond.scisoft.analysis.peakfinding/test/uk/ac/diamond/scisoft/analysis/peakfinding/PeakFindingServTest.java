package uk.ac.diamond.scisoft.analysis.peakfinding;

//import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import uk.ac.diamond.scisoft.analysis.peakfinding.peakfinders.DummyPeakFinder;

public class PeakFindingServTest {
	
	/**
	 * This has been heavily based on OperationsTest (u.a.d.s.a.processing.test).
	 * The role of this class is to test with JUnit, therefore not using extension points
	 */
	
	private static IPeakFindingService peakFindServ;
	private String dummyID = DummyPeakFinder.class.getName();
	
	@BeforeClass
	public static void setupNonOSGiService() throws Exception {
		peakFindServ = (IPeakFindingService)Activator.getService(IPeakFindingService.class);
		
		//Grab the all the PeakFinders in u.a.d.s.a.peakfinding.peakfinders
		peakFindServ.addPeakFindersByClass(peakFindServ.getClass().getClassLoader(), "uk.ac.diamond.scisoft.analysis.peakfinding.peakfinders");
		
	}
	
	@Rule
	public ExpectedException thrower = ExpectedException.none();
	
	@Test
	public void testGetService() {
		assertNotNull(peakFindServ);
	}
	
	@Test
	public void testServiceHasPeakFinders() throws Exception {
		final Collection<String> peakFinderNames = peakFindServ.getRegisteredPeakFinders();
		assertNotNull(peakFinderNames);
		assertFalse(peakFinderNames.isEmpty());
	}
	
	@Test
	public void testActivePeakFinderExceptions() throws Exception {
		thrower.expect(IllegalArgumentException.class);
		thrower.expectMessage("not active");
		thrower.expectMessage("already active");
	
		peakFindServ.deactivatePeakFinder(dummyID);
		peakFindServ.deactivatePeakFinder(dummyID);	
		peakFindServ.activatePeakFinder(dummyID);
		peakFindServ.activatePeakFinder(dummyID);
	}
	
	@Test
	public void testActivatePeakFinders() throws Exception {
		//Resources for test
		Set<String> activePFs;
		Iterator<String> activePFIter;
		
		peakFindServ.activatePeakFinder(dummyID);
		
		activePFs = (Set<String>) peakFindServ.getActivePeakFinders();
		activePFIter = activePFs.iterator();
		boolean gotDummy = false;
		while (activePFIter.hasNext()) {
			String pfID = activePFIter.next();
			if (pfID.equals(dummyID)) gotDummy = true;
		}
		assertTrue(gotDummy);
		
		peakFindServ.deactivatePeakFinder(dummyID);
		activePFs = (Set<String>) peakFindServ.getActivePeakFinders();
		activePFIter = activePFs.iterator();
		while (activePFIter.hasNext()) {
			String pfID = activePFIter.next();
			if (pfID.equals(dummyID)) gotDummy = true;
		}
		assertFalse(gotDummy);	
		}
	
	@Test
	public void testOnePeakFinder() throws Exception {
		final Set<String> peakFinderIDs = (Set<String>) peakFindServ.getRegisteredPeakFinders();
		
		Map<Integer, Double>testData = new TreeMap<Integer, Double>();
		testData.put(1, 0.6);
		testData.put(2, 1.2);
		testData.put(3, 0.9);
		testData.put(5, 1.7);
		testData.put(7, 2.5);
		testData.put(11, 0.9);
		testData.put(13, 0.6);
		
		//Find the dummy peakfinder
		String dummyPFClassName = DummyPeakFinder.class.getName();
		if (!dummyPFClassName.contains(dummyPFClassName)) {
			fail("Dummy peak finder not registered");
		}
//		IPeakFinder testPF = peakFindServ.getPeakFinder(dummyPFClassName);
//		Map<Integer, Double> peakPosnsSigs = testPF.findPeaks(null, null, null);
//		assertEquals(testData, peakPosnsSigs);
	}

}
