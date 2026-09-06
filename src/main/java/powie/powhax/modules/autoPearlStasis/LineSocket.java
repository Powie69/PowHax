package powie.powhax.modules.autoPearlStasis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.function.Consumer;

public class LineSocket implements AutoCloseable {
    private final Socket socket;
    private final BufferedReader in;
    private final PrintWriter out;
    private volatile boolean closed;

    protected LineSocket(Socket socket) throws IOException {
        this.socket = socket;
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);
    }

    protected void listen(Consumer<String> onMessage, Runnable onDisconnect) {
        Thread receiver = new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    onMessage.accept(line);
                }
            } catch (IOException ignored) {
                // Socket closed / connection lost.
            } finally {
                close();
                if (onDisconnect != null) onDisconnect.run();
            }
        }, "pearl-stasis-reader");

        receiver.setDaemon(true);
        receiver.start();
    }

    protected void send(String message) {
        if (!closed) out.println(message);
    }

    protected boolean isOpen() {
        return !closed && !socket.isClosed();
    }

    protected String getRemoteAddress() {
        return socket.getRemoteSocketAddress().toString();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;

        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
