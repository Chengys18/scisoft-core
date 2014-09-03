###
# Copyright 2011 Diamond Light Source Ltd.
# 
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
# 
#   http://www.apache.org/licenses/LICENSE-2.0
# 
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
###

'''
Maths package
'''

import uk.ac.diamond.scisoft.analysis.dataset.Dataset as _ds
import uk.ac.diamond.scisoft.analysis.dataset.Maths as _maths
import uk.ac.diamond.scisoft.analysis.dataset.Stats as _stats

import types as _types

from jarray import array as _array

_arraytype = type(_array([0], 'f')) # this is used for testing if returned object is a Java array


from math import pi as _ppi
from math import e as _e
from java.lang.Double import POSITIVE_INFINITY as _jinf #@UnresolvedImport
from java.lang.Double import MAX_VALUE as _jmax #@UnresolvedImport
from java.lang.Double import NaN as _jnan #@UnresolvedImport

pi = _ppi
e = _e
inf = _jinf
nan = _jnan

floatmax = _jmax # maximum float value (use sys.float_info.max for 2.6+)

from jycore import _wrap
from jycore import asarray as _asarray

# these functions call (wrapped) instance methods
def prod(a, axis=None, dtype=None):
    '''Product of input'''
    return a.prod(axis, dtype)

def sum(a, axis=None, dtype=None): #@ReservedAssignment
    '''Sum of input'''
    return a.sum(axis, dtype)

def mean(a, axis=None):
    '''Arithmetic mean of input'''
    return a.mean(axis)

def std(a, axis=None, ddof=0):
    '''Standard deviation of input'''
    return a.std(axis, ddof)

def var(a, axis=None, ddof=0):
    '''Variance of input'''
    return a.var(axis, ddof)

def ptp(a, axis=None):
    '''Peak-to-peak of input'''
    return a.ptp(axis)

def amax(a, axis=None):
    '''Maximum of input'''
    return a.max(axis)

def amin(a, axis=None):
    '''Minimum of input'''
    return a.min(axis)

def real(a):
    '''Real part of input'''
    return _asarray(a).real

def imag(a):
    '''Imaginary part of input'''
    return _asarray(a).imag

@_wrap
def abs(a, out=None): #@ReservedAssignment
    '''Absolute value of input'''
    return _maths.abs(a, out)

absolute = abs

fabs = abs # supports complex types too

@_wrap
def angle(a):
    '''Angle of complex argument'''
    return _maths.angle(a)

@_wrap
def conjugate(a, out=None):
    '''Complex conjugate of input'''
    return _maths.conjugate(a, out)

conj = conjugate

@_wrap
def add(a, b, out=None):
    '''Add two array-like objects together'''
    return _maths.add(a, b, out)

@_wrap
def subtract(a, b, out=None):
    '''Subtract one array-like object from another'''
    return _maths.subtract(a, b, out)

@_wrap
def multiply(a, b, out=None):
    '''Multiply two array-like objects together'''
    return _maths.multiply(a, b, out)

@_wrap
def divide(a, b, out=None):
    '''Divide one array-like object by another'''
    return _maths.divide(a, b, out)

@_wrap
def floor_divide(a, b, out=None):
    '''Calculate largest integers smaller or equal to division'''
    return _maths.floorDivide(a, b, out)

@_wrap
def remainder(a, b, out=None):
    '''Return remainder of division of inputs'''
    return _maths.remainder(a, b, out)
#>>> np.floor_divide(7,3)
#2
#>>> np.floor_divide([1., 2., 3., 4.], 2.5)
#array([ 0.,  0.,  1.,  1.])

# modf
#Return the fractional and integral part of a number.
#
#The fractional and integral parts are negative if the given number is negative.

#>>> np.modf(2.5)
#(0.5, 2.0)
#>>> np.modf(-.4)
#(-0.40000000000000002, -0.0)

#>>> np.remainder([4,7],[2,3])
#array([0, 1])

fmod = remainder
# FIXME these are different
# >>> np.fmod([-3, -2, -1, 1, 2, 3], 2)
# array([-1,  0, -1,  1,  0,  1])
# >>> np.mod([-3, -2, -1, 1, 2, 3], 2)
# array([1, 0, 1, 1, 0, 1])
#
mod = remainder

