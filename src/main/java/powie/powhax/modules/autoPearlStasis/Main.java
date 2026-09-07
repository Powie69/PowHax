package powie.powhax.modules.autoPearlStasis;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.Utils;
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
import static powie.powhax.Powhax.LOG;

public class Main {
    private final AutoPearlStasis m;

    protected boolean hasPearlLoaded;
    protected int pops;
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
        if (m.totemPops.get() <= 0 || pops < m.totemPops.get()) return;
        requestPull("Popped " + pops + " totems.");
        pops = 0;
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
        if (!hasPearlLoaded) {
            m.error("Pearl not loaded");
            return;
        }
        m.info("Pearl pulled: " + reason);
        socket.send(GSON.toJson(new AutoPearlStasis.PullRequest(reason)));
    }

    /**
     * Puller will send back {@link AutoPearlStasis#PullerStatus}
     *
     * @return true if no connection
     */
    protected void testConnection() {
        if (socket.connection == null) return;
        m.info(socket.connection.getRemoteAddress());
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

                while (running) {
                    m.info("Waiting for Puller...");

                    Socket socket = serverSocket.accept();
                    m.info("Puller connected: " + socket.getRemoteSocketAddress());
                    connection = new LineSocket(socket);
                    connection.listen(this::onMessage, () -> m.info("Puller disconnected."));

                    send(GSON.toJson(new AutoPearlStasis.SetUsername(mc.player.getName().getString())));

                    while (running && connection.isOpen()) {
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            } catch (IOException e) {
                if (running) {
                    m.error("Deactivating: " + e.getMessage());
                    m.disable();
                }
            }
        }

        private void onMessage(String message) {
            AutoPearlStasis.NetworkMessage msg;
            try {
                msg = AutoPearlStasis.NetworkMessage.decode(message);
            } catch (Exception e) {
                m.error("Ignoring bad message from Puller: " + message);
                return;
            }

            switch (msg) {
                case AutoPearlStasis.PullerStatus ps -> {
                    if (!ps.server().equalsIgnoreCase(Utils.getWorldName())) {
                        m.error("Puller is connected but they're not on the same server");
                    }
                }
                case AutoPearlStasis.PearlStatus ps -> {
                    hasPearlLoaded = ps.loaded();
                    m.info(hasPearlLoaded ? "pearl loaded." : "pearl destroyed.");
                }
                default -> LOG.error("Ignoring unexpected message from Puller: {}", msg);
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
    }

}
