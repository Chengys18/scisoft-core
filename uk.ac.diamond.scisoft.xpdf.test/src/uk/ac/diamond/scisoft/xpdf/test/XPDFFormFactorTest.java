package uk.ac.diamond.scisoft.xpdf.test;

import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.Maths;

import com.github.tschoonj.xraylib.Xraylib;

import junit.framework.TestCase;
import uk.ac.diamond.scisoft.xpdf.XPDFElementalFormFactors;

public class XPDFFormFactorTest extends TestCase {

	public void testFofx() {
		double accuracyTarget = 2e-2;
		double accuracy;
		Dataset difference;
		
		double [] Oexp = {7.99763981,  7.99144711,  7.98114548,  7.96676409,
        7.94834352,  7.92593551,  7.8694179 ,  7.79783436,  7.61270031,
        7.37819627,  7.24523371,  6.87673666,  6.47195773,  6.04883804,
        5.62283612,  4.80813454,  4.08925296,  3.00620978,  2.33790735,
        1.94641375,  1.71409078,  1.56715432,  1.4627594 ,  1.3770202 ,
        1.1831647 ,  0.99690228,  0.67326336,  0.44203168,  0.29237238,
        0.19693118,  0.13428949,  0.06517045,  0.03803777,  0.02956003,
        0.02748337,  0.02702211,  0.027014  ,  0.027014  ,  0.027014  ,
        0.027014  ,  0.027014  ,  0.027014  ,  0.027014  ,  0.027014};
		
		double[] Siexp = {13.9928796 ,  13.9748189 ,  13.94488844,
		        13.90334112,  13.85052461,  13.78687528,  13.62922031,
		        13.43532317,  12.96235619,  12.41767844,  12.13342145,
		        11.42716902,  10.76817336,  10.18255719,   9.67481284,
		         8.85950308,   8.22971612,   7.2031956 ,   6.23962251,
		         5.31198038,   4.47063445,   3.75062885,   3.1629216 ,
		         2.70032668,   1.96788228,   1.59966403,   1.26126401,
		         1.05714331,   0.87350073,   0.70463929,   0.55787296,
		         0.34399088,   0.22657137,   0.17346297,   0.15348157,
		         0.14552638,   0.14507302,   0.145073  ,   0.145073  ,
		         0.145073  ,   0.145073  ,   0.145073  ,   0.145073  ,   0.145073};
		
		double[] Ceexp = {57.97097272,  57.91835218,  57.8314051 ,
		        57.71123735,  57.55934868,  57.37758683,  56.93323634,
		        56.39767579,  55.13655453,  53.74686044,  53.03869393,
		        51.28585046,  49.57774723,  47.90148849,  46.24612237,
		        43.03925529,  40.08959994,  35.14850396,  31.16360084,
		        27.76261597,  24.85962692,  22.45751129,  20.51568139,
		        18.94650626,  16.01654096,  13.71013713,   9.91037442,
		         7.27235219,   5.70304268,   4.82394399,   4.27582476,
		         3.41272363,   2.47756838,   1.40436633,   0.20116751,
		        -2.54015219, -10.6327597 , -18.98882694, -38.12248271,
		       -38.38592711, -38.38601694, -38.386017  , -38.386017  , -38.386017};

		double[] Wexp = {73.95119856,  73.91414353,  73.85262186,
		        73.76698512,  73.65771868,  73.525435  ,  73.19484955,
		        72.78231672,  71.74613874,  70.49304917,  69.80961116,
		        68.00505652,  66.14663503,  64.29947957,  62.48957517,
		        58.97942952,  55.57716905,  49.1922861 ,  43.66112664,
		        38.97886316,  34.93125563,  31.36554412,  28.23470991,
		        25.53710974,  20.57362563,  17.51902914,  13.83211019,
		        11.03102959,   8.74005724,   7.022564  ,   5.83568436,
		         4.55432819,   3.93941931,   3.46780606,   2.97598809,
		         1.83531272,  -1.84909594,  -6.35949508, -28.8435509 ,
		       -32.74341646, -32.85979471, -32.864574  , -32.864574  , -32.864574};
		
		int z = 8;
		Dataset O = XPDFElementalFormFactors.fofx(z, DatasetFactory.createFromObject(xValues()));
		difference = Maths.subtract(O, Oexp);
		accuracy = Math.sqrt((double) Maths.square(difference).max());
		assertTrue("Error in oxygen form factor too high", accuracy < accuracyTarget*z);

		z = 14;
		Dataset Si = XPDFElementalFormFactors.fofx(z, DatasetFactory.createFromObject(xValues()));
		difference = Maths.subtract(Si, Siexp);
		accuracy = Math.sqrt((double) Maths.square(difference).max());
		assertTrue("Error in silicon form factor too high", accuracy < accuracyTarget*z);
		
		z = 58;
		Dataset Ce = XPDFElementalFormFactors.fofx(z, DatasetFactory.createFromObject(xValues()));
		difference = Maths.subtract(Ce, Ceexp);
		accuracy = Math.sqrt((double) Maths.square(difference).max());
//		assertTrue("Error in cerium form factor too high", accuracy < accuracyTarget*z);  // Form factors should not be negative
		
		z = 74;
		Dataset W = XPDFElementalFormFactors.fofx(z, DatasetFactory.createFromObject(xValues()));
		difference = Maths.subtract(W, Wexp);
		accuracy = Math.sqrt((double) Maths.square(difference).max());
//		assertTrue("Error in tungsten form factor too high", accuracy < accuracyTarget*z); // Form factors should not be negative
		
	}

