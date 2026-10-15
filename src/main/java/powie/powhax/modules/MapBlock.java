package powie.powhax.modules;

import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WConfirmedMinus;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import powie.powhax.Powhax;
import powie.powhax.utils.Config;

import java.util.HashSet;
import java.util.Set;

public class MapBlock extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Boolean> replace = sgGeneral.add(new BoolSetting.Builder()
        .name("replace")
        .description("Replaces the map with powhax logo")
        .defaultValue(true)
        .build());

    public final Set<Integer> maps = new HashSet<>();

    public MapBlock() {
        super(Powhax.CATEGORY, "map-blocker", "Blocks rendering of specified maps based on their IDs");
        runInMainMenu = true;
    }

    //render
    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList l = theme.verticalList();
        WTable table = theme.table();

        l.add(theme.label("Enter map ID:")).padLeft(6).marginTop(6);
        WHorizontalList actionButtons = l.add(theme.horizontalList()).expandX().padLeft(6).widget();
        WTextBox addInput = actionButtons.add(theme.textBox("", (_, c) -> Character.isDigit(c))).expandX().widget();
        WButton addButton = actionButtons.add(theme.button("Add")).minWidth(50).expandX().widget();
        addButton.action = () -> {
            if (!addMap(addInput.get())) return;
            fillTable(theme, table);
            addInput.set("");
        };
        //

        WSection mapsList = l.add(theme.section("Blocked maps")).expandX().widget();
        fillTable(theme, table);
        mapsList.add(table);
        return l;
    }

    private void fillTable(GuiTheme theme, WTable table) {
        table.clear();
        if (!Utils.canUpdate()) {
            table.add(theme.label("Join a world first"));
            return;
        }

        for (Integer map : maps) {
            WConfirmedMinus removeButton = table.add(theme.confirmedMinus()).widget();
            removeButton.action = () -> { // maps get saved on game left so no need to save here (or maybe I do for crashes?)
                maps.remove(map);
                fillTable(theme, table);
            };
            table.add(theme.label(map.toString()));
            table.row();
        }
    }

    // serialize
    private boolean addMap(String value) {
        if (!Utils.canUpdate() || value.isBlank()) return false;
        try {
            int mapId = Integer.parseInt(value);
            if (mapId < 0) return false;
            if (maps.contains(mapId)) {
                error(value + " already exists");
                return false;
            }
            maps.add(mapId);
            Config.saveMapFile(Utils.getFileWorldName(), maps);
            return true;
        } catch (NumberFormatException e) {
            // Invalid integer format
            error("Invalid map ID");
            return false;
        }
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        Set<Integer> mapData = Config.getMapFile(Utils.getFileWorldName());
        if (mapData.isEmpty()) return;
        maps.addAll(mapData);
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        Config.saveMapFile(Utils.getFileWorldName(), maps);
        maps.clear();
    }
}
