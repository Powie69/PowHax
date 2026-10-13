package powie.powhax.settings;

import meteordevelopment.meteorclient.gui.utils.CharFilter;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.settings.IVisible;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.StringListSetting;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class StringIntListSetting extends StringListSetting {
    public StringIntListSetting(String name, String description, List<String> defaultValue,
                                Consumer<List<String>> onChanged,
                                Consumer<Setting<List<String>>> onModuleActivated,
                                IVisible visible,
                                Class<? extends WTextBox.Renderer> renderer,
                                CharFilter filter) {
        super(name, description, defaultValue, onChanged, onModuleActivated, visible, renderer, filter);
    }

    // Not serialized by Meteor; the module persists this per-world through Config
    @Override
    public CompoundTag save(CompoundTag tag) {
        return tag;
    }

    @Override
    public List<String> load(CompoundTag tag) {
        return get();
    }

    public static class Builder extends SettingBuilder<Builder, List<String>, StringIntListSetting> {
        private Class<? extends WTextBox.Renderer> renderer;

        public Builder() {
            super(new ArrayList<>(0));
        }

        public Builder renderer(Class<? extends WTextBox.Renderer> renderer) {
            this.renderer = renderer;
            return this;
        }

        @Override
        public StringIntListSetting build() {
            return new StringIntListSetting(name, description, defaultValue, onChanged, onModuleActivated, visible,
                renderer, (text, c) -> Character.isDigit(c));
        }
    }
}
