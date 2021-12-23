package com.github.shoothzj.zookeeper.client.examples;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.zookeeper.server.NIOServerCnxnFactory;
import org.apache.zookeeper.server.ServerCnxnFactory;
import org.apache.zookeeper.server.SessionTracker;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.assertj.core.util.Files;
import org.junit.jupiter.api.Assertions;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public class TestZkServer {

    public static final String ZK_HOST = "127.0.0.1";

    private static final int TICK_TIME = 1000;

    private final File zkDataDir;

    private ServerCnxnFactory serverFactory;

    private ZooKeeperServer zks;

    private int zkPort;

    public TestZkServer() throws Exception {
        this.zkDataDir = Files.newTemporaryFolder();
        this.zkDataDir.deleteOnExit();
        System.setProperty("zookeeper.4lw.commands.whitelist", "*");
        System.setProperty("zookeeper.admin.enableServer", "false");
        start();
    }

    public void start() throws Exception {
        this.zks = new ZooKeeperServer(zkDataDir, zkDataDir, TICK_TIME);
        this.serverFactory = new NIOServerCnxnFactory();
        this.serverFactory.configure(new InetSocketAddress(0), 1000);
        this.serverFactory.startup(zks, true);

        this.zkPort = serverFactory.getLocalPort();
        log.info("Started test ZK server on port {}", zkPort);

        boolean zkServerReady = waitZkStart();
        Assertions.assertTrue(zkServerReady);
    }

    public int getZkPort() {
        return zkPort;
    }

    public String getZkAddr() {
        return "localhost:" + zkPort;
    }

    private boolean waitZkStart() throws Exception {
        long start = System.currentTimeMillis();
        while (true) {
            List<String> zkStats = ZkUtil.getZkStats(ZK_HOST, getZkPort());
            if (zkStats.get(0).startsWith("Zookeeper version:")) {
                return true;
            }
            if (System.currentTimeMillis() > start + 30_000L) {
                break;
            }
            TimeUnit.MILLISECONDS.sleep(50);
        }
        return false;
    }

    public void close() throws Exception {
        if (serverFactory != null) {
            serverFactory.shutdown();
            serverFactory = null;
        }

        if (zks != null) {
            SessionTracker sessionTracker = zks.getSessionTracker();
            zks.shutdown();
            zks.getZKDatabase().close();
            if (sessionTracker instanceof Thread sessionTrackerThread) {
                sessionTrackerThread.interrupt();
                sessionTrackerThread.join();
            }
            zks = null;
        }
        FileUtils.deleteDirectory(zkDataDir);
    }

}
