"""Single-owner OAuth 2.1 PKCE authorization for Nova Phone Lab MCP.

Protected by an independently-provisioned pairing secret; accepts only the
verified ChatGPT stable CIMD and redirect endpoint. No public user enrollment,
no arbitrary redirect URIs and no embedded credentials in plugin manifests.
"""
from __future__ import annotations

import base64
import hashlib
import html
import logging
import os
import re
import secrets
import time
from dataclasses import dataclass
from urllib.parse import parse_qs, urlencode

import jwt
from fastapi import Request
from fastapi.responses import HTMLResponse, JSONResponse, RedirectResponse

LOG = logging.getLogger("phone_lab_oauth")
BASE = "https://nova-phone-lab-private-relay.onrender.com"
MCP_RESOURCE = BASE
CLIENT_ID = "https://chatgpt.com/oauth/client.json"
REDIRECT_URI = "https://chatgpt.com/connector_platform_oauth_redirect"
SCOPE = "lab:control"
PROFILE_SUBJECT = "nova-phone-lab-owner-v1"
CODE_TTL = 300
ACCESS_TTL = 3600
REFRESH_TTL = 30 * 86400


@dataclass(frozen=True)
class AuthorizeRequest:
    client_id: str
    redirect_uri: str
    state: str
    code_challenge: str
    scope: str
    resource: str
    created_at: float


