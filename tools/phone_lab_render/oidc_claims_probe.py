"""Public-safe JWT claim comparison + remote auth probe, no raw claims/tokens logged."""
import base64, json, os
from urllib import request, parse, error
REPO='harbolddavid-wq/nova-starfall-defense'
BRANCH='refs/heads/experiment/nova-phone-lab-live-2026-11-01'
REF=REPO+'/.github/workflows/nova-phone-lab-hosted-render.yml@'+BRANCH
for role in ('agent','controller'):
    audience='nova-phone-lab-'+role
    u=os.environ['ACTIONS_ID_TOKEN_REQUEST_URL'];sep='&' if '?' in u else '?'
    req=request.Request(u+sep+parse.urlencode({'audience':audience}),headers={'Authorization':'bearer '+os.environ['ACTIONS_ID_TOKEN_REQUEST_TOKEN']})
    with request.urlopen(req,timeout=15) as response: token=json.load(response)['value']
    payload=token.split('.')[1];payload+='='*((4-len(payload)%4)%4)
    claims=json.loads(base64.urlsafe_b64decode(payload))
    flags={'repository':claims.get('repository')==REPO,'branch':claims.get('ref')==BRANCH,
            'workflow_ref':claims.get('workflow_ref')==REF,'subject_present':bool(claims.get('sub')),
            'runner_environment':claims.get('runner_environment')=='github-hosted',
            'event_name':claims.get('event_name') in ('push','workflow_dispatch')}
    print('Token issuer claims (boolean matches only):',role,flags,flush=True)
    assert all(flags.values()), 'Signed identity claims did not match expected workflow'
    endpoint='https://nova-phone-lab-private-relay.onrender.com/v1/sessions'
    test=request.Request(endpoint,headers={'Authorization':'Bearer '+token})
    try:
        with request.urlopen(test,timeout=30) as response:
            status = response.status
    except error.HTTPError as e:
        status = e.code
    print('Remote controller probe:',role,status,flush=True)
    assert status == (200 if role == 'controller' else 401), 'Unexpected remote authorization result'