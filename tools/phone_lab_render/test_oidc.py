import time
from types import SimpleNamespace
import pytest
import jwt
from cryptography.hazmat.primitives.asymmetric import rsa
from oidc_auth import GithubOIDCVerifier, AUDIENCES, ISSUER

REF = 'harbolddavid-wq/nova-starfall-defense/.github/workflows/nova-phone-lab-hosted-render.yml@refs/heads/experiment/nova-phone-lab-live-2026-11-01'
REPO = 'harbolddavid-wq/nova-starfall-defense'
BRANCH='refs/heads/experiment/nova-phone-lab-live-2026-11-01'
KEY = rsa.generate_private_key(public_exponent=65537,key_size=2048)

class Keys:
    def get_signing_key_from_jwt(self, token):
        return SimpleNamespace(key=KEY.public_key())

@pytest.fixture
def v():
    verifier = GithubOIDCVerifier(REF)
    verifier.jwks = Keys()
    return verifier

def tok(role='agent', **extra):
    now=int(time.time())
    claims={'iss':ISSUER,'iat':now,'nbf':now,'exp':now+240, 'aud': AUDIENCES[role],
            'sub':'repo:'+REPO+':ref:'+BRANCH, 'repository':REPO,'ref':BRANCH,
            'workflow_ref':REF,'event_name':'push','runner_environment':'github-hosted'}
    claims.update(extra)
    return jwt.encode(claims, KEY, algorithm='RS256')

def test_agent_authorized(v):
    assert v.verify(tok('agent'),'agent')
    assert v.verify(tok('controller'),'controller')

def test_no_cross_role(v):
    assert not v.verify(tok('agent'),'controller')
    assert not v.verify(tok('controller'),'agent')

def test_provenance_restrictions(v):
    assert not v.verify(tok(repository='stranger/repo'),'agent')
    assert not v.verify(tok(ref='refs/heads/main'),'agent')
    assert not v.verify(tok(workflow_ref='stranger/workflow'),'agent')
    assert not v.verify(tok(sub='repo:other:ref:refs/heads/main'),'agent')
    assert not v.verify(tok(event_name='pull_request'),'agent')
    assert not v.verify(tok(runner_environment='self-hosted'),'agent')

def test_expiration_and_tamper(v):
    assert not v.verify(tok(exp=int(time.time())-120),'agent')
    valid=tok()
    assert not v.verify(valid[:-5]+'qwert','agent')
    assert not v.verify('', 'agent')