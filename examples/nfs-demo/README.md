# Local NFSv3 demo

This example builds an NFS-Ganesha server and runs a minimal Java app in a second
container against it using the library in this checkout. Requires Docker Compose and the JDK 25
toolchain configured by the root build. Java source targets Java 17.

From the repository root:

```sh
bash examples/nfs-demo/run.sh
```

The script starts the server, waits for RPC/NFS/export health checks, runs the app,
and saves the report and server logs under `build/reports/nfs-demo/`.
It returns a nonzero exit code if startup or any client check fails.

The app verifies mount/lookup, NULL, mkdir, create, write/commit, read with exact
byte comparison, file attributes, directory listing, rename, read after rename,
file deletion, and directory deletion. Each run uses a unique directory and
removes its data on success. Failed runs retain their directory for inspection.

The server stays running with a persistent named Docker volume. It exports
`/export` via TCP on the private Compose network: rpcbind 111, NFS 2049, mountd 20048.
No ports are published on the host, avoiding conflicts with macOS rpcbind.
The demo uses AUTH_SYS with UID/GID 0 and no root squashing, for local development.
Ganesha VFS needs `DAC_READ_SEARCH` to open file handles. There are no host
directory mounts and the container does not run in privileged mode.

Run the client again against the running server:

```sh
docker compose -f examples/nfs-demo/compose.yaml run --rm client
```

Pass `hostname /path` after `client` to override the target. To run the app on the
host against an external server, use `./gradlew nfsDemo -PnfsServer=hostname -PnfsExport=/path`.

Stop the server (keep its volume):

```sh
docker compose -f examples/nfs-demo/compose.yaml down
```

Delete the demo volume as well when finished:

```sh
docker compose -f examples/nfs-demo/compose.yaml down -v
```

Configuration references: [Docker Compose](https://docs.docker.com/reference/compose-file/)
and [Ganesha VFS requirements](https://github.com/nfs-ganesha/nfs-ganesha/wiki/VFS).
