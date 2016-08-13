package la.manga.app.net;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import fi.iki.elonen.NanoHTTPD;

/**
 * An actual local HTTP server, for testing.
 */
public class TestHttpServer extends NanoHTTPD {
    private final static int PORT = 8089;
    public final static String TEST_FILE = "http://localhost:" + PORT + "/TEST_FILE";
    public final static int TEST_FILE_SIZE = 0x100000;
    private final byte[] buffer = getBuffer();
    private boolean shouldFail = false;

    public TestHttpServer() {
        super(PORT);

        // disable logging, since sometimes we'll do test where
        // the client behaves against the rules.
        // only enable this when you need it, and then set it back,
        // in order to avoid polluting the test log.
        Logger.getLogger(NanoHTTPD.class.getName()).setLevel(Level.OFF);
    }

    public void setFailAlways(boolean b) {
        shouldFail = b;
    }

    @Override
    public Response serve(IHTTPSession session) {
        if (shouldFail)
            return super.serve(session);

        int offset = 0;
        int count = buffer.length;

        String range = session.getHeaders().get("range");

        if (range != null) {
            String bytes = range.split("=")[1];
            String[] parts = bytes.split("-");

            offset = Integer.parseInt(parts[0]);

            if (parts.length == 2)
                count = Integer.parseInt(parts[1]) - offset + 1;
        }

        ByteArrayInputStream is = new ByteArrayInputStream(buffer, offset, count);

        return newChunkedResponse(NanoHTTPD.Response.Status.OK, "application/zip", is);
    }

    private byte[] getBuffer() {
        byte[] buffer = new byte[TEST_FILE_SIZE];

        for (int i = 0; i < buffer.length; i++)
            buffer[i] = (byte) i;

        return buffer;
    }
}
