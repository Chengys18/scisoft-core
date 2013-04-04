/*
 * Copyright 2011 Diamond Light Source Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.ac.diamond.scisoft.analysis.dataset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

import org.apache.commons.math.complex.Complex;
import org.apache.commons.math.stat.descriptive.moment.FourthMoment;
import org.apache.commons.math.stat.descriptive.moment.Kurtosis;
import org.apache.commons.math.stat.descriptive.moment.Skewness;


/**
 * Statistics class
 */
public class Stats {
	// calculates statistics and returns sorted dataset (0th element if compound)
	private static AbstractDataset calcQuartileStats(final AbstractDataset a) {
		AbstractDataset s = null;
		final int is = a.getElementsPerItem();

		if (is == 1) {
			s = DatasetUtils.sort(a, null);
		
			a.setStoredValue("median", Double.valueOf(pQuantile(s, 0.5)));
			a.setStoredValue("quartile1", Double.valueOf(pQuantile(s, 0.25)));
			a.setStoredValue("quartile3", Double.valueOf(pQuantile(s, 0.75)));
		} else {
			AbstractDataset w = AbstractDataset.zeros(a.shape, a.getDtype());
			a.setStoredValue("median", new double[is]);
			a.setStoredValue("quartile1", new double[is]);
			a.setStoredValue("quartile3", new double[is]);
			for (int j = 0; j < is; j++) {
				((AbstractCompoundDataset) a).copyElements(w, j);
				w.sort(null);

				double[] store;
				store = (double[]) a.getStoredValue("median");
				store[j] = pQuantile(w, 0.5);
				store = (double[]) a.getStoredValue("quartile1");
				store[j] = pQuantile(w, 0.25);
				store = (double[]) a.getStoredValue("quartile3");
				store[j] = pQuantile(w, 0.75);
				if (j == 0)
					s = w.clone();
			}
		}
		return s;
	}

	static private Object getQStatistics(final AbstractDataset a, final String stat) {
		Object m = a.getStoredValue(stat);
		if (m == null) {
			calcQuartileStats(a);
			m = a.getStoredValue(stat);
		}
		return m;
	}

	static private AbstractDataset getQStatistics(final AbstractDataset a, int axis, final String stat) {
		axis = a.checkAxis(axis);
		Object obj = a.getStoredValue(stat);
		final int is = a.getElementsPerItem();

		if (obj == null) {
			if (is == 1) {
				AbstractDataset s = DatasetUtils.sort(a, axis);

				a.setStoredValue("median-" + axis, pQuantile(s, axis, 0.5));
				a.setStoredValue("quartile1-" + axis, pQuantile(s, axis, 0.25));
				a.setStoredValue("quartile3-" + axis, pQuantile(s, axis, 0.75));
			} else {
				AbstractDataset w = AbstractDataset.zeros(a.shape, a.getDtype());
				for (int j = 0; j < is; j++) {
					((AbstractCompoundDataset) a).copyElements(w, j);
					w.sort(axis);

					CompoundDoubleDataset s;
					final AbstractDataset c = pQuantile(w, axis, 0.5);
					if (j == 0) {
						s = (CompoundDoubleDataset) AbstractDataset.zeros(is, c.shape, c.getDtype());
						a.setStoredValue("median-" + axis, s);
						s = (CompoundDoubleDataset) AbstractDataset.zeros(is, c.shape, c.getDtype());
						a.setStoredValue("quartile1-" + axis, s);
						s = (CompoundDoubleDataset) AbstractDataset.zeros(is, c.shape, c.getDtype());
						a.setStoredValue("quartile3-" + axis, s);
					}
					s = (CompoundDoubleDataset) a.getStoredValue("median-" + axis);
					s.setElements(c, j);

					s = (CompoundDoubleDataset) a.getStoredValue("quartile1-" + axis);
					s.setElements(pQuantile(w, axis, 0.25), j);

					s = (CompoundDoubleDataset) a.getStoredValue("quartile3-" + axis);
					s.setElements(pQuantile(w, axis, 0.75), j);
				}
			}
			obj = a.getStoredValue(stat);
		}

		return (AbstractDataset) obj;
	}

	// process a sorted dataset
	private static double pQuantile(final AbstractDataset s, final double q) {
		double f = (s.size - 1) * q; // fraction of sample number
		if (f < 0)
			return Double.NaN;
		int qpt = (int) Math.floor(f); // quantile point
		f -= qpt;

		double quantile = s.getElementDoubleAbs(qpt);
		if (f > 0) {
			quantile = (1-f)*quantile + f*s.getElementDoubleAbs(qpt+1);
		}
		return quantile;
	}

	// process a sorted dataset and returns a double or compound double dataset
	private static AbstractDataset pQuantile(final AbstractDataset s, final int axis, final double q) {
		final int rank = s.getRank();
		final int is = s.getElementsPerItem();

		int[] oshape = s.getShape();

		double f = (oshape[axis] - 1) * q; // fraction of sample number
		int qpt = (int) Math.floor(f); // quantile point
		f -= qpt;

		oshape[axis] = 1;
		int[] qshape = AbstractDataset.squeezeShape(oshape, false);
		AbstractDataset qds = AbstractDataset.zeros(is, qshape, AbstractDataset.FLOAT64);

		IndexIterator qiter = qds.getIterator(true);
		int[] qpos = qiter.getPos();
		int[] spos = oshape;

		while (qiter.hasNext()) {
			int i = 0;
			for (; i < axis; i++) {
				spos[i] = qpos[i];
			}
			spos[i++] = qpt;
			for (; i < rank; i++) {
				spos[i] = qpos[i-1];
			}

			Object obj = s.getObject(spos);
			qds.set(obj, qpos);
		}

		if (f > 0) {
			qiter = qds.getIterator(true);
			qpos = qiter.getPos();
			qpt++;
			AbstractDataset rds = AbstractDataset.zeros(is, qshape, AbstractDataset.FLOAT64);
			
			while (qiter.hasNext()) {
				int i = 0;
				for (; i < axis; i++) {
					spos[i] = qpos[i];
				}
				spos[i++] = qpt;
				for (; i < rank; i++) {
					spos[i] = qpos[i-1];
				}

				Object obj = s.getObject(spos);
				rds.set(obj, qpos);
			}
			rds.imultiply(f);
			qds.imultiply(1-f);
			qds.iadd(rds);
		}

		return qds;
	}

	/**
	 * Calculate quantile of dataset which is defined as the inverse of the cumulative distribution function (CDF)
	 * @param a
	 * @param q
	 * @return point at which CDF has value q
	 */
	public static double quantile(final AbstractDataset a, final double q) {
		if (q < 0 || q > 1) {
			throw new IllegalArgumentException("Quantile requested is outside [0,1]");
		}
		final AbstractDataset s = calcQuartileStats(a);
		return pQuantile(s, q);
	}

