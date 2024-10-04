package powie.powhax.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.command.CommandSource;
import net.minecraft.util.math.BlockPos;

public class Coords extends Command {
    public Coords() {
        super("coordinates", "Gets your coordinates", "coords", "position", "pos");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> copyPos());
        builder.then(literal("copy").executes(context -> copyPos()));
        builder.then(literal("print").executes(context -> {
            info(getPos());
            return SINGLE_SUCCESS;
        }));
        builder.then(literal("share-in-chat").executes(context -> {
            ChatUtils.sendPlayerMsg(getPos());
            return SINGLE_SUCCESS;
        }));
    }

    private String getPos() {
        BlockPos pos = mc.player.getBlockPos();
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private int copyPos() {
        mc.keyboard.setClipboard(getPos());
        info("Coordinates were copied to your clipboard");
        return SINGLE_SUCCESS;
    }
}
