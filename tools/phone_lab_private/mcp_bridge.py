"""Nova Phone Lab -- stateless Streamable HTTP MCP adapter.

Never exposes shell/ADB, APK uploads, secrets or public screenshot URLs.
Tool invocation requires a separately provisioned MCP bearer key.  The MCP
handshake/tool definitions reveal no private session data.
"""
from __future__ import annotations

import base64
import json
import os
import secrets
from typing import Callable

from fastapi import Request, Response, HTTPException
from fastapi.responses import JSONResponse
from oauth_server import install_oauth, BASE
from pydantic import ValidationError

MAX_RPC_BYTES = 64_000
PROTOCOL_VERSION = "2025-06-18"


def _tool(name: str, desc: str, props: dict, required: list[str], read_only=False):
    return {"name": name, "description": desc,
            "securitySchemes": [{"type": "oauth2", "scopes": ["lab:control"]}],
            "inputSchema": {"type": "object", "properties": props, "required": required, "additionalProperties": False},
            "annotations": {"readOnlyHint": read_only, "destructiveHint": False}}

SID = {"type": "string", "minLength": 8, "maxLength": 128, "description": "Session ID returned by list_sessions."}
COORD = {"type": "integer", "minimum": 0, "maximum": 10000}
TOOLS = [
    _tool("list_sessions", "Find active short-lived Android 16 emulator sessions. Returns [] if none are attached.", {}, [], True),
    _tool("device_status", "Read device API level and screen size of a connected session.", {"session_id": SID}, ["session_id"], True),
    _tool("see_screen", "Get a fresh, actual PNG screenshot of the Android emulator as image content.", {"session_id": SID}, ["session_id"], True),
    _tool("tap_screen", "Tap a bounded x,y coordinate on the Android 16 emulator, then use see_screen.", {"session_id": SID, "x": COORD, "y": COORD}, ["session_id", "x", "y"]),
    _tool("swipe_screen", "Swipe from x1,y1 to x2,y2 on the Android emulator, then use see_screen.",
          {"session_id": SID, "x1": COORD, "y1": COORD, "x2": COORD, "y2": COORD,
           "duration_ms": {"type": "integer", "minimum": 100, "maximum": 3000, "default": 450}},
          ["session_id", "x1", "y1", "x2", "y2"]),
    _tool("press_back", "Press Android Back on the connected session.", {"session_id": SID}, ["session_id"]),
    _tool("press_home", "Press Android Home on the connected session.", {"session_id": SID}, ["session_id"]),
    _tool("open_android_settings", "Open Android Settings in the connected session.", {"session_id": SID}, ["session_id"]),
]
NAME_TO_ACTION = {"device_status": "status", "see_screen": "screenshot", "tap_screen": "tap",
                  "swipe_screen": "swipe", "press_back": "back", "press_home": "home",
                  "open_android_settings": "settings"}


