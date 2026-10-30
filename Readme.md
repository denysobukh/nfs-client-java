[![Dependency Security](https://github.com/denysobukh/nfs-client-java/actions/workflows/security.yml/badge.svg?branch=master)](https://github.com/denysobukh/nfs-client-java/actions/workflows/security.yml)

NFS Client for Java
===

Maintained fork of the EMC NFS client with security fixes.

Version **1.1.1** uses **Netty 4.2.16.Final** and requires **Java 17 or newer**
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
        <version>1.1.1</version>
    </dependency>

You can also use it as a Gradle dependency:

    implementation 'io.github.denysobukh:nfs-client:1.1.1'

**Version 1.1.1 requires Java 17 or newer. Java 21 is a supported runtime choice.**

Docs
---

Browse the [Javadocs](https://denysobukh.github.io/nfs-client-java/latest/javadoc/).

How to use the bare client
---

For a local NFS server and a minimal client app, run
`bash examples/nfs-demo/run.sh`. The [Docker NFS demo](examples/nfs-demo/README.md)
checks basic file operations against NFS-Ganesha and prints a pass/fail report.

The Nfs client instances provide both direct and lightly wrapped access to all NFS functionality.
Working examples for how to instantiate an Nfs3 client and how to exercise most 
NFS functionality are available in the JUnit tests.
[Test_Nfs3.java](src/test/java/com/emc/ecs/nfsclient/nfs/nfs3/Test_Nfs3.java)

How to use the NfsFile instances
---

The NfsFile instances provide file-based access to NFS functionality, including caching of repeatedly used
data to minimize the number of remote calls. Working examples for how to instantiate Nfs3File instances and
how to exercise most NfsFile functionality are available in the JUnit tests.
[Test_Nfs3File.java](src/test/java/com/emc/ecs/nfsclient/nfs/io/Test_Nfs3File.java).

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
