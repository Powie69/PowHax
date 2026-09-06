package powie.powhax.modules.autoPearlStasis;

import com.google.gson.JsonObject;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.player.Player;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import static powie.powhax.Powhax.GSON;

public class Main {
    private final AutoPearlStasis m;

    private int pops;
    protected final HostSocket socket;

    public Main(AutoPearlStasis module) {
        m = module;
        socket = new HostSocket(m.serverPort.get());
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (!(event.packet instanceof ClientboundEntityEventPacket p)) return;
        if (p.getEventId() != EntityEvent.PROTECTED_FROM_DEATH) return;

        Entity entity = p.getEntity(mc.level);
        if (entity == null || !entity.equals(mc.player)) return;

        pops++;
        if (m.totemPops.get() > 0 && pops >= m.totemPops.get()) requestPull("Popped " + pops + " totems.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        float playerHealth = mc.player.getHealth();

        if (playerHealth <= m.health.get()) {
            requestPull("Health was lower than " + m.health.get() + ".");
            return;
        }

        if (m.smart.get()
            && !mc.player.isInvulnerable()
            && !mc.player.getAbilities().invulnerable
            && playerHealth + mc.player.getAbsorptionAmount() - PlayerUtils.possibleHealthReductions() < m.health.get()) {
            requestPull("Health was going to be lower than " + m.health.get() + ".");
            return;
        }

        if (!m.onlyTrusted.get() && m.entities.get().isEmpty())
            return; // only check all entities if needed

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof Player player && player.getUUID() != mc.player.getUUID()) {
                if (m.onlyTrusted.get() && player != mc.player && !Friends.get().isFriend(player)) {
                    requestPull("Non-trusted player '" + player.getName().getString() + "' appeared in your render distance.");
                    return;
                }
            } else if (m.entities.get().contains(entity.getType())) {
                requestPull(entity.getType().getDescription().getString() + " appeared in your render distance.");
            }
        }
    }

    protected void requestPull(String reason) {
        if (mc.player.isDeadOrDying()) return;
        socket.send(GSON.toJson(new AutoPearlStasis.PullRequest(reason)));
    }

    protected void testConnection() {
//        m.info(socket.connection.getRemoteAddress());
        socket.send(GSON.toJson(new AutoPearlStasis.SetUsername(mc.player.getName().getString())));
    }

    protected class HostSocket {
        private final int port;

        private ServerSocket serverSocket;
        private volatile LineSocket connection;
        private volatile boolean running = true;

        private HostSocket(int port) {
            this.port = port;

            Thread acceptThread = new Thread(this::acceptLoop, "pearl-stasis-host-accept");
            acceptThread.setDaemon(true);
            acceptThread.start();
        }

        private void acceptLoop() {
            try {
                serverSocket = new ServerSocket(port);
                m.info("Host listening on port " + port);

                while (running) {
                    m.info("Waiting for worker...");

                    Socket socket = serverSocket.accept();
                    m.info("Worker connected: " + socket.getRemoteSocketAddress());
                    connection = new LineSocket(socket);
                    connection.listen(this::onMessage, () -> m.info("Puller disconnected."));

                    send(GSON.toJson(new AutoPearlStasis.SetUsername(mc.player.getName().getString())));

                    // Don't accept() again until the current worker is gone.
                    while (running && connection.isOpen()) {
                        sleep(200);
                    }
                }
            } catch (IOException e) {
                if (running) m.info("Host error: " + e.getMessage());
            }
        }

        private void onMessage(String message) {
            m.info("Worker: " + message);

            JsonObject obj;
            try {
                obj = GSON.fromJson(message, JsonObject.class);
            } catch (Exception e) {
                m.info("Ignoring bad message from host: " + message);
                return;
            }

            String type = obj.has("type") ? obj.get("type").getAsString() : "";

            switch (type) {
                case AutoPearlStasis.PullerStatus.TYPE -> {
                    m.info("Worker status: " + obj.get("status").getAsString());
                }
                case AutoPearlStasis.PearlStatus.TYPE -> {
                    if (obj.get("loaded").getAsBoolean()) {
                        m.info("pearl loaded.");
                    } else {
                        m.info("pearl destroyed.");
                    }
                }
                default -> throw new IllegalStateException("Unexpected value: " + type);
            }
        }

        protected void send(String message) {
            if (connection != null) connection.send(message);
        }

        protected void stop() {
            running = false;

            if (connection != null) connection.close();

            try {
                if (serverSocket != null) serverSocket.close();
            } catch (IOException ignored) {
            }

            m.info("Host stopped.");
        }

        private void sleep(long ms) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

}
