"""Strict GitHub Actions OIDC authentication for ephemeral Phone Lab sessions.

No token is stored in source, logs or a public GitHub issue. Both workflow
identity and audience/role are checked after cryptographic signature validation.
"""
import os
import logging

logger = logging.getLogger("phone_lab_oidc")
from dataclasses import dataclass
import jwt
from jwt import PyJWKClient
from jwt.exceptions import PyJWTError

ISSUER = "https://token.actions.githubusercontent.com"
JWKS_URL = ISSUER + "/.well-known/jwks"
AUDIENCES = {"agent": "nova-phone-lab-agent", "controller": "nova-phone-lab-controller"}

@dataclass
class GithubOIDCVerifier:
    workflow_ref: str
    repository: str = "harbolddavid-wq/nova-starfall-defense"

    def __post_init__(self):
        self.jwks = PyJWKClient(JWKS_URL, cache_jwk_set=True, lifespan=600, timeout=8)

    @classmethod
    def from_environment(cls):
        ref = os.environ.get("PHONE_LAB_ALLOWED_GITHUB_WORKFLOW_REF", "")
        return cls(ref) if ref else None

    def verify(self, token: str, role: str) -> bool:
        if not token or len(token) > 16000 or role not in AUDIENCES:
            return False
        try:
            key = self.jwks.get_signing_key_from_jwt(token)
            claims = jwt.decode(token, key.key, algorithms=["RS256"],
                                audience=AUDIENCES[role], issuer=ISSUER,
                                options={"require": ["exp", "iat", "iss", "aud", "sub"]},
                                leeway=15)
        except (PyJWTError, ValueError, OSError, TimeoutError) as exc:
            logger.warning("GitHub OIDC signature/expiry verification rejected: %s", type(exc).__name__)
            return False
        branch = "refs/heads/experiment/nova-phone-lab-live-2026-11-01"
        checks = {
            "repository": claims.get("repository") == self.repository,
            "ref": claims.get("ref") == branch,
            "workflow_ref": claims.get("workflow_ref") == self.workflow_ref,
            "event_name": claims.get("event_name") in {"push", "workflow_dispatch"},
            "runner_environment": claims.get("runner_environment") == "github-hosted",
        }
        failed = [name for name, ok in checks.items() if not ok]
        if failed:
            logger.warning("GitHub OIDC validated signature but rejected identity checks: %s", ",".join(failed))
        return not failed