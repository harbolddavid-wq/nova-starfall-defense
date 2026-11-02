"""Authenticated controller SDK for a private relay; can be called by future MCP tools."""
import os
from urllib import request, error
import json


class PrivatePhoneLab:
    def __init__(self, base_url=None, token=None):
        self.base_url = (base_url or os.environ["PHONE_LAB_RELAY_HTTPS_URL"]).rstrip("/")
        self.token = token or os.environ["PHONE_LAB_CONTROL_TOKEN"]
        if not (self.base_url.startswith("https://") or self.base_url.startswith("http://127.0.0.1:")
                or self.base_url.startswith("http://localhost:")):
            raise ValueError("Control transport must be HTTPS or local loopback")

    def _request(self, path, payload=None):
        data = None if payload is None else json.dumps(payload).encode("utf8")
        req = request.Request(self.base_url + path, data=data,
                              headers={"Authorization": "Bearer " + self.token,
                                       "Content-Type": "application/json", "Cache-Control": "no-store"},
                              method="GET" if data is None else "POST")
        with request.urlopen(req, timeout=35) as response:
            content = response.read()
            return content if response.headers.get_content_type() == "image/png" else json.loads(content)

    def sessions(self):
        return self._request("/v1/sessions")

    def command(self, session_id, action, **kwargs):
        return self._request("/v1/command", {"session_id": session_id, "action": action, **kwargs})