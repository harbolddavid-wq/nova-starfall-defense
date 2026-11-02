import unittest
from checkpoint_service import CheckpointService
from private_store import PrivateStore
from test_private_store import Fake
from checkpoint_crypto import CheckpointError

class Tests(unittest.TestCase):
    def test_encrypted_round_trip_and_wrong_key_cannot_overwrite(self):
        fake=Fake(); store=PrivateStore('me/backups','token','checkpoints/fixture.npl',fake)
        good=CheckpointService(store,b'a'*32,'com.example.fixture','a'*64)
        self.assertIsNone(good.read());good.write(b'private synthetic state')
        self.assertNotIn(b'private synthetic state',fake.data)
        restored=CheckpointService(store,b'a'*32,'com.example.fixture','a'*64)
        self.assertEqual(restored.read(),b'private synthetic state')
        bad=CheckpointService(store,b'b'*32,'com.example.fixture','a'*64)
        with self.assertRaises(CheckpointError): bad.read()
        with self.assertRaises(RuntimeError): bad.write(b'bad')
        self.assertEqual(restored.read(),b'private synthetic state')

if __name__=='__main__': unittest.main()