	/**
	 * Calculate quantiles of dataset which is defined as the inverse of the cumulative distribution function (CDF)
	 * @param a
	 * @param values
	 * @return points at which CDF has given values
	 */
	public static double[] quantile(final AbstractDataset a, final double... values) {
		final double[] points  = new double[values.length];
		final AbstractDataset s = calcQuartileStats(a);
		for (int i = 0; i < points.length; i++) {
			final double q = values[i];
			if (q < 0 || q > 1) {
				throw new IllegalArgumentException("Quantile requested is outside [0,1]");
			}
			points[i] = pQuantile(s, q);
		}
		return points;
	}

	/**
	 * Calculate quantiles of dataset which is defined as the inverse of the cumulative distribution function (CDF)
	 * @param a
	 * @param axis
	 * @param values
	 * @return points at which CDF has given values
	 */
	public static AbstractDataset[] quantile(final AbstractDataset a, final int axis, final double... values) {
		final AbstractDataset[] points  = new AbstractDataset[values.length];
		final int is = a.getElementsPerItem();

		if (is == 1) {
			AbstractDataset s = DatasetUtils.sort(a, axis);
			for (int i = 0; i < points.length; i++) {
				final double q = values[i];
				if (q < 0 || q > 1) {
					throw new IllegalArgumentException("Quantile requested is outside [0,1]");
				}
				points[i] = pQuantile(s, axis, q);
			}
		} else {
			AbstractDataset w = AbstractDataset.zeros(a.shape, a.getDtype());
			for (int j = 0; j < is; j++) {
				((AbstractCompoundDataset) a).copyElements(w, j);
				w.sort(axis);

				for (int i = 0; i < points.length; i++) {
					final double q = values[i];
					if (q < 0 || q > 1) {
						throw new IllegalArgumentException("Quantile requested is outside [0,1]");
					}
					final AbstractDataset c = pQuantile(w, axis, q);
					if (j == 0) {
						points[i] = AbstractDataset.zeros(is, c.shape, c.getDtype());
					}
					((CompoundDoubleDataset) points[i]).setElements(c, j);
				}
			}
		}

		return points;
	}

	/**
	 * @param a dataset
	 * @param axis
	 * @return median
	 */
	public static AbstractDataset median(final AbstractDataset a, final int axis) {
		return getQStatistics(a, axis, "median-" + axis);
	}

	/**
	 * @param a dataset
	 * @return median
	 */
	public static Object median(final AbstractDataset a) {
		return getQStatistics(a, "median");
	}

	/**
	 * Interquartile range: Q3 - Q1
	 * @param a
	 * @return range
	 */
	public static Object iqr(final AbstractDataset a) {
		final int is = a.getElementsPerItem();
		if (is == 1) {
			double q3 = ((Double) getQStatistics(a, "quartile3"));
			return Double.valueOf(q3 - ((Double) a.getStoredValue("quartile1")).doubleValue());
		}

		double[] q1 = (double[]) getQStatistics(a, "quartile1");
		double[] q3 = (double[]) getQStatistics(a, "quartile3");
		for (int j = 0; j < is; j++) {
			q3[j] -= q1[j];
		}
		return q3;
	}

	/**
	 * Interquartile range: Q3 - Q1
	 * @param a
	 * @param axis
	 * @return range
	 */
	public static AbstractDataset iqr(final AbstractDataset a, final int axis) {
		AbstractDataset q3 = getQStatistics(a, axis, "quartile3-" + axis);

		return Maths.subtract(q3, a.getStoredValue("quartile1-" + axis));
	}

	static private Object getFourthMoment(final AbstractDataset a, final boolean ignoreNaNs) {
		String n = AbstractDataset.storeName(ignoreNaNs, "moment4");
		Object obj = a.getStoredValue(n);
		if (obj == null) {
			final int is = a.getElementsPerItem();
			final IndexIterator iter = a.getIterator();

			if (is == 1) {
				FourthMoment moment = new FourthMoment();
				if (ignoreNaNs) {
					while (iter.hasNext()) {
						final double x = a.getElementDoubleAbs(iter.index);
						if (Double.isNaN(x))
							continue;
						moment.increment(x);
					}
				} else {
					while (iter.hasNext()) {
						moment.increment(a.getElementDoubleAbs(iter.index));
					}
				}
				a.setStoredValue(n, moment);
			} else {
				FourthMoment[] moments = new FourthMoment[is];

				for (int j = 0; j < is; j++) {
					moments[j] = new FourthMoment();
				}
				if (ignoreNaNs) {
					while (iter.hasNext()) {
						boolean skip = false;
						for (int j = 0; j < is; j++) {
							if (Double.isNaN(a.getElementDoubleAbs(iter.index + j))) {
								skip = true;
								break;
							}
						}
						if (!skip)
							for (int j = 0; j < is; j++)
								moments[j].increment(a.getElementDoubleAbs(iter.index + j));
					}
				} else {
					while (iter.hasNext()) {
						for (int j = 0; j < is; j++)
							moments[j].increment(a.getElementDoubleAbs(iter.index + j));
					}
				}
				a.setStoredValue(n, moments);
			}
			obj = a.getStoredValue(n);
		}

		return obj;
	}

	/**
	 * See {@link #skewness(AbstractDataset a, boolean ignoreNaNs)} with ignoreNaNs = false
	 * @param a dataset
	 * @return skewness
	 */
	public static Object skewness(final AbstractDataset a) {
		return skewness(a, false);
	}

	/**
	 * @param a dataset
	 * @param ignoreNaNs if true, skip NaNs
	 * @return skewness
	 */
	public static Object skewness(final AbstractDataset a, final boolean ignoreNaNs) {
		Object m = getFourthMoment(a, ignoreNaNs);
		if (m instanceof FourthMoment) {
			return Double.valueOf((new Skewness((FourthMoment) m)).getResult());
		}

		FourthMoment[] mos = (FourthMoment[]) m;
		final int is = mos.length;
		double[] skews = new double[is];
		for (int j = 0; j < is; j++) {
			skews[j] = (new Skewness(mos[j])).getResult();
		}
		return skews;
	}

	/**
	 * See {@link #kurtosis(AbstractDataset a, boolean ignoreNaNs)} with ignoreNaNs = false
	 * @param a dataset
	 * @return kurtosis
	 */
	public static Object kurtosis(final AbstractDataset a) {
		return kurtosis(a, false);
	}

