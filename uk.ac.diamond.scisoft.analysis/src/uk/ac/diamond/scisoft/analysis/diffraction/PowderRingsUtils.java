/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.diffraction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;

import javax.measure.unit.NonSI;
import javax.vecmath.Vector3d;

import org.apache.commons.math3.optim.SimpleBounds;
import org.apache.commons.math3.optim.nonlinear.scalar.MultivariateOptimizer;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.eclipse.dawnsci.analysis.api.diffraction.DetectorProperties;
import org.eclipse.dawnsci.analysis.api.diffraction.DiffractionCrystalEnvironment;
import org.eclipse.dawnsci.analysis.api.fitting.IConicSectionFitFunction;
import org.eclipse.dawnsci.analysis.api.fitting.IConicSectionFitter;
import org.eclipse.dawnsci.analysis.api.roi.IROI;
import org.eclipse.dawnsci.analysis.dataset.roi.CircularROI;
import org.eclipse.dawnsci.analysis.dataset.roi.EllipticalFitROI;
import org.eclipse.dawnsci.analysis.dataset.roi.EllipticalROI;
import org.eclipse.dawnsci.analysis.dataset.roi.PointROI;
import org.eclipse.dawnsci.analysis.dataset.roi.PolylineROI;
import org.eclipse.dawnsci.analysis.dataset.roi.RectangularROI;
import org.eclipse.january.IMonitor;
import org.eclipse.january.dataset.BooleanDataset;
import org.eclipse.january.dataset.BooleanIterator;
import org.eclipse.january.dataset.Comparisons;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.DatasetUtils;
import org.eclipse.january.dataset.Stats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.uncommons.maths.combinatorics.CombinationGenerator;

import uk.ac.diamond.scisoft.analysis.crystallography.HKL;
import uk.ac.diamond.scisoft.analysis.fitting.Fitter;
import uk.ac.diamond.scisoft.analysis.fitting.Generic1DFitter;
import uk.ac.diamond.scisoft.analysis.fitting.functions.IdentifiedPeak;
import uk.ac.diamond.scisoft.analysis.fitting.functions.Polynomial;
import uk.ac.diamond.scisoft.analysis.roi.ROIProfile;

/**
 * Utilities to fit powder rings
 */
public class PowderRingsUtils {
	private static Logger logger = LoggerFactory.getLogger(PowderRingsUtils.class);

	private static final double FULL_CIRCLE = 2.0*Math.PI;

	private static final double ARC_LENGTH = 8;
	private static final double RADIAL_DELTA = 10;
	private static final int MAX_POINTS = 200;

	private static final int PEAK_SMOOTHING = 15;
	private static final double MAX_FWHM_FACTOR = 2;
	private static final double RING_SEPARATION = 4;

	public static PolylineROI findPOIsNearCircle(IMonitor mon, Dataset image, BooleanDataset mask, CircularROI circle) {
		return findPOIsNearCircle(mon, image, mask, circle, ARC_LENGTH, RADIAL_DELTA, MAX_POINTS);
	}

	public static PolylineROI findPOIsNearCircle(IMonitor mon, Dataset image, BooleanDataset mask, CircularROI circle,
			double arcLength, double radialDelta, int maxPoints) {
		return findPOIsNearEllipse(mon, image, mask, new EllipticalROI(circle), arcLength, radialDelta, maxPoints);
	}

	public static PolylineROI findPOIsNearEllipse(IMonitor mon, Dataset image, BooleanDataset mask, EllipticalROI ellipse) {
		return findPOIsNearEllipse(mon, image, mask, ellipse, ARC_LENGTH, RADIAL_DELTA, MAX_POINTS);
	}

