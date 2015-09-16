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

package io.loli.kaze.redirect;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.SocketConnectorHandler;
import org.glassfish.grizzly.WriteHandler;
import org.glassfish.grizzly.asyncqueue.AsyncQueueWriter;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.jasypt.encryption.pbe.StandardPBEByteEncryptor;

/**
 * Simple tunneling filter, which maps input of one connection to the output of
 * another and vise versa.
 *
 * @author Alexey Stashok
 */
public class TunnelRedirectServerFilter extends BaseFilter {
    private static final Logger logger = Grizzly
            .logger(TunnelRedirectServerFilter.class);

    private Attribute<Connection> peerConnectionAttribute = Grizzly.DEFAULT_ATTRIBUTE_BUILDER
            .createAttribute("TunnelFilter.peerConnection");

    // Transport, which will be used to create peer connection
    private final SocketConnectorHandler transport;

    // Destination address for peer connections
    private final SocketAddress redirectAddress;

    public TunnelRedirectServerFilter(SocketConnectorHandler transport,
            String host, int port) {
        this(transport, new InetSocketAddress(host, port));
    }

    public TunnelRedirectServerFilter(SocketConnectorHandler transport,
            SocketAddress redirectAddress) {
        this.transport = transport;
        this.redirectAddress = redirectAddress;
    }

    private static StandardPBEByteEncryptor binaryEncryptor = new StandardPBEByteEncryptor();
    static {
        binaryEncryptor.setPassword("proxy");
    }

    /**
     * This method will be called, once {@link Connection} has some available
     * data
     */
    @Override
    public NextAction handleRead(final FilterChainContext ctx)
            throws IOException {
        logger.log(Level.FINEST, "Connection: {0} handleRead: {1}",
                new Object[] { ctx.getConnection(), ctx.getMessage() });

        final Connection connection = ctx.getConnection();
        final Connection peerConnection = peerConnectionAttribute
                .get(connection);

        // if connection is closed - stop the execution
        if (!connection.isOpen()) {
            return ctx.getStopAction();
        }

        final NextAction suspendNextAction = ctx.getSuspendAction();

        // if peerConnection wasn't created - create it (usually happens on
        // first connection request)
        if (peerConnection == null) {
            // "Peer connect" phase could take some time - so execute it in
            // non-blocking mode

            // Connect peer connection and register completion handler
            transport.connect(redirectAddress,
                    new ConnectCompletionHandler(ctx));

            // return suspend status
            return suspendNextAction;
        }

        final Object message = ctx.getMessage();
        // if peer connection is already created - just forward data to peer
        redirectToPeer(ctx, peerConnection, message);
        // if peer connection is already created - just forward data to peer

        final AsyncQueueWriter writer = (AsyncQueueWriter) connection
                .getTransport().getWriter(false);

        if (writer.canWrite(peerConnection)) {
            return ctx.getStopAction();
        }

        // Make sure we don't overload peer's output buffer and do not cause
        // OutOfMemoryError
        ctx.suspend();
        writer.notifyWritePossible(peerConnection, new WriteHandler() {

            @Override
            public void onWritePossible() throws Exception {
                finish();
            }

            @Override
            public void onError(Throwable t) {
                finish();
            }

            private void finish() {
                ctx.resumeNext();
            }
        });

        // return ctx.getStopAction();
        return suspendNextAction;
    }

    public static byte[] longToBytes(long l) {
        byte[] result = new byte[8];
        for (int i = 7; i >= 0; i--) {
            result[i] = (byte) (l & 0xFF);
            l >>= 8;
        }
        return result;
    }

    public static long bytesToLong(byte[] b) {
        long result = 0;
        for (int i = 0; i < 8; i++) {
            result <<= 8;
            result |= (b[i] & 0xFF);
        }
        return result;
    }

