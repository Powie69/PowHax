package powie.powhax.modules.autoPearlStasis;

import powie.powhax.events.AutoPearlStasisUpdateInfoTableEvent;
import powie.powhax.modules.autoPearlStasis.AutoPearlStasis.NetworkMessage;

import static meteordevelopment.meteorclient.MeteorClient.EVENT_BUS;

public abstract class BaseSocket {
    protected final AutoPearlStasis m;
    protected volatile LineSocket connection;
    protected volatile boolean running = true;

    protected BaseSocket(AutoPearlStasis module) {
        this.m = module;
    }

    protected void send(String message) {
        if (connection != null) connection.send(message);
    }

    protected void stop() {
        running = false;
        if (connection != null) connection.close();
//        m.info(getClass().getSimpleName() + " stopped.");
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

    protected void onDisconnect() {
        EVENT_BUS.post(new AutoPearlStasisUpdateInfoTableEvent("", "", false));
        m.info("Connection lost.");
    }

    protected abstract void handleMessage(NetworkMessage msg);
}
