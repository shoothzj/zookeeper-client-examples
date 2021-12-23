package com.github.shoothzj.zookeeper.client.examples;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

class ZkIdGeneratorTest {

    private static TestZkServer testZkServer;

    @BeforeAll
    static void initZkServer() throws Exception {
        testZkServer = new TestZkServer();
        testZkServer.start();
    }

    @Test
    public void testGenIdSuccess() {
        ZkIdGenerator zkIdGenerator = new ZkIdGenerator(testZkServer.getZkAddr());
        Optional<String> strOp = zkIdGenerator.genId();
        Assertions.assertTrue(strOp.isPresent());
    }

    @AfterAll
    static void closeZkServer() throws Exception {
        testZkServer.close();
    }

}