import asyncio
import base64
import json
import os
from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parent))
from fastapi import FastAPI, HTTPException
from fastapi.responses import Response
from httpx import ASGITransport, AsyncClient
from pydantic import BaseModel
from typing import Literal
from mcp_bridge import install_mcp

os.environ['PHONE_LAB_MCP_TOKEN'] = 'm'*48
os.environ['PHONE_LAB_CONTROL_TOKEN'] = 'c'*48
os.environ['PHONE_LAB_OAUTH_SIGNING_KEY'] = 's'*64

class Command(BaseModel):
    session_id: str
    action: Literal['screenshot','status','tap','swipe','back','home','settings']
    x: int | None = None
    y: int | None = None
    x1: int | None = None
    y1: int | None = None
    x2: int | None = None
    y2: int | None = None
    duration_ms: int | None = None

PNG1 = b'\x89PNG\r\n\x1a\n' + b'pngfixtureone'
PNG2 = b'\x89PNG\r\n\x1a\n' + b'pngfixturetwo'

def rpc(method,id=1,params=None):
    val={'jsonrpc':'2.0','method':method,'id':id}
    if params is not None:val['params']=params
    return val

def new_app():
    app=FastAPI()
    pressed={'value':False}
    calls=[]
    async def sessions(authorization):
        assert authorization == 'Bearer '+os.environ['PHONE_LAB_CONTROL_TOKEN']
        return {'sessions':[{'session_id':'session-12345678','device':{'api_level':'36'},'expires_in_seconds':100}]}
    async def execute(cmd,authorization):
        assert authorization == 'Bearer '+os.environ['PHONE_LAB_CONTROL_TOKEN']
        calls.append(cmd)
        if cmd.session_id != 'session-12345678':raise HTTPException(404,'No active session')
        if cmd.action == 'screenshot':return Response(PNG2 if pressed['value'] else PNG1,media_type='image/png')
        if cmd.action == 'tap':pressed['value']=True
        return {'session_id':cmd.session_id,'result':{'action':cmd.action,'ok':True}}
    install_mcp(app,execute,sessions,Command,oidc_controller_auth=lambda x: x=='Bearer valid-gh-oidc')
    return app,calls

async def run():
    app,calls=new_app()
    async with AsyncClient(transport=ASGITransport(app=app),base_url='http://mcp.local') as c:
        headers={'Content-Type':'application/json'}
        a=await c.post('/mcp',json=rpc('initialize',1,{'protocolVersion':'2025-06-18','capabilities':{},'clientInfo':{'name':'test','version':'1'}}),headers=headers)
        assert a.status_code==200 and a.json()['result']['protocolVersion']=='2025-06-18'
        l=await c.post('/mcp',json=rpc('tools/list',2),headers=headers)
        assert l.status_code==200 and len(l.json()['result']['tools'])==8
        forbidden=await c.post('/mcp',json=rpc('tools/call',3,{'name':'list_sessions','arguments':{}}),headers=headers)
        assert forbidden.json()["result"]["isError"] and "mcp/www_authenticate" in forbidden.json()["result"]["_meta"]
        bad=await c.post('/mcp',json=rpc('tools/call',4,{'name':'list_sessions','arguments':{}}),headers={**headers,'Authorization':'Bearer bad'})
        assert bad.json()["result"]["isError"]
        auth={**headers,'Authorization':'Bearer '+os.environ['PHONE_LAB_MCP_TOKEN']}
        sessions=await c.post('/mcp',json=rpc('tools/call',5,{'name':'list_sessions','arguments':{}}),headers=auth)
        assert sessions.json()['result']['structuredContent']['sessions'][0]['device']['api_level']=='36'
        first=await c.post('/mcp',json=rpc('tools/call',6,{'name':'see_screen','arguments':{'session_id':'session-12345678'}}),headers=auth)
        assert base64.b64decode(first.json()['result']['content'][0]['data']) == PNG1
        tap=await c.post('/mcp',json=rpc('tools/call',7,{'name':'tap_screen','arguments':{'session_id':'session-12345678','x':50,'y':100}}),headers=auth)
        assert tap.json()['result']['structuredContent']['result']['action']=='tap'
        after=await c.post('/mcp',json=rpc('tools/call',8,{'name':'see_screen','arguments':{'session_id':'session-12345678'}}),headers=auth)
        assert base64.b64decode(after.json()['result']['content'][0]['data']) == PNG2
        assert calls[-1].action=='screenshot'
        for tool,args in [('swipe_screen',{'session_id':'session-12345678','x1':50,'y1':100,'x2':55,'y2':120,'duration_ms':300}),('press_back',{'session_id':'session-12345678'}),('press_home',{'session_id':'session-12345678'}),('open_android_settings',{'session_id':'session-12345678'}),('device_status',{'session_id':'session-12345678'})]:
            result=await c.post('/mcp',json=rpc('tools/call',10,{'name':tool,'arguments':args}),headers=auth)
            assert result.status_code==200 and not result.json()['result'].get('isError'),tool
        err=await c.post('/mcp',json=rpc('tools/call',11,{'name':'tap_screen','arguments':{'session_id':'session-12345678','x':-100,'y':30}}),headers=auth)
        assert err.json()['result'].get('isError')
        gh=await c.post('/mcp',json=rpc('tools/call',12,{'name':'list_sessions','arguments':{}}),headers={**headers,'Authorization':'Bearer valid-gh-oidc'})
        assert gh.json()['result']['structuredContent']['sessions']
        unknown=await c.post('/mcp',json=rpc('nonsense',13),headers=headers)
        assert unknown.json()['error']['code']==-32601
        print('PASS: MCP initialize/list, separate bearer auth, role-specific OIDC, screenshots as PNG image, tap/swipe/nav, bounded errors (13 checks)')
if __name__=='__main__': asyncio.run(run())