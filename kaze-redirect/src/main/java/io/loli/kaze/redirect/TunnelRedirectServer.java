package io.loli.kaze.redirect;

import java.io.IOException;
import java.util.logging.Logger;

import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;

public class TunnelRedirectServer {
    private String targetHost;
    private Integer targetPort;

    private String localHost;
    private Integer localPort;

    private TCPNIOTransport transport = null;

    public TunnelRedirectServer targetHost(String targetHost) {
        this.targetHost = targetHost;
        return this;
    }

    public TunnelRedirectServer targetPort(Integer targetPort) {
        this.targetPort = targetPort;
        return this;
    }

    public TunnelRedirectServer localHost(String localHost) {
        this.localHost = localHost;
        return this;
    }

    public TunnelRedirectServer localPort(Integer localPort) {
        this.localPort = localPort;
        return this;
    }

    public void shutdown() throws IOException {
        logger.info("Stopping transport...");
        // stop the transport
        transport.shutdownNow();
        logger.info("Stopped transport...");
    }

    public void start() throws IOException {
        // Create TCP transport
        transport = TCPNIOTransportBuilder.newInstance().build();

        // Create a FilterChain using FilterChainBuilder
        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        // Add TransportFilter, which is responsible
        // for reading and writing data to the connection
        filterChainBuilder.add(new TransportFilter());
        // filterChainBuilder.add(new ClientEncodeFilter());
        filterChainBuilder.add(new TunnelRedirectServerFilter(
                TCPNIOConnectorHandler.builder(transport).build(), targetHost,
                targetPort));
        transport.setProcessor(filterChainBuilder.build());
        // transport.setReadBufferSize(512);
        // Set async write queue size limit
        // transport.getAsyncQueueIO().getWriter()
        // .setMaxPendingBytesPerConnection(10240 * 10240);
        // binding transport to start listen on certain host and port
        transport.bind(localHost, localPort);
        // start the transport
        transport.start();
        logger.info("Press any key to stop the server...");
    }

    private static final Logger logger = Logger
            .getLogger(TunnelRedirectServer.class.getName());

    public static void main(String[] args) throws IOException {
        new TunnelRedirectServer().targetHost(args[0])
                .targetPort(Integer.parseInt(args[1])).localHost(args[2])
                .localPort(Integer.parseInt(args[3])).start();
        System.in.read();
    }
}