	/**
	 * Find a set of points of interests near given ellipse from an image.
	 * <p>
	 * The ellipse is divided into sub-areas and these POIs are considered to
	 * be the locations of maximum pixel intensities found within those sub-areas.
	 * @param mon
	 * @param image
	 * @param mask (can be null)
	 * @param ellipse
	 * @param arcLength step size along arc in pixels
	 * @param radialDelta +/- value to define area to search
	 * @param maxPoints maximum number of points to return
	 * @return polyline ROI
	 */
	public static PolylineROI findPOIsNearEllipse(IMonitor mon, Dataset image, BooleanDataset mask, EllipticalROI ellipse,
			double arcLength, double radialDelta, int maxPoints) {
		if (image.getRank() != 2) {
			logger.error("Dataset must have two dimensions");
			throw new IllegalArgumentException("Dataset must have two dimensions");
		}
		if (mask != null && !image.isCompatibleWith(mask)) {
			logger.error("Mask must match image shape");
			throw new IllegalArgumentException("Mask must match image shape");
		}

		final int[] shape = image.getShape();
		final int h = shape[0];
		final int w = shape[1];
		if (ellipse.containsPoint(-1,-1) && ellipse.containsPoint(-1,h+1) && ellipse.containsPoint(w+1,h+1) && ellipse.containsPoint(w+1,-1)) {
			throw new IllegalArgumentException("Ellipse does not intersect image!");
		}
		
		final double aj = ellipse.getSemiAxis(0);
		final double an = ellipse.getSemiAxis(1);
		if (an < arcLength) {
			logger.error("Ellipse/circle is too small");
			throw new IllegalArgumentException("Ellipse/circle is too small");
		}

		final double xc = ellipse.getPointX();
		final double yc = ellipse.getPointY();
		final double ang = ellipse.getAngle();
		final double ca = Math.cos(ang);
		final double sa = Math.sin(ang);

		final double pdelta = arcLength / aj; // change in angle
		double rdelta = radialDelta; // semi-width of annulus of interest
		if (rdelta < 1) {
			logger.warn("Radial delta was set too low: setting to 1");
			rdelta = 1;
		}
		final double rsj = aj - rdelta;
		final double rej = aj + rdelta;
		final double rsn = an - rdelta;
		final double ren = an + rdelta;

		final int imax = (int) Math.ceil(FULL_CIRCLE / pdelta);

		logger.debug("Major semi-axis = [{}, {}]; {}", new Object[] { rsj, rej, imax });
		final int[] start = new int[2];
		final int[] stop = new int[2];
		final int[] step = new int[] { 1, 1 };
		HashSet<PointROI> pointSet = new HashSet<PointROI>();
		for (int i = 0; i < imax; i++) {
			double p = i * pdelta;
			double cp = Math.cos(p);
			double sp = Math.sin(p);
			Dataset sub;
			final int[] beg = new int[] { (int) (yc + rsj * sa * cp + rsn * ca * sp),
					(int) (xc + rsj * ca * cp - rsn * sa * sp) };
			final int[] end = new int[] { (int) (yc + rej * sa * cp + ren * ca * sp),
					(int) (xc + rej * ca * cp - ren * sa * sp) };
			start[0] = Math.max(0, Math.min(beg[0], end[0]));
			stop[0] = Math.min(h, Math.max(beg[0], end[0]));
			if (start[0] == stop[0]) {
				if (stop[0] == h) {
					start[0]--;
				} else {
					stop[0]++;
				}
			} else if (start[0] > stop[0] || start[0] >= h) {
				continue;
			} else {
				stop[0] = Math.min(Math.max(stop[0], start[0] + (int) radialDelta), h);
			}
			start[1] = Math.max(0, Math.min(beg[1], end[1]));
			stop[1] = Math.min(w, Math.max(beg[1], end[1]));
			if (start[1] == stop[1]) {
				if (stop[1] == w) {
					start[1]--;
				} else {
					stop[1]++;
				}
			} else if (start[1] > stop[1] || start[1] >= w) {
				continue;
			} else {
				stop[1] = Math.min(Math.max(stop[1], start[1] + (int) radialDelta), w);
			}
			sub = image.getSlice(start, stop, step);

			// TODO ensure slice has peaky data
			double iqr = (Double) Stats.iqr(sub);
			double low = (Double) sub.mean() + 0.5*iqr;
			if (sub.max().doubleValue() < low) {
				logger.info("Discard sub at {} ({}): {}; {}; {} [{}] => {} cf {}", new Object[] {Arrays.toString(start), sub.getShape(), 
						sub.min(), sub.mean(), sub.max(), low, 0.5*iqr, sub.stdDeviation()});
				continue;
			}

			int[] pos = sub.maxPos();
			pos[0] += start[0];
			pos[1] += start[1];

			if (mask != null) {
				if (mask.get(pos)) {
					Dataset sorted = DatasetUtils.sort(sub);
					int l = sorted.getSize() - 1;
					do {
						double x = sorted.getElementDoubleAbs(l);
						pos = sub.getNDPosition(DatasetUtils.findIndexEqualTo(sub, x));
						pos[0] += start[0];
						pos[1] += start[1];
					} while (!mask.get(pos) && --l >= 0);
					if (l < 0) {
						logger.warn("Could not find unmasked value for slice!");
					} else {
						//add 0.5 to make pointROI at centre of pixel
//						pointSet.add(new PointROI(pos[1], pos[0]));
						pointSet.add(new PointROI(pos[1]+0.5, pos[0]+0.5));
					}
				}
			} else {
//			System.err.printf("Slice: %s, %s has max at %s\n", Arrays.toString(start), Arrays.toString(stop), Arrays.toString(pos));
				//add 0.5 to make pointROI at centre of pixel
				pointSet.add(new PointROI(pos[1]+0.5, pos[0]+0.5));
//				pointSet.add(new PointROI(pos[1], pos[0]));
			}

			if (mon != null)
				mon.worked(1);
		}

		// analyse pixel values
		int n = pointSet.size();
		double[] values = new double[n];
		int i = 0;
		for (PointROI p : pointSet) {
			int[] pos = p.getIntPoint();
			values[i++] = image.getDouble(pos[1], pos[0]);
		}

		Dataset pixels = DatasetFactory.createFromObject(values);
//		System.err.println(pixels.toString(true));

		// threshold with population stats from maxima
		logger.debug("Stats: {} {} {} {}", new Object[] {pixels.min(), pixels.mean(), pixels.max(),
				Arrays.toString(Stats.quantile(pixels, 0.25, 0.5, 0.75))});

		double threshold;
		if (n > maxPoints) {
			threshold = Stats.quantile(pixels, 1 - maxPoints/(double) n);
			logger.debug("Threshold: {} setting for highest {}", threshold, maxPoints);
		} else {
			double iqr = (Double) Stats.iqr(pixels);
			threshold = (Double) pixels.mean() - 2*iqr;
			double min = pixels.min().doubleValue();
			while (threshold < min) {
				threshold += iqr;
			}
			logger.debug("Threshold: {} setting by mean - 2IQR", threshold);
		}

		PolylineROI polyline = new PolylineROI();
		if (threshold > (Double) pixels.min()) {
			for (PointROI p : pointSet) {
				int[] pos = p.getIntPoint();
				double v = image.getDouble(pos[1], pos[0]);
				if (v >= threshold) {
//					System.err.printf("Adding %f %s\n", v, Arrays.toString(pos));
					polyline.insertPoint(p);
//				} else {
//					System.err.println("Rejecting " + p + " = " + v);
				}
			}
		} else {
			for (PointROI p : pointSet) {
				polyline.insertPoint(p);
			}
		}
		if (mon != null)
			mon.worked(polyline.getNumberOfPoints());

		logger.debug("Used {} of {} pixels", polyline.getNumberOfPoints(), pointSet.size());

		return polyline;
	}

	public static EllipticalFitROI fitAndTrimOutliers(IMonitor mon, PolylineROI points, boolean circleOnly) {
		return fitAndTrimOutliers(mon, points, RADIAL_DELTA, circleOnly);
	}

	/**
	 * Fit an ellipse to given points, trim points if they fall outside given distance
	 * and re-fit
	 * @param points
	 * @param trimDelta trim distance
	 * @param circleOnly if true, then fit a circle
	 * @return fitted ellipse
	 */
	public static EllipticalFitROI fitAndTrimOutliers(IMonitor mon, PolylineROI points, double trimDelta, boolean circleOnly) {
		try {
			EllipticalFitROI efroi = new EllipticalFitROI(points, circleOnly);

			PolylineROI cpts = points;
			int n = cpts.getNumberOfPoints();
			IConicSectionFitter f = efroi.getFitter();
			IConicSectionFitFunction fn = f.getFitFunction(null, null);

			Dataset d = DatasetUtils.convertToDataset(fn.calcDistanceSquared(f.getParameters()));

			// find outliers
			double h = trimDelta * trimDelta;
			double ds = d.max().doubleValue();
			logger.debug("Range: [0, {}] cf [{}, {}, {}]", new Object[] { h, d.min(), d.mean(), d.max() });
			if (ds < h)
				return efroi;

			BooleanDataset b = Comparisons.lessThanOrEqualTo(d, h);
			BooleanIterator it = d.getBooleanIterator(b, true);
			PolylineROI npts = new PolylineROI();
			while (it.hasNext()) {
				npts.insertPoint(cpts.getPoint(it.index));
			}
			int m = npts.getNumberOfPoints();
			if (m < n) {
				logger.debug("Found some outliers: {}/{}", n - m, n);
				efroi.setPoints(npts);
				if (mon != null)
					mon.worked(m);
			}

			return efroi;
		} catch (Exception e) {
			logger.error("Problem with trimming: {}", e);
			throw new IllegalArgumentException("Problem!: ", e);
		}
	}

	/**
	 * Find other ellipses from given ellipse and image.
	 * <p>
	 * This is done by looking at the box profile along spokes from the
	 * given centre (from a minimum distance of the minor semi-axis) and finding peaks.
	 * Then the distance out to those peaks is used to search for more POIs and so more ellipses
	 * @param image
	 * @param mask (can be null)
	 * @param roi initial ellipse
	 * @return list of ellipses
	 */
	public static List<EllipticalROI> findOtherEllipses(IMonitor mon, Dataset image, BooleanDataset mask, EllipticalROI roi) {
		return findOtherEllipses(mon, image, mask, roi, roi.getSemiAxis(1), RADIAL_DELTA, ARC_LENGTH, 0.3*RADIAL_DELTA, MAX_POINTS);
	}

