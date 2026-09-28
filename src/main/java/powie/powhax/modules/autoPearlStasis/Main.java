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
import powie.powhax.events.AutoPearlStasisUpdateInfoTableEvent;
import powie.powhax.modules.autoPearlStasis.AutoPearlStasis.NetworkMessage;

import java.io.IOException;
import java.net.ServerSocket;

import static meteordevelopment.meteorclient.MeteorClient.EVENT_BUS;
import static meteordevelopment.meteorclient.MeteorClient.mc;
import static powie.powhax.Powhax.GSON;
import static powie.powhax.Powhax.LOG;

public class Main {
    private final AutoPearlStasis m;

    protected int pops;
    protected final HostSocket socket;
    private boolean hasPearlLoaded;
    private long lastRequestTime;

    public Main(AutoPearlStasis module) {
        m = module;
        socket = new HostSocket(m.serverPort.get(), module);
        testConnection();
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

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastRequestTime < m.requestCooldown.get()) return;
        lastRequestTime = currentTime;

        if (socket.connection == null || !socket.connection.isOpen()) {
            m.error("Puller not found");
            return;
        }
        if (!hasPearlLoaded) {
            m.error("Pearl not loaded");
            return;
        }
        m.info("Pearl pulled: " + reason);
        socket.send(GSON.toJson(new AutoPearlStasis.PullRequest(reason)));
    }

    /**
     * Puller will send back {@link AutoPearlStasis.PullerStatus}
     */
    protected void testConnection() {
        if (socket.connection == null) return;
        socket.send(GSON.toJson(new AutoPearlStasis.SetUsername(mc.player.getName().getString())));
    }

    protected class HostSocket extends BaseSocket {
        private final int port;
        private ServerSocket serverSocket;

        private HostSocket(int port, AutoPearlStasis module) {
            this.port = port;
            super(module);

            Thread acceptThread = new Thread(this::acceptLoop, "pearl-stasis-host-accept");
            acceptThread.setDaemon(true);
            acceptThread.start();
        }

        private void acceptLoop() {
            try {
                serverSocket = new ServerSocket(port);

                while (running) {
                    m.info("Waiting for Puller...");

                    connection = new LineSocket(serverSocket.accept());
                    m.info("Puller connected: " + connection.getRemoteAddress());
                    connection.listen(this::onMessage, this::onDisconnect);

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

        @Override
        protected void handleMessage(NetworkMessage msg) {
            switch (msg) {
                case AutoPearlStasis.PullerStatus ps -> {
                    if (!ps.server().equalsIgnoreCase(Utils.getWorldName())) {
                        m.error("Puller is connected but they're not on the same server");
                    }
                    EVENT_BUS.post(new AutoPearlStasisUpdateInfoTableEvent(
                        ps.pullerUsername(),
                        socket.connection.getRemoteAddress())); // temporarily
                }
                case AutoPearlStasis.PearlStatus ps -> {
                    hasPearlLoaded = ps.loaded();
                    m.info(ps.loaded() ? "pearl loaded." : "pearl destroyed.");
                    EVENT_BUS.post(new AutoPearlStasisUpdateInfoTableEvent(ps.loaded()));
                }
                case AutoPearlStasis.SendInfo si -> {
                    switch (si.infoType()) {
                        case info -> m.info(si.message());
                        case error -> m.error(si.message());
                        default -> throw new IllegalArgumentException("Invalid info type: " + si.infoType());
                    }
                }
                default -> LOG.error("Ignoring unexpected message from Puller: {}", msg);
            }
        }

        @Override
        protected void stop() {
            super.stop();

            try {
                if (serverSocket != null) serverSocket.close();
            } catch (IOException ignored) {
            }
        }
    }

}