	/**
	 * @param a dataset
	 * @param ignoreNaNs if true, skip NaNs
	 * @return kurtosis
	 */
	public static Object kurtosis(final AbstractDataset a, final boolean ignoreNaNs) {
		Object m = getFourthMoment(a, ignoreNaNs);
		if (m instanceof FourthMoment) {
			return Double.valueOf((new Kurtosis((FourthMoment) m)).getResult());
		}

		FourthMoment[] mos = (FourthMoment[]) m;
		final int is = mos.length;
		double[] kurts = new double[is];
		for (int j = 0; j < is; j++) {
			kurts[j] = (new Kurtosis(mos[j])).getResult();
		}
		return kurts;
	}

	static private void calculateHigherMoments(final AbstractDataset a, final boolean ignoreNaNs, final int axis) {
		final int rank = a.getRank();
		final int is = a.getElementsPerItem();
		final int[] oshape = a.getShape();
		final int alen = oshape[axis];
		oshape[axis] = 1;

		final int[] nshape = AbstractDataset.squeezeShape(oshape, false);
		final AbstractDataset sk;
		final AbstractDataset ku;


		if (is == 1) {
			sk = new DoubleDataset(nshape);
			ku = new DoubleDataset(nshape);
			final IndexIterator qiter = sk.getIterator(true);
			final int[] qpos = qiter.getPos();
			final int[] spos = oshape;

			while (qiter.hasNext()) {
				int i = 0;
				for (; i < axis; i++) {
					spos[i] = qpos[i];
				}
				spos[i++] = 0;
				for (; i < rank; i++) {
					spos[i] = qpos[i - 1];
				}

				FourthMoment moment = new FourthMoment();
				if (ignoreNaNs) {
					for (int j = 0; j < alen; j++) {
						spos[axis] = j;
						final double val = a.getDouble(spos);
						if (Double.isNaN(val))
							continue;

						moment.increment(val);
					}
				} else {
					for (int j = 0; j < alen; j++) {
						spos[axis] = j;
						moment.increment(a.getDouble(spos));
					}
				}
				sk.set((new Skewness(moment)).getResult(), spos);
				ku.set((new Kurtosis(moment)).getResult(), spos);
			}
		} else {
			sk = new CompoundDoubleDataset(is, nshape);
			ku = new CompoundDoubleDataset(is, nshape);
			final IndexIterator qiter = sk.getIterator(true);
			final int[] qpos = qiter.getPos();
			final int[] spos = oshape;
			final double[] s = new double[is];
			final double[] k = new double[is];

			while (qiter.hasNext()) {
				int i = 0;
				for (; i < axis; i++) {
					spos[i] = qpos[i];
				}
				spos[i++] = 0;
				for (; i < rank; i++) {
					spos[i] = qpos[i-1];
				}
				FourthMoment[] moments = new FourthMoment[is];

				for (int j = 0; j < is; j++) {
					moments[j] = new FourthMoment();
				}
				int index = a.get1DIndex(spos);
				if (ignoreNaNs) {
					boolean skip = false;
					for (int j = 0; j < is; j++) {
						if (Double.isNaN(a.getElementDoubleAbs(index + j))) {
							skip = true;
							break;
						}
					}
					if (!skip)
						for (int j = 0; j < is; j++)
							moments[j].increment(a.getElementDoubleAbs(index + j));
				} else {
					for (int j = 0; j < is; j++)
						moments[j].increment(a.getElementDoubleAbs(index + j));
				}
				for (int j = 0; j < is; j++) {
					s[j] = (new Skewness(moments[j])).getResult(); 
					k[j] = (new Kurtosis(moments[j])).getResult(); 
				}
				sk.set(s, spos);
				ku.set(k, spos);
			}
		}

		a.setStoredValue(AbstractDataset.storeName(ignoreNaNs, "skewness-" + axis), sk);
		a.setStoredValue(AbstractDataset.storeName(ignoreNaNs, "kurtosis-" + axis), ku);
	}

	static private DoubleDataset getHigherStatistic(final AbstractDataset a, final boolean ignoreNaNs, int axis, String stat) {
		axis = a.checkAxis(axis);

		DoubleDataset obj = (DoubleDataset) a.getStoredValue(stat);
		if (obj == null) {
			calculateHigherMoments(a, ignoreNaNs, axis);
			obj = (DoubleDataset) a.getStoredValue(stat);
		}

		return obj;
	}

	/**
	 * See {@link #skewness(AbstractDataset a, boolean ignoreNaNs, int axis)} with ignoreNaNs = false
	 * @param a dataset
	 * @param axis
	 * @return skewness
	 */
	public static AbstractDataset skewness(final AbstractDataset a, final int axis) {
		return skewness(a, false, axis);
	}

	/**
	 * @param a dataset
	 * @param ignoreNaNs if true, skip NaNs
	 * @param axis
	 * @return skewness
	 */
	public static AbstractDataset skewness(final AbstractDataset a, final boolean ignoreNaNs, final int axis) {
		return getHigherStatistic(a, ignoreNaNs, axis, AbstractDataset.storeName(ignoreNaNs, "skewness-" + axis));
	}

	/**
	 * See {@link #kurtosis(AbstractDataset a, boolean ignoreNaNs, int axis)} with ignoreNaNs = false
	 * @param a dataset
	 * @param axis
	 * @return kurtosis
	 */
	public static AbstractDataset kurtosis(final AbstractDataset a, final int axis) {
		return kurtosis(a, false, axis);
	}

	/**
	 * @param a dataset
	 * @param ignoreNaNs if true, skip NaNs
	 * @param axis
	 * @return kurtosis
	 */
	public static AbstractDataset kurtosis(final AbstractDataset a, final boolean ignoreNaNs, final int axis) {
		return getHigherStatistic(a, ignoreNaNs, axis, AbstractDataset.storeName(ignoreNaNs, "kurtosis-" + axis));
	}

	/**
	 * See {@link #product(AbstractDataset a, boolean ignoreNaNs)} with ignoreNaNs = false
	 * @param a
	 * @return product of all items in dataset
	 */
	public static Object product(final AbstractDataset a) {
		return product(a, false);
	}

	/**
	 * @param a
	 * @param ignoreNaNs if true, skip NaNs
	 * @return product of all items in dataset
	 */
	public static Object product(final AbstractDataset a, final boolean ignoreNaNs) {
		return typedProduct(a, a.getDtype(), ignoreNaNs);
	}

	/**
	 * See {@link #typedProduct(AbstractDataset a, int dtype, boolean ignoreNaNs)} with ignoreNaNs = false
	 * @param a
	 * @param dtype
	 * @return product of all items in dataset
	 */
	public static Object typedProduct(final AbstractDataset a, final int dtype) {
		return typedProduct(a, dtype, false);
	}

