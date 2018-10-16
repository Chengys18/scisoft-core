/*-
 * Copyright 2018 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.xpdf.gudrun;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.api.tree.Attribute;
import org.eclipse.dawnsci.analysis.api.tree.DataNode;
import org.eclipse.dawnsci.analysis.api.tree.GroupNode;
import org.eclipse.dawnsci.analysis.api.tree.IFindInTree;
import org.eclipse.dawnsci.analysis.api.tree.NodeLink;
import org.eclipse.dawnsci.analysis.api.tree.Tree;
import org.eclipse.dawnsci.analysis.api.tree.TreeUtils;
import org.eclipse.dawnsci.analysis.dataset.operations.AbstractOperation;
import org.eclipse.dawnsci.analysis.dataset.slicer.SliceFromSeriesMetadata;
import org.eclipse.dawnsci.hdf5.nexus.NexusFileHDF5;
import org.eclipse.dawnsci.nexus.NXentry;
import org.eclipse.dawnsci.nexus.NXprocess;
import org.eclipse.dawnsci.nexus.NXnote;
import org.eclipse.dawnsci.nexus.NXobject;
import org.eclipse.dawnsci.nexus.NXsample;
import org.eclipse.dawnsci.nexus.NexusFile;
import org.eclipse.dawnsci.nexus.NexusUtils;
import org.eclipse.january.IMonitor;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.january.dataset.ILazyDataset;

import uk.ac.diamond.scisoft.xpdf.XPDFMetadataImpl;
import java.io.File;

import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;


/**
 * @author Tekevwe Kwakpovwe
 * Takes information from the nexus file on the processing tab in dawn
 * and reproduces this information in the form of an input file to gudrun
 *
 */
public class GenerateGudrun extends AbstractOperation<GenerateGudrunModel, OperationData>{
	
	private static final Logger LOGGER = Logger.getLogger( GenerateGudrun.class.getName());
	
	@Override
	public String getId() {
		return "uk.ac.diamond.scisoft.xpdf.gudrun.GenerateGudrun";
	}

	@Override
	public OperationRank getInputRank() {
		return OperationRank.ANY;
	}

	@Override
	public OperationRank getOutputRank() {
		return OperationRank.SAME;
	}

	/**
	 * The interface between dawn and the eclipse code
	 */
	@Override
	protected OperationData process(IDataset input, IMonitor monitor) throws OperationException {
		
		/*
		 * This code is modelled on code from XPDFReadMetadataOperation line 66 onwards
		 * this is to get the filepath of the nexus file being processed in dawn
		 */
		SliceFromSeriesMetadata ssm = input.getFirstMetadata(SliceFromSeriesMetadata.class);

		try {
			//this is the processed files filepath
			String operationFile = ssm.getFilePath();
			File thisFile = new File(operationFile);
			File fileDirec = new File(thisFile.getParent());
			//filter all the nexus files in the directory and store them in the File array "nexusFiles
			File[] processFiles = fileDirec.listFiles(new FilenameFilter() {
				//&& name.contains(sampleLabel)
		        public boolean accept(File dir, String name) {
				    return (name.contains("rocess") && name.endsWith("nxs"));
				}
			});
			File[] nexusFiles = {thisFile};
			//the bulk of the autogudrun generation is handled by this function
			getGudrun(nexusFiles, operationFile, processFiles[0]);
		} catch (Exception e1) {
			throw new OperationException(this, e1);
		}
		
		return new OperationData(input);
	}
	
	//How the program obtains the NXclasses as objects for interaction with the metadata
	private static IFindInTree getSample() {
		return getSomething("NXsample");
	}
	
	private static IFindInTree getSomething(String NXclass) {
		
		return new IFindInTree() {
			@Override
			public boolean found(NodeLink node) {
				if (node.getDestination() instanceof GroupNode) {
					Attribute nxClass = ((GroupNode) node.getDestination()).getAttribute("NX_class");
					if (nxClass != null
							&& nxClass.getFirstElement() != null
							&& nxClass.getFirstElement().equals(NXclass))
						return true;
				}
				return false;
			}
		};
	}
	
