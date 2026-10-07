package powie.powhax.modules;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import powie.powhax.Powhax;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MapartBlock extends Module {
    private final Set<Integer> blockedMaparts = new HashSet<>();

    public MapartBlock() {
        super(Powhax.CATEGORY, "mapart-blocker", "Blocks rendering of specified Maparts based on their IDs");
    }

    private final Settings tempSettings = new Settings();

    private final Setting<List<String>> tempOption = tempSettings.getDefaultGroup().add(new StringListSetting.Builder()
        .name("Maparts")
        .build()
    );

    @Override
    public WWidget getWidget(GuiTheme theme) {
        return theme.settings(tempSettings); // just renders it
    }

    private void fillTable(GuiTheme theme, WTable table) {
        table.clear();
        if (!Utils.canUpdate()) {
            table.add(theme.label("Join a world first"));
            return;
        }
    }
}
