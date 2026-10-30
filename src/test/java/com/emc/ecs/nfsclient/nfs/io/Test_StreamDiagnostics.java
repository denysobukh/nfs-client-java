package com.emc.ecs.nfsclient.nfs.io;

import com.emc.ecs.nfsclient.LogCapture;
import com.emc.ecs.nfsclient.nfs.NfsFsInfo;
import com.emc.ecs.nfsclient.nfs.NfsFsInfoResponse;
import com.emc.ecs.nfsclient.rpc.Xdr;
import java.io.IOException;
import java.lang.reflect.Proxy;
import org.junit.Test;
import static org.junit.Assert.*;

public class Test_StreamDiagnostics {
    @Test
    public void suppressedCloseFailureWarnsAboutBufferedDataAndFile() throws Exception {
        NfsFsInfo info = new NfsFsInfo(new Xdr(64));
        info.wtpref = 8;
        NfsFsInfoResponse fsInfo = new NfsFsInfoResponse(3) {
            @Override
            public NfsFsInfo getFsInfo() {
                return info;
            }
        };
        IOException failure = new IOException("simulated write failure");
        NfsFile<?, ?> file = (NfsFile<?, ?>) Proxy.newProxyInstance(NfsFile.class.getClassLoader(),
                new Class<?>[] {NfsFile.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                    case "exists":
                    case "canExtend":
                    case "canModify":
                        return true;
                    case "getAbsolutePath":
                        return "server:/export/test.bin";
                    case "fsinfo":
                        return fsInfo;
                    case "write":
                        throw failure;
                    default:
                        throw new AssertionError("Unexpected file operation: " + method.getName());
                    }
                });
        NfsFileOutputStream stream = new NfsFileOutputStream(file);
        stream.write(new byte[] {1, 2, 3});
        try (LogCapture logs = new LogCapture()) {
            stream.close(); // Retains the existing close() exception-suppression behavior.
            assertTrue(logs.output().contains("WARN"));
            assertTrue(logs.output().contains("file=server:/export/test.bin"));
            assertTrue(logs.output().contains("bufferedBytes=3"));
            assertTrue(logs.output().contains("data may not have reached stable storage"));
            assertTrue(logs.output().contains("simulated write failure"));
        }
    }
}
