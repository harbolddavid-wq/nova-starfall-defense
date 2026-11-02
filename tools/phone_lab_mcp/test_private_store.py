import base64
import unittest
from private_store import PrivateStore, StoreError, Conflict

class Fake:
    def __init__(self): self.data=None; self.sha=None; self.private=True; self.badread=False
    def __call__(self,method,path,payload=None):
        if path=='/repos/me/backups': return 200,{'private':self.private,'full_name':'me/backups'}
        if method=='GET':
            if self.data is None:return 404,{}
            data=b'broken' if self.badread else self.data
            return 200,{'sha':self.sha,'encoding':'base64','content':base64.b64encode(data).decode()}
        if (self.sha is None and 'sha' in payload) or (self.sha is not None and payload.get('sha')!=self.sha): return 409,{}
        self.data=base64.b64decode(payload['content']);self.sha=('a' if self.sha is None else 'b')*40
        return 201,{}

class Tests(unittest.TestCase):
    def setUp(self): self.f=Fake();self.s=PrivateStore('me/backups','test-token','checkpoints/fixture.npl',self.f)
    def test_write_read_and_cas(self):
        self.assertEqual(self.s.read(),(None,None)); sha=self.s.write(b'cipher',None)
        self.assertEqual(self.s.read(),(b'cipher',sha)); self.s.write(b'cipher2',sha)
        with self.assertRaises(Conflict): self.s.write(b'old',sha)
        self.assertEqual(self.s.read()[0],b'cipher2')
    def test_reject_public_and_failures(self):
        self.f.private=False
        with self.assertRaises(StoreError): self.s.write(b'cipher',None)
        self.assertIsNone(self.f.data)
        self.f.private=True;self.f.badread=True
        with self.assertRaises(StoreError): self.s.write(b'cipher',None)
    def test_forbid_unscoped_path_and_oversize(self):
        with self.assertRaises(StoreError): PrivateStore('me/backups','x','../secrets',self.f)
        with self.assertRaises(StoreError): self.s.write(b'x'*(512*1024+1),None)

if __name__=='__main__': unittest.main()
