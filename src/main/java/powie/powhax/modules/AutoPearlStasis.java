package powie.powhax.modules;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpServer;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.phys.BlockHitResult;
import powie.powhax.Powhax;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;

public class AutoPearlStasis extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTriggers = settings.createGroup("Tiggers");
    //    private final SettingGroup sgHost = settings.createGroup("Host (Main)");
    private final SettingGroup sgPuller = settings.createGroup("Worker (Puller)");

    // General
    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("""
            - Main: The account that has the pearl loaded in the stasis
            - Puller: The account that will pull the pearl
            """)
        .defaultValue(Mode.Main)
        .build()
    );

    private final Setting<Integer> serverPort = sgGeneral.add(new IntSetting.Builder()
        .name("port")
        .description("The network port used for the connection. (so Main and Puller can connect)")
        .defaultValue(67)
        .range(1, 65535)
        .noSlider()
        .build()
    );

    // Triggers
    private final Setting<Keybind> triggerBind = sgTriggers.add(new KeybindSetting.Builder()
        .name("trigger-bind")
        .description("The keybind to manually trigger pearl stasis")
        .visible(() -> mode.get() == Mode.Main)
        .action(() -> requestPull("Pressed bind"))
        .build()
    );

    private final Setting<Integer> health = sgTriggers.add(new IntSetting.Builder()
        .name("health")
        .description("Automatically disconnects when health is lower or equal to this value. Set to 0 to disable.")
        .defaultValue(6)
        .range(0, 19)
        .sliderMax(19)
        .visible(() -> mode.get() == Mode.Main)
        .build()
    );

    private final Setting<Boolean> smart = sgTriggers.add(new BoolSetting.Builder()
        .name("predict-incoming-damage")
        .description("Disconnects when it detects you're about to take enough damage to set you under the 'health' setting.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Main)
        .build()
    );

    private final Setting<Integer> totemPops = sgTriggers.add(new IntSetting.Builder()
        .name("totem-pops")
        .description("Disconnects when you have popped this many totems. Set to 0 to disable.")
        .defaultValue(0)
        .min(0)
        .visible(() -> mode.get() == Mode.Main)
        .build()
    );

    private final Setting<Boolean> onlyTrusted = sgTriggers.add(new BoolSetting.Builder()
        .name("only-trusted")
        .description("Disconnects when a player not on your friends list appears in render distance.")
        .defaultValue(false)
        .visible(() -> mode.get() == Mode.Main)
        .build()
    );

    private final Setting<Set<EntityType<?>>> entities = sgTriggers.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Disconnects when a specified entity is present within a specified range.")
        .visible(() -> mode.get() == Mode.Main)
        .build()
    );

    // Main
