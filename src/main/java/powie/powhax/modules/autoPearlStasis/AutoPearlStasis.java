package powie.powhax.modules.autoPearlStasis;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import powie.powhax.Powhax;

import java.io.IOException;
import java.util.Set;

public class AutoPearlStasis extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTriggers = settings.createGroup("Triggers");
    //    private final SettingGroup sgHost = settings.createGroup("Host (Main)");
    private final SettingGroup sgPuller = settings.createGroup("Worker (Puller)");

    // General
    protected final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("""
            - Main: The account that has the pearl loaded in the stasis
            - Puller: The account that will pull the pearl
            """)
        .defaultValue(Mode.Main)
        .onChanged(v -> handleModeSwitchingWhileActive(v))
        .build()
    );

    protected final Setting<Integer> serverPort = sgGeneral.add(new IntSetting.Builder()
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
        .action(this::handleTriggerBind)
        .build()
    );

    protected final Setting<Integer> health = sgTriggers.add(new IntSetting.Builder()
        .name("health")
        .description("Automatically disconnects when health is lower or equal to this value. Set to 0 to disable.")
        .defaultValue(6)
        .range(0, 19)
        .sliderMax(19)
        .visible(() -> mode.get() == Mode.Main)
        .build()
    );

    protected final Setting<Boolean> smart = sgTriggers.add(new BoolSetting.Builder()
        .name("predict-incoming-damage")
        .description("Disconnects when it detects you're about to take enough damage to set you under the 'health' setting.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Main)
        .build()
    );

    protected final Setting<Integer> totemPops = sgTriggers.add(new IntSetting.Builder()
        .name("totem-pops")
        .description("Disconnects when you have popped this many totems. Set to 0 to disable.")
        .defaultValue(0)
        .min(0)
        .visible(() -> mode.get() == Mode.Main)
        .build()
    );

    protected final Setting<Boolean> onlyTrusted = sgTriggers.add(new BoolSetting.Builder()
        .name("only-trusted")
        .description("Disconnects when a player not on your friends list appears in render distance.")
        .defaultValue(false)
        .visible(() -> mode.get() == Mode.Main)
        .build()
    );

    protected final Setting<Set<EntityType<?>>> entities = sgTriggers.add(new EntityTypeListSetting.Builder()
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
    protected final Setting<BlockPos> trapdoorPos = sgPuller.add(new BlockPosSetting.Builder()
        .name("trapdoor-position")
        .description("The position of the trapdoor")
        .visible(() -> mode.get() == Mode.Puller)
        .build()
    );

    protected final Setting<Boolean> rotate = sgPuller.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Determines whether you should rotate towards the trapdoor.")
        .defaultValue(false)
        .visible(() -> mode.get() == Mode.Puller)
        .build()
    );

    private Main main;
    private Puller puller;

    private String currentActiveMode;

    /**
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
        testConnectionButton.action = () -> {
            if (main != null) main.testConnection();
        };

        return l;
    }

    @Override
    public void onActivate() {
        if (mode.get() == Mode.Main) {
            startMainMode();
        } else {
            startPullerMode();
        }
        currentActiveMode = mode.get().name();
    }

    @Override
    public void onDeactivate() {
        if (mode.get() == Mode.Main) {
            stopMainMode();
        } else {
            stopPullerMode();
        }
        currentActiveMode = null;
    }

    @Override
    public String getInfoString() {
        if (mode.get() == Mode.Main) {
            return "Main";
        } else {
            return "Puller | " + (puller != null ? puller.mainAccountName : "");
        }
    }

    private void startPullerMode() {
        try {
            puller = new Puller(this);
        } catch (IOException e) {
            error(String.valueOf(e));
            toggle();
        }
        MeteorClient.EVENT_BUS.subscribe(puller);
    }

    private void startMainMode() {
        main = new Main(this);
        MeteorClient.EVENT_BUS.subscribe(main);
        main.testConnection();
    }

    private void stopPullerMode() {
        if (puller == null) return;
        MeteorClient.EVENT_BUS.unsubscribe(puller);
        puller.socket.stop();
        puller = null;
    }

    private void stopMainMode() {
        if (main == null) return;
        MeteorClient.EVENT_BUS.unsubscribe(main);
        main.socket.stop();
        main = null;
    }

    private void handleTriggerBind() {
        if (main != null) main.requestPull("Pressed bind");
    }

    private void handleModeSwitchingWhileActive(Mode v) {
        info("mode" + v);
        if (!isActive()) return;
        if (currentActiveMode == null || currentActiveMode.equals(v.name())) return;

        if (v == Mode.Main) {
            stopPullerMode();
            startMainMode();
        } else {
            stopMainMode();
            startPullerMode();
        }
        currentActiveMode = v.name();
    }

    protected enum Mode {
        Main,
        Puller
    }

    record SetUsername(String type, String username) {
        static final String TYPE = "setUsername";
        SetUsername(String username) { this(TYPE, username); }
    }

    record PullRequest(String type, String reason) {
        static final String TYPE = "pull";
        PullRequest(String reason) { this(TYPE, reason); }
    }

    record PearlStatus(String type, boolean loaded) {
        static final String TYPE = "pearlStatus";
        PearlStatus(boolean loaded) { this(TYPE, loaded); }
    }

    record PullerStatus(String type, String pullerName, String server) {
        static final String TYPE = "pullerStatus";
        PullerStatus(String pullerName, String server) { this(TYPE, pullerName, server); }
    }
}