def install_oauth(app, pairing_key: str | None = None, signing_key: str | None = None):
    pairing = pairing_key or os.environ.get("PHONE_LAB_MCP_TOKEN", "")
    secret = signing_key or os.environ.get("PHONE_LAB_OAUTH_SIGNING_KEY", "")
    if len(pairing) < 32 or len(secret) < 48 or pairing == secret:
        raise RuntimeError("OAuth must have separate strong pairing and JWT signing keys")
    pending: dict[str, AuthorizeRequest] = {}
    codes: dict[str, AuthorizeRequest] = {}

    def prune():
        now = time.monotonic()
        for table in (pending, codes):
            for key, item in list(table.items()):
                if now - item.created_at > CODE_TTL:
                    table.pop(key, None)
        # Limit memory per worker: one owner; prevent unbounded unauth requests.
        if len(pending) > 200 or len(codes) > 200:
            pending.clear()
            codes.clear()

    def oauth_token(kind: str, seconds: int) -> str:
        now = int(time.time())
        claims = {"iss": BASE, "aud": MCP_RESOURCE, "sub": PROFILE_SUBJECT,
                  "client_id": CLIENT_ID, "scope": SCOPE,
                  "token_kind": kind, "iat": now, "nbf": now, "exp": now + seconds,
                  "jti": secrets.token_urlsafe(16)}
        return jwt.encode(claims, secret, algorithm="HS256")

    def valid_access(authorization: str | None) -> bool:
        if not authorization or not authorization.startswith("Bearer "):
            return False
        token = authorization[7:]
        try:
            claims = jwt.decode(token, secret, algorithms=["HS256"], audience=MCP_RESOURCE,
                                issuer=BASE, options={"require": ["iss", "aud", "sub", "exp", "iat", "scope"]})
            return (claims.get("token_kind") == "access" and claims.get("client_id") == CLIENT_ID
                    and SCOPE in claims.get("scope", "").split() and claims.get("sub") == PROFILE_SUBJECT)
        except (jwt.InvalidTokenError, ValueError):
            return False

    @app.get("/.well-known/oauth-protected-resource")
    @app.get("/.well-known/oauth-protected-resource/mcp")
    async def protected_resource():
        return JSONResponse({"resource": MCP_RESOURCE, "authorization_servers": [BASE],
                             "scopes_supported": [SCOPE], "bearer_methods_supported": ["header"]},
                            headers={"Cache-Control": "no-store"})

    @app.get("/.well-known/oauth-authorization-server")
    async def authorization_server():
        return JSONResponse({"issuer": BASE, "authorization_endpoint": BASE + "/oauth/authorize",
                             "token_endpoint": BASE + "/oauth/token",
                             "response_types_supported": ["code"],
                             "grant_types_supported": ["authorization_code", "refresh_token"],
                             "code_challenge_methods_supported": ["S256"],
                             "token_endpoint_auth_methods_supported": ["none"],
                             "client_id_metadata_document_supported": True,
                             "authorization_response_iss_parameter_supported": True,
                             "scopes_supported": [SCOPE]}, headers={"Cache-Control": "no-store"})

    @app.get("/oauth/authorize")
    async def authorize(request: Request):
        q = request.query_params
        # We accept only ChatGPT's stable verified CIMD and callback; never an
        # arbitrary client ID URL or caller-chosen redirect target.
        if (q.get("client_id") != CLIENT_ID or q.get("redirect_uri") != REDIRECT_URI
            or q.get("response_type") != "code" or q.get("code_challenge_method") != "S256"
            or not re.fullmatch(r"[A-Za-z0-9_-]{43,128}", q.get("code_challenge", ""))
            or not 8 <= len(q.get("state", "")) <= 512
            or q.get("resource", MCP_RESOURCE) != MCP_RESOURCE
            or q.get("scope", SCOPE) != SCOPE):
            return HTMLResponse("Invalid authorization request", status_code=400)
        prune()
        nonce = secrets.token_urlsafe(24)
        pending[nonce] = AuthorizeRequest(CLIENT_ID, REDIRECT_URI,
                                          q["state"], q["code_challenge"],
                                          SCOPE, MCP_RESOURCE, time.monotonic())
        page = '''<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Nova Phone Lab — Authorize</title></head><body style="max-width:35rem;margin:3rem auto;font-family:system-ui;padding:1rem"><h1>Connect Nova Phone Lab</h1><p>Allow ChatGPT to view and control the short-lived test Android emulator. Only synthetic test apps belong here.</p><form method="post" action="/oauth/authorize"><input type="hidden" name="nonce" value="%s"><label>Private pairing key<br><input name="pairing_key" type="password" autocomplete="off" required style="width:100%%;padding:.7rem;margin:.6rem 0"></label><button type="submit" style="padding:.8rem 1.1rem">Authorize ChatGPT</button></form></body></html>''' % html.escape(nonce, quote=True)
        return HTMLResponse(page, headers={"Cache-Control": "no-store", "Referrer-Policy": "no-referrer", "X-Frame-Options": "DENY"})

    async def form_data(req: Request, max_bytes: int = 8192):
        if req.headers.get("content-type", "").split(";")[0] != "application/x-www-form-urlencoded":
            return None
        raw = await req.body()
        if len(raw) > max_bytes:
            return None
        try:
            data = parse_qs(raw.decode("utf-8"), keep_blank_values=True)
        except (UnicodeDecodeError, ValueError):
            return None
        return {k: v[0] for k, v in data.items() if len(v) == 1}

    @app.post("/oauth/authorize")
    async def authorize_confirm(req: Request):
        data = await form_data(req)
        if not data:
            return HTMLResponse("Invalid form", status_code=400)
        nonce = data.get("nonce", "")
        challenge = pending.pop(nonce, None)
        if not challenge or time.monotonic() - challenge.created_at > CODE_TTL:
            return HTMLResponse("Authorization request expired", status_code=400)
        if not secrets.compare_digest(data.get("pairing_key", "").encode(), pairing.encode()):
            LOG.warning("Owner pairing authorization rejected")
            return HTMLResponse("Wrong pairing key. Return to ChatGPT and try connecting again.", status_code=401)
        prune()
        code = secrets.token_urlsafe(32)
        codes[code] = challenge
        redirect = challenge.redirect_uri + "?" + urlencode({"code": code, "state": challenge.state, "iss": BASE})
        return RedirectResponse(redirect, status_code=302, headers={"Cache-Control": "no-store", "Referrer-Policy": "no-referrer"})

    @app.post("/oauth/token")
    async def token(req: Request):
        data = await form_data(req)
        if not data:
            return JSONResponse({"error": "invalid_request"}, status_code=400, headers={"Cache-Control": "no-store"})
        grant = data.get("grant_type")
        if (data.get("client_id") != CLIENT_ID or data.get("resource", MCP_RESOURCE) != MCP_RESOURCE):
            return JSONResponse({"error": "invalid_client"}, status_code=400)
        if grant == "authorization_code":
            code = data.get("code", "")
            auth = codes.pop(code, None)
            verifier = data.get("code_verifier", "")
            if (not auth or time.monotonic() - auth.created_at > CODE_TTL
                or data.get("redirect_uri") != REDIRECT_URI
                or not 43 <= len(verifier) <= 128 or not re.fullmatch(r"[A-Za-z0-9._~-]+", verifier)):
                return JSONResponse({"error": "invalid_grant"}, status_code=400)
            check = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b"=").decode()
            if not secrets.compare_digest(check, auth.code_challenge):
                return JSONResponse({"error": "invalid_grant"}, status_code=400)
        elif grant == "refresh_token":
            try:
                claims = jwt.decode(data.get("refresh_token", ""), secret, algorithms=["HS256"],
                                    issuer=BASE, audience=MCP_RESOURCE)
                if claims.get("token_kind") != "refresh" or claims.get("sub") != PROFILE_SUBJECT or claims.get("client_id") != CLIENT_ID:
                    raise ValueError("Wrong refresh token")
            except (jwt.InvalidTokenError, ValueError):
                return JSONResponse({"error": "invalid_grant"}, status_code=400)
        else:
            return JSONResponse({"error": "unsupported_grant_type"}, status_code=400)
        response = {"access_token": oauth_token("access", ACCESS_TTL), "token_type": "Bearer",
                    "expires_in": ACCESS_TTL, "refresh_token": oauth_token("refresh", REFRESH_TTL), "scope": SCOPE}
        return JSONResponse(response, headers={"Cache-Control": "no-store"})

    return valid_access