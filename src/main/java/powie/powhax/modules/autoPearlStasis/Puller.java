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
import net.minecraft.world.phys.Vec3;
import powie.powhax.events.AutoPearlStasisUpdateInfoTableEvent;

import java.io.IOException;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static meteordevelopment.meteorclient.MeteorClient.EVENT_BUS;
import static meteordevelopment.meteorclient.MeteorClient.mc;
import static powie.powhax.Powhax.GSON;
import static powie.powhax.Powhax.LOG;
import static powie.powhax.modules.autoPearlStasis.AutoPearlStasis.*;

public class Puller {
    private final AutoPearlStasis m;
    protected final WorkerSocket socket;
    protected final Set<UUID> hasPearlLoaded = new HashSet<>();

    protected String mainAccountName;

    protected Puller(AutoPearlStasis module) {
        m = module;
        socket = new WorkerSocket(m.serverPort.get(), module);
    }

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        if (event.entity instanceof ThrownEnderpearl pearl) {
            if (pearl.getOwner() != null
                && pearl.getOwner().getName().getString().equalsIgnoreCase(mainAccountName)
                && isWithinTrapdoor(pearl.position())) {
                hasPearlLoaded.add(pearl.getUUID());
                m.info("pearl loaded");
                if (!hasPearlLoaded.isEmpty()) {
                    socket.send(GSON.toJson(new PearlStatus(true)));
                    EVENT_BUS.post(new AutoPearlStasisUpdateInfoTableEvent(true));
                }
                ;
            }
        }
    }

    @EventHandler
    private void onEntityRemoved(EntityRemovedEvent event) {
        if (event.entity instanceof ThrownEnderpearl pearl) {
            if (hasPearlLoaded.contains(pearl.getUUID())) {
                hasPearlLoaded.remove(pearl.getUUID());
                if (hasPearlLoaded.isEmpty()) {
                    socket.send(GSON.toJson(new PearlStatus(false)));
                    EVENT_BUS.post(new AutoPearlStasisUpdateInfoTableEvent(false));
                }
            }
        }
    }

    protected void pullPearl() {
        if (hasPearlLoaded.isEmpty()) return;

        if (!(mc.level.getBlockState(m.trapdoorPos.get()).getBlock() instanceof TrapDoorBlock)) {
            printAndSendInfo("selected position is not a trapdoor", InfoType.error);
            return;
        }
        if (!PlayerUtils.isWithinReach(m.trapdoorPos.get())) {
            printAndSendInfo("selected position is out of reach", InfoType.error);
            return;
        }
        if (!mc.level.getBlockState(m.trapdoorPos.get()).getValue(TrapDoorBlock.OPEN)) {
            printAndSendInfo("trapdoor is closed", InfoType.error);
            return;
        }

        if (m.rotate.get()) {
            Rotations.rotate(
                Rotations.getYaw(m.trapdoorPos.get()),
                Rotations.getPitch(m.trapdoorPos.get()),
                this::interactWithTrapdoor);
        } else {
            interactWithTrapdoor();
        }
    }

    private void interactWithTrapdoor() {
        BlockUtils.interact(new BlockHitResult(
                Utils.vec3(m.trapdoorPos.get()),
                Direction.UP,
                m.trapdoorPos.get(),
                false),
            InteractionHand.MAIN_HAND,
            true);
    }

    private boolean isWithinTrapdoor(Vec3 pearlPos) {
        return Math.floor(pearlPos.x) == m.trapdoorPos.get().getX()
            && Math.floor(pearlPos.z) == m.trapdoorPos.get().getZ();
    }

    protected void testConnection() {
        if (socket.connection == null) return;
        m.info(socket.connection.getRemoteAddress());
    }

    protected void sendPullerStatus() {
        socket.send(GSON.toJson(new PullerStatus(
            mc.player.getName().getString(),
            Utils.getWorldName()
        )));
    }

    private void printAndSendInfo(String message, InfoType infoType) {
        switch (infoType) {
            case info -> m.info(message);
            case error -> m.error(message);
            default -> throw new IllegalArgumentException("Invalid info type: " + infoType);
        }
        socket.send(GSON.toJson(new SendInfo(infoType, message)));
    }

    protected class WorkerSocket extends BaseSocket {
        private static final int RECONNECT_DELAY_MS = 3000;

        private final int port;

        private volatile boolean running = true;
        protected volatile LineSocket connection;

        private WorkerSocket(int port, AutoPearlStasis module) {
            super(module);
            this.port = port;

            Thread connectThread = new Thread(this::connectLoop, "pearl-stasis-worker-connect");
            connectThread.setDaemon(true);
            connectThread.start();
        }

        private void connectLoop() {
            while (running) {
                try {
                    connection = new LineSocket(new Socket("localhost", port));
                    m.info("Connected to main.");
                    connection.listen(this::onMessage, () -> m.info("Connection lost."));

                    send(GSON.toJson(new PearlStatus(!hasPearlLoaded.isEmpty())));

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

        @Override
        protected void handleMessage(NetworkMessage msg) {
            switch (msg) {
                case PullRequest pr -> {
                    m.info("Pull request: " + pr.reason());
                    pullPearl();
                }
                case SetUsername su -> {
                    mainAccountName = su.username();
                    sendPullerStatus();
                    EVENT_BUS.post(new AutoPearlStasisUpdateInfoTableEvent(
                        su.username(),
                        socket.connection.getRemoteAddress()));
                    m.info("Set main account to: " + mainAccountName);
                }
                default -> LOG.error("Ignoring unexpected message from host: {}", msg);
            }
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
