"""End-to-end single-owner OAuth PKCE tests, no real credentials."""
import asyncio
import base64
import hashlib
import os
import re
from urllib.parse import urlencode, urlsplit, parse_qs
import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from fastapi import FastAPI
from httpx import ASGITransport,AsyncClient
from oauth_server import install_oauth,BASE,CLIENT_ID,REDIRECT_URI,SCOPE

async def main():
    app=FastAPI()
    valid=install_oauth(app,pairing_key='P'*60,signing_key='S'*64)
    verifier='v'*48
    challenge=base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b'=').decode()
    args={'response_type':'code','client_id':CLIENT_ID,'redirect_uri':REDIRECT_URI,
          'code_challenge_method':'S256','code_challenge':challenge,'state':'some-random-state-0001',
          'resource':BASE,'scope':SCOPE}
    async with AsyncClient(transport=ASGITransport(app=app),base_url='https://mcp.internal',follow_redirects=False) as c:
        resource=await c.get('/.well-known/oauth-protected-resource')
        assert resource.json()['authorization_servers']==[BASE]
        meta=await c.get('/.well-known/oauth-authorization-server')
        assert meta.json()['authorization_response_iss_parameter_supported'] is True
        assert 'S256' in meta.json()['code_challenge_methods_supported']
        bad=await c.get('/oauth/authorize',params={**args,'redirect_uri':'https://attacker.invalid/callback'})
        assert bad.status_code==400
        bad_client=await c.get('/oauth/authorize',params={**args,'client_id':'https://attacker.invalid/client.json'})
        assert bad_client.status_code==400
        page=await c.get('/oauth/authorize',params=args)
        assert page.status_code==200 and 'password' in page.text
        nonce=re.search(r'name="nonce" value="([A-Za-z0-9_-]+)"',page.text).group(1)
        invalid=await c.post('/oauth/authorize',data={'nonce':nonce,'pairing_key':'wrong'})
        assert invalid.status_code==401
        retry=await c.post('/oauth/authorize',data={'nonce':nonce,'pairing_key':'P'*60})
        assert retry.status_code==400 # one-time nonce, no replay
        page=await c.get('/oauth/authorize',params=args)
        nonce=re.search(r'name="nonce" value="([A-Za-z0-9_-]+)"',page.text).group(1)
        good=await c.post('/oauth/authorize',data={'nonce':nonce,'pairing_key':'P'*60})
        assert good.status_code==302
        redir=urlsplit(good.headers['location'])
        assert good.headers['location'].startswith(REDIRECT_URI+'?')
        q=parse_qs(redir.query)
        assert q['state'][0]==args['state'] and q['iss'][0]==BASE
        code=q['code'][0]
        base={'grant_type':'authorization_code','client_id':CLIENT_ID,'redirect_uri':REDIRECT_URI,'resource':BASE,'code':code}
        wrong=await c.post('/oauth/token',data={**base,'code_verifier':'x'*48})
        assert wrong.status_code==400
        reused=await c.post('/oauth/token',data={**base,'code_verifier':verifier})
        assert reused.status_code==400 # incorrect PKCE consumed the code
        page=await c.get('/oauth/authorize',params=args)
        nonce=re.search(r'name="nonce" value="([A-Za-z0-9_-]+)"',page.text).group(1)
        good=await c.post('/oauth/authorize',data={'nonce':nonce,'pairing_key':'P'*60})
        code=parse_qs(urlsplit(good.headers['location']).query)['code'][0]
        result=await c.post('/oauth/token',data={**base,'code':code,'code_verifier':verifier})
        assert result.status_code==200,result.text
        auth=result.json()
        assert auth['token_type']=='Bearer' and auth['expires_in']==3600
        assert valid('Bearer '+auth['access_token'])
        assert not valid('Bearer '+auth['refresh_token'])
        again=await c.post('/oauth/token',data={**base,'code':code,'code_verifier':verifier})
        assert again.status_code==400
        refresh=await c.post('/oauth/token',data={'grant_type':'refresh_token','client_id':CLIENT_ID,'resource':BASE,'refresh_token':auth['refresh_token']})
        assert refresh.status_code==200 and valid('Bearer '+refresh.json()['access_token'])
        wrongclient=await c.post('/oauth/token',data={'grant_type':'refresh_token','client_id':'bad','resource':BASE,'refresh_token':auth['refresh_token']})
        assert wrongclient.status_code==400
        print('PASS: OAuth metadata + CIMD + issuer-bound redirect + PKCE S256 + nonce + bearer JWT + refresh + replay rejection (17 checks)')
if __name__=='__main__':asyncio.run(main())