@_wrap
def reciprocal(a, out=None):
    '''Calculate reciprocal of input'''
    return _maths.reciprocal(a, out)

@_wrap
def sin(a, out=None):
    '''Sine of input'''
    return _maths.sin(a, out)

@_wrap
def cos(a, out=None):
    '''Cosine of input'''
    return _maths.cos(a, out)

@_wrap
def tan(a, out=None):
    '''Tangent of input'''
    return _maths.tan(a, out)

@_wrap
def arcsin(a, out=None):
    '''Inverse sine of input'''
    return _maths.arcsin(a, out)

@_wrap
def arccos(a, out=None):
    '''Inverse cosine of input'''
    return _maths.arccos(a, out)

@_wrap
def arctan(a, out=None):
    '''Inverse tangent of input'''
    return _maths.arctan(a, out)

@_wrap
def arctan2(a, b, out=None):
    '''Inverse tangent of a/b with correct choice of quadrant'''
    return _maths.arctan2(a, b, out)

@_wrap
def hypot(a, b, out=None):
    '''Hypotenuse of triangle of given sides'''
    return _maths.hypot(a, b, out)

@_wrap
def sinh(a, out=None):
    '''Hyperbolic sine of input'''
    return _maths.sinh(a, out)

@_wrap
def cosh(a, out=None):
    '''Hyperbolic cosine of input'''
    return _maths.cosh(a, out)

@_wrap
def tanh(a, out=None):
    '''Hyperbolic tangent of input'''
    return _maths.tanh(a, out)

@_wrap
def arcsinh(a, out=None):
    '''Inverse hyperbolic sine of input'''
    return _maths.arcsinh(a, out)

@_wrap
def arccosh(a, out=None):
    '''Inverse hyperbolic cosine of input'''
    return _maths.arccosh(a, out)

@_wrap
def arctanh(a, out=None):
    '''Inverse hyperbolic tangent of input'''
    return _maths.arctanh(a, out)

@_wrap
def log(a, out=None):
    '''Natural logarithm of input'''
    return _maths.log(a, out)

@_wrap
def log2(a, out=None):
    '''Logarithm of input to base 2'''
    return _maths.log2(a, out)

@_wrap
def log10(a, out=None):
    '''Logarithm of input to base 10'''
    return _maths.log10(a, out)

@_wrap
def log1p(x, out=None):
    '''Natural logarithm of (x+1)'''
    return _maths.log1p(x, out)

@_wrap
def exp(a, out=None):
    '''Exponential of input'''
    return _maths.exp(a, out)

@_wrap
def expm1(x, out=None):
    '''Exponential of (x-1)'''
    return _maths.expm1(x, out)

@_wrap
def sqrt(a, out=None):
    '''Square root of input'''
    return _maths.sqrt(a, out)

@_wrap
def square(a, out=None):
    '''Square of input'''
    return _maths.square(a, out)

@_wrap
def power(a, p, out=None):
    '''Input raised to given power'''
    return _maths.power(a, p, out)

@_wrap
def floor(a, out=None):
    '''Largest integer smaller or equal to input'''
    return _maths.floor(a, out)

@_wrap
def ceil(a, out=None):
    '''Smallest integer greater or equal to input'''
    return _maths.ceil(a, out)

@_wrap
def rint(a, out=None):
    '''Round elements of input to nearest integers'''
    return _maths.rint(a, out)

@_wrap
def rad2deg(a, out=None):
    '''Convert from radian to degree'''
    return _maths.toDegrees(a, out)

@_wrap
def deg2rad(a, out=None):
    '''Convert from degree to radian'''
    return _maths.toRadians(a, out)

degrees = rad2deg
radians = deg2rad

@_wrap
def sign(a, out=None):
    '''Sign of input, indicated by -1 for negative, +1 for positive and 0 for zero'''
    return _maths.signum(a, out)

@_wrap
def negative(a, out=None):
    '''Negate input'''
    return _maths.negative(a, out)

