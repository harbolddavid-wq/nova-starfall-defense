import base64
import unittest
from checkpoint_crypto import CheckpointError, decode_key, encrypt, decrypt, MAX_BYTES

class CheckpointCryptoTests(unittest.TestCase):
    def setUp(self):
        self.key = bytes(range(32)); self.package = 'com.novasky.phonelab.fixture'; self.sha = 'a'*64
    def test_round_trip_and_unique_nonce(self):
        x=encrypt(b'synthetic data',self.key,self.package,self.sha)
        y=encrypt(b'synthetic data',self.key,self.package,self.sha)
        self.assertNotEqual(x,y)
        self.assertNotIn(b'synthetic data',x)
        self.assertEqual(decrypt(x,self.key,self.package,self.sha),b'synthetic data')
    def test_corruption_wrong_key_and_identity_fail(self):
        x=encrypt(b'test',self.key,self.package,self.sha)
        bad=x[:-1]+bytes([x[-1]^1])
        for blob,key,pkg,sha in [(bad,self.key,self.package,self.sha),(x,b'x'*32,self.package,self.sha),(x,self.key,'com.example.other',self.sha),(x,self.key,self.package,'b'*64)]:
            with self.assertRaises(CheckpointError): decrypt(blob,key,pkg,sha)
    def test_invalid_and_oversized_inputs_fail(self):
        for value in ['', 'bad key',base64.b64encode(b'x').decode()]:
            with self.assertRaises(CheckpointError): decode_key(value)
        with self.assertRaises(CheckpointError): encrypt(b'',self.key,self.package,self.sha)
        with self.assertRaises(CheckpointError): encrypt(b'x'*(MAX_BYTES+1),self.key,self.package,self.sha)
        with self.assertRaises(CheckpointError): decrypt(b'bad',self.key,self.package,self.sha)
    def test_valid_base64_key(self):
        self.assertEqual(decode_key(base64.b64encode(self.key).decode()),self.key)

if __name__ == '__main__': unittest.main()
