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
import powie.powhax.utils.Config;

import java.util.*;
import java.util.function.Consumer;

public class MapIdListSetting extends Setting<List<String>> {
    public final Class<? extends WTextBox.Renderer> renderer;
    public final CharFilter filter;

    public MapIdListSetting(String name, String description, List<String> defaultValue, Consumer<List<String>> onChanged, Consumer<Setting<List<String>>> onModuleActivated, IVisible visible, Class<? extends WTextBox.Renderer> renderer, CharFilter filter) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible);

        this.renderer = renderer;
        this.filter = filter;
    }

    @Override
    protected List<String> parseImpl(String str) {
        return Arrays.asList(str.split(","));
    }

    @Override
    protected boolean isValueValid(List<String> value) {
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        Set<Integer> maparts = new HashSet<>();
        for (String s : get()) {
            try {
                maparts.add(Integer.parseInt(s));
            } catch (NumberFormatException ignored) {
            }
        }
        Config.saveMapartFile(Utils.getWorldName(), maparts);
        return tag;
    }

    @Override
    public List<String> load(CompoundTag tag) {
        get().clear();

        Set<Integer> maparts = Config.getMapartFile(Utils.getWorldName());
        for (Integer id : maparts) {
            get().add(id.toString());
        }

        return get();
    }

    @Override
    public void resetImpl() {
        value = new ArrayList<>(defaultValue);
    }

    public static void fillTable(GuiTheme theme, WTable table, MapIdListSetting setting) {
        table.clear();

        ArrayList<String> strings = new ArrayList<>(setting.get());
        CharFilter filter = setting.filter == null ? (_, _) -> true : setting.filter;

        for (int i = 0; i < setting.get().size(); i++) {
            int msgI = i;
            String message = setting.get().get(i);

            WTextBox textBox = table.add(theme.textBox(message, filter, setting.renderer)).expandX().widget();
            textBox.action = () -> strings.set(msgI, textBox.get());
            textBox.actionOnUnfocused = () -> setting.set(strings);

            WMinus delete = table.add(theme.minus()).widget();
            delete.action = () -> {
                strings.remove(msgI);
                setting.set(strings);

                fillTable(theme, table, setting);
            };

            table.row();
        }

        if (!setting.get().isEmpty()) {
            table.add(theme.horizontalSeparator()).expandX();
            table.row();
        }

        WButton add = table.add(theme.button("Add")).expandX().widget();
        add.action = () -> {
            strings.add("");
            setting.set(strings);

            fillTable(theme, table, setting);
        };

        WButton reset = table.add(theme.button(GuiRenderer.RESET)).widget();
        reset.action = () -> {
            setting.reset();

            fillTable(theme, table, setting);
        };
        reset.tooltip = "Reset";
    }

    public static class Builder extends Setting.SettingBuilder<MapIdListSetting.Builder, List<String>, MapIdListSetting> {
        private Class<? extends WTextBox.Renderer> renderer;
        private CharFilter filter;

        public Builder() {
            super(new ArrayList<>(0));
        }

        public MapIdListSetting.Builder defaultValue(String... defaults) {
            return defaultValue(defaults != null ? Arrays.asList(defaults) : new ArrayList<>());
        }

        public MapIdListSetting.Builder renderer(Class<? extends WTextBox.Renderer> renderer) {
            this.renderer = renderer;
            return this;
        }

        public MapIdListSetting.Builder filter(CharFilter filter) {
            this.filter = filter;
            return this;
        }

        @Override
        public MapIdListSetting build() {
            return new MapIdListSetting(name, description, defaultValue, onChanged, onModuleActivated, visible, renderer, filter);
        }
    }
}