	/**
	 * @param a
	 * @param dtype
	 * @param ignoreNaNs if true, skip NaNs
	 * @return product of all items in dataset
	 */
	public static Object typedProduct(final AbstractDataset a, final int dtype, final boolean ignoreNaNs) {

		if (a.isComplex()) {
			IndexIterator it = a.getIterator();
			double rv = 1, iv = 0;

			if (ignoreNaNs) {
				while (it.hasNext()) {
					final double r1 = a.getElementDoubleAbs(it.index);
					final double i1 = a.getElementDoubleAbs(it.index + 1);
					if (Double.isNaN(r1) || Double.isNaN(i1))
						continue;
					final double tv = r1*rv - i1*iv;
					iv = r1*iv + i1*rv;
					rv = tv;
				}
			} else {
				while (it.hasNext()) {
					final double r1 = a.getElementDoubleAbs(it.index);
					final double i1 = a.getElementDoubleAbs(it.index + 1);
					final double tv = r1*rv - i1*iv;
					iv = r1*iv + i1*rv;
					rv = tv;
				}
			}

			return new Complex(rv, iv);
		}

		IndexIterator it = a.getIterator();
		final int is;
		final long[] lresults;
		final double[] dresults;

		switch (dtype) {
		case AbstractDataset.BOOL:
		case AbstractDataset.INT8: case AbstractDataset.INT16:
		case AbstractDataset.INT32: case AbstractDataset.INT64:
			long lresult = 1;
			while (it.hasNext()) {
				lresult *= a.getElementLongAbs(it.index);
			}
			return new Long(lresult);
		case AbstractDataset.ARRAYINT8: case AbstractDataset.ARRAYINT16:
		case AbstractDataset.ARRAYINT32: case AbstractDataset.ARRAYINT64:
			lresult = 1;
			is = a.getElementsPerItem();
			lresults = new long[is];
			for (int j = 0; j < is; j++) {
				lresults[j] = 1;
			}
			while (it.hasNext()) {
				for (int j = 0; j < is; j++)
					lresults[j] *= a.getElementLongAbs(it.index+j);
			}
			return lresults;
		case AbstractDataset.FLOAT32: case AbstractDataset.FLOAT64:
			double dresult = 1.;
			if (ignoreNaNs) {
				while (it.hasNext()) {
					final double x = a.getElementDoubleAbs(it.index);
					if (Double.isNaN(x))
						continue;
					dresult *= x;
				}
			} else {
				while (it.hasNext()) {
					dresult *= a.getElementDoubleAbs(it.index);
				}
			}
			return Double.valueOf(dresult);
		case AbstractDataset.ARRAYFLOAT32:
		case AbstractDataset.ARRAYFLOAT64:
			is = a.getElementsPerItem();
			dresults = new double[is];
			for (int j = 0; j < is; j++) {
				dresults[j] = 1.;
			}
			if (ignoreNaNs) {
				while (it.hasNext()) {
					boolean skip = false;
					for (int j = 0; j < is; j++) {
						if (Double.isNaN(a.getElementDoubleAbs(it.index + j))) {
							skip = true;
							break;
						}
					}
					if (!skip)
						for (int j = 0; j < is; j++) {
							dresults[j] *= a.getElementDoubleAbs(it.index + j);
						}
				}
			} else {
				while (it.hasNext()) {
					for (int j = 0; j < is; j++)
						dresults[j] *= a.getElementDoubleAbs(it.index + j);
				}
			}
			return dresults;
		}

		return null;
	}

	/**
	 * See {@link #product(AbstractDataset a, boolean ignoreNaNs, int axis)} with ignoreNaNs = false
	 * @param a
	 * @param axis
	 * @return product of items along axis in dataset
	 */
	public static AbstractDataset product(final AbstractDataset a, final int axis) {
		return product(a, false, axis);
	}

	/**
	 * @param a
	 * @param ignoreNaNs if true, skip NaNs
	 * @param axis
	 * @return product of items along axis in dataset
	 */
	public static AbstractDataset product(final AbstractDataset a, final boolean ignoreNaNs, final int axis) {
		return typedProduct(a, a.getDtype(), ignoreNaNs, axis);
	}

	/**
	 * See {@link #typedProduct(AbstractDataset a, int dtype, boolean ignoreNaNs, int axis)} with ignoreNaNs = false
	 * @param a
	 * @param dtype
	 * @param axis
	 * @return product of items along axis in dataset
	 */
	public static AbstractDataset typedProduct(final AbstractDataset a, final int dtype, final int axis) {
		return typedProduct(a, dtype, false, axis);
	}