	public void testSofx() {
		double accuracyTarget = 1e-2;
		double accuracy;
		Dataset difference;
		
		double[] Oexp = {3.00500481e-03,   1.14074145e-02,
		         2.53698450e-02,   4.48303708e-02,   6.97031940e-02,
		         9.98795246e-02,   1.75599431e-01,   2.70706975e-01,
		         5.12624039e-01,   8.10907032e-01,   9.76133895e-01,
		         1.42008695e+00,   1.88582091e+00,   2.35077646e+00,
		         2.79925480e+00,   3.61242853e+00,   4.29189864e+00,
		         5.25853395e+00,   5.82736014e+00,   6.17392319e+00,
		         6.41199321e+00,   6.59698989e+00,   6.75536337e+00,
		         6.89921028e+00,   7.21458113e+00,   7.46285503e+00,
		         7.76394505e+00,   7.89687807e+00,   7.95584458e+00,
		         7.98264905e+00,   7.99398406e+00,   7.99952272e+00,
		         7.99997842e+00,   7.99999944e+00,   7.99999999e+00,
		         8.00000000e+00,   8.00000000e+00,   8.00000000e+00,
		         8.00000000e+00,   8.00000000e+00,   8.00000000e+00,
		         8.00000000e+00,   8.00000000e+00,   8.00000000e+00};

		double[] Siexp = {1.13570406e-02,   3.69118104e-02,
		         7.91148584e-02,   1.37394923e-01,   2.10973966e-01,
		         2.98887867e-01,   5.13088021e-01,   7.69597669e-01,
		         1.36350062e+00,   1.99292148e+00,   2.29923432e+00,
		         3.00054854e+00,   3.59180870e+00,   4.08568520e+00,
		         4.50812974e+00,   5.21816879e+00,   5.82180378e+00,
		         6.90060640e+00,   7.92345867e+00,   8.86537566e+00,
		         9.67675231e+00,   1.03397360e+01,   1.08641378e+01,
		         1.12741829e+01,   1.19691068e+01,   1.24080994e+01,
		         1.29470440e+01,   1.32858864e+01,   1.35370766e+01,
		         1.37207323e+01,   1.38440164e+01,   1.39614464e+01,
		         1.39930151e+01,   1.39990724e+01,   1.39999097e+01,
		         1.39999997e+01,   1.40000000e+01,   1.40000000e+01,
		         1.40000000e+01,   1.40000000e+01,   1.40000000e+01,
		         1.40000000e+01,   1.40000000e+01,   1.40000000e+01};
		
		double[] Ceexp = {4.91793200e-02,   1.22581490e-01,
		         2.42837769e-01,   4.06939978e-01,   6.10893859e-01,
		         8.49917313e-01,   1.41150002e+00,   2.04667877e+00,
		         3.37560469e+00,   4.61007650e+00,   5.16877345e+00,
		         6.43468072e+00,   7.62363845e+00,   8.82561561e+00,
		         1.00591438e+01,   1.25394139e+01,   1.48792832e+01,
		         1.87807251e+01,   2.17691613e+01,   2.43071988e+01,
		         2.66891052e+01,   2.89781907e+01,   3.11391777e+01,
		         3.31264188e+01,   3.72017117e+01,   4.01140748e+01,
		         4.39698637e+01,   4.68422132e+01,   4.91865698e+01,
		         5.09944494e+01,   5.23166011e+01,   5.39707063e+01,
		         5.49851520e+01,   5.57612047e+01,   5.63954973e+01,
		         5.72760188e+01,   5.79542924e+01,   5.79990442e+01,
		         5.80000000e+01,   5.80000000e+01,   5.80000000e+01,
		         5.80000000e+01,   5.80000000e+01,   5.80000000e+01};
		
		double[] Wexp = {6.01816106e-02,   1.11191090e-01,
		         1.95437418e-01,   3.11788676e-01,   4.58703853e-01,
		         6.34274483e-01,   1.06222252e+00,   1.57510612e+00,
		         2.76524886e+00,   4.03426592e+00,   4.65730954e+00,
		         6.11249867e+00,   7.40648316e+00,   8.59071825e+00,
		         9.73517212e+00,   1.20652878e+01,   1.44816591e+01,
		         1.92094786e+01,   2.33364196e+01,   2.67495259e+01,
		         2.96312099e+01,   3.22090335e+01,   3.46295671e+01,
		         3.69482126e+01,   4.22652045e+01,   4.67157991e+01,
		         5.31466299e+01,   5.75007904e+01,   6.07345058e+01,
		         6.31534471e+01,   6.49271537e+01,   6.72684614e+01,
		         6.88833469e+01,   7.02037476e+01,   7.12968620e+01,
		         7.28018789e+01,   7.39289411e+01,   7.39986386e+01,
		         7.40000000e+01,   7.40000000e+01,   7.40000000e+01,
		         7.40000000e+01,   7.40000000e+01,   7.40000000e+01};
		
		int z = 8;
		Dataset O = XPDFElementalFormFactors.sofx(z, DatasetFactory.createFromObject(xValues()));
		difference = Maths.subtract(O, Oexp);
		accuracy = Math.sqrt((double) Maths.square(difference).max());
		assertTrue("Error in oxygen form factor too high", accuracy < accuracyTarget*z);
		
		z = 14;
		Dataset Si = XPDFElementalFormFactors.sofx(14, DatasetFactory.createFromObject(xValues()));
		difference = Maths.subtract(Si, Siexp);
		accuracy = Math.sqrt((double) Maths.square(difference).max());
		assertTrue("Error in silicon form factor too high", accuracy < accuracyTarget*z);
		
		z = 58;
		Dataset Ce = XPDFElementalFormFactors.sofx(58, DatasetFactory.createFromObject(xValues()));
		difference = Maths.subtract(Ce, Ceexp);
		accuracy = Math.sqrt((double) Maths.square(difference).max());
		assertTrue("Error in cerium form factor too high", accuracy < accuracyTarget*z);
		
		z = 74;
		Dataset W = XPDFElementalFormFactors.sofx(74, DatasetFactory.createFromObject(xValues()));
		difference = Maths.subtract(W, Wexp);
		accuracy = Math.sqrt((double) Maths.square(difference).max());
		assertTrue("Error in tungsten form factor too high", accuracy < accuracyTarget*74);
		
	}

