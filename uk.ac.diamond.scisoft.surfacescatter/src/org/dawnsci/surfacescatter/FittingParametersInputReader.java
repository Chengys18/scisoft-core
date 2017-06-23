package org.dawnsci.surfacescatter;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.eclipse.dawnsci.analysis.api.io.IDataHolder;
import org.eclipse.dawnsci.analysis.api.io.IFileLoader;
import org.eclipse.dawnsci.analysis.api.tree.Attribute;
import org.eclipse.dawnsci.analysis.api.tree.DataNode;
import org.eclipse.dawnsci.analysis.api.tree.GroupNode;
import org.eclipse.dawnsci.analysis.api.tree.Tree;
import org.eclipse.dawnsci.analysis.tree.impl.DataNodeImpl;
import org.eclipse.dawnsci.hdf5.nexus.NexusFileFactoryHDF5;
import org.eclipse.dawnsci.nexus.INexusFileFactory;
import org.eclipse.dawnsci.nexus.NexusException;
import org.eclipse.dawnsci.nexus.NexusFile;
import org.eclipse.january.DatasetException;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.january.dataset.ILazyDataset;
import org.eclipse.january.dataset.SliceND;
import org.eclipse.january.metadata.IMetadata;

import uk.ac.diamond.scisoft.analysis.io.LoaderFactory;

public class FittingParametersInputReader {
	
	
	private static Scanner in;
	private static INexusFileFactory nexusFileFactory = new NexusFileFactoryHDF5();
	
	public static FittingParameters reader(String title){
	
		try {
			in = new Scanner(new FileReader(title));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		    
		FittingParameters fp = new FittingParameters();
	    
		
	    while (in.hasNextLine()) {
	        String next = in.nextLine();
	    	if(!next.startsWith("#")){
		    	String[] columns = next.split("	");
	            fp.setPt0(Integer.parseInt(columns[0]));
	            fp.setPt1(Integer.parseInt(columns[1]));
	            fp.setLen0(Integer.parseInt(columns[2]));
	            fp.setLen1(Integer.parseInt(columns[3]));
	            fp.setBgMethod(AnalaysisMethodologies.toMethodology(columns[4]));
	            fp.setTracker(TrackingMethodology.toTracker1(columns[5]));
	            fp.setFitPower(AnalaysisMethodologies.toFitPower(columns[6]));
		        fp.setBoundaryBox(Integer.parseInt(columns[7]));
		        fp.setSliderPos(Integer.parseInt(columns[8]));
		        fp.setXValue(Double.parseDouble(columns[9]));
		        fp.setFile(columns[10]);
		        
	        }
	    }
	    
	    in.close();
	    
	    return fp;
	}
	
	public static void readerFromNexus (Tree tree,
										int frameNumber,
										FrameModel m){
	
		
		FittingParameters fp = new FittingParameters();

		String pointNode = "/point_" + frameNumber;
		
		String[] attributeNames = new String[]{"/Boundary_Box",
											   "/Fit_Power",
											   "/Tracker_Type",
											   "/Background_Methodology",
											   "/ROI_Location"};
		
		GroupNode gn = tree.getGroupNode();
		GroupNode point = gn.getGroupNode(pointNode);
			
		Attribute boundaryBoxAttribute = point.getAttribute(attributeNames[0]);
		int boundaryBox = boundaryBoxAttribute.getValue().getInt(0);
		
		m.setBoundaryBox(boundaryBox);
		
		Attribute fitPowerAttribute = point.getAttribute(attributeNames[1]);
		int fitPower = fitPowerAttribute.getValue().getInt(0);
		
		m.setFitPower(fitPower);
		
		Attribute trackerTypeAttribute = point.getAttribute(attributeNames[2]);
		String trackerType = trackerTypeAttribute.getValue().getString(0);
		
		m.setTrackingMethodology(trackerType);
		
		Attribute backgroundMethodologyAttribute = point.getAttribute(attributeNames[3]);
		String backgroundMethodology = backgroundMethodologyAttribute.getValue().getString(0);
		
		m.setBackgroundMethodology(backgroundMethodology);
		
		Attribute roiAttribute = point.getAttribute(attributeNames[4]);
		double[] roi = new double[8];
		
		for(int h =0; h<8 ; h++){
			roi[h] = roiAttribute.getValue().getDouble(h); 
		}
		
		m.setRoiLocation(roi);
		
	}
	

	public static FittingParameters fittingParametersFromFrameModel(FrameModel fm){
		
		FittingParameters fp = new FittingParameters();
	    
		int[][] lenPt = LocationLenPtConverterUtils.locationToLenPtConverter(fm.getRoiLocation());
		int[] len = lenPt[0];
		int[] pt = lenPt[1];
		
	    fp.setPt0(pt[0]);
	    fp.setPt1(pt[1]);
	    fp.setLen0(len[0]);
	    fp.setLen1(len[1]);
	    fp.setLenpt(lenPt);
	    fp.setBgMethod(fm.getBackgroundMethdology());
	    fp.setTracker(fm.getTrackingMethodology());
	    fp.setFitPower(fm.getFitPower());
		fp.setBoundaryBox(fm.getBoundaryBox());
		fp.setSliderPos(0);
//		fp.setXValue(null);
//		fp.setFile(columns[10]);
		
	    return fp;
		
	}
	
	
}