	/**
	 * @param a
	 * @param dtype
	 * @param ignoreNaNs if true, skip NaNs
	 * @param axis
	 * @return product of items along axis in dataset
	 */
	public static AbstractDataset typedProduct(final AbstractDataset a, final int dtype, final boolean ignoreNaNs, int axis) {
		axis = a.checkAxis(axis);
		final int rank = a.getRank();
		final int[] oshape = a.getShape();
		final int is = a.getElementsPerItem();
		final int alen = oshape[axis];
		oshape[axis] = 1;

		int[] nshape = AbstractDataset.squeezeShape(oshape, false);

		AbstractDataset result = AbstractDataset.zeros(is, nshape, dtype);

		IndexIterator qiter = result.getIterator(true);
		int[] qpos = qiter.getPos();
		int[] spos = oshape;

		while (qiter.hasNext()) {
			int i = 0;
			for (; i < axis; i++) {
				spos[i] = qpos[i];
			}
			spos[i++] = 0;
			for (; i < rank; i++) {
				spos[i] = qpos[i-1];
			}

			if (a.isComplex()) {
				double rv = 1, iv = 0;
				switch (dtype) {
				case AbstractDataset.COMPLEX64:
					ComplexFloatDataset af = (ComplexFloatDataset) a;
					if (ignoreNaNs) {
						for (int j = 0; j < alen; j++) {
							spos[axis] = j;
							final float r1 = af.getReal(spos);
							final float i1 = af.getImag(spos);
							if (Float.isNaN(r1) || Float.isNaN(i1))
								continue;
							final double tv = r1*rv - i1*iv;
							iv = r1*iv + i1*rv;
							rv = tv;
						}
					} else {
						for (int j = 0; j < alen; j++) {
							spos[axis] = j;
							final float r1 = af.getReal(spos);
							final float i1 = af.getImag(spos);
							final double tv = r1*rv - i1*iv;
							iv = r1*iv + i1*rv;
							rv = tv;
						}
					}
					break;
				case AbstractDataset.COMPLEX128:
					ComplexDoubleDataset ad = (ComplexDoubleDataset) a;
					if (ignoreNaNs) {
						for (int j = 0; j < alen; j++) {
							spos[axis] = j;
							final double r1 = ad.getReal(spos);
							final double i1 = ad.getImag(spos);
							if (Double.isNaN(r1) || Double.isNaN(i1))
								continue;
							final double tv = r1*rv - i1*iv;
							iv = r1*iv + i1*rv;
							rv = tv;
						}
					} else {
						for (int j = 0; j < alen; j++) {
							spos[axis] = j;
							final double r1 = ad.getReal(spos);
							final double i1 = ad.getImag(spos);
							final double tv = r1*rv - i1*iv;
							iv = r1*iv + i1*rv;
							rv = tv;
						}
					}
					break;
				}

				result.set(new Complex(rv, iv), qpos);
			} else {
				final long[] lresults;
				final double[] dresults;

				switch (dtype) {
				case AbstractDataset.BOOL:
				case AbstractDataset.INT8: case AbstractDataset.INT16:
				case AbstractDataset.INT32: case AbstractDataset.INT64:
					long lresult = 1;
					for (int j = 0; j < alen; j++) {
						spos[axis] = j;
						lresult *= a.getInt(spos);
					}
					result.set(lresult, qpos);
					break;
				case AbstractDataset.ARRAYINT8:
					lresults = new long[is];
					for (int k = 0; k < is; k++) {
						lresults[k] = 1;
					}
					for (int j = 0; j < alen; j++) {
						spos[axis] = j;
						final byte[] va = (byte[]) a.getObject(spos);
						for (int k = 0; k < is; k++) {
							lresults[k] *= va[k];
						}
					}
					result.set(lresults, qpos);
					break;
				case AbstractDataset.ARRAYINT16:
					lresults = new long[is];
					for (int k = 0; k < is; k++) {
						lresults[k] = 1;
					}
					for (int j = 0; j < alen; j++) {
						spos[axis] = j;
						final short[] va = (short[]) a.getObject(spos);
						for (int k = 0; k < is; k++) {
							lresults[k] *= va[k];
						}
					}
					result.set(lresults, qpos);
					break;
				case AbstractDataset.ARRAYINT32:
					lresults = new long[is];
					for (int k = 0; k < is; k++) {
						lresults[k] = 1;
					}
					for (int j = 0; j < alen; j++) {
						spos[axis] = j;
						final int[] va = (int[]) a.getObject(spos);
						for (int k = 0; k < is; k++) {
							lresults[k] *= va[k];
						}
					}
					result.set(lresults, qpos);
					break;
				case AbstractDataset.ARRAYINT64:
					lresults = new long[is];
					for (int k = 0; k < is; k++) {
						lresults[k] = 1;
					}
					for (int j = 0; j < alen; j++) {
						spos[axis] = j;
						final long[] va = (long[]) a.getObject(spos);
						for (int k = 0; k < is; k++) {
							lresults[k] *= va[k];
						}
					}
					result.set(lresults, qpos);
					break;
				case AbstractDataset.FLOAT32: case AbstractDataset.FLOAT64:
					double dresult = 1.;
					if (ignoreNaNs) {
						for (int j = 0; j < alen; j++) {
							spos[axis] = j;
							final double x = a.getDouble(spos); 
							if (Double.isNaN(x))
								continue;
							dresult *= x;
						}
					} else {
						for (int j = 0; j < alen; j++) {
							spos[axis] = j;
							dresult *= a.getDouble(spos);
						}
					}
					result.set(dresult, qpos);
					break;
				case AbstractDataset.ARRAYFLOAT32:
					dresults = new double[is];
					for (int k = 0; k < is; k++) {
						dresults[k] = 1.;
					}
					if (ignoreNaNs) {
						for (int j = 0; j < alen; j++) {
							spos[axis] = j;
							final float[] va = (float[]) a.getObject(spos);
							boolean skip = false;
							for (int k = 0; k < is; k++) {
								if (Float.isNaN(va[k])) {
									skip = true;
									break;
								}
							}
							if (!skip)
								for (int k = 0; k < is; k++) {
									dresults[k] *= va[k];
								}
						}
					} else {
						for (int j = 0; j < alen; j++) {
							spos[axis] = j;
							final float[] va = (float[]) a.getObject(spos);
							for (int k = 0; k < is; k++) {
								dresults[k] *= va[k];
							}
						}
					}
					result.set(dresults, qpos);
					break;
				case AbstractDataset.ARRAYFLOAT64:
					dresults = new double[is];
					for (int k = 0; k < is; k++) {
						dresults[k] = 1.;
					}
					if (ignoreNaNs) {
						for (int j = 0; j < alen; j++) {
							spos[axis] = j;
							final double[] va = (double[]) a.getObject(spos);
							boolean skip = false;
							for (int k = 0; k < is; k++) {
								if (Double.isNaN(va[k])) {
									skip = true;
									break;
								}
							}
							if (!skip)
								for (int k = 0; k < is; k++) {
									dresults[k] *= va[k];
								}
						}
					} else {
						for (int j = 0; j < alen; j++) {
							spos[axis] = j;
							final double[] va = (double[]) a.getObject(spos);
							for (int k = 0; k < is; k++) {
								dresults[k] *= va[k];
							}
						}
					}
					result.set(dresults, qpos);
					break;
				}
			}
		}

		return result;
	}

	/**
	 * See {@link #cumulativeProduct(AbstractDataset a, boolean ignoreNaNs)} with ignoreNaNs = false
	 * @param a
	 * @return cumulative product of items in flattened dataset
	 */
	public static AbstractDataset cumulativeProduct(final AbstractDataset a) {
		return cumulativeProduct(a, false);
	}

	/**
	 * @param a
	 * @param ignoreNaNs if true, skip NaNs
	 * @return cumulative product of items along axis in dataset
	 */
	public static AbstractDataset cumulativeProduct(final AbstractDataset a, boolean ignoreNaNs) {
		return cumulativeProduct(a.flatten(), ignoreNaNs, 0);
	}

	/**
	 * See {@link #cumulativeProduct(AbstractDataset a, boolean ignoreNaNs, int axis)} with ignoreNaNs = false
	 * @param a
	 * @param axis
	 * @return cumulative product of items along axis in dataset
	 */
	public static AbstractDataset cumulativeProduct(final AbstractDataset a, int axis) {
		return cumulativeProduct(a, false, axis);
	}

