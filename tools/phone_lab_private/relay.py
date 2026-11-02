"""Nova Phone Lab private relay, pre-deployment prototype.

No GitHub issue transport, no ADB listener, no arbitrary shell endpoint.
Requires a private host with HTTPS/WSS TLS termination in production.
"""
import asyncio
import base64
import binascii
import os
import secrets
import time
from dataclasses import dataclass, field
from typing import Any, Literal

from fastapi import FastAPI, Header, HTTPException, WebSocket, WebSocketDisconnect
from fastapi.responses import Response
from pydantic import BaseModel, Field

ACTIONS = {"screenshot", "status", "tap", "swipe", "back", "home", "settings"}
MAX_FRAME_BYTES = 8_000_000
PNG_HEADER = b"\x89PNG\r\n\x1a\n"


class Command(BaseModel):
    session_id: str = Field(min_length=8, max_length=128)
    action: Literal["screenshot", "status", "tap", "swipe", "back", "home", "settings"]
    x: int | None = None
    y: int | None = None
    x1: int | None = None
    y1: int | None = None
    x2: int | None = None
    y2: int | None = None
    duration_ms: int | None = Field(default=None, ge=100, le=3000)


@dataclass
class Session:
    id: str
    socket: WebSocket
    created: float
    device: dict[str, Any] = field(default_factory=dict)
    pending: dict[str, asyncio.Future] = field(default_factory=dict)
    lock: asyncio.Lock = field(default_factory=asyncio.Lock)