	/*
	 * The "elsewhere" is :
	 * uk.ac.diamond.scisoft.xpdf.operations.XPDFReadMetadataOperation.readAndAddContainerInfo(XPDFMetadataImpl, Tree, ILazyDataset) 
	 */
	
	//Taken from elsewhere (found above)
	private  <T extends NXobject> T getFirstSomething(Tree tree, String NXclass) {

		Map<String, NodeLink> nodeMap = TreeUtils.treeBreadthFirstSearch(tree.getGroupNode(), getSomething(NXclass), true, null);
		if (nodeMap.size() == 0) return null;
		GroupNode node = (GroupNode) nodeMap.values().toArray(
				new NodeLink[nodeMap.size()])[0].getDestination();
		//The cast has already been checked, since NXobject is a derived class of GroupNode
		@SuppressWarnings("unchecked")
		T tt = (T) node;
		return tt;
	}
	//Taken from "elsewhere", not sure about its operations, required for creating nexus objects.
	private NXsample getNXsampleFromTree(XPDFMetadataImpl xpdfMeta, Tree tree, ILazyDataset parent) {
		Map<String, NodeLink> nodeMap = TreeUtils.treeBreadthFirstSearch(tree.getGroupNode(), getSample(), true, null);

		if (nodeMap.size() < 1) throw new OperationException(this, "Sample information requested, but no NXsample data was found.");
		if (nodeMap.size() > 1) throw new OperationException(this, "Multiple NXsample data found. Giving up.");

		//Get the first (only) NXsample
		GroupNode sampleNode = (GroupNode) nodeMap.values().toArray(
				new NodeLink[nodeMap.size()])[0].getDestination();
		return (NXsample) sampleNode;
	}
	/**
	 * Makes a copy of the background xy file and places it in the same directory as the sample filer
	 * @param bckFilDir: the directory of the background file
	 * @param inFilDir: the directory of the sample file.
	 * @param sB: a SamepleBackground file.
	 */
	private void moveFiles(String bckFilDir, String inFilDir, SampleBackground sB){
	    for (String fileName: sB.getFileNames()) {
            try {
			    Files.copy((new File(bckFilDir + "/" + fileName)).toPath(),(new File(inFilDir + "/" + fileName)).toPath());
		    } catch (IOException e) {
			    LOGGER.log(Level.WARNING, "An I/O exception was thrown, does the background file exist?", e);
		    }
  	    }
	}
	
	/**
	 * Takes all the names of the nexus files within the directoy and finds the sample background file that they each
	 * use, if there are any that use the same background file, only one of such file is stored to avoid duplicates in the
	 * autogudrun.txt file
	 * 
	 * @param fileNames: The names of the nexus files within the directory
	 * @return a sampleBackground with the appropriate attributes (an array of unique background files)
	 */
	private SampleBackground getGudrunBackground(File[] fileNames) {
		//new sample background to be returned with the appropriate attributes
		SampleBackground sampleBackground = new SampleBackground();
		NexusFile containerFile;
		Tree containerTree;
		String[] xyfileContainer = new String[fileNames.length];
		
		//gets all the container files from each nexus file
		for (int i = 0; i < fileNames.length; i ++) {
			String filepath = fileNames[i].toString();
		    try {
			    containerFile = NexusFileHDF5.openNexusFileReadOnly(filepath);
			    containerTree = NexusUtils.loadNexusTree(containerFile);
		    } catch (Exception e1) {
			    throw new OperationException(this, e1);
		    }
		    GroupNode containerFileNameNode = getFirstSomething(containerTree, "NXcontainer");
		    String confilepath = containerFileNameNode.getDataNode("inside_of_file_name").getString();
		    File confilePathFile = new File(confilepath);
		    String xyfilename = (confilePathFile).getName().replace("_pe2AD.hdf5","_tth_det2_0.xy");
		    /*
		     *Background files and data files need to all be in the same directory
		     *Find the xy file that was previously processed and move it into current directory
		     */
		    xyfileContainer[i] = xyfilename; 
		}

		//Create an array with only containers that have a unique sample background file
		List<String> containersList = Arrays.asList(xyfileContainer);
		Set<String> containerSet = new HashSet<String>(containersList);
		String[] uniqContainers = containerSet.toArray(new String[containerSet.size()]);
	    
		//get the unique elements in the array of file names
		sampleBackground.setFileNames(uniqContainers);
		return sampleBackground;
	}
	