	/**
	 * Find other ellipses from given ellipse and image.
	 * <p>
	 * This is done by looking at the box profile along spokes from the
	 * given centre and finding peaks. Then the distance out to those peaks is used
	 * to search for more POIs and so more ellipses
	 * @param image
	 * @param mask (can be null)
	 * @param roi initial ellipse
	 * @param radialMin
	 * @param radialDelta
	 * @param arcLength
	 * @param trimDelta
	 * @param maxPoints
	 * @return list of ellipses
	 */
	public static List<EllipticalROI> findOtherEllipses(IMonitor mon, Dataset image, BooleanDataset mask, EllipticalROI roi,
			double radialMin, double radialDelta, double arcLength, double trimDelta, int maxPoints) {
		if (image.getRank() != 2) {
			logger.error("Dataset must have two dimensions");
			throw new IllegalArgumentException("Dataset must have two dimensions");
		}
		if (mask != null && !image.isCompatibleWith(mask)) {
			logger.error("Mask must match image shape");
			throw new IllegalArgumentException("Mask must match image shape");
		}

		// explore all corners
		final int[] shape = image.getShape();
		final int h = shape[0];
		final int w = shape[1];
		double[] ec = roi.getPoint();
		TreeSet<Double> majors = new TreeSet<Double>();

		// TODO farm this out across several threads
		findMajorAxes(mon, majors, image, mask, roi, radialMin, radialDelta, ec, 0 - ec[0], 0 - ec[1]); // TL
		findMajorAxes(mon, majors, image, mask, roi, radialMin, radialDelta, ec, w - ec[0], 0 - ec[1]); // TR
		findMajorAxes(mon, majors, image, mask, roi, radialMin, radialDelta, ec, w - ec[0], h - ec[1]); // BR
		findMajorAxes(mon, majors, image, mask, roi, radialMin, radialDelta, ec, 0 - ec[0], h - ec[1]); // BL

		findMajorAxes(mon, majors, image, mask, roi, radialMin, radialDelta, ec, 0, h - ec[1]); // T
		findMajorAxes(mon, majors, image, mask, roi, radialMin, radialDelta, ec, w - ec[0], 0); // R
		findMajorAxes(mon, majors, image, mask, roi, radialMin, radialDelta, ec, 0, 0 - ec[1]); // B
		findMajorAxes(mon, majors, image, mask, roi, radialMin, radialDelta, ec, 0 - ec[0], 0); // L

		// and finally find POIs
		List<EllipticalROI> ells = new ArrayList<EllipticalROI>();
		double major = roi.getSemiAxis(0);
		double aspect = roi.getSemiAxis(0)/roi.getSemiAxis(1);
		double last = Double.NEGATIVE_INFINITY;
		for (double a : majors) {
			System.err.println("Current " + a + ", last " + last);
			if (a < last) {
				System.err.println("Dropped as less than last");
				continue;
			}
			if (Math.abs(a - last) < RING_SEPARATION) { // omit close rings
				last = a;
				System.err.println("Dropped as too close");
				continue;
			}
			if (Math.abs(a - major) < RING_SEPARATION) {
				last = major;
				System.err.println("Add original");
				ells.add(roi);
			} else {
				EllipticalROI er = new EllipticalROI(a, a/aspect, roi.getAngle(), ec[0], ec[1]);
				try {
					PolylineROI polyline = findPOIsNearEllipse(mon, image, mask, er, arcLength, 0.8*radialDelta, maxPoints);
					if (polyline.getNumberOfPoints() > 2) {
						er = fitAndTrimOutliers(mon, polyline, trimDelta, roi.isCircular());
						double emaj = er.getSemiAxis(0);
						if (Math.abs(emaj - last) < RING_SEPARATION) { // omit close rings
							last = a;
							System.err.println("Dropped as fit is too close");
							continue;
						}
						double[] c = er.getPointRef();
						if (Math.hypot(c[0] - ec[0], c[1] - ec[1]) > 8*radialDelta) {
							last = a; // omit fits with far-off centres
							System.err.println("Dropped as centre is far-off");
							continue;
						}
						if (Math.abs(emaj - major) < RING_SEPARATION) {
							System.err.println("Add fit that is close to original");
						}
						last = Math.max(a, emaj);
						ells.add(er);
					} else {
						logger.warn("Could not find enough points at {}", er);
					}
				} catch (IllegalArgumentException e) {
					logger.debug("Problem with {}", er, e);
					last = a;
				}
				if (mon != null)
					mon.worked(1);
			}
		}

		return ells;
	}

	/**
	 * Find major axes by looking along thick line given by relative coordinates to centre for
	 * maximum intensity values
	 * @param mon
	 * @param axes
	 * @param image
	 * @param mask
	 * @param roi
	 * @param offset minimum position of peaks
	 * @param width of line
	 * @param centre
	 * @param dx
	 * @param dy
	 */
	private static void findMajorAxes(IMonitor mon, TreeSet<Double> axes, Dataset image, Dataset mask, EllipticalROI roi, double offset, double width, double[] centre, double dx, double dy) {
		RectangularROI rroi = new RectangularROI();
		rroi.setPoint(centre);
		rroi.setAngle(Math.atan2(dy, dx));
		rroi.setLengths(Math.hypot(dx, dy), width);
		rroi.translate(0, -0.5);
		rroi.setClippingCompensation(true);
		Dataset profile = ROIProfile.maxInBox(image, mask, rroi)[0];

		List<IdentifiedPeak> peaks = Generic1DFitter.findPeaks(DatasetFactory.createRange(profile.getSize(), Dataset.INT), profile, PEAK_SMOOTHING);
		if (mon != null)
			mon.worked(profile.getSize());

		System.err.printf("\n");
		DescriptiveStatistics stats = new DescriptiveStatistics();
		int[] pb = new int[1];
		int[] pe = new int[1];
		for (IdentifiedPeak p : peaks) {
			if (p.getPos() < offset) {
				continue;
			}
			pb[0] = (int) p.getMinXVal();
			pe[0] = (int) p.getMaxXVal();
			p.setArea((Double) profile.getSlice(pb, pe, null).sum());
			stats.addValue(p.getArea());
			System.err.printf("P %f A %f W %f H %f\n", p.getPos(), p.getArea(), p.getFWHM(), p.getHeight());
		}
		
		double area = stats.getMean() + 0.4*(stats.getPercentile(75) - stats.getPercentile(25));
		logger.debug("Area: {}", stats);
		logger.debug("Minimum threshold: {}", area);

		double majorFactor = roi.getSemiAxis(0)/roi.getDistance(rroi.getAngle());
		double maxFWHM = MAX_FWHM_FACTOR*width;
		for (IdentifiedPeak p : peaks) {
			double l = p.getPos();
			if (l < offset) {
				continue;
			}
//			System.err.println(p);
			// filter on area and FWHM
			if (p.getFWHM() > maxFWHM) {
				continue;
			}
			if (p.getArea() < area) {
				break;
			}
			axes.add(l*majorFactor);
		}
		if (mon != null)
			mon.worked(peaks.size());

	}

