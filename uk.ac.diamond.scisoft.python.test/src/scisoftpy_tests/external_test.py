###
# Copyright 2013 Diamond Light Source Ltd.
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
Test random class
import unittest
unittest.TestProgram(argv=["external_test"])
'''
from __future__ import print_function
import unittest

from scisoftpy.external import save_args, load_args, pyenv
import scisoftpy as dnp

import shutil


class Test(unittest.TestCase):
    from os import path
    epath_list = [path.dirname(__file__)]

    def checkArgs(self, arg):
        d = save_args(arg)
        a = load_args(d)
        shutil.rmtree(d)
        self.assertTrue(self.equals(arg, a), '%s != %s' % (arg, a))

    def equals(self, a, b):
        if type(a) != type(b):
            return False
        if isinstance(a, dict):
            if list(a.keys()) != list(b.keys()):
                return False
            for k in a:
                if not self.equals(a[k], b[k]):
                    return False
        elif isinstance(a, (list, tuple)):
            if len(a) != len(b):
                return False
            for ia, ib in zip(a, b):
                if not self.equals(ia, ib):
                    return False
        elif isinstance(a, dnp.ndarray):
            return dnp.all(a == b)
        else:
            return a == b
        return True

    def testSaveArgs(self):
        self.checkArgs(None)
        self.checkArgs(([None, None], [None,]))
        self.checkArgs(([None, 1, 1.5], [None,]))
        self.checkArgs(([None, 1, 1.5], (None,)))
        self.checkArgs(([None, 1, 1.5], dnp.arange(12)))
        self.checkArgs(([None, 1, 1.5], {'blah':dnp.arange(12), 'foo': dnp.ones((3,4)), 'boo': -2.345}))

    def testSubprocess(self):
        import subprocess as sub
        pyexe, pypath, _pyldpath = pyenv()
        import os
        env = dict(os.environ)
        env["PYTHONPATH"] = ":".join(pypath)
        from pprint import pprint
        pprint(pypath)
#        p = sub.Popen('echo $PYTHONPATH', shell=True, env=_env, stdin=sub.PIPE, stdout=sub.PIPE, stderr=sub.PIPE)
#        print p.communicate()
        p = sub.Popen([pyexe,], env=env, shell=False, stdin=sub.PIPE, stdout=sub.PIPE, stderr=sub.PIPE)
        p.stdin.write(b'print "Hello World"\n')
        p.stdin.write(b'print "Hello World2"\n')
        p.stdin.close()
        l = p.stdout.read()
        print(l)


    def testDLSmodule(self):
        print(dnp.external.get_dls_module())

    def testPythonInstallation(self):
        print(dnp.external.get_python())

    def testExternal(self):
        from .external_functions import fun, funadd, fundec#, funexception
        a = fun()
        efun = dnp.external.create_function(fun, dls_module=True)
        print(a, self.equals(efun(), a))
        efun = dnp.external.create_function("fun", "external_functions", dls_module=True)
        print(a, self.equals(efun(), a))

        a = funadd(dnp.arange(3.), 1.5)
        efun = dnp.external.create_function(funadd, dls_module=True)
        print(a, self.equals(efun(dnp.arange(3.), 1.5), a))
        efun = dnp.external.create_function("funadd", "external_functions", dls_module=True)
        print(a, self.equals(efun(dnp.arange(3.), 1.5), a))

        a = fundec(dnp.arange(3.))
        efun = dnp.external.create_function(fundec, dls_module=True)
        print(a, self.equals(efun(dnp.arange(3.)), a))
        efun = dnp.external.create_function("fundec", "external_functions", dls_module=True)
        print(a, self.equals(efun(dnp.arange(3.)), a))

        a = fundec(dnp.arange(3.), b=2.5)
        efun = dnp.external.create_function(fundec, dls_module=True)
        print(a, self.equals(efun(dnp.arange(3.), b=2.5), a))
        efun = dnp.external.create_function("fundec", "external_functions", dls_module=True)
        print(a, self.equals(efun(dnp.arange(3.), b=2.5), a))

    def testException(self):
#        from external_functions import funexception
#        funexception()
        efunexception = dnp.external.create_function("funexception", "external_functions", extra_path=Test.epath_list, dls_module=True)
        self.assertRaises(ValueError, efunexception)
#        efunexception()

    def testPyAna(self):
        efun = dnp.external.create_function("funpyana", "external_functions", extra_path=Test.epath_list, dls_module="python/anaconda")
        print('2,7', end=' ')
        self.assertEqual(efun(), (2,7))
        print('passed')

    def testArrayScalar(self):
        efun = dnp.external.create_function("funarrayscalar", "external_functions", extra_path=Test.epath_list, dls_module=True)
        a = 2+3j, 1., 123, True
        print(a, end=' ')
        self.assertEqual(efun(), a)
        print('passed')

    def testSpeed(self):
        efun = dnp.external.create_function("fun", "external_functions", extra_path=Test.epath_list, dls_module=True, keep=False)
        import time
        t = time.clock()
        for _i in range(10):
            efun()
        print(time.clock() - t)
        efun = dnp.external.create_function("fun", "external_functions", extra_path=Test.epath_list, dls_module=True, keep=True)
        t = time.clock()
        for _i in range(10):
            efun()
        print(time.clock() - t)

    def testHello(self):
        py = dnp.external.PythonSubProcess("python", None)
        print(py.communicate("print \"Hello World!\"\n"))
        print(py.communicate("print \"Hello World2!\"\n"))
        print(py.communicate("for i in range(4): print i\n"))
        py.stop()

def suite():
    suite = unittest.TestSuite()
    suite.addTest(unittest.TestLoader().loadTestsFromTestCase(Test))
    return suite 

if __name__ == '__main__':
    unittest.TextTestRunner(verbosity=2).run(suite())
