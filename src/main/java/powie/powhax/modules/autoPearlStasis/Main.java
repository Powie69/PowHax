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

import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import static powie.powhax.Powhax.GSON;

public class Main {
    private final AutoPearlStasis m;
    private final HttpClient client = HttpClient.newHttpClient();
    private int pops;

    public Main(AutoPearlStasis module) {
        m = module;
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
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + m.serverPort.get() + "/pull"))
            .GET()
            .build();

        client.sendAsync(
            request,
            HttpResponse.BodyHandlers.discarding()
        ).thenAccept(_ -> {
            m.info("Pulled: " + reason);
        }).exceptionally(e -> {
            if (e.getCause() instanceof UnknownHostException) {
                m.error("Connection error: Puller's side is not active");
            } else {
                m.error("Connection error: " + e.getMessage());
            }
            return null;
        });
    }

    protected void testConnection() {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + m.serverPort.get() + "/ping"))
            .POST(HttpRequest.BodyPublishers.ofString(
                GSON.toJson(new AutoPearlStasis.PingRequest(mc.player.getName().getString())))
            )
            .build();

        client.sendAsync(
            request,
            HttpResponse.BodyHandlers.ofString()
        ).thenAccept(response -> {
            if (response.statusCode() >= 400) {
                m.error("Connection error");
                return;
            }

            AutoPearlStasis.PingResponse ping = GSON.fromJson(response.body(), AutoPearlStasis.PingResponse.class);
            if (ping.server().isEmpty()) {
                m.error("Connection found but Puller is not online");
                return;
            }
            if (!ping.server().equals(Utils.getWorldName())) {
                m.error("Connection found but Puller is on the wrong server: " + ping.server());
                return;
            }

            m.info("Connection found. Puller's username is: " + ping.PullerUsername());
        }).exceptionally(e -> {
            if (e.getCause() instanceof UnknownHostException) {
                m.error("Connection error: Puller's side is not active");
            } else {
                m.error("Connection error: " + e.getMessage());
            }
            return null;
        });
    }
}
