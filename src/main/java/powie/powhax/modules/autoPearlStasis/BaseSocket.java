package powie.powhax.modules.autoPearlStasis;

import powie.powhax.modules.autoPearlStasis.AutoPearlStasis.NetworkMessage;

public abstract class BaseSocket {
    protected volatile LineSocket connection;
    protected volatile boolean running = true;
    protected final AutoPearlStasis m;

    protected BaseSocket(AutoPearlStasis module) {
        this.m = module;
    }

    protected void send(String message) {
        if (connection != null) connection.send(message);
    }

    protected void stop() {
        running = false;
        if (connection != null) connection.close();
        m.info(getClass().getSimpleName() + " stopped.");
    }

    protected void onMessage(String message) {
        NetworkMessage msg;
        try {
            msg = NetworkMessage.decode(message);
        } catch (Exception e) {
            m.error("Ignoring bad message: " + message);
            return;
        }
        handleMessage(msg);
    }

    protected abstract void handleMessage(NetworkMessage msg);
}
