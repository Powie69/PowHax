package powie.powhax.modules.autoPearlStasis;

import com.google.gson.JsonObject;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import powie.powhax.Powhax;
import powie.powhax.events.AutoPearlStasisUpdateInfoTableEvent;

import java.util.Set;

import static powie.powhax.Powhax.GSON;

public class AutoPearlStasis extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTriggers = settings.createGroup("Triggers");
    private final SettingGroup sgPuller = settings.createGroup("Puller");

    // General
    protected final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("""
            - Main: The account that has the pearl loaded in the stasis
            - Puller: The account that will pull the pearl
            """)
        .defaultValue(Mode.Main)
        .onChanged(this::handleModeSwitchingWhileActive)
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

    // Puller
    protected final Setting<BlockPos> trapdoorPos = sgPuller.add(new BlockPosSetting.Builder()
        .name("trapdoor-position")
        .description("The position of the trapdoor")
        .onChanged(this::handleTrapdoorBlockPosChange)
        .visible(() -> mode.get() == Mode.Puller)
        .build()
    );

    protected final Setting<Boolean> rotate = sgPuller.add(new BoolSetting.Builder()
        .name("rotate")
        .description("whether you should rotate towards the trapdoor.")
        .defaultValue(false)
        .visible(() -> mode.get() == Mode.Puller)
        .build()
    );

    private Main main;
    private Puller puller;

    private String currentActiveMode;
    private GuiTheme theme;
    private WTable table;
    private String connectionUsername, connectionAddress, isPearlLoaded;

    /**
     * <p>TODO: auto pearl reload</p>
     * <p>TODO: better ux for folia servers</p>
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

        WTable table = new WTable();
        this.table = table;
        this.theme = theme;
        handleTestConnection();

        l.add(table);

        l.add(theme.horizontalSeparator()).expandX();
        WButton testConnectionButton = l.add(theme.button("test Connection")).expandX().widget();
        testConnectionButton.action = this::handleTestConnection;
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
            if (main == null) return "Main | Not Connected";
            return "Main | Pearl loaded: " + isPearlLoaded + " | Popped: " + main.pops;
        } else {
            return "Puller | " + connectionUsername;
        }
    }

    @EventHandler
    private void onAutoPearlStasisUpdateInfoTable(AutoPearlStasisUpdateInfoTableEvent event) {
        if (event.username != null) connectionUsername = event.username;
        if (event.connection != null) connectionAddress = event.connection;
        if (event.pearlStatus != null) isPearlLoaded = event.pearlStatus ? "Yes" : "No";

        fillInfoTable(theme, table);
    }

    private void startPullerMode() {
        puller = new Puller(this);
        MeteorClient.EVENT_BUS.subscribe(puller);
    }

    private void startMainMode() {
        main = new Main(this);
        MeteorClient.EVENT_BUS.subscribe(main);
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

    private void handleModeSwitchingWhileActive(Mode v) {
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

    private void handleTrapdoorBlockPosChange(BlockPos blockPos) {
        if (mode.get() != Mode.Puller || puller == null || !Utils.canUpdate()) return;
        puller.sendPullerStatus();
    }

    private void handleTriggerBind() {
        if (main != null) main.requestPull("Pressed bind");
    }

    private void handleTestConnection() {
        if (mode.get() == Mode.Main && main != null) main.testConnection();
        else if (mode.get() == Mode.Puller && puller != null) puller.testConnection();
    }

    private void fillInfoTable(GuiTheme theme, WTable table) {
        table.clear();
        String role = mode.get() == Mode.Main ? "Puller's" : "Main's";
        table.add(theme.label(role + " username: " + connectionUsername)).expandCellX();
        table.row();
        table.add(theme.label(role + " connection: " + connectionAddress)).expandCellX();
        table.row();
        table.add(theme.label("Is pearl loaded: " + isPearlLoaded));
    }

    protected enum Mode {
        Main,
        Puller
    }

    protected enum InfoType {
        info,
        error
    }

    // Communications stuff
    protected sealed interface NetworkMessage {
        static NetworkMessage decode(String json) {
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            String type = obj.has("type") ? obj.get("type").getAsString() : "";

            return switch (type) {
                case PullRequest.TYPE -> GSON.fromJson(obj, PullRequest.class);
                case SetUsername.TYPE -> GSON.fromJson(obj, SetUsername.class);
                case PearlStatus.TYPE -> GSON.fromJson(obj, PearlStatus.class);
                case PullerStatus.TYPE -> GSON.fromJson(obj, PullerStatus.class);
                case SendInfo.TYPE -> GSON.fromJson(obj, SendInfo.class);
                default -> throw new IllegalArgumentException("Unknown message type '" + type + "' in: " + json);
            };
        }
    }

    // Main to Puller
    record PullRequest(String type, String reason) implements NetworkMessage {
        static final String TYPE = "pull";

        PullRequest(String reason) {
            this(TYPE, reason);
        }
    }

    record SetUsername(String type, String username) implements NetworkMessage {
        static final String TYPE = "setUsername";

        SetUsername(String username) {
            this(TYPE, username);
        }
    }

    // Puller to Main
    record PearlStatus(String type, boolean loaded) implements NetworkMessage {
        static final String TYPE = "pearlStatus";

        PearlStatus(boolean loaded) {
            this(TYPE, loaded);
        }
    }

    record PullerStatus(String type, String pullerUsername, String server) implements NetworkMessage {
        static final String TYPE = "pullerStatus";

        PullerStatus(String pullerUsername, String server) {
            this(TYPE, pullerUsername, server);
        }
    }

    record SendInfo(String type, InfoType infoType, String message) implements NetworkMessage {
        static final String TYPE = "sendInfo";

        SendInfo(InfoType infoType, String message) {
            this(TYPE, infoType, message);
        }
    }
}
