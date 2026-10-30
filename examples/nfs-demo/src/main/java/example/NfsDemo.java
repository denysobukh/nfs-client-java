package example;

import com.emc.ecs.nfsclient.network.NetMgr;
import com.emc.ecs.nfsclient.nfs.io.Nfs3File;
import com.emc.ecs.nfsclient.nfs.io.NfsFileInputStream;
import com.emc.ecs.nfsclient.nfs.io.NfsFileOutputStream;
import com.emc.ecs.nfsclient.nfs.nfs3.Nfs3;
import com.emc.ecs.nfsclient.rpc.CredentialUnix;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

/** A real NFSv3 smoke test: every PASS includes a server response check. */
public final class NfsDemo {
    private static int passed;

    public static void main(String[] args) throws Exception {
        String server = args.length > 0 ? args[0] : "127.0.0.1";
        String export = args.length > 1 ? args[1] : "/export";
        long started = System.nanoTime();
        System.out.println("NFS CLIENT REPORT | " + Instant.now());
        System.out.println("Target: " + server + ":" + export + " | NFSv3/TCP | AUTH_SYS uid=0 gid=0");
        try {
            run(server, export);
            System.out.printf("RESULT: PASS | %d/%d checks passed | %.3f seconds%n",
                    passed, passed, (System.nanoTime() - started) / 1_000_000_000.0);
        } catch (Exception | AssertionError failure) {
            System.out.println("RESULT: FAIL | " + passed + " checks passed | " + failure);
            throw failure;
        } finally {
            NetMgr.getInstance().shutdown();
        }
    }

    private static void run(String server, String export) throws Exception {
        Nfs3 nfs = new Nfs3(server, export, new CredentialUnix(0, 0, null), 1);
        Nfs3File root = new Nfs3File(nfs, "/");
        check(root.exists() && root.isDirectory(), "MOUNT / LOOKUP", "export root is a directory");
        nfs.nullCall();
        check(true, "NULL", "NFS service responded on port " + nfs.getPort());

        // A unique directory keeps concurrent runs and existing export data separate.
        String directory = "/demo-" + UUID.randomUUID();
        Nfs3File dir = new Nfs3File(nfs, directory);
        dir.mkdir();
        check(dir.exists() && dir.isDirectory(), "MKDIR", directory);

        String path = directory + "/hello.txt";
        Nfs3File file = new Nfs3File(nfs, path);
        check(file.createNewFile() && file.exists(), "CREATE", "hello.txt");
        byte[] payload = "Hello from nfs-client-java!\nNFSv3 create, write, read, rename, delete.\n"
                .getBytes(StandardCharsets.UTF_8);
        try (NfsFileOutputStream output = new NfsFileOutputStream(file)) {
            output.write(payload);
        }
        check(new Nfs3File(nfs, path).lengthEx() == payload.length,
                "WRITE / COMMIT", payload.length + " bytes persisted");
        try (NfsFileInputStream input = new NfsFileInputStream(new Nfs3File(nfs, path))) {
            check(Arrays.equals(payload, input.readAllBytes()), "READ", "content matches byte for byte");
        }
        Nfs3File fresh = new Nfs3File(nfs, path);
        check(fresh.isFile() && fresh.lengthEx() == payload.length,
                "GETATTR", "regular file, size=" + fresh.lengthEx());
        check(dir.list().contains("hello.txt"), "READDIR", dir.list().toString());

        String renamedPath = directory + "/renamed.txt";
        check(file.renameTo(new Nfs3File(nfs, renamedPath))
                        && !new Nfs3File(nfs, path).exists()
                        && new Nfs3File(nfs, renamedPath).exists(),
                "RENAME", "hello.txt -> renamed.txt; old path absent");
        try (NfsFileInputStream input = new NfsFileInputStream(new Nfs3File(nfs, renamedPath))) {
            check(Arrays.equals(payload, input.readAllBytes()), "READ after RENAME", "content preserved");
        }
        new Nfs3File(nfs, renamedPath).delete();
        check(!new Nfs3File(nfs, renamedPath).exists() && dir.list().isEmpty(),
                "REMOVE", "file absent and directory empty");
        dir.delete();
        check(!new Nfs3File(nfs, directory).exists() && !root.list().contains(directory.substring(1)),
                "RMDIR", "run directory removed; no sample data remains");
    }

    private static void check(boolean condition, String operation, String detail) {
        if (!condition) {
            throw new AssertionError(operation + ": " + detail);
        }
        passed++;
        System.out.printf("PASS | %-18s | %s%n", operation, detail);
    }
}
