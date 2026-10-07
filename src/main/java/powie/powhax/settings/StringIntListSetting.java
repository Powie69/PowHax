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
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Same shape as StringListSetting, but intentionally does NOT participate in
 * Meteor's own save()/load() to modules.nbt — persistence for this setting is
 * handled externally (per-world), so save()/load() are no-ops to avoid the two
 * mechanisms fighting over the value.
 */
public class StringIntListSetting extends Setting<List<String>> {
    public final Class<? extends WTextBox.Renderer> renderer;
    public final CharFilter filter;

    public StringIntListSetting(String name, String description, List<String> defaultValue, Consumer<List<String>> onChanged, Consumer<Setting<List<String>>> onModuleActivated, IVisible visible, Class<? extends WTextBox.Renderer> renderer, CharFilter filter) {
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

    // Intentionally not persisted via Meteor's modules.nbt — Config owns persistence for this setting.
    @Override
    public CompoundTag save(CompoundTag tag) {
        return tag;
    }

    @Override
    public List<String> load(CompoundTag tag) {
        return get();
    }

    @Override
    public void resetImpl() {
        value = new ArrayList<>(defaultValue);
    }

    public static void fillTable(GuiTheme theme, WTable table, StringIntListSetting setting) {
        table.clear();

        ArrayList<String> strings = new ArrayList<>(setting.get());
        CharFilter filter = setting.filter == null ? (text, c) -> true : setting.filter;

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

    public static class Builder extends SettingBuilder<Builder, List<String>, StringIntListSetting> {
        private Class<? extends WTextBox.Renderer> renderer;
        private CharFilter filter;

        public Builder() {
            super(new ArrayList<>(0));
        }

        public Builder renderer(Class<? extends WTextBox.Renderer> renderer) {
            this.renderer = renderer;
            return this;
        }

        public Builder filter(CharFilter filter) {
            this.filter = filter;
            return this;
        }

        @Override
        public StringIntListSetting build() {
            return new StringIntListSetting(name, description, defaultValue, onChanged, onModuleActivated, visible, renderer, filter);
        }
    }
}
