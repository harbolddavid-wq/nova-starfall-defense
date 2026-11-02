import base64, contextlib, io, os, sys, tempfile, unittest
from unittest.mock import patch
import synthetic_private_checkpoint as testee

class Storage:
    def __init__(self): self.blob=None; self.sha=None
    def read(self): return self.blob,self.sha
    def write(self,blob,sha):
        assert sha==self.sha
        self.blob=blob; self.sha='a'*40
        return self.sha

class CheckpointProofTests(unittest.TestCase):
    def test_save_restore_no_plaintext_in_storage(self):
        storage=Storage(); events=[]
        def adb(*args,input_bytes=None):
            events.append((args,input_bytes))
            return b'synthetic counter=2' if args[0]=='exec-out' else b''
        with tempfile.NamedTemporaryFile() as apk, patch.dict(os.environ,{'GITHUB_RUN_ID':'123','PHONE_LAB_BACKUP_TOKEN':'synthetic-token','PHONE_LAB_CHECKPOINT_KEY':base64.b64encode(b'k'*32).decode()}), patch.object(testee,'PrivateStore',return_value=storage), patch.object(testee,'adb',side_effect=adb), contextlib.redirect_stdout(io.StringIO()):
            apk.write(b'test apk');apk.flush()
            with patch.object(sys,'argv',['script','save',apk.name]):testee.main()
            self.assertNotIn(b'synthetic counter=2',storage.blob)
            with patch.object(sys,'argv',['script','restore',apk.name]):testee.main()
            self.assertEqual(events[-1][1],b'synthetic counter=2')
    def test_restore_missing_checkpoint_fails_before_adb(self):
        with tempfile.NamedTemporaryFile() as apk, patch.dict(os.environ,{'GITHUB_RUN_ID':'123','PHONE_LAB_BACKUP_TOKEN':'synthetic-token','PHONE_LAB_CHECKPOINT_KEY':base64.b64encode(b'k'*32).decode()}), patch.object(testee,'PrivateStore',return_value=Storage()), patch.object(testee,'adb') as adb:
            apk.write(b'test apk');apk.flush()
            with patch.object(sys,'argv',['script','restore',apk.name]):
                with self.assertRaises(RuntimeError): testee.main()
            adb.assert_not_called()

if __name__=='__main__':unittest.main()