	/**
	 * Fit ellipses to a single detector with/without fixing wavelength.
	 * <p>
	 * Combinations of spacings are used to fit the ellipses (if there are more ellipses than spacings then
	 * the outer ellipses are used)
	 * @param mon
	 * @param detector
	 * @param env
	 * @param ellipses
	 * @param spacings
	 *            a list of possible spacings
	 * @param fixedWavelength 
	 * @return q-space
	 */
	public static QSpace fitEllipsesToQSpace(IMonitor mon, DetectorProperties detector, DiffractionCrystalEnvironment env, List<EllipticalROI> ellipses, List<HKL> spacings, boolean fixedWavelength) {

		int n = ellipses.size();

		double dmax = spacings.get(0).getD().doubleValue(NonSI.ANGSTROM);
		{
			double rmin = detector.getDetectorDistance() * Math.tan(2.0 * Math.asin(0.5 * env.getWavelength() / dmax)) / detector.getVPxSize();
			int l = 0;
			for (EllipticalROI e : ellipses) {
				if (e.getSemiAxis(0) > rmin)
					break;
				l++;
			}

			if (l >= n) {
				throw new IllegalArgumentException("Maybe all rings are too small!");
			}
			if (l > 0) {
				logger.debug("Discarding first {} rings", l);
				ellipses = ellipses.subList(l, n);
				n = ellipses.size();
			}
		}

		if (n >= spacings.size()) { // always allow a choice to be made
			int l = n - spacings.size();
			logger.warn("The number of d-spacings ({}) should be greater than or equal to {}: using outer rings",
					l, n-1);
			ellipses = ellipses.subList(l+1, n);
			n = ellipses.size();
		}
		logger.debug("Using {} rings:", n);
		boolean allCircles = true;
		for (int i = 0; i < n; i++) {
			EllipticalROI e = ellipses.get(i);
			logger.debug("    {}", e);
			if (allCircles && !e.isCircular()) {
				allCircles = false;
			}
		}

		DetectorFitFunction f;
		if (allCircles) {
			logger.debug("All rings are circular");
			f = createQFitFunction4(ellipses, detector, env.getWavelength(), fixedWavelength);
		} else {
			f = createQFitFunction7(ellipses, detector, env.getWavelength(), fixedWavelength);
		}

		logger.debug("Init: {}", f.getInitial());

		// set up a combination generator for all
		List<Double> s = new ArrayList<Double>();
		for (int i = 0, imax = spacings.size(); i < imax; i++) {
			HKL d = spacings.get(i);
			s.add(d.getD().doubleValue(NonSI.ANGSTROM));
		}
		CombinationGenerator<Double> gen = new CombinationGenerator<Double>(s, n);
		if (mon != null) {
			mon.worked(1);
		}
		logger.debug("There are {} combinations", gen.getTotalCombinations());

		double min = Double.POSITIVE_INFINITY;

		MultivariateOptimizer opt = FittingUtils.createOptimizer(f.getN());

		// opt.setMaxEvaluations(2000);
		List<Double> fSpacings = null;

		int i = 0;
		for (List<Double> list : gen) { // find combination that minimizes residuals
			f.setSpacings(list);
			double res = FittingUtils.optimize(f, opt, min);
			if (res < min) {
				min = res;
				fSpacings = list;
			}
//			System.err.printf(".");
			if (mon != null) {
				mon.worked(10);
				if (mon.isCancelled())
					return null;
			}
			if (i++ == 100) {
//				System.err.printf("\n");
				i = 0;
			}
		}
//		System.err.printf("\n");

		if (fSpacings == null || f.getParameters() == null) {
			logger.warn("Problem with fitting - as could not find a single fit!");
			return null;
		}

		logger.debug("Parameters: w {}, D {}, e {} (min {})", new Object[] { f.getWavelength(), f.getDistance(), f.getNormalAngles(), min });
		logger.debug("Spacings used: {}", fSpacings);
		f.setSpacings(fSpacings);
		logger.debug("Residual value: {}", f.value(f.getParameters()));

		QSpace q = new QSpace(f.getDetectorProperties().get(0).clone(), new DiffractionCrystalEnvironment(f.getWavelength()));
		q.setResidual(f.value(f.getParameters()));
		return q;
	}

	/**
	 * Fit ellipses to a single detector with/without fixing wavelength.
	 * <p>
	 * The list of ROIs should be of equal size to the list of d-spacings and can have null
	 * entries if there are no corresponding figures on the detector
	 * @param mon
	 * @param detector
	 * @param env
	 * @param rois
	 * @param spacings
	 *            a list of spacings
	 * @param fixedWavelength 
	 * @return q-space
	 */
	public static QSpace fitAllEllipsesToQSpace(IMonitor mon, DetectorProperties detector, DiffractionCrystalEnvironment env, List<? extends IROI> rois, List<HKL> spacings, boolean fixedWavelength) {
		int n = rois.size();
		if (n != spacings.size()) { // always allow a choice to be made
			throw new IllegalArgumentException("Number of ellipses should be equal to spacings");
		}

		// build up list of ellipses and spacings
		boolean allCircles = true;
		List<EllipticalROI> ellipses = new ArrayList<EllipticalROI>();
		List<Double> s = new ArrayList<Double>();
		for (int i = 0; i < n; i++) {
			IROI r = rois.get(i);
			if (r instanceof CircularROI) {
				r = new EllipticalROI((CircularROI) r);
			} else if (!(r instanceof EllipticalROI))
				continue;
			EllipticalROI e = (EllipticalROI) r;
			ellipses.add(e);
			HKL d = spacings.get(i);
			s.add(d.getD().doubleValue(NonSI.ANGSTROM));
			logger.debug("    {}", e);
			if (allCircles && !e.isCircular()) {
				allCircles = false;
			}
		}
		n = ellipses.size();

		DetectorFitFunction f;
		if (allCircles) {
			logger.debug("All rings are circular");
			f = createQFitFunction4(ellipses, detector, env.getWavelength(), fixedWavelength);
		} else {
			f = createQFitFunction7(ellipses, detector, env.getWavelength(), fixedWavelength);
		}

		logger.debug("Init: {}", f.getInitial());

		if (mon != null) {
			mon.worked(1);
			if (mon.isCancelled())
				return null;
		}

		MultivariateOptimizer opt = FittingUtils.createOptimizer(f.getN());

		f.setSpacings(s);
		double res = FittingUtils.optimize(f, opt, Double.POSITIVE_INFINITY);

		if (mon != null) {
			mon.worked(10);
			if (mon.isCancelled())
				return null;
		}

		if (f.getParameters() == null) {
			logger.warn("Problem with fitting - as could not find a single fit!");
		}

		logger.debug("Parameters: w {}, D {}, e {} (min {})", new Object[] { f.getWavelength(), f.getDistance(), f.getNormalAngles(), res });
		logger.debug("Spacings used: {}", s);
		logger.debug("Residual value: {}", f.value(f.getParameters()));

		QSpace q = new QSpace(f.getDetectorProperties().get(0).clone(), new DiffractionCrystalEnvironment(f.getWavelength()));
		q.setResidual(f.value(f.getParameters()));
		return q;
	}