def install_mcp(app, execute: Callable, list_sessions: Callable, Command: type, oidc_controller_auth: Callable | None = None):
    """Mount MCP on the existing relay without changing its authenticated endpoints."""
    secret = os.environ.get("PHONE_LAB_MCP_TOKEN", "")
    # Refuse to publish MCP tool execution without a dedicated key.
    if len(secret) < 32:
        raise RuntimeError("PHONE_LAB_MCP_TOKEN must contain >=32 characters")

    oauth_access = install_oauth(app, pairing_key=secret)

    def rpc_result(request_id, result):
        return {"jsonrpc": "2.0", "id": request_id, "result": result}

    def rpc_error(request_id, code, message):
        return {"jsonrpc": "2.0", "id": request_id, "error": {"code": code, "message": message}}

    def authorized(auth):
        return bool(auth and auth.startswith("Bearer ") and
                    (secrets.compare_digest(auth[7:].encode(), secret.encode()) or
                     (oidc_controller_auth is not None and oidc_controller_auth(auth)) or
                     oauth_access(auth)))

    async def call_tool(name: str, args: dict):
        allowed = next((t for t in TOOLS if t["name"] == name), None)
        if allowed is None:
            return {"isError": True, "content": [{"type": "text", "text": "Unknown tool"}]}
        required = set(allowed["inputSchema"]["required"])
        if not required.issubset(args) or set(args) - set(allowed["inputSchema"]["properties"]):
            return {"isError": True, "content": [{"type": "text", "text": "Invalid parameters"}]}
        try:
            for key, value in args.items():
                schema = allowed["inputSchema"]["properties"][key]
                if schema["type"] == "integer":
                    if type(value) is not int or not schema["minimum"] <= value <= schema["maximum"]:
                        raise ValueError("Invalid integer coordinate/duration")
                elif schema["type"] == "string":
                    if not isinstance(value, str) or not schema["minLength"] <= len(value) <= schema["maxLength"]:
                        raise ValueError("Invalid session ID")
            # The relay's existing permissioned handlers hold the actual session,
            # timeouts, command allowlist, and bounded frame checks.
            ctl_auth = "Bearer " + os.environ["PHONE_LAB_CONTROL_TOKEN"]
            if name == "list_sessions":
                response = await list_sessions(authorization=ctl_auth)
            else:
                cmd = Command(session_id=args["session_id"], action=NAME_TO_ACTION[name],
                              **{k: v for k, v in args.items() if k != "session_id"})
                response = await execute(cmd=cmd, authorization=ctl_auth)
            if name == "see_screen":
                png = response.body
                return {"content": [
                    {"type": "image", "data": base64.b64encode(png).decode("ascii"), "mimeType": "image/png"},
                    {"type": "text", "text": "Fresh Android emulator PNG received."}
                ]}
            return {"content": [{"type": "text", "text": json.dumps(response, separators=(",", ":"))}],
                    "structuredContent": response}
        except (HTTPException, ValidationError, ValueError, KeyError) as exc:
            if isinstance(exc, HTTPException):
                detail = str(exc.detail)
            else:
                detail = "Invalid request or unavailable session"
            return {"isError": True, "content": [{"type": "text", "text": detail[:160]}]}

    @app.api_route("/mcp", methods=["POST", "GET", "DELETE"])
    async def mcp_endpoint(request: Request):
        if request.method != "POST":
            return Response(status_code=405, headers={"Allow": "POST"})
        if request.headers.get("content-type", "").split(";")[0].strip().lower() != "application/json":
            return JSONResponse(rpc_error(None, -32600, "Content-Type must be application/json"), status_code=415)
        if request.headers.get("content-length") and int(request.headers["content-length"]) > MAX_RPC_BYTES:
            return Response(status_code=413)
        raw = await request.body()
        if len(raw) > MAX_RPC_BYTES:
            return Response(status_code=413)
        try:
            obj = json.loads(raw)
        except (ValueError, UnicodeDecodeError):
            return JSONResponse(rpc_error(None, -32700, "Parse error"))
        if not isinstance(obj, dict) or obj.get("jsonrpc") != "2.0" or not isinstance(obj.get("method"), str):
            return JSONResponse(rpc_error(obj.get("id") if isinstance(obj, dict) else None, -32600, "Invalid request"))
        if "id" not in obj:
            return Response(status_code=202)
        method = obj["method"]
        rid = obj.get("id")
        if method == "initialize":
            result = {"protocolVersion": PROTOCOL_VERSION, "capabilities": {"tools": {"listChanged": False}},
                      "serverInfo": {"name": "nova-phone-lab-live", "version": "0.3.0"},
                      "instructions": "Use list_sessions before seeing or touching a device. Sessions expire. Tool calls do not launch or persist an emulator. Never run commands on a device without an authorized session."}
            return JSONResponse(rpc_result(rid, result), headers={"Cache-Control": "no-store"})
        if method == "ping":
            return JSONResponse(rpc_result(rid, {}))
        if method == "tools/list":
            return JSONResponse(rpc_result(rid, {"tools": TOOLS}), headers={"Cache-Control": "no-store"})
        if method == "tools/call":
            if not authorized(request.headers.get("authorization")):
                challenge = ('Bearer resource_metadata="' + BASE + '/.well-known/oauth-protected-resource",'
                             ' error="insufficient_scope", error_description="Connect Nova Phone Lab to continue"')
                return JSONResponse(rpc_result(rid, {
                    "isError": True,
                    "content": [{"type": "text", "text": "Authentication required; connect Nova Phone Lab."}],
                    "_meta": {"mcp/www_authenticate": [challenge]}}),
                    headers={"WWW-Authenticate": challenge, "Cache-Control": "no-store"})
            params = obj.get("params", {})
            if not isinstance(params, dict) or not isinstance(params.get("name"), str) or not isinstance(params.get("arguments", {}), dict):
                return JSONResponse(rpc_error(rid, -32602, "Invalid tool parameters"))
            result = await call_tool(params["name"], params.get("arguments", {}))
            return JSONResponse(rpc_result(rid, result), headers={"Cache-Control": "no-store"})
        return JSONResponse(rpc_error(rid, -32601, "Method not found"))