	/**
	 * @param a
	 * @param ignoreNaNs if true, skip NaNs
	 * @param axis
	 * @return cumulative product of items along axis in dataset
	 */
	public static AbstractDataset cumulativeProduct(final AbstractDataset a, boolean ignoreNaNs, int axis) {
		axis = a.checkAxis(axis);
		int dtype = a.getDtype();
		int[] oshape = a.getShape();
		int alen = oshape[axis];
		oshape[axis] = 1;

		AbstractDataset result = AbstractDataset.zeros(a);
		PositionIterator pi = result.getPositionIterator(axis);

		int[] pos = pi.getPos();

		while (pi.hasNext()) {

			if (a.isComplex()) {
				double rv = 1, iv = 0;
				switch (dtype) {
				case AbstractDataset.COMPLEX64:
					ComplexFloatDataset af = (ComplexFloatDataset) a;
					if (ignoreNaNs) {
						for (int j = 0; j < alen; j++) {
							pos[axis] = j;
							final float r1 = af.getReal(pos);
							final float i1 = af.getImag(pos);
							if (Float.isNaN(r1) || Float.isNaN(i1))
								continue;
							final double tv = r1*rv - i1*iv;
							iv = r1*iv + i1*rv;
							rv = tv;
							af.set((float) rv, (float) iv, pos);
						}
					} else {
						for (int j = 0; j < alen; j++) {
							pos[axis] = j;
							final float r1 = af.getReal(pos);
							final float i1 = af.getImag(pos);
							final double tv = r1*rv - i1*iv;
							iv = r1*iv + i1*rv;
							rv = tv;
							af.set((float) rv, (float) iv, pos);
						}
					}
					break;
				case AbstractDataset.COMPLEX128:
					ComplexDoubleDataset ad = (ComplexDoubleDataset) a;
					if (ignoreNaNs) {
						for (int j = 0; j < alen; j++) {
							pos[axis] = j;
							final double r1 = ad.getReal(pos);
							final double i1 = ad.getImag(pos);
							if (Double.isNaN(r1) || Double.isNaN(i1))
								continue;
							final double tv = r1*rv - i1*iv;
							iv = r1*iv + i1*rv;
							rv = tv;
							ad.set(rv, iv, pos);
						}
					} else {
						for (int j = 0; j < alen; j++) {
							pos[axis] = j;
							final double r1 = ad.getReal(pos);
							final double i1 = ad.getImag(pos);
							final double tv = r1*rv - i1*iv;
							iv = r1*iv + i1*rv;
							rv = tv;
							ad.set(rv, iv, pos);
						}
					}
					break;
				}

				result.set(new Complex(rv, iv), pos);
			} else {
				final int is;
				final long[] lresults;
				final double[] dresults;

				switch (dtype) {
				case AbstractDataset.BOOL:
				case AbstractDataset.INT8: case AbstractDataset.INT16:
				case AbstractDataset.INT32: case AbstractDataset.INT64:
					long lresult = 1;
					for (int j = 0; j < alen; j++) {
						pos[axis] = j;
						lresult *= a.getInt(pos);
						result.set(lresult, pos);
					}
					break;
				case AbstractDataset.ARRAYINT8:
					is = a.getElementsPerItem();
					lresults = new long[is];
					for (int k = 0; k < is; k++) {
						lresults[k] = 1;
					}
					for (int j = 0; j < alen; j++) {
						pos[axis] = j;
						final byte[] va = (byte[]) a.getObject(pos);
						for (int k = 0; k < is; k++) {
							lresults[k] *= va[k];
						}
						result.set(lresults, pos);
					}
					break;
				case AbstractDataset.ARRAYINT16:
					is = a.getElementsPerItem();
					lresults = new long[is];
					for (int k = 0; k < is; k++) {
						lresults[k] = 1;
					}
					for (int j = 0; j < alen; j++) {
						pos[axis] = j;
						final short[] va = (short[]) a.getObject(pos);
						for (int k = 0; k < is; k++) {
							lresults[k] *= va[k];
						}
						result.set(lresults, pos);
					}
					break;
				case AbstractDataset.ARRAYINT32:
					is = a.getElementsPerItem();
					lresults = new long[is];
					for (int k = 0; k < is; k++) {
						lresults[k] = 1;
					}
					for (int j = 0; j < alen; j++) {
						pos[axis] = j;
						final int[] va = (int[]) a.getObject(pos);
						for (int k = 0; k < is; k++) {
							lresults[k] *= va[k];
						}
						result.set(lresults, pos);
					}
					break;
				case AbstractDataset.ARRAYINT64:
					is = a.getElementsPerItem();
					lresults = new long[is];
					for (int k = 0; k < is; k++) {
						lresults[k] = 1;
					}
					for (int j = 0; j < alen; j++) {
						pos[axis] = j;
						final long[] va = (long[]) a.getObject(pos);
						for (int k = 0; k < is; k++) {
							lresults[k] *= va[k];
						}
						result.set(lresults, pos);
					}
					break;
				case AbstractDataset.FLOAT32: case AbstractDataset.FLOAT64:
					double dresult = 1.;
					if (ignoreNaNs) {
						for (int j = 0; j < alen; j++) {
							pos[axis] = j;
							final double x = a.getDouble(pos);
							if (Double.isNaN(x))
								continue;
							dresult *= x;
							result.set(dresult, pos);
						}
					} else {
						for (int j = 0; j < alen; j++) {
							pos[axis] = j;
							dresult *= a.getDouble(pos);
							result.set(dresult, pos);
						}
					}
					break;
				case AbstractDataset.ARRAYFLOAT32:
					is = a.getElementsPerItem();
					dresults = new double[is];
					for (int k = 0; k < is; k++) {
						dresults[k] = 1.;
					}
					if (ignoreNaNs) {
						for (int j = 0; j < alen; j++) {
							pos[axis] = j;
							final float[] va = (float[]) a.getObject(pos);
							boolean skip = false;
							for (int k = 0; k < is; k++) {
								if (Float.isNaN(va[k])) {
									skip = true;
									break;
								}
							}
							if (!skip)
								for (int k = 0; k < is; k++) {
									dresults[k] *= va[k];
								}
							result.set(dresults, pos);
						}
					} else {
						for (int j = 0; j < alen; j++) {
							pos[axis] = j;
							final float[] va = (float[]) a.getObject(pos);
							for (int k = 0; k < is; k++) {
								dresults[k] *= va[k];
							}
							result.set(dresults, pos);
						}
					}
					break;
				case AbstractDataset.ARRAYFLOAT64:
					is = a.getElementsPerItem();
					dresults = new double[is];
					for (int k = 0; k < is; k++) {
						dresults[k] = 1.;
					}
					if (ignoreNaNs) {
						for (int j = 0; j < alen; j++) {
							pos[axis] = j;
							final double[] va = (double[]) a.getObject(pos);
							boolean skip = false;
							for (int k = 0; k < is; k++) {
								if (Double.isNaN(va[k])) {
									skip = true;
									break;
								}
							}
							if (!skip)
								for (int k = 0; k < is; k++) {
									dresults[k] *= va[k];
								}
							result.set(dresults, pos);
						}
					} else {
						for (int j = 0; j < alen; j++) {
							pos[axis] = j;
							final double[] va = (double[]) a.getObject(pos);
							for (int k = 0; k < is; k++) {
								dresults[k] *= va[k];
							}
							result.set(dresults, pos);
						}
					}
					break;
				}
			}
		}

		return result;
	}