	/**
	 * swaps the direction of forward and back slashes for when data is being stored on windows vs linux
	 * @param filepath to be converted
	 * @return: the same filepath except with the forward slashes as backslashes for windows users
	 */
	private String linuxToWindows(String filepath) {
		String drive = "Y";
	    return (filepath.replace("/dls/i15-1/data/",drive+":\\")).replace("/","\\");
	}

	/**
	 * Obtains the required metadata to write the "SAMPLE" section of the autogudrun.txt and stores it in
	 * a sample object.
	 * 
	 * @param filepath: the filepath of the nexus file being processed
	 * @param normaliseTo: A normalising constant for the dataFator
	 * @return sample: a sample object with the appropriate attributes 
	 */
	private Sample getGudrunSample(String filepath, String fileName) {
		NexusFile containerFile;
		Tree containerTree;
		try {
			containerFile = NexusFileHDF5.openNexusFileReadOnly(filepath);
			containerTree = NexusUtils.loadNexusTree(containerFile);
		} catch (Exception e1) {
			throw new OperationException(this, e1);
		}
		NXsample nxSample= getNXsampleFromTree(null, containerTree, null); 
		String composition = (nxSample.getChemical_formulaScalar());
		String name = nxSample.getNameScalar();
		Sample sample = new Sample();
		sample.setName(name);
		sample.setDataFactor(1);
		sample.setFileNames(new String[] {fileName.replace(".nxs","_tth_det2_0.xy")});
		sample.setComposition(composition);
		return sample;
	
	}
	
	/**
	 * @param samplesArr
	 * @return
	 * @throws IOException
	 */
	public String[] getOutFileNames(Sample[] samplesArr) throws IOException {

		String[] outFileArray = new String[2];
		String outFileName;
		String outDirecName;
		if (model.getFilePath() == null) {
			outFileName = "";
		} else {
			outFileName = model.getFilePath();
		}

		if (model.getFileDirectory() == null) {
			outDirecName = "";
		} else {
		    outDirecName = model.getFileDirectory();
		}
		
		if (!(outDirecName.isEmpty())) {
			File oFDFile = new File(outDirecName);
			File autoTxtFile = new File(outDirecName + "/autogudrun_" + samplesArr[0].getName() + ".txt");
			if (!(oFDFile.exists())){  
				outDirecName += "/autogudrun_" + samplesArr[0].getName() + ".txt";
				oFDFile.mkdirs();
				autoTxtFile.createNewFile();
			}else if (!(autoTxtFile.exists())) {
				outDirecName += "/autogudrun_" + samplesArr[0].getName() + ".txt";
				autoTxtFile.createNewFile();
			}
			outFileArray[0] = outDirecName;
		}
		if (!(outFileName.isEmpty())) {
			outFileArray[1] = outFileName;
		} 
		return outFileArray;
	}
	
