[![Dependency Security](https://github.com/denysobukh/nfs-client-java/actions/workflows/security.yml/badge.svg?branch=master)](https://github.com/denysobukh/nfs-client-java/actions/workflows/security.yml)

NFS Client for Java
===

Maintained fork of the EMC NFS client with security fixes.

Version **1.1.1** uses **Netty 4.2.16.Final** and requires **Java 17 or newer**
at runtime. Builds use **JDK 25**.

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

Migrated from the end-of-life `io.netty:netty:3.10.6.Final` dependency to
`io.netty:netty-transport` and `io.netty:netty-codec-base`, both at **4.2.16.Final**.
The migration updates connection handling, event loops, buffers, and RPC framing.

This removes the old Netty dependency associated with these Trivy findings:

| Vulnerability | Severity in the original scan | Finding |
| --- | --- | --- |
| CVE-2019-20444 | CRITICAL | HTTP request smuggling |
| CVE-2019-16869 | HIGH | HTTP request smuggling |
| CVE-2021-37136 | HIGH | Unrestricted Bzip2 decompression |
| CVE-2021-37137 | HIGH | Unrestricted Snappy frame buffering |

The post-migration Trivy scan of the resolved CycloneDX SBOM reported
**0 HIGH or CRITICAL findings**, compared with **3 HIGH and 1 CRITICAL** before
the migration. These are dependency scan results, not a determination that the
NFS client exercised the affected HTTP or compression code. Results reflect the
vulnerability database used for that scan and may change over time.

The transport tests cover fragmented and coalesced RPC records, TCP
request/response handling, disconnects, and connection failures. NFS server
integration tests require an external server.

Dependency vulnerability alerts
---

The [Dependency Security workflow](https://github.com/denysobukh/nfs-client-java/actions/workflows/security.yml)
generates an aggregate CycloneDX SBOM from resolved runtime and test dependencies
for the library, tool modules, and demo, then scans it with Trivy. It runs on
pushes to `master`, pull requests, manually, and every Monday at 07:43 UTC using
the current vulnerability database. Any **HIGH or CRITICAL** finding fails the
workflow, including vulnerabilities without a fix. The SBOM is available as a
workflow artifact; scan findings appear in the Actions log.

To generate and scan the same inventory locally (requires Trivy):

```sh
./gradlew --no-daemon cyclonedxBom
trivy sbom --severity HIGH,CRITICAL --exit-code 1 build/reports/cyclonedx/bom.json
```

[Dependabot alerts](https://github.com/denysobukh/nfs-client-java/security/dependabot)
and Dependabot security updates are enabled for this repository. The Dependency
submission workflow submits resolved Gradle dependencies, including transitive
dependencies, to GitHub's dependency graph on pushes to `master` and weekly.
It can also be run manually from Actions. GitHub checks that inventory against
its advisory database and raises alerts for affected dependencies. Dependabot
can open security update pull requests when it can determine a supported fix;
updates are not merged automatically.

Installation
---

Maven coordinates for this fork (once published to Maven Central):

    <dependency>
        <groupId>io.github.denysobukh</groupId>
        <artifactId>nfs-client</artifactId>
        <version>1.1.1</version>
    </dependency>

You can also use it as a Gradle dependency:

    implementation 'io.github.denysobukh:nfs-client:1.1.1'

**Version 1.1.1 requires Java 17 or newer. Java 21 is a supported runtime choice.**

Using Java 21
---

Applications using this library can run on Java 21. Configure your application's
Java toolchain or `JAVA_HOME` to use JDK 21; the library's Java 17 bytecode is
compatible with that runtime.

On macOS with Homebrew, select Java 21 for your application with:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
```

Building this library itself still uses the JDK 25 toolchain and targets Java 17,
as described below.

Docs
---

Javadocs are [here](https://emcecs.github.io/nfs-client-java/latest/javadoc/).

Building
---

Install JDK 25 and make it available to Gradle. All three modules (`nfs-client`,
`tools`, and `readingTools`) use the Java 25 toolchain with `--release 17`, which
limits compilation to Java 17 APIs and generates Java 17 bytecode. Consumers
can run the library on Java 17 or newer without installing JDK 25.

The Gradle 9.3.1 wrapper is included. Build and run the tests with:

```sh
./gradlew clean build
```

Create the distribution ZIP with:

```sh
./gradlew distZip
```

On macOS with Homebrew, you can select JDK 25 explicitly:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./gradlew clean build
```

The JDK 25 build with Java 17 output passed, including packaging and the tests
that do not require an NFS server.

Publishing to Maven Central
---

Publishing uses the Central Portal through the Vanniktech Maven Publish plugin.
Configure a Central Portal token in `~/.gradle/gradle.properties`:

```properties
mavenCentralUsername=YOUR_TOKEN_USERNAME
mavenCentralPassword=YOUR_TOKEN_PASSWORD
signing.gnupg.keyName=YOUR_SIGNING_KEY_ID
```

Artifacts are signed with the local GPG command and its agent. Keep tokens and
signing credentials outside this repository. The `io.github.denysobukh` namespace
must be verified in the Central Portal.

Generate the POM without signing or uploading:

```sh
./gradlew generatePomFileForMavenPublication
```

Upload a signed deployment for review in the Central Portal:

```sh
./gradlew publishToMavenCentral
```

GitHub Releases and Deployments
---

CI runs `clean build` on pushes to `master` and pull requests, using JDK 25
and targeting Java 17. Tests requiring an NFS server remain skipped unless
server configuration is supplied.

To prepare a GitHub Release, update the Gradle version and Maven coordinates,
commit the changes, and push a matching tag:

```sh
git tag -a v1.1.1 -m "NFS Client 1.1.1"
git push origin v1.1.1
```

The Release workflow builds and tests the tagged revision, checks that the tag
matches the generated POM version, and creates a **draft** release. It attaches
the library, sources, Javadoc, POM, distribution ZIP/TAR, and SHA-256 checksums.
Review the draft under [Releases](https://github.com/denysobukh/nfs-client-java/releases)
and publish it when ready. Re-running the workflow can replace draft assets;
it will refuse to modify an already published release.

Maven Central uploads run on your Mac with the local GPG/YubiKey configuration.
From a clean checkout of the pushed release tag, validate first:

```sh
git checkout v1.1.1
python3 scripts/deploy-maven-central.py v1.1.1 --check
```

Then upload the signed artifacts and record the result in GitHub's
`maven-central` deployment environment:

```sh
python3 scripts/deploy-maven-central.py v1.1.1
```

The helper requires Python 3, an authenticated GitHub CLI, JDK 25, Central Portal
token credentials in your local Gradle configuration, and access to your GPG
signing key. It builds before creating the deployment and records upload
success or failure. A successful GitHub deployment means the artifacts were
uploaded for review; finish publishing in the
[Central Portal](https://central.sonatype.com/publishing/deployments), then publish
the GitHub release draft. No Maven Central token or private key is stored in
GitHub by these workflows. Before retrying a failed upload, check the Portal
for a deployment that may already have been received.

Testing
---
  
To run the unit tests, you will need to set up an NFS export
that can be used for testing, and add the parameters for this export
to `~/test.properties` (see the [template](https://raw.githubusercontent.com/EMCECS/nfs-client-java/master/src/test/resources/test.properties.template)).
The test client will need read/write access to this share. If you do not
do this, you can still run the tests, but most of them will be skipped.

How to use the bare client
---

The Nfs client instances provide both direct and lightly wrapped access to all NFS functionality.
Working examples for how to instantiate an Nfs3 client and how to exercise most 
NFS functionality are available in the JUnit tests.
[Test_Nfs3.java](https://raw.githubusercontent.com/EMCECS/nfs-client-java/master/src/test/java/com/emc/ecs/nfsclient/nfs/nfs3/Test_Nfs3.java)

How to use the NfsFile instances
---

The NfsFile instances provide file-based access to NFS functionality, including caching of repeatedly used
data to minimize the number of remote calls. Working examples for how to instantiate Nfs3File instances and
how to exercise most NfsFile functionality are available in the JUnit tests.
[Test_Nfs3File.java](https://raw.githubusercontent.com/EMCECS/nfs-client-java/master/src/test/java/com/emc/ecs/nfsclient/nfs/io/Test_Nfs3File.java).

How to use the NfsFileInputStream and NfsFileOutputStream instances
---

The NfsFileInputStream and NfsFileOutputStream instances provide easy access to NFS IO functionality, and 
include optimizations of buffer sizes.
Working examples for how to instantiate NfsFileInputStream and NfsFileOutputStream instances and
how to exercise most NFS IO functionality are available in the JUnit tests.
[Test_Streams.java](https://raw.githubusercontent.com/EMCECS/nfs-client-java/master/src/test/java/com/emc/ecs/nfsclient/nfs/io/Test_Streams.java).

How to use the tools jar
---

The tools jar currently contains only functionality for load testing NFS client IO functionality, as this is the
most likely  bottleneck. Working examples of how to exercise this code are available in the tools jar JUnit tests.
[Test_LoadTest.java](https://raw.githubusercontent.com/EMCECS/nfs-client-java/master/tools/src/test/java/com/emc/ecs/nfsclient/nfs/io/Test_LoadTest.java).
