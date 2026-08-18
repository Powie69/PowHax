package powie.powhax.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.utils.StarscriptTextBoxRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.MeteorStarscript;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import powie.powhax.Powhax;

import java.util.*;

public class DeathCommands extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Boolean> SupportStarscript = sgGeneral.add(new BoolSetting.Builder()
        .name("Support Starscript")
        .description("Makes Starscript work with death commands")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> toggleAfterDeath = sgGeneral.add(new BoolSetting.Builder()
        .name("Toggle After Death")
        .description("Turns off the module after its activated.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> StartDelay = sgGeneral.add(new IntSetting.Builder()
        .name("Start Delay")
        .description("Tick delay before running commands")
        .defaultValue(5)
        .min(0)
        .sliderMax(600)
        .build()
    );

    private final Setting<Integer> IntervalDelay = sgGeneral.add(new IntSetting.Builder()
        .name("Interval Delay")
        .description("Tick delay for each command")
        .defaultValue(5)
        .min(0)
        .sliderMax(600)
        .build()
    );

    private final Setting<List<String>> commands = sgGeneral.add(new StringListSetting.Builder()
        .name("commands")
        .description("List of commands to be sent.")
        .defaultValue(Arrays.asList(
            "oh no i died :(",
            "/kit default"
        ))
        .visible(() -> !SupportStarscript.get())
        .build()
    );

    private final Setting<List<String>> commandsStarscript = sgGeneral.add(new StringListSetting.Builder()
        .name("commands")
        .description("List of commands to be sent.")
        .defaultValue(Arrays.asList(
            "aw man i died :(",
            "and i respawned at {player.pos}"
        ))
        .renderer(StarscriptTextBoxRenderer.class)
        .visible(SupportStarscript::get)
        .build()
    );

    public DeathCommands() {
        super(Powhax.CATEGORY, "death-commands", "Run commands when you die.");
    }

    private final Queue<String> commandQueue = new ArrayDeque<>();

    boolean firstCommand = true;
    boolean running;
    int startDelay, intervalDelay;

    @Override
    public void onActivate() {
        if (SupportStarscript.get() && commandsStarscript.get().isEmpty()) {
            error("Theres no commands to run.");
            toggle();
            return;
        }
        if (!SupportStarscript.get() && commands.get().isEmpty()) {
            error("Theres no commands to run.");
            toggle();
        }
    }

    @Override
    public void onDeactivate() {
        commandQueue.clear();
        running = false;
        startDelay = 0;
        intervalDelay = 0;
        firstCommand = true;
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (!(event.packet instanceof net.minecraft.network.protocol.game.ClientboundRespawnPacket) || running) return;

        commandQueue.clear();

        if (SupportStarscript.get()) {
            commandQueue.addAll(commandsStarscript.get());
        } else {
            commandQueue.addAll(commands.get());
        }
        if (commandQueue.isEmpty()) {
            error("Theres no commands to run.");
            toggle();
            return;
        }
        running = true;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!running) return;
        if (startDelay <= StartDelay.get()) {
            startDelay++;
            return;
        }
        if (intervalDelay <= IntervalDelay.get() && !firstCommand) {
            intervalDelay++;
            return;
        }

        String command = commandQueue.poll();
        if (SupportStarscript.get()) {
            ChatUtils.sendPlayerMsg(MeteorStarscript.run(MeteorStarscript.compile(command)));
        } else {
            ChatUtils.sendPlayerMsg(command);
        }

        firstCommand = false;
        intervalDelay = 0;

        if (!commandQueue.isEmpty()) return;
        if (toggleAfterDeath.get()) toggle();
        firstCommand = true;
        running = false;
        startDelay = 0;
    }
}
