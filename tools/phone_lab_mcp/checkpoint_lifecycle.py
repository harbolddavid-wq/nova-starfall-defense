"""Fail-closed session lifecycle for future checkpoint integration.

Callers provide concrete, authenticated storage and app-specific backup hooks.
This module does not provide durable storage or connect itself to Phone Lab.
"""
from __future__ import annotations


def run_with_checkpoint(*, read_checkpoint, restore_checkpoint, run_session,
                        capture_checkpoint, write_checkpoint):
    """Restore before starting, and save after a normally completed session.

    Missing prior checkpoint is represented by None; failures must raise and
    never be translated into None. A failed restore prevents startup and a
    failed capture must never overwrite a previously verified checkpoint.
    Return from write_checkpoint means a durable read-back was verified.
    Uncatchable termination and forcibly cancelled jobs cannot be saved here.
    """
    old = read_checkpoint()
    if old is not None:
        restore_checkpoint(old)
    result = run_session()
    new = capture_checkpoint()
    if not isinstance(new, bytes) or not new:
        raise ValueError('Empty checkpoint; existing save retained')
    write_checkpoint(new)
    return result
