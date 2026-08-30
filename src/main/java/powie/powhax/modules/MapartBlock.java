package powie.powhax.modules;

import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import powie.powhax.Powhax;
import powie.powhax.settings.MapIdListSetting;

import java.util.List;

public class MapartBlock extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<String>> maparts = sgGeneral.add(new MapIdListSetting.Builder()
        .name("maparts")
        .description("Maparts to block")
        .build()
    );

    public MapartBlock() {
        super(Powhax.CATEGORY, "mapart-blocker", "Blocks rendering of specified Maparts based on their IDs");
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {

    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WTable table = new WTable();

        return table;
    }
}
