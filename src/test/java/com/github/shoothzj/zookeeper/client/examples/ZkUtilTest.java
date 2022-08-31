package com.github.shoothzj.zookeeper.client.examples;

import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

@Slf4j
class ZkUtilTest {

    private static TestZkServer testZkServer;

    @BeforeAll
    static void initZkServer() throws Exception {
        testZkServer = new TestZkServer();
        testZkServer.start();
    }

    @Test
    public void testZkStats() throws Exception {
        TestZkServer testZkServer = new TestZkServer();
        testZkServer.start();
        Configurator.setRootLevel(Level.INFO);
        List<String> stats = ZkUtil.getZkStats("127.0.0.1", testZkServer.getZkPort());
        Assertions.assertNotNull(stats);
        Assertions.assertTrue(stats.size() > 0);
        testZkServer.close();
    }

    @AfterAll
    static void closeZkServer() throws Exception {
        testZkServer.close();
    }

}