	/**
	 * Reads the metadata from the cluster processing file and
	 * finds the location of the xy file from it
	 *
	 * @param xyLocation
	 * @return the file path of the xyfile
	 */
	private String getXYFilePath(String xyLocation) {
		NexusFile nxFile;
		Tree fileTree;
		String xyFileDirec = "";
		try {
			//opens the clusterProcessing file
			nxFile = NexusFileHDF5.openNexusFileReadOnly(xyLocation);
			fileTree = NexusUtils.loadNexusTree(nxFile);
		} catch (Exception e1) {
			throw new OperationException(this, e1);
		}
		//Entry-->Process-->Notes
		NXentry NxEntry = getFirstSomething(fileTree, "NXentry");
		NXprocess NxProcessList = NxEntry.getProcess();
		//To store all the notes
		List<DataNode> processData = new ArrayList<DataNode>();
		for(int i = 0; i < NxProcessList.getNumberOfGroupNodes() - 1; i ++) {
			NXnote process = NxProcessList.getNote(String.valueOf(i));
			List<DataNode> something = process.getDataNodes();
			//Obtaining all the notes for each process
			processData.add(something.get(0));
		}
		for (int i = 0; i < processData.size(); i ++) {
			String possiblePath = processData.get(i).toString();
			//Tries to find the process with the note that references the filepath of the xy file
			if (possiblePath.contains("outputDirectory")) {
				//get the substring that contains the filepath from the metadata string
				String[] theChosenOne = processData.get(i).toString().split(",")[0].split(":");
				xyFileDirec = theChosenOne[1].replaceAll("\"", "");
			}
		}
		//returns the filepath
		return xyFileDirec;
	}
	
	/**
	 * Calls the main generate() methods from the GudrunInput class that calls the subsequent generate() methods, this function is essentially
	 * what writes the metadata to the autogudrun text file after the objects of the various classes have been created. 
	 * @param processedFile the address of the file that was placed on the processing pipeline.
	 * @param fileNames other nexus files that are in the directory of the processedFile.
	 * @throws IOException 
	 */
	private void getGudrun(File[] fileNames, String operationFile, File xyLocation) throws IOException {
		
		//os.path.basename() = tail of the path so the "arts" in mixed/martial/arts
		//os.path.dirname() = the head and body of the path so the "mixed/martial" in mixed/martial/arts

		File nexusFile = new File(operationFile);
		//This is what will be used when the operation is actually in operation.
		String dataFileDirectory= nexusFile.getParent();
		String inputFileDirectory = getXYFilePath(dataFileDirectory + "/" + xyLocation.getName());
		Instrument instrument = new Instrument();	
		
		instrument.setQMax(25);
		instrument.setQMin(0.5);
		instrument.setDataFileDirectory(dataFileDirectory);
		instrument.setInDirectory(inputFileDirectory);
		File iFDFile = new File(dataFileDirectory);
		//If the file doesn't exist then it is created here
		if (!(iFDFile.exists())){
			iFDFile.mkdirs();
		}
		
		Beam beam = new Beam();
		Normalisation normalisation = new Normalisation();
		
		/*
		 * gets all the unique sample backgrounds so no duplicates are obtained for
		 * samples that use the same background.
		 */
		
		SampleBackground sampleBackground = getGudrunBackground(fileNames);
		ArrayList<Sample> samplesList = new ArrayList<Sample>();
		
		moveFiles(inputFileDirectory, dataFileDirectory, sampleBackground);
		
		for (File filePath : fileNames) {
			String strFilePath = filePath.toString();
			String strFileName = filePath.getName();
			Sample sample = getGudrunSample(strFilePath, strFileName);
			samplesList.add(sample);
		}
		Sample[] samplesArr = samplesList.toArray(new Sample[samplesList.size()]);
		
		/*
		 * With the objects set, the file can now be written, 
		 * the objects are passed into GudrunInput as parameters to do this 
		 */
		try {
			String[] outArray = getOutFileNames(samplesArr);
			if (outArray[0] != null && !(outArray[0].equals(""))) {
				(new GudrunInput(instrument,samplesArr,beam,sampleBackground,normalisation)).generate(outArray[0]);
			}
			if (outArray[1] != null && !(outArray[1].equals(""))) {
				(new AppendGudrun(instrument,samplesArr,beam,sampleBackground,normalisation)).generate(outArray[1]);
				LOGGER.log(Level.FINE, "File succesfully written");
			}
		} catch (FileNotFoundException e) {
			LOGGER.log(Level.INFO, "The input file was not found", e);
		}		
	}
}