//    private final Setting<Boolean> autoReloadPearl = sgHost.add(new BoolSetting.Builder()
//        .name("auto-reload-pearl")
//        .description("Automatically reloads your pearl upon activation")
//        .defaultValue(true)
//        .build()
//    );

    // Puller
    private final Setting<BlockPos> trapdoorPos = sgPuller.add(new BlockPosSetting.Builder()
        .name("trapdoor-position")
        .description("The position of the trapdoor")
        .visible(() -> mode.get() == Mode.Puller)
        .build()
    );

    private final Setting<Boolean> rotate = sgPuller.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Determines whether you should rotate towards the trapdoor.")
        .defaultValue(false)
        .visible(() -> mode.get() == Mode.Puller)
        .build()
    );

    private final Gson GSON = new GsonBuilder().create();
    private final HttpClient client = HttpClient.newHttpClient();

    private EmbeddedHttpServer httpServer;
    private int currentActivePort; // Because port changes don't update running server
    private int pops;
    private boolean hasPearlLoaded;

    /**
     * <blockquote>FAREX PULL</blockquote>
     * <cite>dr donut</cite>
     * <p>TODO: auto pearl reload</p>
     */
    public AutoPearlStasis() {
        super(Powhax.CATEGORY,
            "auto-pearl-stasis",
            "Automatically triggers your pearl stasis you when certain requirements are met.\n2nd account required",
            "Auto pearl puller");
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList l = theme.verticalList();

        WButton testConnectionButton = l.add(theme.button("test Connection")).expandX().widget();
        testConnectionButton.action = this::testConnection;

        return l;
    }

    @Override
    public void onActivate() {
        pops = 0;

        if (mode.get() != Mode.Puller) return;
        try {
            httpServer = new EmbeddedHttpServer(serverPort.get());
            currentActivePort = serverPort.get();
        } catch (IOException e) {
            error("Failed to start HTTP server: " + e);
            toggle();
        }
    }

    @Override
    public void onDeactivate() {
        if (mode.get() != Mode.Puller || httpServer == null) return;
        httpServer.stop();
        httpServer = null;
        currentActivePort = 0;
    }

    @Override
    public String getInfoString() {
        if (mode.get() == Mode.Main) {
            return "Main | Pearl Loaded: " + hasPearlLoaded;
        } else {
            return "Puller | Listening on: " + currentActivePort;
        }
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (!(event.packet instanceof ClientboundEntityEventPacket p)) return;
        if (p.getEventId() != EntityEvent.PROTECTED_FROM_DEATH) return;

        Entity entity = p.getEntity(mc.level);
        if (entity == null || !entity.equals(mc.player)) return;

        pops++;
        if (totemPops.get() > 0 && pops >= totemPops.get()) requestPull("Popped " + pops + " totems.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        float playerHealth = mc.player.getHealth();

        if (playerHealth <= health.get()) {
            requestPull("Health was lower than " + health.get() + ".");
            return;
        }

        if (smart.get()
            && !mc.player.isInvulnerable()
            && !mc.player.getAbilities().invulnerable
            && playerHealth + mc.player.getAbsorptionAmount() - PlayerUtils.possibleHealthReductions() < health.get()) {
            requestPull("Health was going to be lower than " + health.get() + ".");
            return;
        }

        if (!onlyTrusted.get() && entities.get().isEmpty())
            return; // only check all entities if needed

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof Player player && player.getUUID() != mc.player.getUUID()) {
                if (onlyTrusted.get() && player != mc.player && !Friends.get().isFriend(player)) {
                    requestPull("Non-trusted player '" + player.getName().getString() + "' appeared in your render distance.");
                    return;
                }
            } else if (entities.get().contains(entity.getType())) {
                requestPull(entity.getType().getDescription().getString() + " appeared in your render distance.");
            }
        }
    }

    // TODO: handle on puller side
    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        if (event.entity instanceof ThrownEnderpearl pearl) {
            if (pearl.getOwner() != null && pearl.getOwner().getName().getString().equals(mc.player.getName().getString())
                && PlayerUtils.isWithin(trapdoorPos.get(), 3)) {
                hasPearlLoaded = true;
                info("pearl loaded");
            }
        }
    }

    private void requestPull(String reason) {
        if (!hasPearlLoaded) return;
        if (mc.player.isDeadOrDying()) return;
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + serverPort.get() + "/pull"))
            .GET()
            .build();

        client.sendAsync(
            request,
            HttpResponse.BodyHandlers.discarding()
        ).thenAccept(_ -> {
            info("Pulled: " + reason);
            hasPearlLoaded = false;
        }).exceptionally(e -> {
            if (e.getCause() instanceof UnknownHostException) {
                error("Connection error: Puller's side is not active");
            } else {
                error("Connection error: " + e.getMessage());
            }
            return null;
        });
    }

    public void pullPearl() {
        if (mode.get().equals(Mode.Main)) return;

        if (!(mc.level.getBlockState(trapdoorPos.get()).getBlock() instanceof TrapDoorBlock)) {
            error("selected position is not a trapdoor");
            return;
        }
        if (!PlayerUtils.isWithinReach(trapdoorPos.get())) {
            error("selected position is out of reach");
            return;
        }

        if (rotate.get()) Rotations.rotate(Rotations.getYaw(trapdoorPos.get()), Rotations.getPitch(trapdoorPos.get()));

        BlockUtils.interact(new BlockHitResult(
                Utils.vec3(trapdoorPos.get()),
                Direction.UP,
                trapdoorPos.get(),
                false),
            InteractionHand.MAIN_HAND,
            true);
    }

    private void testConnection() {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + serverPort.get() + "/ping"))
            .GET()
            .build();

        client.sendAsync(
            request,
            HttpResponse.BodyHandlers.ofString()
        ).thenAccept(response -> {
            if (response.statusCode() >= 400) {
                error("Connection error");
                return;
            }

            PingResponse ping = GSON.fromJson(response.body(), PingResponse.class);
            if (ping.server.isEmpty()) {
                error("Connection found but Puller is not online");
                return;
            }
            if (!ping.server.equals(Utils.getWorldName())) {
                error("Connection found but Puller is on the wrong server: " + ping.server());
                return;
            }

            info("Connection found. Puller's username is: " + ping.player);
        }).exceptionally(e -> {
            if (e.getCause() instanceof UnknownHostException) {
                error("Connection error: Puller's side is not active");
            } else {
                error("Connection error: " + e.getMessage());
            }
            return null;
        });
    }

    public enum Mode {
        Main,
        Puller
    }

    private record PingResponse(String player, String server) {
    }

    private class EmbeddedHttpServer {
        private HttpServer server;

        public EmbeddedHttpServer(int port) throws IOException {
            start(port);
        }

        public void start(int port) throws IOException {
            if (server != null) {
                return;
            }

            server = HttpServer.create(
                new InetSocketAddress("localhost", port),
                0
            );

            server.createContext("/ping", exchange -> {
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1); // 405 Method Not Allowed
                    return;
                }

                String response = GSON.toJson(new PingResponse(
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

                pullPearl();

                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            });

            server.start();
        }

        public void stop() {
            if (server == null) return;
            server.stop(0);
            server = null;
        }
    }
}