	private static double calcBaseRollAngle(List<EllipticalROI> ellipses) {
		int n = ellipses.size();

		double base = 0;
		for (int i = 0; i < n; i++) {
			double a = ellipses.get(i).getAngle();
			base += a > Math.PI ? a - FULL_CIRCLE : a; // ensure angle is in range +/- pi
		}
		return base/n;
	}

	/**
	 * Fit all ellipses for a list of detectors to a single wavelength that is initially fixed
	 * <p>
	 * Each list of ROIs should be of equal size to the list of d-spacings and can have null
	 * entries if there are no corresponding figures on the detector
	 * @param mon
	 * @param lDetectors
	 * @param env
	 * @param lROIs list containing lists of ROIs for each detector
	 * @param spacings
	 *            a list of spacings
	 * @param postFitChange if true then linearly fit wavelength after detector fits
	 * @return a list of q-spaces
	 */
	public static List<QSpace> fitAllEllipsesToAllQSpacesAtFixedWavelength(IMonitor mon, List<DetectorProperties> lDetectors, DiffractionCrystalEnvironment env, List<List<? extends IROI>> lROIs, List<HKL> spacings, boolean postFitChange) {
		int n = lDetectors.size();
		if (n != lROIs.size()) {
			throw new IllegalArgumentException("Number of detectors must match number of lists of ROIs");
		}

		List<QSpace> qs = new ArrayList<QSpace>();
		for (int i = 0; i < n; i++) {
			DetectorProperties dp = lDetectors.get(i);
			List<? extends IROI> rois = lROIs.get(i);
			QSpace q = null;
			try {
				q = fitAllEllipsesToQSpace(mon, dp, env, rois, spacings, true);
			} catch (IllegalArgumentException e) {
				logger.warn("Problem in calibrating image: {}", i, e);
			} 
			qs.add(q);
		}

		if (!postFitChange)
			return qs;

		List<Double> odist = new ArrayList<Double>();
		List<Double> ndist = new ArrayList<Double>();
		for (int i = 0; i < n; i++) {
			DetectorProperties dp = lDetectors.get(i);
			QSpace q = qs.get(i);
			odist.add(dp.getDetectorDistance());
			ndist.add(q.getDetectorProperties().getDetectorDistance());
		}
		if (odist.size() < 3) {
			logger.warn("Need to use three or more images");
			return qs;
		}

		Polynomial p;
		try {
			p = Fitter.polyFit(new Dataset[] {DatasetFactory.createFromList(odist)}, DatasetFactory.createFromList(ndist), 1e-15, 1);
		} catch (Exception e) {
			logger.error("Problem with fit", e);
			return qs;
		}
		logger.debug("Straight line fit: 0 {}, 1 {}", p.getParameter(0), p.getParameter(1));

		double l = env.getWavelength() * p.getParameterValue(0);
		for (int i = 0; i < n; i++) {
			qs.get(i).setDiffractionCrystalEnvironment(new DiffractionCrystalEnvironment(l));
		}
		return qs;
	}

	/**
	 * Fit all ellipses for a list of detectors to a single wavelength.
	 * <p>
	 * Each list of ROIs should be of equal size to the list of d-spacings and can have null
	 * entries if there are no corresponding figures on the detector
	 * @param mon
	 * @param lDetectors
	 * @param env
	 * @param lROIs list containing lists of ROIs for each detector
	 * @param spacings
	 *            a list of spacings
	 * @return a list of q-spaces
	 */
	public static List<QSpace> fitAllEllipsesToAllQSpaces(IMonitor mon, List<DetectorProperties> lDetectors, DiffractionCrystalEnvironment env, List<List<? extends IROI>> lROIs, List<HKL> spacings) {
		int m = lDetectors.size();

		if (lROIs.size() != m) {
			throw new IllegalArgumentException("Number of lists of ROIs should be equal to number of detectors/images");
		}

		// build up list of lists of ellipses and flattened list of spacings
		List<List<EllipticalROI>> lEllipses = new ArrayList<List<EllipticalROI>>();
		List<Double> s = new ArrayList<Double>();
		for (int j = 0; j < m; j++) {
			List<? extends IROI> rois = lROIs.get(j);
			int n = rois.size();
			if (n != spacings.size()) { // always allow a choice to be made
				throw new IllegalArgumentException("Number of ellipses should be equal to spacings");
			}
			List<EllipticalROI> ellipses = new ArrayList<EllipticalROI>();
			for (int i = 0; i < n; i++) {
				IROI r = rois.get(i);
				if (r instanceof CircularROI) {
					r = new EllipticalROI((CircularROI) r);
				} else if (!(r instanceof EllipticalROI))
					continue;
				EllipticalROI e = (EllipticalROI) r;
				ellipses.add(e);
				HKL d = spacings.get(i);
				s.add(d.getD().doubleValue(NonSI.ANGSTROM));
				logger.debug("    {}", e);
			}
			lEllipses.add(ellipses);
		}

		DetectorFitFunction f = createQFitFunctionForAllImages(lEllipses, lDetectors, env.getWavelength());

		logger.debug("Init: {}", f.getInitial());

		if (mon != null) {
			mon.worked(1);
			if (mon.isCancelled())
				return null;
		}

		MultivariateOptimizer opt = FittingUtils.createOptimizer(f.getN());
		f.setSpacings(s);
		double res = FittingUtils.optimize(f, opt, Double.POSITIVE_INFINITY);

		if (mon != null) {
			mon.worked(10);
			if (mon.isCancelled())
				return null;
		}

		if (f.getParameters() == null) {
			logger.warn("Problem with fitting - as could not find a single fit!");
		}

		logger.debug("Parameters: w {}, D {}, e {} (min {})", new Object[] { f.getWavelength(), f.getDistances(), f.getNormalAngles(), res });
		logger.debug("Spacings used: {}", s);
		logger.debug("Residual value: {}", f.value(f.getParameters()));

		List<QSpace> qs = new ArrayList<QSpace>();
		DiffractionCrystalEnvironment nEnv = new DiffractionCrystalEnvironment(f.getWavelength());

		for (DetectorProperties d : f.getDetectorProperties()) {
			QSpace q = new QSpace(d.clone(), nEnv.clone());
			q.setResidual(f.value(f.getParameters()));
			qs.add(q);
		}

		return qs;
	}

