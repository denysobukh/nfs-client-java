package com.emc.ecs.nfsclient;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/** Captures the test runtime's SLF4J Simple output; tests run sequentially. */
public final class LogCapture implements AutoCloseable {
    private final PrintStream previous = System.err;
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final PrintStream stream = new PrintStream(bytes, true, StandardCharsets.UTF_8);

    public LogCapture() {
        System.setErr(stream);
    }

    public String output() {
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        System.setErr(previous);
        stream.close();
    }
}
