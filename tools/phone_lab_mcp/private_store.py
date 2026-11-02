"""Small encrypted blobs in a dedicated private GitHub repository.

Caller supplies a scoped credential and authenticated ciphertext; no secrets or
plaintext are logged. A ref/credential/key must be provisioned separately.
"""
import base64
import json
import re
import urllib.error
import urllib.request

MAX_BLOB = 512 * 1024
OWNER_REPO = re.compile(r'^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$')
PATH = re.compile(r'^checkpoints/[a-z0-9_.-]+\.npl$')

class StoreError(RuntimeError): pass
class Conflict(StoreError): pass

class PrivateStore:
    def __init__(self, repo, token, path, request=None):
        if not OWNER_REPO.fullmatch(repo) or not PATH.fullmatch(path) or not token:
            raise StoreError('Invalid storage configuration')
        self.repo, self.path, self._token = repo, path, token
        self._request = request or self._http

    def _http(self, method, endpoint, payload=None):
        req=urllib.request.Request('https://api.github.com'+endpoint,
            data=None if payload is None else json.dumps(payload).encode(), method=method,
            headers={'Accept':'application/vnd.github+json','Authorization':'Bearer '+self._token,
                     'Content-Type':'application/json','X-GitHub-Api-Version':'2022-11-28'})
        try:
            with urllib.request.urlopen(req, timeout=20) as r:
                return r.status,json.load(r)
        except urllib.error.HTTPError as e:
            return e.code,{}
        except (urllib.error.URLError, TimeoutError, ValueError) as e:
            raise StoreError('Private storage request failed') from e

    def _verify_private(self):
        status,meta=self._request('GET','/repos/'+self.repo)
        if status!=200 or meta.get('private') is not True or meta.get('full_name','').lower()!=self.repo.lower():
            raise StoreError('Storage unavailable or repository is not private')

    def read(self):
        self._verify_private()
        status,obj=self._request('GET','/repos/'+self.repo+'/contents/'+self.path)
        if status==404: return None,None
        if status!=200 or obj.get('encoding')!='base64' or not re.fullmatch(r'[0-9a-f]{40}',obj.get('sha','')):
            raise StoreError('Checkpoint cannot be read')
        try: blob=base64.b64decode(''.join(obj['content'].split()), validate=True)
        except (KeyError,ValueError) as e: raise StoreError('Invalid checkpoint data') from e
        if not 1 <= len(blob) <= MAX_BLOB: raise StoreError('Checkpoint size invalid')
        return blob,obj['sha']

    def write(self, blob, expected_sha):
        if not isinstance(blob,bytes) or not 1<=len(blob)<=MAX_BLOB:
            raise StoreError('Checkpoint size invalid')
        self._verify_private()
        body={'message':'Save encrypted Phone Lab checkpoint','content':base64.b64encode(blob).decode()}
        if expected_sha is not None:
            if not re.fullmatch(r'[0-9a-f]{40}',expected_sha): raise StoreError('Invalid previous revision')
            body['sha']=expected_sha
        status,_=self._request('PUT','/repos/'+self.repo+'/contents/'+self.path,body)
        if status in (409,422): raise Conflict('Checkpoint changed; prior save retained')
        if status not in (200,201): raise StoreError('Checkpoint write failed')
        check,sha=self.read()
        if check!=blob: raise StoreError('Checkpoint read-back verification failed')
        return sha