	/**
	 * Create function which uses 6/7 parameters: wavelength (Angstrom), detector origin (mm), orientation angles (degrees)
	 */
	static DetectorFitFunction createQFitFunction7(List<EllipticalROI> ellipses, DetectorProperties dp, double wavelength, boolean fixedWavelength) {
		int n = ellipses.size();
		double[][] known = new double[n][FitFunctionBase.nC];
		double[] weight = new double[n];

		double base = -calcBaseRollAngle(ellipses);
		logger.debug("Mean roll angle: {}", Math.toDegrees(base));

		for (int i = 0; i < n; i++) {
			EllipticalROI e = ellipses.get(i);
			weight[i] = e.getSemiAxis(0);
			int j = 0;
			double a = base - e.getAngle();
			for (double off : FitFunctionBase.angles) {
				double[] pt = e.getPoint(a + off);
				known[i][j++] = pt[0];
				known[i][j++] = pt[1];
			}
		}

		DetectorFitFunction f;
		Vector3d o = dp.getOrigin();
		double[] a = dp.getNormalAnglesInDegrees();
		if (fixedWavelength) {
			f = new QSpaceFitFixedWFunction7(known, weight, dp.getVPxSize(), wavelength);
			f.setInitial(o.getX(), o.getY(), o.getZ(), a[0], a[1], a[2]);
		} else {
			f = new QSpaceFitFunction7(known, weight, dp.getVPxSize());
			f.setInitial(wavelength, o.getX(), o.getY(), o.getZ(), a[0], a[1], a[2]);
		}
		f.setBaseRollAngle(base);
		return f;
	}

	/**
	 * Create function which uses 3/4 parameters: wavelength (Angstrom), detector origin (mm)
	 */
	static DetectorFitFunction createQFitFunction4(List<EllipticalROI> ellipses, DetectorProperties dp, double wavelength, boolean fixedWavelength) {
		int n = ellipses.size();
		double[][] known = new double[n][FitFunctionBase.nC];
		double[] weight = new double[n];

		for (int i = 0; i < n; i++) {
			EllipticalROI e = ellipses.get(i);
			weight[i] = e.getSemiAxis(0);
			int j = 0;
			for (double off : FitFunctionBase.angles) {
				double[] pt = e.getPoint(off);
				known[i][j++] = pt[0];
				known[i][j++] = pt[1];
			}
		}

		DetectorFitFunction f;
		Vector3d o = dp.getOrigin();
		if (fixedWavelength) {
			f = new QSpaceFitFixedWFunction4(known, weight, dp.getVPxSize(), wavelength);
			f.setInitial(o.getX(), o.getY(), o.getZ());
		} else {
			f = new QSpaceFitFunction4(known, weight, dp.getVPxSize());
			f.setInitial(wavelength, o.getX(), o.getY(), o.getZ());
		}
		f.setBaseRollAngle(ellipses.get(0).getAngle());
		return f;
	}

	/**
	 * Create function which uses 6N+1 parameters: wavelength (Angstrom), and per image, detector origin (mm), orientation angles (degrees)
	 */
	static DetectorFitFunction createQFitFunctionForAllImages(List<List<EllipticalROI>> lEllipses, List<DetectorProperties> lDP, double wavelength) {
		int m = lEllipses.size();
		if (lDP.size() != m) {
			throw new IllegalArgumentException("Number of lists of ellipses should be equal to number of detectors");
		}

		double[][][] allKnowns = new double[m][][];
		double[][] allWeights = new double[m][];
		double[] bases = new double[m];
		double[] init = new double[6*m+1];

		int l = 0;
		init[l++] = wavelength;
		for (int k = 0; k < m; k++) {
			List<EllipticalROI> ellipses = lEllipses.get(k);
			DetectorProperties dp = lDP.get(k);
			double dist = dp.getBeamCentreDistance();
			int n = ellipses.size();
			double[][] known = new double[n][FitFunctionBase.nC];
			allKnowns[k] = known;
			double[] weight = new double[n];
			allWeights[k] = weight;
			
			double base = -calcBaseRollAngle(ellipses);
			bases[k] = base;
			logger.debug("Mean roll angle: {}", Math.toDegrees(base));

			for (int i = 0; i < n; i++) {
				EllipticalROI e = ellipses.get(i);
				weight[i] = e.getSemiAxis(0)/dist;
				int j = 0;
				double a = base - e.getAngle();
				for (double off : FitFunctionBase.angles) {
					double[] pt = e.getPoint(a + off);
					known[i][j++] = pt[0];
					known[i][j++] = pt[1];
				}
			}
			Vector3d o = dp.getOrigin();
			init[l++] = o.getX();
			init[l++] = o.getY();
			init[l++] = o.getZ();
			double[] a = dp.getNormalAnglesInDegrees();
			init[l++] = a[0];
			init[l++] = a[1];
			init[l++] = a[2];
		}

		DetectorFitFunction f = new QSpacesFitFunction(allKnowns, allWeights, lDP.get(0).getVPxSize());
		f.setInitial(init);
		f.setBaseRollAngles(bases);
		return f;
	}

	interface DetectorFitFunction extends FittingUtils.FitFunction {
		public double[] getNormalAngles();

		/**
		 * @return wavelength (in Angstrom)
		 */
		public double getWavelength();

		/**
		 * @return distance (in mm)
		 */
		public double getDistance();

		public double[] getDistances();

		/**
		 * @param spacings (in Angstrom)
		 */
		public void setSpacings(List<Double> spacings);

		public void setSigma(double[] sigma);

		/**
		 * @param baseAngle (in radians)
		 */
		public void setBaseRollAngle(double baseAngle);

		/**
		 * @param baseAngles (in radians)
		 */
		public void setBaseRollAngles(double[] baseAngles);

		/**
		 * 
		 * @return list of detector properties fitted
		 */
		public List<DetectorProperties> getDetectorProperties();
	}

	static abstract class FitFunctionBase implements DetectorFitFunction {
		private double[] initial;
		protected double[] spacing; // in Angstroms

		protected int nR; // number of rings to fit
//		protected int nV; // number of calculated internally values
		protected double[] angle;
		protected double[] base;
		protected double[] parameters;
		protected SimpleBounds bounds;
		protected double[] sigma;
		protected int n; // number of parameters
		protected double maxWlen; // maximum wavelength allowed, in Angstroms

		protected static final double WAVE_MIN = 5e-2; // 0.05A
		protected static final double WAVE_MAX = 10; // 10.0A
		protected static final double DIST_MIN = 10;   // (in mm)
		protected static final double DIST_MAX = 2e5;   // (in mm)

		protected static final double SIGMA_WAVE = 2e-3; // 0.002A
		protected static final double SIGMA_POSN = 3; // (in mm)
		protected static final double SIGMA_ANG  = 6; // (in degrees)

		// points are selected from given offset angles to fit to on each ring
//		protected static final double[] angles = {0, RIGHT_ANGLE, Math.PI, -RIGHT_ANGLE}; // offset angles
		protected static final double[] angles = {0, Math.PI/3, 2*Math.PI/3, Math.PI, -2*Math.PI/3, -Math.PI/3}; // offset angles
//		protected static final double[] angles = {0, Math.PI/6, Math.PI/3, Math.PI/2, 2*Math.PI/3, 5*Math.PI/6, Math.PI, -5*Math.PI/6, -2*Math.PI/3, -Math.PI/2, -Math.PI/3, -Math.PI/6}; // offset angles
		protected static final int nC = 2 * angles.length; // number of coordinate values per ring

		@Override
		public int getN() {
			return n;
		}

