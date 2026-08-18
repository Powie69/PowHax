package powie.powhax.modules;

import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.FileSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import powie.powhax.Powhax;
import powie.powhax.utils.Config;

import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BedrockSeedfinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<YLevel> searchY = sgGeneral.add(new EnumSetting.Builder<YLevel>()
        .name("y-level")
        .description("get the bedrock from floor or ceiling?")
        .defaultValue(YLevel.Floor)
        .build()
    );

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("""
            - powhax: Write to powhax's predefined folder
            - custom: select a file to which the coordinates will be written
            """)
        .defaultValue(Mode.powhax)
        .build()
    );

    private final Setting<WriteMode> writeMode = sgGeneral.add(new EnumSetting.Builder<WriteMode>()
        .name("Write mode")
        .description("""
            - overwrite: Overwrites ALL of the contents of the file.
            - append: Append to the file
            """)
        .defaultValue(WriteMode.overwrite)
        .visible(() -> mode.get() == Mode.custom)
        .build()
    );

    private final Setting<File> file = sgGeneral.add(new FileSetting.Builder()
        .name("save-file")
        .description("Output file for bedrock coordinates data")
        .visible(() -> mode.get() == Mode.custom)
        .onChanged(this::isInvalidFile)
        .build()
    );

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private volatile BufferedWriter writer;
    private int lines;
    private File outputFile;

    /**
     * Thanks to <a href="https://github.com/Nippaku-Zanmu/">Nippaku Zanmu</a> for initial code snippet
     */
    public BedrockSeedfinder() {
        super(Powhax.CATEGORY,
            "bedrock-seedfinder",
            "Writes the position of bedrock in the nether in a file. meant for seed cracking.",
            "Seedcracker");
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList l = theme.verticalList();
        l.add(theme.label("This module is meant to be used for Nether Bedrock Cracker"));
        l.add(theme.label("https://github.com/19MisterX98/Nether_Bedrock_Cracker/"));
        WButton button = l.add(theme.button("open github repo")).widget();
        button.action = () -> Util.getPlatform().openUri("https://github.com/19MisterX98/Nether_Bedrock_Cracker/");
        return l;
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        // implementation note is only a suggestion right?
        executor.execute(() -> {
            ChunkAccess c = event.chunk();
            int y = searchY.get().getValue();
            BlockPos.MutableBlockPos sPos = new BlockPos.MutableBlockPos();
            for (int x = c.getPos().getMinBlockX(); x <= c.getPos().getMaxBlockX(); x++) {
                for (int z = c.getPos().getMinBlockZ(); z <= c.getPos().getMaxBlockZ(); z++) {
                    sPos.set(x, y, z);
                    if (!c.getBlockState(sPos).is(Blocks.BEDROCK)) continue;
                    try {
                        writer.write(x + " " + y + " " + z + " Bedrock");
                        writer.newLine();
                        lines++;
                    } catch (IOException e) {
                        info(e.getMessage());
                    }
                }
            }
        });
    }

    @Override
    public void onActivate() {
        if (mode.get() == Mode.powhax) {
            outputFile = Config.newBedrockFile().toFile();
        } else {
            if (isInvalidFile(file.get())) {
                toggle();
                return;
            }
            outputFile = file.get();
        }
        if (!mc.level.dimensionTypeRegistration().is(BuiltinDimensionTypes.NETHER))
            warning("This module is only meant to be used in the nether");
        try {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(
                outputFile,
                mode.get() == Mode.custom && writeMode.get() == WriteMode.append
            )));
        } catch (FileNotFoundException e) {
            error("Failed to open file for writing: " + e.getMessage());
            toggle();
        }
    }

    @Override
    public void onDeactivate() {
        executor.execute(() -> {
            try {
                if (writer == null) return;
                writer.flush();
                writer.close();
                MutableComponent message = Component.literal(String.format("Saved %d bedrock coordinates to: ", lines));
                message.append(Component.literal(outputFile.getName())
                    .withStyle(style -> style
                        .applyFormat(ChatFormatting.YELLOW)
                        .withClickEvent(new ClickEvent.OpenFile(outputFile))
                    )
                );
                info(message);
            } catch (IOException e) {
                info(e.getMessage());
            } finally {
                lines = 0;
            }
        });
    }

    @Override
    public String getInfoString() {
        if (outputFile == null) return null;
        return outputFile.getName() + " | " + lines;
    }

    private boolean isInvalidFile(File file) {
        if (file == null) return true;
        if (!file.exists()) {
            error("File does not exist");
            return true;
        }
        if (!file.isFile()) {
            error("The specified path is a directory, not a file");
            return true;
        }
        if (!file.canWrite()) {
            error("File is not writable");
            return true;
        }
        return false;
    }

    private enum Mode {
        powhax,
        custom
    }

    private enum WriteMode {
        overwrite,
        append
    }

    private enum YLevel {
        Floor(4),
        Ceiling(123);

        private final int value;

        YLevel(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }
}
