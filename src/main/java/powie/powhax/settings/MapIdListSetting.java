package powie.powhax.settings;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.utils.CharFilter;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.settings.IVisible;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class MapIdListSetting extends Setting<Set<Integer>> {
    private static final CharFilter DIGITS = (_, c) -> Character.isDigit(c);

    public final Class<? extends WTextBox.Renderer> renderer;

    public MapIdListSetting(String name, String description, Set<Integer> defaultValue,
                            Consumer<Set<Integer>> onChanged,
                            Consumer<Setting<Set<Integer>>> onModuleActivated,
                            IVisible visible,
                            Class<? extends WTextBox.Renderer> renderer) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible);
        this.renderer = renderer;
    }

    @Override
    protected Set<Integer> parseImpl(String str) {
        Set<Integer> result = new LinkedHashSet<>();
        for (String part : str.split(",")) {
            try {
                result.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    @Override
    protected boolean isValueValid(Set<Integer> value) {
        return true;
    }

    // Not written to modules.nbt: the ids are stored per-world through Config.
    // (To persist globally instead, write/read them here like StringListSetting does.)
    @Override
    public CompoundTag save(CompoundTag tag) {
        return tag;
    }

    @Override
    public Set<Integer> load(CompoundTag tag) {
        return get();
    }

    @Override
    protected void resetImpl() {
        value = new LinkedHashSet<>(defaultValue);
    }

    public static void fillTable(GuiTheme theme, WTable table, MapIdListSetting setting) {
        table.clear();

        List<Integer> ids = new ArrayList<>(setting.get());

        for (int i = 0; i < ids.size(); i++) {
            int idx = i;

            WTextBox textBox = table.add(
                theme.textBox(String.valueOf(ids.get(i)), DIGITS, setting.renderer)
            ).expandX().widget();

            textBox.action = () -> {
                try {
                    ids.set(idx, Integer.parseInt(textBox.get().trim()));
                } catch (NumberFormatException ignored) {} // empty / overflow: keep the old value
            };
            textBox.actionOnUnfocused = () -> setting.set(new LinkedHashSet<>(ids));

            WMinus delete = table.add(theme.minus()).widget();
            delete.action = () -> {
                ids.remove(idx);
                setting.set(new LinkedHashSet<>(ids));
                fillTable(theme, table, setting);
            };

            table.row();
        }

        if (!ids.isEmpty()) {
            table.add(theme.horizontalSeparator()).expandX();
            table.row();
        }

        WButton add = table.add(theme.button("Add")).expandX().widget();
        add.action = () -> {
            if (!Utils.canUpdate()) return;
            int next = 0;
            while (ids.contains(next)) next++; // smallest unused id, user edits it afterwards
            ids.add(next);
            setting.set(new LinkedHashSet<>(ids));
            fillTable(theme, table, setting);
        };

        WButton reset = table.add(theme.button(GuiRenderer.RESET)).widget();
        reset.action = () -> {
            setting.reset();
            fillTable(theme, table, setting);
        };
        reset.tooltip = "Reset";
    }

    public static class Builder extends SettingBuilder<Builder, Set<Integer>, MapIdListSetting> {
        private Class<? extends WTextBox.Renderer> renderer;

        public Builder() {
            super(new LinkedHashSet<>());
        }

        public Builder renderer(Class<? extends WTextBox.Renderer> renderer) {
            this.renderer = renderer;
            return this;
        }

        @Override
        public MapIdListSetting build() {
            return new MapIdListSetting(name, description, defaultValue, onChanged, onModuleActivated, visible, renderer);
        }
    }
}