		@Override
		public void setParameters(double[] arg) {
			parameters = arg;
		}

		@Override
		public double[] getParameters() {
			return parameters;
		}

		@Override
		public double[] getSigma() {
			return sigma;
		}

		@Override
		public void setSigma(double[] sigma) {
			this.sigma = sigma;
		}

		@Override
		public SimpleBounds getBounds() {
			return bounds;
		}

		@Override
		public void setInitial(double... init) {
			initial = init;
		}

		@Override
		public double[] getInitial() {
			return initial;
		}

		@Override
		public void setBaseRollAngle(double baseAngle) {
			base[0] = baseAngle;
		}

		@Override
		public double[] getNormalAngles() {
			return null;
		}

		@Override
		public void setSpacings(List<Double> spacings) {
			double min = Double.POSITIVE_INFINITY;
			for (int i = 0; i < nR; i++) {
				spacing[i] = spacings.get(i);
				if (spacing[i] < min) {
					min = spacing[i];
				}
			}
			// wavelength must be smaller than twice the smallest spacing
			/*
			 * l/(2d_i) = sin(2t) < 1
			 * l < 2 d_i
			 * => l < 2 d_min = l_max for any solution of Bragg's law
			 */
			maxWlen = 2 * min;
		}

		@Override
		public void setBaseRollAngles(double[] baseAngles) {
			base = baseAngles;
		}
	}

	/**
	 * LS function uses 7 parameters: wavelength (Angstrom), detector origin (mm), orientation angles (degrees)
	 */
	static class QSpaceFitFunction7 extends FitFunctionBase {
		protected DetectorProperties dp;
		private double[][] target;
		private double[] weight;

		public QSpaceFitFunction7(double[][] known, double[] weight, double pix) {
			n = 7;
			nR = known.length;
			target = known;
			spacing = new double[nR];
			bounds = new SimpleBounds(new double[] {WAVE_MIN, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, DIST_MIN,
					-90, -90, -180}, new double[] {WAVE_MAX, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, DIST_MAX,
					90, 90, 180});
			sigma = new double[] {SIGMA_WAVE, SIGMA_POSN, SIGMA_POSN, SIGMA_POSN, SIGMA_ANG, SIGMA_ANG, SIGMA_ANG};
			dp = new DetectorProperties();
			dp.setBeamVector(new Vector3d(0,  0, 1));
			dp.setHPxSize(pix);
			dp.setVPxSize(pix);
			dp.setOrigin(new Vector3d(0, 0, 200));
			base = new double[1];

			if (weight.length != nR) {
				throw new IllegalArgumentException("Weight array should have length to match number of rings");
			}
			this.weight = weight;
			double w = 0; // normalise weights
			for (int i = 0; i < nR; i++) {
				w += weight[i] * nC;
			}
			for (int i = 0; i < nR; i++) {
				weight[i] /= w;
			}
		}

		@Override
		public double getWavelength() {
			return parameters[0];
		}

		protected void setDetector(int off, double... arg) {
			dp.setNormalAnglesInDegrees(arg[off+3], arg[off+4], arg[off+5]);
			dp.setOrigin(new Vector3d(arg[off], arg[off+1], arg[off+2]));
		}

		@Override
		public double getDistance() {
			setDetector(1, parameters);
			return dp.getDetectorDistance();
		}

		@Override
		public double[] getDistances() {
			return new double[] {getDistance()};
		}

		@Override
		public double[] getNormalAngles() {
			setDetector(1, parameters);
			return dp.getNormalAnglesInDegrees();
		}

		@Override
		public List<DetectorProperties> getDetectorProperties() {
			setDetector(1, parameters);
			List<DetectorProperties> l = new ArrayList<DetectorProperties>();
			l.add(dp);
			return l;
		}

		double calcValue(double wlen, double... arg) {
			if (wlen > maxWlen) {
				return Double.POSITIVE_INFINITY;
			}
			setDetector(0, arg);
			double s = 0;
			boolean any = false;
			for (int i = 0; i < nR; i++) {
				IROI r;
				try {
					r = DSpacing.conicFromDSpacing(dp, wlen, spacing[i]);
				} catch (Exception e) {
					return Double.POSITIVE_INFINITY;
				}
				if (r instanceof EllipticalROI) {
					any = true;
					EllipticalROI ell = (EllipticalROI) r;
					double a = base[0] - ell.getAngle();
					double[] pt = target[i];
					int j = 0;
					double t = 0;
					for (double off : angles) {
						double[] pv = ell.getPoint(a + off);
						double u = pv[0] - pt[j++];
						t += u * u;
						u = pv[1] - pt[j++];
						t += u * u;
					}
					s += t*weight[i];
				}
			}
//			System.err.println(s + "; w=" + wlen + "; d=" + dp); // XXX
			return any ? s : Double.POSITIVE_INFINITY;
		}

		@Override
		public double value(double[] arg) {
			return calcValue(arg[0], arg[1], arg[2], arg[3], arg[4], arg[5], arg[6]);
		}
	}

	/**
	 * LS function uses 6 parameters: detector origin (mm), orientation angles (degrees)
	 */
	static class QSpaceFitFixedWFunction7 extends QSpaceFitFunction7 {
		protected double lambda;

		public QSpaceFitFixedWFunction7(double[][] known, double[] weight, double pix, double wavelength) {
			super(known, weight, pix);
			n = 6;
			int bl = bounds.getLower().length;
			bounds = new SimpleBounds(Arrays.copyOfRange(bounds.getLower(), 1, bl),
					Arrays.copyOfRange(bounds.getUpper(), 1, bl));
			sigma = Arrays.copyOfRange(sigma, 1, sigma.length);
			lambda = wavelength;
		}

		@Override
		public double getWavelength() {
			return lambda;
		}

		@Override
		public double getDistance() {
			setDetector(0, parameters);
			return dp.getDetectorDistance();
		}

		@Override
		public double[] getNormalAngles() {
			setDetector(0, parameters);
			return dp.getNormalAnglesInDegrees();
		}

		@Override
		public List<DetectorProperties> getDetectorProperties() {
			setDetector(0, parameters);
			List<DetectorProperties> l = new ArrayList<DetectorProperties>();
			l.add(dp);
			return l;
		}

		@Override
		public double value(double[] arg) {
			return calcValue(lambda, arg);
		}
	}

	/**
	 * LS function uses 4 parameters: wavelength (Angstrom), detector origin (mm)
	 */
	static class QSpaceFitFunction4 extends QSpaceFitFunction7 {
		public QSpaceFitFunction4(double[][] known, double[] weight, double pix) {
			super(known, weight, pix);
			n = 4;
			bounds = new SimpleBounds(new double[] {WAVE_MIN, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, DIST_MIN},
					new double[] {WAVE_MAX, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, DIST_MAX});
			sigma = new double[] {SIGMA_WAVE, SIGMA_POSN, SIGMA_POSN, SIGMA_POSN};
		}

		@Override
		protected void setDetector(int off, double... arg) {
			dp.setNormalAnglesInDegrees(0, 0, Math.toDegrees(base[0])); // need to correct for roll
			dp.setOrigin(new Vector3d(arg[off], arg[off+1], arg[off+2]));
		}

