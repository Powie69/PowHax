package powie.powhax.modules.autoPearlStasis;

import com.sun.net.httpserver.HttpServer;
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
import java.io.OutputStream;
import java.net.InetSocketAddress;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import static powie.powhax.Powhax.GSON;

public class Puller {
    private final AutoPearlStasis m;
    private EmbeddedHttpServer httpServer;
    private boolean hasPearlLoaded;
    protected String mainAccountName;

    protected Puller(AutoPearlStasis module) throws IOException {
        m = module;
        httpServer = new EmbeddedHttpServer(this, m.serverPort.get());
    }

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        if (event.entity instanceof ThrownEnderpearl pearl) {
            if (pearl.getOwner() != null && pearl.getOwner().getName().getString().equalsIgnoreCase(mainAccountName)
                && PlayerUtils.isWithin(m.trapdoorPos.get(), 3)) {
                hasPearlLoaded = true;
                m.info("pearl loaded");
            }
        }
    }

    @EventHandler
    private void onEntityRemoved(EntityRemovedEvent event) {
        if (event.entity instanceof ThrownEnderpearl pearl) {
            if (pearl.getOwner() != null && pearl.getOwner().getName().getString().equalsIgnoreCase(mainAccountName)) {
                hasPearlLoaded = false;
                m.info("pearl removed");
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

    // ts so dumb
    protected void stopHttpServer() {
        if (httpServer == null) return;
        httpServer.stop();
        httpServer = null;
    }

    private static class EmbeddedHttpServer {
        private final Puller puller;
        private HttpServer server;

        private EmbeddedHttpServer(Puller puller, int port) throws IOException {
            this.puller = puller;
            start(port);
        }

        private void start(int port) throws IOException {
            if (server != null) {
                return;
            }

            server = HttpServer.create(
                new InetSocketAddress("localhost", port),
                0
            );

            server.createContext("/ping", exchange -> {
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1); // 405 Method Not Allowed
                    return;
                }

                AutoPearlStasis.PingRequest pingRequest = GSON.fromJson(
                    new String(exchange.getRequestBody().readAllBytes()),
                    AutoPearlStasis.PingRequest.class);
                puller.mainAccountName = pingRequest.MainUsername();

                String response = GSON.toJson(new AutoPearlStasis.PingResponse(
                    mc.player.getName().getString(),
                    Utils.getWorldName()));

                exchange.sendResponseHeaders(200, response.length());

                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(response.getBytes());
                }
            });

            server.createContext("/pull", exchange -> {
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1); // 405 Method Not Allowed
                    return;
                }

                puller.pullPearl();

                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            });

            server.start();
        }

        protected void stop() {
            if (server == null) return;
            server.stop(0);
            server = null;
        }
    }
}
