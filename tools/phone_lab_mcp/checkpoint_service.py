"""Encrypt app bytes and verify private-store saves. Configuration is external."""
from checkpoint_crypto import encrypt, decrypt

class CheckpointService:
    def __init__(self, store, key, package, apk_sha256):
        self.store,self.key,self.package,self.apk_sha256=store,key,package,apk_sha256
        self._loaded=False
        self._sha=None

    def read(self):
        blob,sha=self.store.read()
        # Never mark loaded if authentication fails; no failed read can authorize an overwrite.
        data=None if blob is None else decrypt(blob,self.key,self.package,self.apk_sha256)
        self._sha,self._loaded=sha,True
        return data

    def write(self, data):
        if not self._loaded: raise RuntimeError('Checkpoint must be read before writing')
        blob=encrypt(data,self.key,self.package,self.apk_sha256)
        self._sha=self.store.write(blob,self._sha)
        # Verify the stored bytes still authenticate and contain exactly this app's data.
        verified,sha=self.store.read()
        if sha!=self._sha or decrypt(verified,self.key,self.package,self.apk_sha256)!=data:
            raise RuntimeError('Checkpoint verification failed')
        return sha
