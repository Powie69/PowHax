package powie.powhax.modules;

import powie.powhax.Powhax;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import java.util.*;
import java.util.stream.Collectors;

public class AntiVanish extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> interval = sgGeneral.add(new IntSetting.Builder()
        .name("interval")
        .description("Vanish check interval.")
        .defaultValue(100)
        .min(0)
        .sliderMax(300)
        .build()
    );

    private Map<UUID, String> playerCache = new HashMap<>();
    private final List<String> messageCache = new ArrayList<>();

    private final List<Integer> completionIDs = new ArrayList<>();

    private int timer = 0;

    public AntiVanish() {
        super(Powhax.CATEGORY, "anti-vanish", "Notifies user when a admin uses /vanish");
    }

    @Override
    public void onActivate() {
        timer = 0;
        completionIDs.clear();
        messageCache.clear();
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList l = theme.verticalList();
        l.add(theme.label("Only works with servers that have Essential Plugin"));
        l.add(theme.label("not 100% fullproof."));
        return l;
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        messageCache.add(event.getMessage().getString());
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        timer++;
        if (timer < interval.get()) return;


        Map<UUID, String> oldPlayers = Map.copyOf(playerCache);
        playerCache = mc.getNetworkHandler().getPlayerList().stream().collect(Collectors.toMap(e -> e.getProfile().getId(), e -> e.getProfile().getName()));

        for (UUID uuid : oldPlayers.keySet()) {
            if (playerCache.containsKey(uuid)) continue;
            String name = oldPlayers.get(uuid);
            if (name.contains(" ")) continue;
            if (name.length() < 3 || name.length() > 16) continue;
            if (messageCache.stream().noneMatch(s -> s.contains(name))) {
                warning(name + " has gone into vanish.");
            }
        }

        timer = 0;
        messageCache.clear();
    }
}
