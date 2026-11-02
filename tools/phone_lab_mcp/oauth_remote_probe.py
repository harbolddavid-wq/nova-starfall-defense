"""Public OAuth discovery smoke only; never transmits pairing or signing keys."""
import json
from urllib import request, parse, error
BASE="https://nova-phone-lab-private-relay.onrender.com"

def get(path):
    with request.urlopen(BASE+path,timeout=35) as r:
        return json.load(r)

as_info=get('/.well-known/oauth-authorization-server')
assert as_info['issuer']==BASE
assert as_info['client_id_metadata_document_supported'] is True
assert as_info['authorization_response_iss_parameter_supported'] is True
assert as_info['code_challenge_methods_supported']==['S256']
rs=get('/.well-known/oauth-protected-resource')
assert rs['authorization_servers']==[BASE]
assert rs['resource']==BASE
q=parse.urlencode({'response_type':'code','client_id':'https://evil.example/client',
                   'redirect_uri':'https://evil.example/callback','state':'state12345678',
                   'code_challenge_method':'S256','code_challenge':'A'*43,'resource':BASE})
try:
    request.urlopen(BASE+'/oauth/authorize?'+q,timeout=35)
    raise AssertionError('Bad redirect accepted')
except error.HTTPError as e:
    assert e.code==400
print('PASS: actual hosted OAuth discovery, issuer-bound callback support, PKCE, protected resource metadata, forbidden third-party redirect')