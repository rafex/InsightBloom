FROM python:3.12.16-alpine@sha256:c5ba3deef00c7758561a0f03c26f44f10589263d40e732b8748e0d1f38f6dd89

RUN addgroup -S proxy && adduser -S -G proxy -u 10001 proxy
WORKDIR /app
COPY infra/egress-proxy/egress_proxy.py /app/egress_proxy.py
RUN chmod 0555 /app/egress_proxy.py
USER 10001:10001
EXPOSE 3128
ENTRYPOINT ["python3", "/app/egress_proxy.py"]