	public double[] xValues() {
		double[] xes = {5e-3, 1e-2, 1.5e-2, 2e-2, 2.5e-2, 3e-2, 4e-2, 5e-2, 7e-2, 9e-2,
			1e-1, 1.25e-1, 1.5e-1, 1.75e-1, 2e-1, 2.5e-1, 3e-1, 4e-1, 5e-1, 6e-1, 7e-1, 8e-1, 9e-1,
			1.0, 1.25, 1.50, 2.0, 2.5, 3.0, 3.5, 4.0, 5.0, 6.0, 7.0, 8.0,
			10.0, 15.0, 2e1, 5e1, 8e1, 1e2, 1e3, 1e6, 1e9};
		return xes;
	}
	
	public void testXraylibValues() {
		
		double[] xes = xValues();
		Dataset Of = XPDFElementalFormFactors.fofx(8, DatasetFactory.createFromObject(xes));
		Dataset Os = XPDFElementalFormFactors.sofx(8, DatasetFactory.createFromObject(xes));
		double[] Oflib = new double[xes.length];
		double[] Oslib = new double[xes.length];
		for (int i = 1; i < xes.length; i++) {
			Oflib[i] = Xraylib.FF_Rayl(8, xes[i]);
			Oslib[i] = Xraylib.SF_Compt(8, xes[i]);
		}
		
		double maxError = 1e-3;
		
		assertTrue(Of.getDouble(10)/Oflib[10]-1 < maxError);
		assertTrue(Os.getDouble(10)/Oslib[10]-1 < maxError);
		
		
	}
}
