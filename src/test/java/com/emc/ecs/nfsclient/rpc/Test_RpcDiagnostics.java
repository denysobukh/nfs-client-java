package com.emc.ecs.nfsclient.rpc;

import com.emc.ecs.nfsclient.LogCapture;
import com.emc.ecs.nfsclient.mount.MountStatus;
import com.emc.ecs.nfsclient.nfs.NfsException;
import com.emc.ecs.nfsclient.nfs.NfsResponseHandler;
import com.emc.ecs.nfsclient.nfs.NfsStatus;
import com.emc.ecs.nfsclient.nfs.nfs3.Nfs3GetAttrRequest;
import com.emc.ecs.nfsclient.nfs.nfs3.Nfs3GetAttrResponse;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import org.junit.Test;
import static org.junit.Assert.*;

public class Test_RpcDiagnostics {
    @Test
    public void knownStatusesIncludeSymbolicNameAndCode() throws Exception {
        for (Class<?> type : new Class<?>[] {MountStatus.class, NfsStatus.class, RpcStatus.class,
                AcceptStatus.class, RejectStatus.class, ReplyStatus.class}) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) && field.getType() == type) {
                    field.setAccessible(true);
                    Object status = field.get(null);
                    int code = (Integer) type.getMethod("getValue").invoke(status);
                    assertEquals(field.getName() + "(" + code + ")", status.toString());
                }
            }
        }
        assertTrue(NfsStatus.fromValue(54321).toString().contains("54321"));
        assertTrue(AcceptStatus.fromValue(54321).toString().contains("54321"));
        assertTrue(RejectStatus.fromValue(54321).toString().contains("54321"));
    }

    @Test
    public void deniedRpcReplyIdentifiesTransactionAndStatus() {
        Xdr reply = new Xdr(ByteBuffer.allocate(16).putInt(123).putInt(1).putInt(1).putInt(1).array());
        RpcException error = assertThrows(RpcException.class, () -> new RpcResponse().unmarshalling(reply));
        assertSame(RejectStatus.AUTH_ERROR, error.getStatus());
        assertContains(error.getMessage(), "xid=123", "AUTH_ERROR(1)");
    }

    @Test
    public void retriesIdentifyOperationAndKeepTerminalCause() throws Exception {
        IOException cause = new IOException("connection reset by peer");
        RpcException failure = new RpcException(RpcStatus.NETWORK_ERROR, "test network failure", cause);
        ScriptedWrapper wrapper = new ScriptedWrapper(2, failure, 0);
        try (LogCapture logs = new LogCapture()) {
            NfsException error = assertThrows(NfsException.class,
                    () -> wrapper.callRpcWrapped(request(), handler(), "192.0.2.123"));
            assertEquals(2, wrapper.calls);
            assertSame(failure, error.getCause().getCause());
            assertSame(cause, error.getCause().getCause().getCause());
            assertContains(error.getMessage(), "server=localhost", "port=2049", "attempt=2/2",
                    "NfsGetAttrRequest", "xid:", "remote=192.0.2.123:2049", "NETWORK_ERROR");
            assertContains(logs.output(), "retrying after 0 ms", "attempt=1/2", "NfsGetAttrRequest",
                    "connection reset by peer");
        }
    }

    @Test
    public void nfsStatusFailuresHaveEndpointAndPreserveStatusWithoutRetry() throws Exception {
        ScriptedWrapper wrapper = new ScriptedWrapper(3, null, 13);
        NfsException error = assertThrows(NfsException.class,
                () -> wrapper.callRpcWrapped(request(), handler(), "192.0.2.123"));
        assertEquals(1, wrapper.calls);
        assertSame(NfsStatus.NFS3ERR_ACCES, error.getStatus());
        assertTrue(error.getCause() instanceof NfsException);
        assertContains(error.getMessage(), "server=localhost", "remote=192.0.2.123:2049", "NFS3ERR_ACCES(13)");
    }

    @Test
    public void missingFileRemainsFileNotFoundExceptionWithEndpoint() throws Exception {
        ScriptedWrapper wrapper = new ScriptedWrapper(3, null, 2);
        FileNotFoundException error = assertThrows(FileNotFoundException.class,
                () -> wrapper.callRpcWrapped(request(), handler(), "192.0.2.123"));
        assertEquals(1, wrapper.calls);
        assertTrue(error.getCause() instanceof FileNotFoundException);
        assertContains(error.getMessage(), "remote=192.0.2.123:2049", "NFS3ERR_NOENT(2)");
    }

    private static Nfs3GetAttrRequest request() throws FileNotFoundException {
        return new Nfs3GetAttrRequest(new byte[] {1, 2, 3, 4}, new CredentialNone());
    }

    private static NfsResponseHandler<Nfs3GetAttrResponse> handler() {
        return new NfsResponseHandler<Nfs3GetAttrResponse>() {
            @Override
            protected Nfs3GetAttrResponse makeNewResponse() {
                return new Nfs3GetAttrResponse();
            }
        };
    }

    private static void assertContains(String actual, String... expected) {
        for (String part : expected) {
            assertTrue("Missing '" + part + "' in: " + actual, actual.contains(part));
        }
    }

    private static final class ScriptedWrapper extends RpcWrapper<Nfs3GetAttrRequest, Nfs3GetAttrResponse> {
        private final RpcException failure;
        private final int nfsStatus;
        private int calls;

        ScriptedWrapper(int attempts, RpcException failure, int nfsStatus) {
            super("localhost", 2049, 0, attempts, 1024, 10);
            this.failure = failure;
            this.nfsStatus = nfsStatus;
        }

        @Override
        public Xdr callRpc(String ip, Xdr request, boolean privileged) throws RpcException {
            ++calls;
            if (failure != null) {
                throw failure;
            }
            return new Xdr(ByteBuffer.allocate(28).putInt(request.getXid()).putInt(1)
                    .putInt(0).putInt(0).putInt(0).putInt(0).putInt(nfsStatus).array());
        }
    }
}
