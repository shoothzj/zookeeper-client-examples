package com.github.shoothzj.zookeeper.client.examples;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ZkUtil {

    public static List<String> getZkStats(String host, int port) throws Exception {
        Socket sock = new Socket(host, port);
        BufferedReader reader = null;
        try {
            OutputStream outStream = sock.getOutputStream();
            outStream.write("stat".getBytes(StandardCharsets.UTF_8));
            outStream.flush();

            reader = new BufferedReader(new InputStreamReader(sock.getInputStream()));
            List<String> res = new ArrayList<>();
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                res.add(line);
            }
            return res;
        } finally {
            sock.close();
            if (reader != null) {
                reader.close();
            }
        }
    }

}
