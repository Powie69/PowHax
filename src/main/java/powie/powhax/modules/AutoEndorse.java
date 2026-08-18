package powie.powhax.modules;

import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.mixin.AbstractSignEditScreenAccessor;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerEntity;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import powie.powhax.Powhax;

public class AutoEndorse extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFront = settings.createGroup("Text");

    private final Setting<Target> targets = sgGeneral.add(new EnumSetting.Builder<Target>()
        .name("targets")
        .description("Who to target")
        .defaultValue(Target.Everyone)
        .build()
    );

    //  Distance to the player. Not the placed sign btw.
    private final Setting<SortPriority> targetPriority = sgGeneral.add(new EnumSetting.Builder<SortPriority>()
        .name("target-priority")
        .description("How to prioritize targets")
        .defaultValue(SortPriority.ClosestAngle)
        .build()
    );

    private final Setting<Double> targetRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("target-range")
        .description("The maximum range the player can be to be targeted")
        .defaultValue(10)
        .sliderMax(20)
        .build()
    );

    // lines

    private final Setting<String> firstLine = sgFront.add(new StringSetting.Builder()
        .name("first-line-front")
        .description("The first line of the sign")
        .defaultValue("I Love using")
        .build()
    );

    private final Setting<String> secondLine = sgFront.add(new StringSetting.Builder()
        .name("second-line-front")
        .description("The second line of the sign")
        .defaultValue("Powhax it's")
        .build()
    );

    private final Setting<String> thirdLine = sgFront.add(new StringSetting.Builder()
        .name("third-line-front")
        .description("The third line of the sign")
        .defaultValue("the best")
        .build()
    );

    public AutoEndorse() {
        super(Powhax.CATEGORY, "Auto-Endorse", "Places a sign with specified text. The last line will contain the name of the player nearest to you");
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList l = theme.verticalList();
        l.add(theme.label("Note: The 4th line will be the Target's name if the other lines are full"));
        return l;
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (!(event.screen instanceof AbstractSignEditScreen)) return;

        Entity target = getTarget(targetRange.get(), targetPriority.get());
        if (target == null) {
            warning("no target found");
            return;
        }

        SignBlockEntity sign = ((AbstractSignEditScreenAccessor) event.screen).meteor$getSign();

        String[] lines = composeLines(target.getName().getString());

        mc.player.connection.send(new ServerboundSignUpdatePacket(
            sign.getBlockPos(),
            true,
            lines[0],
            lines[1],
            lines[2],
            lines[3]
        ));

        event.cancel();
    }

    private String[] composeLines(String name) {
        String[] lines = {firstLine.get(), secondLine.get(), thirdLine.get(), ""};

        for (int i = 3; i >= 0; i--) {
            if (i == 0) {
                lines[i] = "-" + name;
                break;
            }

            if (!lines[i - 1].isEmpty()) {
                lines[i] = "-" + name;
                break;
            }
        }

        return lines;
    }

    private Player getTarget(double range, SortPriority priority) {
        if (!Utils.canUpdate()) return null;
        return (Player) TargetUtils.get(entity -> {
            if (!(entity instanceof Player) || entity == mc.player) return false;
            if (((Player) entity).isDeadOrDying() || ((Player) entity).getHealth() <= 0) return false;
            if (!PlayerUtils.isWithin(entity, range)) return false;
            if (targets.get() == Target.Friends && !Friends.get().isFriend((Player) entity)) return false;
            if (targets.get() == Target.NonFriends && Friends.get().isFriend((Player) entity)) return false;
            return entity instanceof FakePlayerEntity;
        }, priority);
    }

    public enum Target {
        Everyone,
        Friends,
        NonFriends
    }
}
