"""Production deployment entrypoint: one process, authenticated only.

The command endpoint and screens remain protected by separate bearer keys.
Use this behind a TLS-terminating platform such as Render HTTPS.
"""
import os
import secrets
import sys


def validate_environment(environ=None):
    env = environ if environ is not None else os.environ
    agent = env.get("PHONE_LAB_AGENT_TOKEN", "")
    control = env.get("PHONE_LAB_CONTROL_TOKEN", "")
    if len(agent) < 32 or len(control) < 32:
        raise ValueError("Each secret must contain at least 32 characters")
    if secrets.compare_digest(agent, control):
        raise ValueError("Agent and control tokens must be distinct")
    port = int(env.get("PORT", "10000"))
    if not 1 <= port <= 65535:
        raise ValueError("Invalid port")
    return port


def main():
    try:
        port = validate_environment()
    except (ValueError, TypeError) as exc:
        print("Private relay refused to start:", str(exc), file=sys.stderr)
        raise SystemExit(2)
    # Deliberately one Uvicorn worker; sessions are stored in this process's memory.
    import uvicorn
    uvicorn.run("relay:app", host="0.0.0.0", port=port, workers=1,
                access_log=False, proxy_headers=True)


if __name__ == "__main__":
    main()