def make_app(agent_token: str | None = None, control_token: str | None = None,
             ttl_seconds: int = 600, command_timeout_seconds: float = 20) -> FastAPI:
    agent_token = agent_token or os.environ.get("PHONE_LAB_AGENT_TOKEN", "")
    control_token = control_token or os.environ.get("PHONE_LAB_CONTROL_TOKEN", "")
    if not agent_token or not control_token or agent_token == control_token:
        raise RuntimeError("Set two distinct nonempty PHONE_LAB_AGENT_TOKEN and PHONE_LAB_CONTROL_TOKEN values")
    if ttl_seconds < 5 or ttl_seconds > 86400:
        raise ValueError("Invalid session TTL")
    app = FastAPI(title="Nova Phone Lab Private Relay", docs_url=None, redoc_url=None, openapi_url=None)
    sessions: dict[str, Session] = {}

    def is_auth(provided: str | None, expected: str) -> bool:
        if not provided or not provided.startswith("Bearer "):
            return False
        return secrets.compare_digest(provided[7:].encode(), expected.encode())

    def require_control(auth: str | None):
        if not is_auth(auth, control_token):
            raise HTTPException(status_code=401, detail="Unauthorized")

    def valid_session(sid: str) -> Session:
        sess = sessions.get(sid)
        if sess is None:
            raise HTTPException(status_code=404, detail="No active session")
        if time.monotonic() - sess.created > ttl_seconds:
            raise HTTPException(status_code=410, detail="Session expired")
        return sess

    @app.get("/health")
    async def health():
        return {"status": "ok"}  # No session/device details leaked.

    @app.get("/v1/sessions")
    async def list_sessions(authorization: str | None = Header(default=None)):
        require_control(authorization)
        now = time.monotonic()
        return {"sessions": [{"session_id": s.id, "device": s.device,
                              "expires_in_seconds": max(0, int(ttl_seconds - (now - s.created)))}
                             for s in sessions.values() if now - s.created <= ttl_seconds]}

    @app.websocket("/v1/agent")
    async def agent_socket(ws: WebSocket):
        if not is_auth(ws.headers.get("authorization"), agent_token):
            await ws.close(code=1008)
            return
        await ws.accept()
        session_id = secrets.token_urlsafe(18)
        sess = Session(id=session_id, socket=ws, created=time.monotonic())
        sessions[session_id] = sess
        try:
            await ws.send_json({"type": "ready", "session_id": session_id,
                                "expires_in_seconds": ttl_seconds})
            while time.monotonic() - sess.created <= ttl_seconds:
                try:
                    msg = await asyncio.wait_for(ws.receive_json(), timeout=min(30.0, ttl_seconds))
                except asyncio.TimeoutError:
                    # Ping request keeps idle connections from being silently abandoned.
                    await ws.send_json({"type": "ping"})
                    continue
                if not isinstance(msg, dict):
                    continue
                if msg.get("type") == "hello":
                    info = msg.get("device")
                    if isinstance(info, dict):
                        sess.device = {str(k)[:40]: str(v)[:120] for k, v in list(info.items())[:8]}
                if msg.get("type") == "result":
                    req_id = msg.get("request_id")
                    future = sess.pending.pop(req_id, None) if isinstance(req_id, str) else None
                    if future is not None and not future.done():
                        future.set_result(msg)
        except (WebSocketDisconnect, RuntimeError):
            pass
        finally:
            sessions.pop(sess.id, None)
            for future in sess.pending.values():
                if not future.done():
                    future.set_exception(ConnectionError("Agent disconnected"))
            sess.pending.clear()
            try:
                await ws.close()
            except RuntimeError:
                pass

    @app.post("/v1/command")
    async def execute(cmd: Command, authorization: str | None = Header(default=None)):
        require_control(authorization)
        sess = valid_session(cmd.session_id)
        args = cmd.model_dump(exclude_none=True, exclude={"session_id"})
        # Reject unsupported parameters before the request touches the emulator.
        allowed = {"tap": {"x", "y"}, "swipe": {"x1", "y1", "x2", "y2", "duration_ms"}}.get(cmd.action, set())
        supplied = set(args) - {"action"}
        required = {"tap": {"x", "y"}, "swipe": {"x1", "y1", "x2", "y2"}}.get(cmd.action, set())
        if not required.issubset(supplied) or not supplied.issubset(allowed):
            raise HTTPException(status_code=422, detail="Invalid action parameters")
        if any(isinstance(v, int) and (v < 0 or v > 10000) for k, v in args.items() if k != "duration_ms" and k != "action"):
            raise HTTPException(status_code=422, detail="Invalid coordinates")
        async with sess.lock:
            request_id = secrets.token_urlsafe(12)
            fut = asyncio.get_running_loop().create_future()
            sess.pending[request_id] = fut
            try:
                await sess.socket.send_json({"type": "command", "request_id": request_id, **args})
                result = await asyncio.wait_for(fut, timeout=command_timeout_seconds)
            except (asyncio.TimeoutError, ConnectionError, WebSocketDisconnect, RuntimeError) as exc:
                raise HTTPException(status_code=504, detail="Agent unavailable or timed out") from exc
            finally:
                sess.pending.pop(request_id, None)
        if result.get("ok") is not True:
            raise HTTPException(status_code=502, detail=str(result.get("error", "Agent rejected command"))[:150])
        if cmd.action == "screenshot":
            encoded = result.get("image_base64")
            if not isinstance(encoded, str) or len(encoded) > MAX_FRAME_BYTES * 2:
                raise HTTPException(status_code=502, detail="Invalid screenshot payload")
            try:
                frame = base64.b64decode(encoded, validate=True)
            except (binascii.Error, ValueError) as exc:
                raise HTTPException(status_code=502, detail="Invalid frame encoding") from exc
            if not frame.startswith(PNG_HEADER) or len(frame) > MAX_FRAME_BYTES:
                raise HTTPException(status_code=502, detail="Invalid screenshot bytes")
            return Response(content=frame, media_type="image/png", headers={"Cache-Control": "no-store"})
        return {"session_id": sess.id, "result": result.get("result", {})}

    return app


# Server refuses to start without proper credentials, including via uvicorn relay:app.
app = None
try:
    app = make_app()
except RuntimeError:
    pass

if __name__ == "__main__":
    import uvicorn
    if app is None:
        raise SystemExit("Set PHONE_LAB_AGENT_TOKEN and PHONE_LAB_CONTROL_TOKEN first")
    uvicorn.run(app, host="127.0.0.1", port=8765)