		@Override
		public double[] getNormalAngles() {
			return new double[3];
		}

		@Override
		public double value(double[] arg) {
			return calcValue(arg[0], arg[1], arg[2], arg[3]);
		}
	}

	/**
	 * LS function uses 3 parameters: detector origin (mm)
	 */
	static class QSpaceFitFixedWFunction4 extends QSpaceFitFunction4 {
		protected double lambda;

		public QSpaceFitFixedWFunction4(double[][] known, double[] weight, double pix, double wavelength) {
			super(known, weight, pix);
			n = 3;
			int bl = bounds.getLower().length;
			bounds = new SimpleBounds(Arrays.copyOfRange(bounds.getLower(), 1, bl),
					Arrays.copyOfRange(bounds.getUpper(), 1, bl));
			sigma = Arrays.copyOfRange(sigma, 1, sigma.length);
			lambda = wavelength;
		}

		@Override
		public double getWavelength() {
			return lambda;
		}

		@Override
		public double getDistance() {
			setDetector(0, parameters);
			return dp.getDetectorDistance();
		}

		@Override
		public List<DetectorProperties> getDetectorProperties() {
			setDetector(0, parameters);
			List<DetectorProperties> l = new ArrayList<DetectorProperties>();
			l.add(dp);
			return l;
		}

		@Override
		public double value(double[] arg) {
			return calcValue(lambda, arg);
		}
	}

	/**
	 * LS function uses 6I+1 parameters: wavelength (Angstrom), per image, detector origin (mm), orientation angles (degrees)
	 */
	static class QSpacesFitFunction extends FitFunctionBase {
		protected List<DetectorProperties> dps;
		private double[][][] target;
		private double[][] weight;

		public QSpacesFitFunction(double[][][] known, double[][] weight, double pix) {
			target = known;
			int m = known.length;
			n = 6*m + 1;
			nR = 0;
			double[] lb = new double[n];
			double[] ub = new double[n];
			sigma = new double[n];
			dps = new ArrayList<DetectorProperties>();
			int j = 0;
			lb[j] = WAVE_MIN;
			ub[j] = WAVE_MAX;
			sigma[j++] = SIGMA_WAVE;
			DetectorProperties dp;
			for (int k = 0; k < m; k++) {
				nR += known[k].length;
				lb[j] = Double.NEGATIVE_INFINITY;
				ub[j] = Double.POSITIVE_INFINITY;
				sigma[j++] = SIGMA_POSN;
				lb[j] = Double.NEGATIVE_INFINITY;
				ub[j] = Double.POSITIVE_INFINITY;
				sigma[j++] = SIGMA_POSN;
				lb[j] = DIST_MIN;
				ub[j] = DIST_MAX;
				sigma[j++] = SIGMA_POSN;
				lb[j] = -90;
				ub[j] = 90;
				sigma[j++] = SIGMA_ANG;
				lb[j] = -90;
				ub[j] = 90;
				sigma[j++] = SIGMA_ANG;
				lb[j] = -180;
				ub[j] = 180;
				sigma[j++] = SIGMA_ANG;
				dp = new DetectorProperties();
				dp.setBeamVector(new Vector3d(0,  0, 1));
				dp.setHPxSize(pix);
				dp.setVPxSize(pix);
				dp.setOrigin(new Vector3d(0,  0, 200));
				dps.add(dp);
			}

			if (weight.length != m) {
				throw new IllegalArgumentException("Weight array should have length to match number of images");
			}
			this.weight = weight;
			double w = 0; // normalise weights
			for (int k = 0; k < m; k++) {
				double[] wgt = weight[k];
				int n = wgt.length;
				if (n != known[k].length) {
					throw new IllegalArgumentException("Weight array should have length to match number of rings");
				}
				for (int i = 0; i < n; i++) {
					w += wgt[i] * nC;
				}
			}
			for (int k = 0; k < m; k++) {
				double[] wgt = weight[k];
				int n = wgt.length;
				for (int i = 0; i < n; i++) {
					wgt[i] /= w;
				}
			}

			spacing = new double[nR];
			bounds = new SimpleBounds(lb, ub);
		}

		@Override
		public double getWavelength() {
			return parameters[0];
		}

		protected void setDetector(int off, double... arg) {
			int i = off;
			for (DetectorProperties dp : dps) {
				int j = i;
				i += 3;
				dp.setNormalAnglesInDegrees(arg[i++], arg[i++], arg[i++]);
				dp.setOrigin(new Vector3d(arg[j++], arg[j++], arg[j++]));
			}
		}

		@Override
		public double getDistance() {
			setDetector(1, parameters);
			return dps.get(0).getDetectorDistance();
		}

		@Override
		public double[] getDistances() {
			setDetector(1, parameters);
			int m = dps.size();
			double[] ds = new double[m];
			for (int k = 0; k < m; k++) {
				ds[k] = dps.get(k).getDetectorDistance();
			}
			return ds;
		}

		@Override
		public double[] getNormalAngles() {
			setDetector(1, parameters);
			int m = dps.size();
			double[] as = new double[3 * m];
			int i = 0;
			for (int k = 0; k < m; k++) {
				double[] a = dps.get(k).getNormalAnglesInDegrees();
				as[i++] = a[0];
				as[i++] = a[1];
				as[i++] = a[2];
			}
			return as;
		}

		@Override
		public List<DetectorProperties> getDetectorProperties() {
			setDetector(1, parameters);
			return dps;
		}

		double calcValue(double wlen, double... arg) {
			if (wlen > maxWlen) {
				return Double.POSITIVE_INFINITY;
			}
			setDetector(0, arg);
			double s = 0;
			boolean any = false;
			int m = target.length;

			int i = 0;
			for (int k = 0; k < m; k++) {
				double[][] tgt = target[k];
				double[] wgt = weight[k];
				int nr = tgt.length;
				DetectorProperties dp = dps.get(k);
				for (int l = 0; l < nr; l++) {
					IROI r;
					try {
						r = DSpacing.conicFromDSpacing(dp, wlen, spacing[i + l]);
					} catch (Exception e) {
						return Double.POSITIVE_INFINITY;
					}
					if (r instanceof EllipticalROI) {
						any = true;
						EllipticalROI ell = (EllipticalROI) r;
						double a = base[k] - ell.getAngle();
						double[] pt = tgt[l];
						int j = 0;
						double t = 0;
						for (double off: angles) {
							double[] pv = ell.getPoint(a + off);
							double u = pv[0] - pt[j++];
							t += u * u;
							u = pv[1] - pt[j++];
							t += u * u;
						}
						s += t * wgt[l];
					}
				}
//				System.err.println(s + "; w=" + wlen + "; d=" + dp); // XXX
				i += nr;
			}
			return any ? s : Double.POSITIVE_INFINITY;
		}

		@Override
		public double value(double[] arg) {
			double[] a = Arrays.copyOfRange(arg, 1, arg.length);
			return calcValue(arg[0], a);
		}
	}

}
