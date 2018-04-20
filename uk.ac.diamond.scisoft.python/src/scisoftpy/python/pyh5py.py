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

from __future__ import print_function

import h5py #@UnresolvedImport

if h5py.version.version_tuple[0] < 2:
    raise ImportError("Installed h5py is too old - it must be at least version 2")

from ..nexus.hdf5 import HDF5tree as _tree
from ..nexus.hdf5 import HDF5group as _group
from ..nexus.hdf5 import HDF5dataset as _dataset
from ..nexus import nxclasses as _nx
io_exception = IOError

from .pycore import ndarray, ndgeneric, asarray, zeros, _key2slice
from itertools import product #@UnresolvedImport

class _lazydataset(object):
    def __init__(self, dataset, isexternal):
        self.maxshape = dataset.maxshape
        self.shape = dataset.shape
        self.dtype = dataset.dtype
        self.file = dataset.file.filename
        self.name = dataset.name
        self.rank = len(self.shape)
        prop = dataset.id.get_create_plist()
        if prop.get_layout() == h5py.h5d.CHUNKED:
            self.chunking = prop.get_chunk()
        else:
            self.chunking = None 
        if isexternal: # is this now necessary?
            dataset.file.close() # need to explicitly close otherwise file becomes inaccessible

    def __getitem__(self, key):
        try:
            fh = h5py.File(self.file, 'r')
        except Exception as e:
            import sys
            print("Could not load |%s|\n" % self.file, file=sys.stderr)
            raise io_exception(e)
        finally:
            pass

        if fh is None:
            raise io_exception("No tree found")

        ds = fh[self.name]

        slices,sliced = _key2slice(key, self.shape)
        dshape = [ l if s is None else len(list(range(*s.indices(l)))) for s, l in zip(slices, self.shape)] # destination shape
        nshape = [ l for f,l in zip(sliced, dshape) if f ]
#        print 'new shape:', nshape

        if self.chunking is None:
            nslices = [ s if s else slice(None) for s in slices ]
            v = ds[tuple(nslices)]
            return v.reshape(nshape)

        split = [ c <= 1 or l > 1 for c, l in zip(self.chunking, dshape) ]
        try:
            split.index(False)
        except:
            nslices = [ s if s else slice(None) for s in slices ]
            v = ds[tuple(nslices)]
            return v.reshape(nshape)

        # load slice-by-slice
        v = zeros(dshape, dtype=self.dtype)

        ssize = 1 # slice size
        for i in range(self.rank):
            if split[i]:
                dshape[i] = 1
            else:
                ssize *= dshape[i]
            

        if ssize == 1:
            for i in reversed(list(range(self.rank))):
                l = v.shape[i]
                if l > 1:
                    dshape[i] = l
                    split[i] = False
                    ssize = l
                    break

        # iterate over split dimensions
        dst_ranges = [ list(range(l)) for f, l in zip(split, v.shape) if f ]
        dst_iter = product(*dst_ranges)

        src_ranges = [ list(range(*s.indices(l))) if s else list(range(l)) for f, l, s in zip(split, self.shape, slices) if f ]
        src_iter = product(*src_ranges)

        for d,s in zip(dst_iter, src_iter):
            dst_pos = []
            src_pos = []
            p = 0
            for i in range(self.rank):
                if split[i]:
                    dst_pos.append(d[p])
                    src_pos.append(s[p])
                    p += 1
                else:
                    dst_pos.append(slice(None))
                    sl = slices[i]
                    src_pos.append(sl if sl else slice(None))

#            print dst_pos, src_pos
            v[dst_pos] = ds[tuple(src_pos)]

        fh.close()
        return v.reshape(nshape)


class SDS(_dataset):
    def __init__(self, dataset, attrs={}, parent=None):
        '''Make a SDS
        
        dataset can be a ndarray or HDF5Dataset when created from a file
        '''
        if not isinstance(dataset, (_lazydataset, ndarray, h5py.Dataset)):
            dataset = asarray(dataset)
        _dataset.__init__(self, dataset, attrs=attrs, parent=parent)
#        self.__data = dataset
#        if not isinstance(dataset, ndarray):
#            self.__shape = tuple(dataset.shape)
#            self.__maxshape = tuple(dataset.maxshape)
#        else:
#            self.__shape = self.__maxshape = tuple(dataset.shape)


class HDF5Loader(object):
    def __init__(self, name):
        self.name = name

    def load(self, warn=True):
        # capture all error messages
        try:
            fh = h5py.File(self.name, 'r')
        except Exception as e:
            import sys
            print("Could not load |%s|\n" % self.name, file=sys.stderr) 
            raise io_exception(e)
        finally:
            pass

        if fh is None:
            raise io_exception("No tree found")

        # convert tree to own tree
        pool = dict()
        t = self._copynode(pool, fh)
        pool.clear()
        fh.close()
        return t

    def _mkgroup(self, node, attrs, parent):
        if parent is None:
            return _tree(node.filename, attrs)
        return _group(attrs, parent)

    def _mkdataset(self, node, attrs, link, parent):
        d = _lazydataset(node, isinstance(link, h5py.ExternalLink))
        return _dataset(d, attrs=attrs, parent=parent)

    def _copynode(self, pool, node, link=None, parent=None):
        if node.id in pool:
            return pool[node.id]

        attrs = []
        for k,v in list(node.attrs.items()):
            if isinstance(v, ndgeneric):
                if v.dtype.kind in 'SUa':
                    v = str(v)
                else:
#                    print v.shape,
#                    v = v.item()
#                    print dir(v)
                    pass
            else:
                if len(v) == 1:
                    v = v[0]
            attrs.append((k, v))

        if isinstance(node, h5py.Group):
            g = self._mkgroup(node, attrs, parent)
            pool[node.id] = g
            nodes = []
            for k in list(node.keys()):
                n = node.get(k)
                l = node.get(k, getlink=True)
                try:
                    nodes.append((k, self._copynode(pool, n, l, g)))
                except Exception as e:
                    print(k, n, e)
            g.init_group(nodes)
            return g
        elif isinstance(node, h5py.Dataset):
            d = self._mkdataset(node, attrs, link, parent)
            pool[node.id] = d
            return d
        else:
            pass

    def setloadmetadata(self, load_metadata):
        pass

class NXLoader(HDF5Loader):
    def _mkgroup(self, node, attrs, parent):
        try:
            cls = node.attrs['NX_class']
        except KeyError:
            cls = None
        if cls is not None:
            cls = str(cls)
            if cls in _nx.NX_CLASSES:
                g = _nx.NX_CLASSES[cls](attrs, parent)
            else:
                print("Unknown Nexus class: %s" % cls)
                g = super(NXLoader, self)._mkgroup(node, attrs, parent)
        elif node.name == '/':
            g = _nx.NXroot(node.filename, attrs)
        else:
            g = _nx.NXobject(attrs, parent)

        return g

    def _mkdataset(self, dataset, attrs, link, parent):
        d = _lazydataset(dataset, isinstance(link, h5py.ExternalLink))
        return SDS(d, attrs, parent)