	/**
	 * See {@link #cumulativeSum(AbstractDataset a, boolean ignoreNaNs)} with ignoreNaNs = false
	 * @param a
	 * @return cumulative sum of items in flattened dataset
	 */
	public static AbstractDataset cumulativeSum(final AbstractDataset a) {
		return cumulativeSum(a, false);
	}

	/**
	 * @param a
	 * @param ignoreNaNs if true, skip NaNs
	 * @return cumulative sum of items in flattened dataset
	 */
	public static AbstractDataset cumulativeSum(final AbstractDataset a, boolean ignoreNaNs) {
		return cumulativeSum(a.flatten(), ignoreNaNs, 0);
	}

	/**
	 * See {@link #cumulativeSum(AbstractDataset a, boolean ignoreNaNs, int axis)} with ignoreNaNs = false
	 * @param a
	 * @param axis
	 * @return cumulative sum of items along axis in dataset
	 */
	public static AbstractDataset cumulativeSum(final AbstractDataset a, int axis) {
		return cumulativeSum(a, false, axis);
	}

	/**
	 * @param a
	 * @param ignoreNaNs if true, skip NaNs
	 * @param axis
	 * @return cumulative sum of items along axis in dataset
	 */
	public static AbstractDataset cumulativeSum(final AbstractDataset a, boolean ignoreNaNs, int axis) {
		axis = a.checkAxis(axis);
		int dtype = a.getDtype();
		int[] oshape = a.getShape();
		int alen = oshape[axis];
		oshape[axis] = 1;

		AbstractDataset result = AbstractDataset.zeros(a);
		PositionIterator pi = result.getPositionIterator(axis);

		int[] pos = pi.getPos();

		while (pi.hasNext()) {

			if (a.isComplex()) {
				double rv = 0, iv = 0;
				switch (dtype) {
				case AbstractDataset.COMPLEX64:
					ComplexFloatDataset af = (ComplexFloatDataset) a;
					if (ignoreNaNs) {
						for (int j = 0; j < alen; j++) {
							pos[axis] = j;
							final float x = af.getReal(pos);
							final float y = af.getImag(pos);
							if (Float.isNaN(x) || Float.isNaN(y))
								continue;
							rv += x;
							iv += y;
							af.set((float) rv, (float) iv, pos);
						}
					} else {
						for (int j = 0; j < alen; j++) {
							pos[axis] = j;
							rv += af.getReal(pos);
							iv += af.getImag(pos);
							af.set((float) rv, (float) iv, pos);
						}
					}
					break;
				case AbstractDataset.COMPLEX128:
					ComplexDoubleDataset ad = (ComplexDoubleDataset) a;
					if (ignoreNaNs) {
						for (int j = 0; j < alen; j++) {
							pos[axis] = j;
							final double x = ad.getReal(pos);
							final double y = ad.getImag(pos);
							if (Double.isNaN(x) || Double.isNaN(y))
								continue;
							rv += x;
							iv += y;
							ad.set(rv, iv, pos);
						}
					} else {
						for (int j = 0; j < alen; j++) {
							pos[axis] = j;
							rv += ad.getReal(pos);
							iv += ad.getImag(pos);
							ad.set(rv, iv, pos);
						}
					}
					break;
				}

				result.set(new Complex(rv, iv), pos);
			} else {
				final int is;
				final long[] lresults;
				final double[] dresults;

				switch (dtype) {
				case AbstractDataset.BOOL:
				case AbstractDataset.INT8: case AbstractDataset.INT16:
				case AbstractDataset.INT32: case AbstractDataset.INT64:
					long lresult = 0;
					for (int j = 0; j < alen; j++) {
						pos[axis] = j;
						lresult += a.getInt(pos);
						result.set(lresult, pos);
					}
					break;
				case AbstractDataset.ARRAYINT8:
					is = a.getElementsPerItem();
					lresults = new long[is];
					for (int j = 0; j < alen; j++) {
						pos[axis] = j;
						final byte[] va = (byte[]) a.getObject(pos);
						for (int k = 0; k < is; k++) {
							lresults[k] += va[k];
						}
						result.set(lresults, pos);
					}
					break;
				case AbstractDataset.ARRAYINT16:
					is = a.getElementsPerItem();
					lresults = new long[is];
					for (int j = 0; j < alen; j++) {
						pos[axis] = j;
						final short[] va = (short[]) a.getObject(pos);
						for (int k = 0; k < is; k++) {
							lresults[k] += va[k];
						}
						result.set(lresults, pos);
					}
					break;
				case AbstractDataset.ARRAYINT32:
					is = a.getElementsPerItem();
					lresults = new long[is];
					for (int j = 0; j < alen; j++) {
						pos[axis] = j;
						final int[] va = (int[]) a.getObject(pos);
						for (int k = 0; k < is; k++) {
							lresults[k] += va[k];
						}
						result.set(lresults, pos);
					}
					break;
				case AbstractDataset.ARRAYINT64:
					is = a.getElementsPerItem();
					lresults = new long[is];
					for (int j = 0; j < alen; j++) {
						pos[axis] = j;
						final long[] va = (long[]) a.getObject(pos);
						for (int k = 0; k < is; k++) {
							lresults[k] += va[k];
						}
						result.set(lresults, pos);
					}
					break;
				case AbstractDataset.FLOAT32: case AbstractDataset.FLOAT64:
					double dresult = 0.;
					if (ignoreNaNs) {
						for (int j = 0; j < alen; j++) {
							pos[axis] = j;
							final double x = a.getDouble(pos);
							if (Double.isNaN(x))
								continue;
							dresult += x;
							result.set(dresult, pos);
						}
					} else {
						for (int j = 0; j < alen; j++) {
							pos[axis] = j;
							dresult += a.getDouble(pos);
							result.set(dresult, pos);
						}
					}
					break;
				case AbstractDataset.ARRAYFLOAT32:
					is = a.getElementsPerItem();
					dresults = new double[is];
					if (ignoreNaNs) {
						for (int j = 0; j < alen; j++) {
							pos[axis] = j;
							final float[] va = (float[]) a.getObject(pos);
							boolean skip = false;
							for (int k = 0; k < is; k++) {
								if (Float.isNaN(va[k])) {
									skip = true;
									break;
								}
							}
							if (!skip)
								for (int k = 0; k < is; k++) {
									dresults[k] += va[k];
								}
							result.set(dresults, pos);
						}
					} else {
						for (int j = 0; j < alen; j++) {
							pos[axis] = j;
							final float[] va = (float[]) a.getObject(pos);
							for (int k = 0; k < is; k++) {
								dresults[k] += va[k];
							}
							result.set(dresults, pos);
						}
					}
					break;
				case AbstractDataset.ARRAYFLOAT64:
					is = a.getElementsPerItem();
					dresults = new double[is];
					if (ignoreNaNs) {
						for (int j = 0; j < alen; j++) {
							pos[axis] = j;
							final double[] va = (double[]) a.getObject(pos);
							boolean skip = false;
							for (int k = 0; k < is; k++) {
								if (Double.isNaN(va[k])) {
									skip = true;
									break;
								}
							}
							if (!skip)
								for (int k = 0; k < is; k++) {
									dresults[k] += va[k];
								}
							result.set(dresults, pos);
						}
					} else {
						for (int j = 0; j < alen; j++) {
							pos[axis] = j;
							final double[] va = (double[]) a.getObject(pos);
							for (int k = 0; k < is; k++) {
								dresults[k] += va[k];
							}
							result.set(dresults, pos);
						}
					}
					break;
				}
			}
		}

		return result;
	}

