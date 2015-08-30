package io.loli.kaze.server;

import io.loli.kaze.cache.CacheFilter;
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
import org.littleshoot.proxy.HttpProxyServerBootstrap;
import org.littleshoot.proxy.SslEngineSource;
import org.littleshoot.proxy.TransportProtocol;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;

public class KazeClient {
    private String jkspw;
    private String serverIp;
    private Integer serverPort;
    private Integer port;

    private CacheFilter filter;

    private Boolean cache;

    private String mode;

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

    public KazeClient port(int port) {
        this.port = port;
        return this;
    }

    public KazeClient serverIp(String serverIp) {
        this.serverIp = serverIp;
        return this;
    }

    public KazeClient serverPort(Integer serverPort) {
        this.serverPort = serverPort;
        return this;
    }

    public KazeClient password(String password) {
        this.jkspw = password;
        return this;
    }

    public KazeClient cache(Boolean cache) {
        this.cache = cache;
        return this;
    }

    public KazeClient filter(CacheFilter filter) {
        this.filter = filter;
        return this;
    }

    public KazeClient mode(String mode) {
        this.mode = mode;
        return this;
    }

    public void start() throws IOException {
        HttpProxyServerBootstrap boot = null;
        if ("client".equals(mode)) {
            boot = DefaultHttpProxyServer.bootstrap()
                    .withAddress(new InetSocketAddress("localhost", port))
                    .withChainProxyManager(chainedProxyManager())
                    .withTransportProtocol(TransportProtocol.TCP);
            if (cache) {
                boot = boot.withFiltersSource(filter);
            }

        } else if ("server".equals(mode)) {
            SslEngineSource sslEngineSource = new KazeSslEngineSource(
                    "kserver.jks", "tserver.jks", false, true, "serverkey",
                    jkspw);
            boot = DefaultHttpProxyServer.bootstrap()
                    .withAddress(new InetSocketAddress("0.0.0.0", port))
                    .withTransportProtocol(TransportProtocol.TCP)
                    .withSslEngineSource(sslEngineSource)
                    .withAuthenticateSslClients(false);
            if (cache) {
                boot = boot.withFiltersSource(filter);
            }
        }
        boot.start();
        System.in.read();
    }

    public KazeClient withFilter(CacheFilter cacheFilter) {
        this.filter = cacheFilter;
        return this;
    }
}
