package powie.powhax.modules.autoPearlStasis;

import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.entity.EntityRemovedEvent;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.phys.BlockHitResult;

import java.io.IOException;
import java.net.Socket;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import static powie.powhax.Powhax.GSON;
import static powie.powhax.Powhax.LOG;

public class Puller {
    private final AutoPearlStasis m;
    protected final WorkerSocket socket;

    protected boolean hasPearlLoaded;
    protected String mainAccountName;

    protected Puller(AutoPearlStasis module) throws IOException {
        m = module;
        socket = new WorkerSocket(m.serverPort.get());
    }

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        if (event.entity instanceof ThrownEnderpearl pearl) {
            if (pearl.getOwner() != null && pearl.getOwner().getName().getString().equalsIgnoreCase(mainAccountName)
                && PlayerUtils.isWithin(m.trapdoorPos.get(), 2)) {
                hasPearlLoaded = true;
                m.info("pearl loaded");
                socket.send(GSON.toJson(new AutoPearlStasis.PearlStatus(true)));
            }
        }
    }

    @EventHandler
    private void onEntityRemoved(EntityRemovedEvent event) {
        if (event.entity instanceof ThrownEnderpearl pearl) {
            if (pearl.getOwner() != null && pearl.getOwner().getName().getString().equalsIgnoreCase(mainAccountName)) {
                hasPearlLoaded = false;
                m.info("pearl removed");
                socket.send(GSON.toJson(new AutoPearlStasis.PearlStatus(false)));
            }
        }
    }

    protected void pullPearl() {
        if (m.mode.get().equals(AutoPearlStasis.Mode.Main)) return;
        if (!hasPearlLoaded) return;

        if (!(mc.level.getBlockState(m.trapdoorPos.get()).getBlock() instanceof TrapDoorBlock)) {
            m.error("selected position is not a trapdoor");
            return;
        }
        if (!PlayerUtils.isWithinReach(m.trapdoorPos.get())) {
            m.error("selected position is out of reach");
            return;
        }

        if (m.rotate.get())
            Rotations.rotate(Rotations.getYaw(m.trapdoorPos.get()), Rotations.getPitch(m.trapdoorPos.get()));

        BlockUtils.interact(new BlockHitResult(
                Utils.vec3(m.trapdoorPos.get()),
                Direction.UP,
                m.trapdoorPos.get(),
                false),
            InteractionHand.MAIN_HAND,
            true);

        hasPearlLoaded = false;
    }

    protected class WorkerSocket {
        private static final long RECONNECT_DELAY_MS = 3000;

        private final int port;

        private volatile LineSocket connection;
        private volatile boolean running = true;

        private WorkerSocket(int port) {
            this.port = port;

            Thread connectThread = new Thread(this::connectLoop, "pearl-stasis-worker-connect");
            connectThread.setDaemon(true);
            connectThread.start();
        }

        private void connectLoop() {
            while (running) {
                try {
                    Socket socket = new Socket("localhost", port);
                    m.info("Connected to host.");

                    connection = new LineSocket(socket);
                    connection.listen(this::onMessage, () -> m.info("Connection lost."));

                    send(GSON.toJson(new AutoPearlStasis.PearlStatus(hasPearlLoaded)));

                    while (running && connection.isOpen()) {
                        sleep(200);
                    }
                } catch (IOException e) {
                    if (running) m.info("Connection failed: " + e.getMessage());
                }

                if (!running) return;

                m.info("Attempting to reconnect in " + (RECONNECT_DELAY_MS / 1000) + " seconds...");
                sleep(RECONNECT_DELAY_MS);
            }
        }

        private void onMessage(String message) {
            AutoPearlStasis.NetworkMessage msg;
            try {
                msg = AutoPearlStasis.NetworkMessage.decode(message);
            } catch (Exception e) {
                m.error("Ignoring bad message from host: " + message);
                return;
            }

            switch (msg) {
                case AutoPearlStasis.SetUsername su -> {
                    mainAccountName = su.username();
                    send(GSON.toJson(new AutoPearlStasis.PullerStatus(mc.player.getName().getString(), Utils.getWorldName())));
                    m.info("Set main account to: " + mainAccountName);
                }
                case AutoPearlStasis.PullRequest pr -> {
                    m.info("Pull request: " + pr.reason());
                    pullPearl();
                }
                default -> LOG.error("Ignoring unexpected message from host: {}", msg);
            }
        }

        protected void send(String message) {
            if (connection != null) connection.send(message);
        }

        protected void stop() {
            running = false;
            if (connection != null) connection.close();
            m.info("Worker stopped.");
        }

        private void sleep(long ms) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            }
        }
    }
}
