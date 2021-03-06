/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2013 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

/**
 * Simple tunneling server
 *
 * @author Alexey Stashok
 */
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
