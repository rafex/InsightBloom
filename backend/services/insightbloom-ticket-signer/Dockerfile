FROM golang:1.26.2-bookworm AS gopass-build
ARG GOPASS_VERSION=v1.17.4
RUN CGO_ENABLED=0 go install github.com/gopasspw/gopass@${GOPASS_VERSION}

FROM python:3.12.12-slim-bookworm
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 GOPASS_HOMEDIR=/var/lib/gopass
RUN apt-get update && apt-get install -y --no-install-recommends gnupg ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 signer && useradd --system --uid 10001 --gid signer --home-dir /var/lib/gopass signer \
    && mkdir -p /var/lib/gopass /var/lib/gopass/.password-store \
    && chown -R signer:signer /var/lib/gopass
COPY --from=gopass-build /go/bin/gopass /usr/local/bin/gopass
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app.py entrypoint.sh .
RUN chmod 0555 /app/entrypoint.sh
USER 10001:10001
EXPOSE 8097
ENTRYPOINT ["/app/entrypoint.sh"]
