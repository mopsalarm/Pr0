package com.pr0gramm.app.util

import android.net.TrafficStats
import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory

/**
 * Simple socket factory that sets the socket send buffer
 * to a smaller value to better track file uploads.
 */
class SmallBufferSocketFactory : SocketFactory() {
    override fun createSocket(): Socket {
        return process(Socket())
    }

    override fun createSocket(host: String, port: Int): Socket {
        return process(Socket(host, port))
    }

    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket {
        return process(Socket(host, port, localHost, localPort))
    }

    override fun createSocket(host: InetAddress, port: Int): Socket {
        return process(Socket(host, port))
    }

    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress,
                              localPort: Int): Socket {
        return process(Socket(address, port, localAddress, localPort))
    }

    private fun process(socket: Socket): Socket {
        socket.sendBufferSize = 1024 * 48
        TrafficStats.tagSocket(socket);
        return socket
    }
}
