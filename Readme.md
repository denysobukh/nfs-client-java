[![Dependency Security](https://github.com/denysobukh/nfs-client-java/actions/workflows/security.yml/badge.svg?branch=master)](https://github.com/denysobukh/nfs-client-java/actions/workflows/security.yml)
[![Error Prone](https://github.com/denysobukh/nfs-client-java/actions/workflows/ci.yml/badge.svg?branch=master&label=Error%20Prone)](https://github.com/denysobukh/nfs-client-java/actions/workflows/ci.yml)
[![SpotBugs](https://github.com/denysobukh/nfs-client-java/actions/workflows/ci.yml/badge.svg?branch=master&label=SpotBugs)](https://github.com/denysobukh/nfs-client-java/actions/workflows/ci.yml)
[![JaCoCo line coverage](https://denysobukh.github.io/nfs-client-java/coverage.svg)](https://denysobukh.github.io/nfs-client-java/coverage/)
[![Code style: Google Java Format (Spotless)](https://img.shields.io/badge/code_style-Google_Java_Format_(Spotless)-blue)](#code-formatting)

NFS Client for Java
===

Maintained fork of the EMC NFS client with security fixes.

Version **1.1.2** uses **Netty 4.2.16.Final** and requires **Java 17 or newer**
at runtime.

Description
---

This project is an NFS Java client, with some extra abstraction
to allow extensions to handle other NFS versions (it currently handles
only NFS v3). The package
com.emc.ecs.nfsclient.nfs has generic classes and interfaces,
while com.emc.ecs.nfsclient.nfs.nfs3 has the NFS Version 3 implementation
based on RFC 1813 (https://tools.ietf.org/html/rfc1813). For full details
on the semantics of these calls and the allowed parameter values, please
consult the RFC.

The project also includes NFS equivalents to java.io.File and related
functionality. The classes for this, both the generic and the
NFS3-specific versions, are in the package com.emc.ecs.nfsclient.nfs.io.

Security changes in 1.1.1
---

Version 1.1.1 replaces the end-of-life Netty 3 dependency with
`io.netty:netty-transport` and `io.netty:netty-codec-base`, both at **4.2.16.Final**.

Installation
---

Maven coordinates for this fork (once published to Maven Central):

    <dependency>
        <groupId>io.github.denysobukh</groupId>
        <artifactId>nfs-client</artifactId>
        <version>1.1.2</version>
    </dependency>

You can also use it as a Gradle dependency:

    implementation 'io.github.denysobukh:nfs-client:1.1.2'

**Version 1.1.2 requires Java 17 or newer. Java 21 is a supported runtime choice.**

Docs
---

Browse the [Javadocs](https://denysobukh.github.io/nfs-client-java/latest/javadoc/).

Code formatting
---

Java sources in all modules, including tests and the NFS demo, use
[Google Java Format](https://github.com/google/google-java-format) through
[Spotless](https://github.com/diffplug/spotless). Run Gradle with JDK 25,
as required by this project's build toolchain.

```sh
./gradlew spotlessApply  # Format Java sources
./gradlew spotlessCheck # Check formatting without changing files
```

Formatting is also checked by `./gradlew check` and the existing CI build.

Static analysis
---

[Error Prone](https://errorprone.info/) runs during Java compilation in all
modules, including tests and the NFS demo. It uses the existing JDK 25 toolchain
and keeps the Java 17 bytecode target. Default error-level findings fail compilation;
default warning-level findings are reported in the compiler output.

```sh
./gradlew clean build demoClasses # Build, test, and analyze all Java source sets
```

[SpotBugs](https://spotbugs.github.io/) also analyzes every Java source set during
`./gradlew check` and `./gradlew build`, including tests and the demo, at maximum
effort with all confidence levels included. Unsuppressed findings fail the build.
HTML and XML reports are generated under each module's `build/reports/spotbugs/`
directory and uploaded as the `spotbugs-reports` CI artifact.

Reviewed exceptions for intentional API, callback, and test patterns are documented
in [the SpotBugs exclusion filter](config/spotbugs/exclude.xml). There is no blanket
baseline; new findings must be reviewed and fixed or narrowly justified.

The Error Prone and SpotBugs badges show the CI workflow status, which includes
these checks.

Test coverage
---

[JaCoCo](https://www.jacoco.org/jacoco/) collects coverage when tests run in any
module. `./gradlew build` also generates a combined report for the client and both
tool modules, merging their test executions so calls into the client from tool
tests count toward client coverage.

```sh
./gradlew jacocoAggregateReport # Run tests and generate combined HTML and XML
```

Open `build/reports/jacoco/aggregate/html/index.html` for the combined report;
modules with test execution also have a report under `build/reports/jacoco/test/`.
CI uploads these as the `jacoco-reports` artifact. The GitHub Pages workflow
publishes the [combined report](https://denysobukh.github.io/nfs-client-java/coverage/)
and updates the README badge with its measured line coverage on each master build.
Tests requiring an external NFS server are skipped unless `test.properties` is
configured; skipped tests do not contribute coverage. No coverage threshold or
production-class exclusions are applied.

How to use the bare client
---

For a local NFS server and a minimal client app, run
`bash examples/nfs-demo/run.sh`. The [Docker NFS demo](examples/nfs-demo/README.md)
checks basic file operations against NFS-Ganesha and prints a pass/fail report.

The Nfs client instances provide both direct and lightly wrapped access to all NFS functionality.
Working examples for how to instantiate an Nfs3 client and how to exercise most 
NFS functionality are available in the JUnit tests.
[Test_Nfs3.java](src/test/java/com/emc/ecs/nfsclient/nfs/nfs3/Test_Nfs3.java)

### Custom server ports

Existing constructors continue to discover NFS and MOUNT ports through the server's
portmapper on TCP port 111. To connect to fixed or forwarded service ports, supply
the NFS and MOUNT TCP destination ports explicitly:

```java
Credential credential = new CredentialUnix(1000, 1000, null);
Nfs<Nfs3File> client = new Nfs3("nfs.example.com", "/export", credential, 3,
                                12049, 20048);
```

These types are in `com.emc.ecs.nfsclient.rpc` (`Credential`, `CredentialUnix`),
`com.emc.ecs.nfsclient.nfs` (`Nfs`), `com.emc.ecs.nfsclient.nfs.nfs3` (`Nfs3`),
and `com.emc.ecs.nfsclient.nfs.io` (`Nfs3File`).

Each port accepts `1` through `65535`; `0` independently enables portmapper discovery
for that service. For example, pass `12049, 0` to fix only the NFS port. Specifying
both ports avoids portmapper entirely. The server must expose NFSv3 and MOUNT v3
over TCP. Pass the hostname or IP address separately from the ports; existing
`server:/export` constructor syntax is unchanged.

If you already have a root file handle, use the full constructor to skip MOUNT:

```java
Nfs3 client = new Nfs3("nfs.example.com", "/export", rootFileHandle,
                       credential, 3, 12049, 0);
```

With an explicit NFS port and a supplied handle, construction requires no RPC
calls (hostname resolution still applies). Explicit ports remain in effect if the client refreshes the root handle.
`Nfs.getPort()` returns the active NFS destination port. `Nfs.getMountPort()` returns
the configured or last discovered MOUNT port, or `0` if unknown; neither getter
performs discovery. The new MOUNT getter defaults to `0` for existing third-party
`Nfs` implementations, which need no changes. These are server destination ports;
privileged client source-port handling is unchanged.

How to use the NfsFile instances
---

The NfsFile instances provide file-based access to NFS functionality, including caching of repeatedly used
data to minimize the number of remote calls. Working examples for how to instantiate Nfs3File instances and
how to exercise most NfsFile functionality are available in the JUnit tests.
[Test_Nfs3File.java](src/test/java/com/emc/ecs/nfsclient/nfs/io/Test_Nfs3File.java).

### Directory listing order

`list()` and `listFiles()`, including their filtered overloads, preserve the
server's directory entry order. The client appends entries from successive
`READDIR` responses, omits `.` and `..`, and does not sort or reorder the remaining
entries. Filtering preserves the relative order of accepted entries.

NFSv3 `READDIR` and `READDIRPLUS` have no sorting parameter and do not guarantee
lexicographical order. The same filenames and `/etc/exports` settings on two
servers do not guarantee the same listing order: server implementations and
underlying directory organization can differ. Alphabetical results from one
environment should not be treated as a guarantee. See
[RFC 1813, section 3.3.16](https://www.rfc-editor.org/rfc/rfc1813.html#section-3.3.16)
and [upstream issue #38](https://github.com/EMCECS/nfs-client-java/issues/38).

Each listing starts a new traversal. Continuation cookies and their verifiers
allow pagination; they do not provide a snapshot of a directory being modified.
If repeated listings of an unchanged directory differ, compare the filenames in
the server's `READDIR` replies with the returned list to locate the variation.

For predictable alphabetical output, sort the complete returned list, rather
than individual response pages:

```java
List<Nfs3File> files = directory.listFiles();
files.sort(Comparator.comparing(Nfs3File::getName));
```

Here `directory` is an `Nfs3File`; `List` and `Comparator` are from `java.util`.
This uses Java's case-sensitive string ordering.

How to use the NfsFileInputStream and NfsFileOutputStream instances
---

The NfsFileInputStream and NfsFileOutputStream instances provide easy access to NFS IO functionality, and 
include optimizations of buffer sizes.
Working examples for how to instantiate NfsFileInputStream and NfsFileOutputStream instances and
how to exercise most NFS IO functionality are available in the JUnit tests.
[Test_Streams.java](src/test/java/com/emc/ecs/nfsclient/nfs/io/Test_Streams.java).

How to use the tools jar
---

The tools jar currently contains only functionality for load testing NFS client IO functionality, as this is the
most likely bottleneck. Working examples of how to exercise this code are available in the tools jar JUnit tests.
[Test_LoadTest.java](tools/src/test/java/com/emc/ecs/nfsclient/nfs/io/Test_LoadTest.java).
