FROM python:3.12-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 PORT=10000
WORKDIR /app
RUN python -m pip install --no-cache-dir 'fastapi>=0.115,<1' 'uvicorn[standard]>=0.30,<1' 'pydantic>=2,<3' 'PyJWT[crypto]>=2.9,<3' \
    && useradd --create-home --uid 10001 lab
COPY tools/phone_lab_private/relay.py /app/relay.py
COPY tools/phone_lab_private/oidc_auth.py /app/oidc_auth.py
COPY tools/phone_lab_private/mcp_bridge.py /app/mcp_bridge.py
COPY tools/phone_lab_private/oauth_server.py /app/oauth_server.py
COPY tools/phone_lab_host/host_start.py /app/host_start.py
USER lab
EXPOSE 10000
CMD ["python", "/app/host_start.py"]