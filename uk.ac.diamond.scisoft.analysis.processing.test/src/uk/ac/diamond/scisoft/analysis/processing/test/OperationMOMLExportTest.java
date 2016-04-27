package uk.ac.diamond.scisoft.analysis.processing.test;

import java.io.File;

import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.processing.ExecutionType;
import org.eclipse.dawnsci.analysis.api.processing.IOperation;
import org.eclipse.dawnsci.analysis.api.processing.IOperationContext;
import org.eclipse.dawnsci.analysis.api.processing.IOperationService;
import org.eclipse.dawnsci.analysis.api.processing.model.ValueModel;
import org.eclipse.dawnsci.analysis.dataset.impl.Random;
import org.eclipse.dawnsci.hdf.object.HierarchicalDataFactory;
import org.eclipse.dawnsci.hdf.object.IHierarchicalDataFile;
import org.junit.BeforeClass;
import org.junit.Test;

import uk.ac.diamond.scisoft.analysis.processing.Activator;
import uk.ac.diamond.scisoft.analysis.processing.actor.actors.OperationTransformer;
import uk.ac.diamond.scisoft.analysis.processing.actor.runner.GraphBuilder;
import uk.ac.diamond.scisoft.analysis.processing.actor.runner.GraphRunner;
import uk.ac.diamond.scisoft.analysis.processing.runner.OperationExporterImpl;
import uk.ac.diamond.scisoft.analysis.processing.runner.OperationRunnerImpl;
import uk.ac.diamond.scisoft.analysis.processing.runner.SeriesRunner;

import com.isencia.passerelle.model.Flow;

/**
 * Class to test exporting of momls. The files 
 * written can be reimported to 
 * 
 * @author Matthew Gerring
 *
 */
public class OperationMOMLExportTest {

	private static IOperationService service;
	private static IOperationContext context;

	@BeforeClass
	public static void before() throws Exception {
		
		service = (IOperationService)Activator.getService(IOperationService.class);
		
		// Just read all these operations.
		service.createOperations(service.getClass().getClassLoader(), "uk.ac.diamond.scisoft.analysis.processing.operations");
		OperationRunnerImpl.setRunner(ExecutionType.SERIES,   new SeriesRunner());
		OperationRunnerImpl.setRunner(ExecutionType.PARALLEL, new SeriesRunner());
		OperationRunnerImpl.setRunner(ExecutionType.GRAPH,    new GraphRunner());
		OperationExporterImpl.setRunner(ExecutionType.GRAPH,  new GraphBuilder());
		
		OperationTransformer.setOperationService(service);
	
		final File output = File.createTempFile("data", ".nxs");
		output.getParentFile().mkdirs();
		if (output.exists()) output.delete();
		
		context = service.createContext();

		IHierarchicalDataFile file = HierarchicalDataFactory.getWriter(output.getAbsolutePath());
		try {
			final IDataset data = Random.rand(0.0, 10.0, 10, 128, 128);
			String group   = file.group("/entry/signal");
			String dataset = file.createDataset("data", data, group);
			
			context.setFilePath(file.getPath());
			context.setDatasetPath(dataset);
//			context.setSlicing("all"); // The 10 in the first dimension.
			context.setDataDimensions(new int[]{1,2});
			
			//output.deleteOnExit();
			
		} finally {
			file.close();
		}

	}

	@Test
	public void testSimpleExport() throws Exception {
		
		
		final IOperation subtract = service.create("uk.ac.diamond.scisoft.analysis.processing.subtractOperation");
		subtract.setModel(new ValueModel(100));
		
		final IOperation add      = service.findFirst("add");
		add.setModel(new ValueModel(100));
	
		context.setSeries(subtract, add);

		GraphBuilder builder = new GraphBuilder();
		builder.init(context);
		Flow flow = builder.createEventDirectorFlow();

		final File tmp = File.createTempFile("workflow", ".moml");
		builder.export(tmp.getAbsolutePath());
		
		
	}
}
