package io.loli.kaze.server;

import io.loli.kaze.cache.CacheFilter;

import java.io.IOException;
import java.util.Properties;

public class ClientOrServer {
    public static void main(String[] args) throws IOException {
        Properties prop = new Properties();
        prop.load(ClientOrServer.class.getResourceAsStream("/kaze.properties"));

        try {
            String pw = prop.getProperty("keystore.pw");
            if (pw == null) {
                pw = "kaze-proxy";
            }

            Boolean cache = prop.getProperty("cache") == null ? false : Boolean
                    .valueOf(prop.getProperty("cache"));
            String mode = prop.getProperty("mode");
            String ip = prop.getProperty("ip");
            if (ip == null) {
                ip = "0.0.0.0";
            }

            String serverIp = prop.getProperty("server-ip");

            String cacheRegex = prop.getProperty("cache.regex");

            Integer serverPort = Integer.parseInt(prop
                    .getProperty("server-port"));
            Integer port = prop.getProperty("port") == null ? 12345 : Integer
                    .parseInt(prop.getProperty("port"));
            new KazeClient().mode(mode).port(port).serverIp(serverIp)
                    .serverPort(serverPort).cache(cache)
                    .filter(new CacheFilter(cacheRegex)).password(pw).start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
