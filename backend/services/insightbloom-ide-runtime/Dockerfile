FROM debian:bookworm-slim

ARG APP_VERSION=dev
ARG GIT_SHA=unknown
ENV APP_VERSION=${APP_VERSION}
ENV GIT_SHA=${GIT_SHA}
ENV CONTROL_PORT=9499
ENV APP_BASE_PORT=9500
ENV MAX_PUBLICATIONS=10

RUN apt-get update && apt-get install -y --no-install-recommends \
    podman uidmap fuse-overlayfs slirp4netns python3 ca-certificates curl \
  && rm -rf /var/lib/apt/lists/* \
  && echo "root:100000:65536" > /etc/subuid \
  && echo "root:100000:65536" > /etc/subgid \
  && mkdir -p /etc/containers \
  && printf '[storage]\ndriver = "overlay"\n\n[storage.options.overlay]\nmount_program = "/usr/bin/fuse-overlayfs"\n' > /etc/containers/storage.conf

COPY podman-agent.py /usr/local/bin/podman-agent.py
RUN chmod 755 /usr/local/bin/podman-agent.py
EXPOSE 9499-9509
ENTRYPOINT ["python3", "/usr/local/bin/podman-agent.py"]
