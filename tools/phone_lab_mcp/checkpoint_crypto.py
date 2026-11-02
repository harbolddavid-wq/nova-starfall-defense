"""Authenticated encryption for bounded Phone Lab app checkpoints.

This module handles bytes only. It does not grant runner storage access or make a
checkpoint durable. Key management and storage are intentionally separate.
"""
from __future__ import annotations

import base64
import json
import os
import re
from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

MAGIC = b'NOVA-SAVE\x01'
MAX_BYTES = 32 * 1024 * 1024
PACKAGE = re.compile(r'^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+$')


class CheckpointError(ValueError):
    pass


def decode_key(text: str) -> bytes:
    try:
        key = base64.b64decode(text, validate=True)
    except (ValueError, TypeError) as e:
        raise CheckpointError('Invalid checkpoint key encoding') from e
    if len(key) != 32:
        raise CheckpointError('Checkpoint key must contain 32 bytes')
    return key


def _identity(package: str, apk_sha256: str) -> bytes:
    if not PACKAGE.fullmatch(package) or not re.fullmatch(r'[0-9a-f]{64}', apk_sha256):
        raise CheckpointError('Invalid app identity')
    return json.dumps({'package': package, 'apk_sha256': apk_sha256},
                      sort_keys=True, separators=(',', ':')).encode()


def encrypt(data: bytes, key: bytes, package: str, apk_sha256: str) -> bytes:
    identity = _identity(package, apk_sha256)
    if len(key) != 32 or not data or len(data) > MAX_BYTES:
        raise CheckpointError('Invalid key or checkpoint size')
    nonce = os.urandom(12)
    # Identity is caller-supplied on restore and authenticated, never trusted
    # from storage. A new random 96-bit nonce is generated for every save.
    return MAGIC + nonce + AESGCM(key).encrypt(nonce, data, MAGIC + identity)


def decrypt(blob: bytes, key: bytes, package: str, apk_sha256: str) -> bytes:
    identity = _identity(package, apk_sha256)
    if len(key) != 32 or not blob.startswith(MAGIC) or not (len(MAGIC)+28 < len(blob) <= len(MAGIC)+28+MAX_BYTES):
        raise CheckpointError('Invalid checkpoint envelope')
    nonce = blob[len(MAGIC):len(MAGIC)+12]
    try:
        return AESGCM(key).decrypt(nonce, blob[len(MAGIC)+12:], MAGIC + identity)
    except InvalidTag as e:
        raise CheckpointError('Checkpoint authentication failed') from e
