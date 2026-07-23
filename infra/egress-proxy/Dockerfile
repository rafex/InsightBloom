FROM python:3.12.10-alpine3.21

RUN addgroup -S proxy && adduser -S -G proxy -u 10001 proxy
WORKDIR /app
COPY infra/egress-proxy/egress_proxy.py /app/egress_proxy.py
RUN chmod 0555 /app/egress_proxy.py
USER 10001:10001
EXPOSE 3128
ENTRYPOINT ["python3", "/app/egress_proxy.py"]
