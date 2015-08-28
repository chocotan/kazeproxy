package io.loli.kaze.server;

import io.netty.handler.codec.http.HttpRequest;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Queue;

import javax.net.ssl.SSLEngine;

import org.littleshoot.proxy.ChainedProxy;
import org.littleshoot.proxy.ChainedProxyAdapter;
import org.littleshoot.proxy.ChainedProxyManager;
import org.littleshoot.proxy.SslEngineSource;
import org.littleshoot.proxy.TransportProtocol;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;

public class KazeClient {
    private String jkspw;
    private String serverIp;
    private Integer serverPort;

    protected ChainedProxyManager chainedProxyManager() {
        return new ChainedProxyManager() {
            @Override
            public void lookupChainedProxies(HttpRequest httpRequest,
                    Queue<ChainedProxy> chainedProxies) {
                chainedProxies.add(newChainedProxy());
            }
        };
    }

    protected ChainedProxy newChainedProxy() {
        return new ChainedProxyAdapter() {
            @Override
            public TransportProtocol getTransportProtocol() {
                return TransportProtocol.TCP;
            }

            @Override
            public boolean requiresEncryption() {
                return true;
            }

            @Override
            public SSLEngine newSslEngine() {
                SslEngineSource sslEngineSource = new KazeSslEngineSource(
                        "kclient.jks", "tclient.jks", false, true, "serverkey",
                        jkspw);
                return sslEngineSource.newSslEngine();
            }

            @Override
            public InetSocketAddress getChainedProxyAddress() {
                try {
                    return new InetSocketAddress(
                            InetAddress.getByName(serverIp), serverPort);
                } catch (UnknownHostException uhe) {
                    throw new RuntimeException("Unable to resolve " + serverIp);
                }
            }

        };
    }

    public void start(int port, String serverIp, int serverPort,
            String jksPasswd) throws IOException {
        this.serverIp = serverIp;
        this.serverPort = serverPort;
        this.jkspw = jksPasswd;
        DefaultHttpProxyServer.bootstrap()
                .withAddress(new InetSocketAddress("localhost", port))
                .withChainProxyManager(chainedProxyManager())
                .withTransportProtocol(TransportProtocol.TCP).start();
        System.in.read();
    }
}
