FROM debian:bookworm-slim
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends netbase nfs-ganesha nfs-ganesha-vfs rpcbind nfs-common \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /export /run/ganesha /run/rpcbind
COPY ganesha.conf /etc/ganesha/ganesha.conf
COPY entrypoint.sh /usr/local/bin/nfs-entrypoint
EXPOSE 111/tcp 2049/tcp 20048/tcp
ENTRYPOINT ["/bin/sh", "/usr/local/bin/nfs-entrypoint"]
