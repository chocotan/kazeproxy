package io.loli.kaze.server;

import java.io.IOException;
import java.net.InetSocketAddress;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.Namespace;

import org.littleshoot.proxy.SslEngineSource;
import org.littleshoot.proxy.TransportProtocol;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;

public class KazeServer {
    public static int port = 12345;

    public static void main(String[] args) throws IOException {
        ArgumentParser parser = ArgumentParsers.newArgumentParser("")
                .description("Kaze Proxy");

        parser.addArgument("-m", "--mode").metavar("mode").type(String.class)
                .help("Server or client model").dest("mode");

        parser.addArgument("-p", "--port").metavar("PORT").type(Integer.class)
                .help("Http port to listen on").dest("port");

        parser.addArgument("-ip", "--host").metavar("HOST").type(String.class)
                .help("Ip address to listen on").dest("host");

        parser.addArgument("-server-ip", "--remort-host").metavar("SERVER HOST")
                .type(String.class)
                .help("Server ip, only works at client model").dest("sh");

        parser.addArgument("-server-port", "--remote-port").metavar("SERVER HOST")
                .type(Integer.class)
                .help("Server port, only works at client model").dest("sp");

        parser.addArgument("-pw", "--jks-passwd").metavar("KEYSTORE PW")
                .type(String.class)
                .help("keystore password").dest("pw");

        try {
            Namespace res = parser.parseArgs(args);
            String pw = res.getString("pw");
            if (pw == null) {
                pw = "kaze-proxy";
            }

            if ("server".equals(res.getString("mode"))) {
                String ip = res.getString("host");
                if (ip == null) {
                    ip = "0.0.0.0";
                }

                Integer port = res.getInt("port");
                if (port == null) {
                    port = 12345;
                }
                new KazeServer().start(ip, port, pw);
            } else {
                Integer port = res.getInt("port");
                if (port == null) {
                    port = 12345;
                }
                String serverHost = res.getString("sh");
                Integer serverPort = res.getInt("sp");
                new KazeClient().start(port, serverHost, serverPort, pw);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void start(String ip, int port, String pw) throws IOException {
        SslEngineSource sslEngineSource = new KazeSslEngineSource(
                "kserver.jks", "tserver.jks", false, true, "serverkey", pw);
        DefaultHttpProxyServer.bootstrap()
                .withAddress(new InetSocketAddress(ip, port))
                .withTransportProtocol(TransportProtocol.TCP)
                .withSslEngineSource(sslEngineSource)
                .withAuthenticateSslClients(false).start();
        System.in.read();
    }
}