    @Override
    public NextAction handleWrite(FilterChainContext ctx) throws IOException {
        // final Object message = ctx.getMessage();
        //
        // HeapBuffer buffer = (HeapBuffer) message;
        // byte[] bytes = new byte[buffer.remaining()];
        // buffer.get(bytes);
        // byte[] encoded = binaryEncryptor.encrypt(bytes);
        // Buffer buf = new HeapMemoryManager().wrap(encoded);
        // ctx.setMessage(buf);
        System.out.println(System.nanoTime() + ":write:"
                + ((Buffer) ctx.getMessage()).remaining());
        return super.handleWrite(ctx);
    }

    /**
     * This method will be called, to notify about {@link Connection} closing.
     */
    @Override
    public NextAction handleClose(FilterChainContext ctx) throws IOException {
        final Connection connection = ctx.getConnection();
        final Connection peerConnection = peerConnectionAttribute
                .get(connection);

        // Close peer connection as well, if it wasn't closed before
        if (peerConnection != null && peerConnection.isOpen()) {
            peerConnection.closeSilently();
        }

        return ctx.getInvokeAction();
    }

    /**
     * Redirect data from {@link Connection} to its peer.
     *
     * @param context
     *            {@link FilterChainContext}
     * @param peerConnection
     *            peer {@link Connection}
     * @throws IOException
     */
    @SuppressWarnings("unchecked")
    private static void redirectToPeer(final FilterChainContext context,
            final Connection peerConnection, Object message) throws IOException {

        final Connection srcConnection = context.getConnection();
        logger.log(
                Level.FINE,
                "Redirecting from {0} to {1} message: {2}",
                new Object[] { srcConnection.getPeerAddress(),
                        peerConnection.getPeerAddress(), message });

        peerConnection.write(message);
    }

    /**
     * Peer connect {@link CompletionHandler}
     */
    private class ConnectCompletionHandler implements
            CompletionHandler<Connection> {
        private final FilterChainContext context;

        private ConnectCompletionHandler(FilterChainContext context) {
            this.context = context;
        }

        @Override
        public void cancelled() {
            context.getConnection().closeSilently();
            resumeContext();
        }

        @Override
        public void failed(Throwable throwable) {
            context.getConnection().closeSilently();
            resumeContext();
        }

        /**
         * If peer was successfully connected - map both connections to each
         * other.
         */
        @Override
        public void completed(Connection peerConnection) {
            final Connection connection = context.getConnection();

            // Map connections
            peerConnectionAttribute.set(connection, peerConnection);
            peerConnectionAttribute.set(peerConnection, connection);

            // Resume filter chain execution
            resumeContext();
        }

        @Override
        public void updated(Connection peerConnection) {
        }

        /**
         * Resume {@link org.glassfish.grizzly.filterchain.FilterChain}
         * execution on stage, where it was earlier suspended.
         */
        private void resumeContext() {
            context.resume();
        }
    }

    private static String hexStr = "0123456789ABCDEF";

    /**
     * 
     * @param bytes
     * @return 将二进制转换为十六进制字符输出
     */
    public static String BinaryToHexString(byte[] bytes) {

        String result = "";
        String hex = "";
        for (int i = 0; i < bytes.length; i++) {
            // 字节高4位
            hex = String.valueOf(hexStr.charAt((bytes[i] & 0xF0) >> 4));
            // 字节低4位
            hex += String.valueOf(hexStr.charAt(bytes[i] & 0x0F));
            result += hex;
        }
        return result;
    }

    /**
     * 
     * @param hexString
     * @return 将十六进制转换为字节数组
     */
    public static byte[] HexStringToBinary(String hexString) {
        // hexString的长度对2取整，作为bytes的长度
        int len = hexString.length() / 2;
        byte[] bytes = new byte[len];
        byte high = 0;// 字节高四位
        byte low = 0;// 字节低四位

        for (int i = 0; i < len; i++) {
            // 右移四位得到高位
            high = (byte) ((hexStr.indexOf(hexString.charAt(2 * i))) << 4);
            low = (byte) hexStr.indexOf(hexString.charAt(2 * i + 1));
            bytes[i] = (byte) (high | low);// 高地位做或运算
        }
        return bytes;
    }
}