@_wrap
def clip(a, a_min, a_max, out=None):
    '''Clip input to given bounds (replace NaNs with midpoint of bounds)'''
    return _maths.clip(a, a_min, a_max, out)

@_wrap
def maximum(a, b, out=None):
    '''Item-wise maximum'''
    return _maths.maximum(a, b)

@_wrap
def minimum(a, b, out=None):
    '''Item-wise minimum'''
    return _maths.minimum(a, b)

@_wrap
def median(a, axis=None):
    '''Median of input'''
    if axis is None:
        return _stats.median(a)
    else:
        return _stats.median(a, axis)

@_wrap
def cumprod(a, axis=None):
    '''Cumulative product of input'''
    if axis is None:
        return _stats.cumulativeProduct(a)
    else:
        return _stats.cumulativeProduct(a, axis)

@_wrap
def cumsum(a, axis=None):
    '''Cumulative sum of input'''
    if axis is None:
        return _stats.cumulativeSum(a)
    else:
        return _stats.cumulativeSum(a, axis)

@_wrap
def diff(a, order=1, axis=-1):
    '''Difference of input'''
    return _maths.difference(a, order, axis)

import uk.ac.diamond.scisoft.analysis.dataset.function.Histogram as _histo

@_wrap
def histogram(a, bins=10, range=None, normed=False, weights=None, new=None): #@ReservedAssignment
    '''Histogram of input'''
    if normed or weights or new:
        raise ValueError, "Option not supported yet"

    h = None
    if range is None:
        h = _histo(bins)
    elif len(range) != 2:
        raise ValueError, "Need two values in range"
    else:
        h = _histo(bins, range[0], range[1])

    from jycore import asDatasetList as _asList
    return h.value(_asList(a))

import uk.ac.diamond.scisoft.analysis.dataset.LinearAlgebra as _linalg

@_wrap
def dot(a, b):
    '''Dot product of two arrays'''
    return _linalg.dotProduct(a, b)

@_wrap
def vdot(a, b):
    '''Dot product of two vectors with first vector conjugated if complex'''
    return _linalg.dotProduct(conjugate(a.flatten()), b.flatten())

@_wrap
def inner(a, b):
    '''Inner product of two arrays (sum product over last dimensions)'''
    return _linalg.tensorDotProduct(a, b, -1, -1)

@_wrap
def tensordot(a, b, axes=2):
    '''Tensor dot product of two arrays
    '''
    if isinstance(axes, int):
        bx = range(axes)
        ao = a.ndim - axes - 1
        ax = [ ao + i for i in bx ]
    else:
        t = type(axes)
        if t is _types.ListType or t is _types.TupleType:
            if len(t) == 0:
                raise ValueError, "Given axes sequence should be non-empty"

            if len(t) == 1:
                ax = axes[0]
                bx = axes[0]
            else:
                ax = axes[0]
                bx = axes[1]

            ta = type(ax)
            tal = ta is _types.ListType or ta is _types.TupleType
            tb = type(bx)
            tbl = tb is _types.ListType or tb is _types.TupleType
            if tal != tbl:
                if tal:
                    bx = list(bx)
                else:
                    ax = list(ax)
        else:
            raise ValueError, "Given axes has wrong type"

    return _linalg.tensorDotProduct(a, b, ax, bx)

@_wrap
def gradient(f, *varargs):
    '''Gradient of array
    
    f -- array
    *varargs -- 0, 1, N scalars for sample distance, or (1 or N-d) datasets for sample points
    '''

    if varargs is None or len(varargs) == 0:
        g = _maths.gradient(f)
    else:
        # check for scalars, etc
        from jycore import arange as _ar
        vl = len(varargs)
        nd = f.getRank()
        if vl == 1:
            varargs = [varargs[0]]*nd
            vl = nd
        if vl != nd:
            raise ValueError, "Number of arguments must be 0, 1 or rank of f"

        xlist = []
        for i in range(vl):
            x = varargs[i]
            xlist.append(x if isinstance(x, _ds) else (_ar(f.shape[i])*x)._jdataset())
        g = _maths.gradient(f, xlist)

    if len(g) == 1:
        return g[0]
    return g
