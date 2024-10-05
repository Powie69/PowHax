// skidded from
// https://github.com/19MisterX98/Nether_Bedrock_Cracker/issues/24

package powie.powhax.modules;

import powie.powhax.Powhax;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.util.Util;

import java.io.*;
import java.util.LinkedHashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BedrockPrinter extends Module {
    SettingGroup sgDefault = settings.getDefaultGroup();

    private final Setting<yLevel> searchY = sgDefault.add(new EnumSetting.Builder<BedrockPrinter.yLevel>()
        .name("Y level")
        .description("get the bedrock from floor or ceiling?")
        .defaultValue(yLevel.Floor)
        .build());

    private final Setting<String> savePath = sgDefault.add(new StringSetting.Builder()
        .name("Path")
        .description("The path to write the positions in")
        .defaultValue("D:/br.txt")
        .build());

    public static ExecutorService executor = Executors.newSingleThreadExecutor();

    public BedrockPrinter() {
        super(Powhax.CATEGORY, "bedrock-printer", "Prints the position of bedrock");
    }

    LinkedHashSet<BlockPos> bedrockPos = new LinkedHashSet<>();

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList l = theme.verticalList();
        l.add(theme.label("this module is meant to be used for Nether Bedrock Cracker"));
        l.add(theme.label("https://github.com/19MisterX98/Nether_Bedrock_Cracker/"));
        WButton button = l.add(theme.button("open github repo")).widget();
        button.action = () -> Util.getOperatingSystem().open("https://github.com/19MisterX98/Nether_Bedrock_Cracker/");
        return l;
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        executor.execute(()->{
            Chunk c = event.chunk();
            for (int x = c.getPos().getStartX(); x <= c.getPos().getEndX(); x++) {
                for (int z = c.getPos().getStartZ(); z <= c.getPos().getEndZ(); z++) {
                    BlockPos sPos = new BlockPos(x, searchY.get().getValue(), z);
                    if (!c.getBlockState(sPos).getBlock().equals(Blocks.BEDROCK)) return;
                    bedrockPos.add(sPos);
                }
            }
        });
    }

    @Override
    public void onDeactivate() {
        BufferedWriter bw = null;
        try {
            File f = new File(savePath.get());
            if (f.exists() && f.canWrite()) {
                bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f)));
                for (BlockPos pos : bedrockPos) {
                    String s = pos.getX() + " " + pos.getY() + " " + pos.getZ() + " Bedrock";
                    bw.write(s);
                    bw.newLine();
                }
                bw.flush();
                bw.close();
                info("Great success!");
            }else {
                info("File not found");
            }
        } catch (Exception e) {
            info(e.getMessage());
        } finally {
            bedrockPos.clear();
            try {
                if (bw != null) {
                    bw.close();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public enum yLevel {
        Floor(4),
        Ceiling(123);

        private final int value;

        yLevel(int value) {
            this.value = value; //wtf is this?????
        }

        public int getValue() {
            return value;
        }
    }
    // skidded from gpt
}
