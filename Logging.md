# Logging and diagnostics

The client uses SLF4J. Applications supply the logging backend and configuration;
the library does not install a backend or change the application's log levels.

* INFO reports mount attempts and privileged source-port fallback.
* WARN reports failed mounts, retries, channel failures, failed mount-registration
  cleanup, and output-stream flush failures suppressed by `close()`.
* DEBUG traces service discovery, TCP connection lifecycle, RPC requests/responses,
  late replies, and file-operation failures converted into fallback values.

Enable DEBUG for `com.emc.ecs.nfsclient` when investigating a failure. With the
`slf4j-simple` backend already used by the tests and demo, pass this JVM option:

```text
-Dorg.slf4j.simpleLogger.log.com.emc.ecs.nfsclient=debug
```

RPC diagnostics include the configured server, selected endpoint, request details
(including transaction ID), attempt count, and symbolic protocol statuses with
their numeric codes. Transport exceptions preserve the original cause. DEBUG
logs can contain file names, export paths, and file handles; file contents and
credential bodies are not intentionally logged.

The command-line reading and load-test tools keep usage/results on stdout and
write failures, operation context, and stack traces to stderr.

## Mount failures

A permission failure now identifies the MOUNT service endpoint separately from
the NFS endpoint, for example:

```text
MOUNT failed: server=files.example, mountPort=20048, export=/share, nfsVersion=3, attempt=1/2, privilegedPort=false, status=MNT3ERR_ACCES(13).
```

The message also points to the server export's client allowlist, UID/GID
permissions, and privileged source-port requirements. A mount access-denied
response does not establish which of those checks failed.

`CredentialUnix` supplies AUTH_SYS UID/GID values to the server. These values do
not grant the Java process local permission to bind a port below 1024. If the
server requires such a source port, the local process must have the appropriate
OS permission. The client retains its existing automatic privileged-port retry
for RPC authentication rejections; a MOUNT `MNT3ERR_ACCES` response does not
automatically trigger that retry.

For a missing service registration, verify that rpcbind advertises the requested
TCP program/version. NFSv3 uses NFS program 100003 and MOUNT program 100005;
their ports can differ. Network failures identify TCP port 111 for portmapper,
the discovered mount port for MOUNT, or the selected NFS endpoint for file calls.

UNMOUNT removes the temporary mount-list registration after the root file handle
is obtained. Cleanup errors are logged on every failed attempt, including the
last; they do not discard that successful mount. Successful service discovery,
mount, and cleanup calls are no longer repeated as though they had failed.

Output-stream `close()` retains its existing behavior of suppressing flush
exceptions. Such failures now produce a WARN containing the file, offsets,
buffered byte count, and stack trace. Call `flush()` explicitly if the application
needs the failure delivered as an exception before closing.