	/**
	 * @param a
	 * @return average deviation value of all items the dataset
	 */
	public static Object averageDeviation(final AbstractDataset a) {
		final IndexIterator it = a.getIterator();
		final int is = a.getElementsPerItem();

		if (is == 1) {
			double mean = (Double) a.mean();
			double sum = 0.0;

			while (it.hasNext()) {
				sum += Math.abs(a.getElementDoubleAbs(it.index) - mean);
			}

			return sum / a.getSize();
		}

		double[] means = (double[]) a.mean();
		double[] sums = new double[is];

		while (it.hasNext()) {
			for (int j = 0; j < is; j++)
				sums[j] += Math.abs(a.getElementDoubleAbs(it.index + j) - means[j]);
		}

		double n = a.getSize();
		for (int j = 0; j < is; j++)
			sums[j] /= n;

		return sums;
	}

	/**
	 * The residual is the sum of squared differences
	 * @param a
	 * @param b
	 * @return residual value
	 */
	public static double residual(final AbstractDataset a, final AbstractDataset b) {
		return a.residual(b);
	}

	/**
	 * Calculate approximate outlier values. These are defined as the values in the dataset
	 * that are approximately below and above the given thresholds - in terms of percentages
	 * of dataset size.
	 * <p>
	 * It approximates by limiting the number of items (given by length) used internally by
	 * data structures - the larger this is, the more accurate will those outlier values become.
	 * The actual thresholds used are returned in the array.
	 * @param a
	 * @param lo percentage threshold for lower limit
	 * @param hi percentage threshold for higher limit
	 * @param length maximum number of items used internally, if negative, then unlimited
	 * @return double array with low and high values, and low and high percentage thresholds
	 */
	public static double[] outlierValues(final AbstractDataset a, double lo, double hi, final int length) {
		if (lo <= 0 || hi <= 0 || lo >= hi || hi >= 100  || Double.isNaN(lo)|| Double.isNaN(hi)) {
			throw new IllegalArgumentException("Thresholds must be between (0,100) and in order");
		}
		final int size = a.getSize();
		int nl = (int) ((lo*size)/100);
		if (length > 0 && nl > length)
			nl = length;
		int nh = (int) (((100-hi)*size)/100);
		if (length > 0 && nh > length)
			nh = length;

		double[] results = Math.max(nl, nh) > 640 ? outlierValuesMap(a, nl, nh) : outlierValuesList(a, nl, nh);

		results[2] = results[2]*100./size;
		results[3] = 100. - results[3]*100./size;
		return results;
	}

	protected static double[] outlierValuesMap(final AbstractDataset a, int nl, int nh) {
		final TreeMap<Double, Integer> lMap = new TreeMap<Double, Integer>();
		final TreeMap<Double, Integer> hMap = new TreeMap<Double, Integer>();

		int ml = 0;
		int mh = 0;
		IndexIterator it = a.getIterator();
		while (it.hasNext()) {
			Double x = a.getElementDoubleAbs(it.index);
			Integer i;
			if (ml == nl) {
				Double k = lMap.lastKey();
				if (x < k) {
					i = lMap.get(k) - 1;
					if (i == 0) {
						lMap.remove(k);
					} else {
						lMap.put(k, i);
					}
					i = lMap.get(x);
					if (i == null) {
						lMap.put(x, 1);
					} else {
						lMap.put(x, i + 1);
					}
				}
			} else {
				i = lMap.get(x);
				if (i == null) {
					lMap.put(x, 1);
				} else {
					lMap.put(x, i + 1);
				}
				ml++;
			}

			if (mh == nh) {
				Double k = hMap.firstKey();
				if (x > k) {
					i = hMap.get(k) - 1;
					if (i == 0) {
						hMap.remove(k);
					} else {
						hMap.put(k, i);
					}
					i = hMap.get(x);
					if (i == null) {
						hMap.put(x, 1);
					} else {
						hMap.put(x, i+1);
					}
				}
			} else {
				i = hMap.get(x);
				if (i == null) {
					hMap.put(x, 1);
				} else {
					hMap.put(x, i+1);
				}
				mh++;
			}
		}

		return new double[] {lMap.lastKey(), hMap.firstKey(), ml, mh};
	}

	protected static double[] outlierValuesList(final AbstractDataset a, int nl, int nh) {
		final List<Double> lList = new ArrayList<Double>(nl);
		final List<Double> hList = new ArrayList<Double>(nh);
//		final List<Double> lList = new LinkedList<Double>();
//		final List<Double> hList = new LinkedList<Double>();

		double lx = Double.POSITIVE_INFINITY;
		double hx = Double.NEGATIVE_INFINITY;

		IndexIterator it = a.getIterator();
		while (it.hasNext()) {
			double x = a.getElementDoubleAbs(it.index);
			if (x < lx) {
				if (lList.size() == nl) {
					lList.remove(lx);
				}
				lList.add(x);
				lx = Collections.max(lList);
			} else if (x == lx) {
				if (lList.size() < nl) {
					lList.add(x);
				}
			}

			if (x > hx) {
				if (hList.size() == nh) {
					hList.remove(hx);
				}
				hList.add(x);
				hx = Collections.min(hList);
			} else if (x == hx) {
				if (hList.size() < nh) {
					hList.add(x);
				}
			}
		}

		return new double[] {lx, hx, lList.size(), hList.size()};
	}	
}
