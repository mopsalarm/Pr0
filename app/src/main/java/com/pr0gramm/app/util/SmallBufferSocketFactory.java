package com.pr0gramm.app.util;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;

import javax.net.SocketFactory;

/**
 * Simple socket factory that sets the socket send buffer
 * to a smaller value to better track file uploads.
 */
public class SmallBufferSocketFactory extends SocketFactory {
    private static final int SEND_BUFFER_SIZE = 1024 * 16;

    @Override
    public Socket createSocket() throws IOException {
        return process(new Socket());
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return process(new Socket(host, port));
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
            throws IOException {
        return process(new Socket(host, port, localHost, localPort));
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return process(new Socket(host, port));
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress,
                               int localPort) throws IOException {
        return process(new Socket(address, port, localAddress, localPort));
    }

    private Socket process(Socket socket) throws SocketException {
        socket.setSendBufferSize(SEND_BUFFER_SIZE);
        // socket.setTcpNoDelay(true);
        return socket;
    }
}
