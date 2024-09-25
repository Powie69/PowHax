package powie.powhax.modules;

import powie.powhax.Powhax;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.screen.slot.SlotActionType;

public class AutoSell extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Integer> SlotDelay = sgGeneral.add(new IntSetting.Builder()
        .name("Slot Click Delay")
        .description("The delay before clicking the slot in minecraft ticks")
        .defaultValue(5)
        .min(0)
        .sliderMin(0)
        .max(2400)
        .sliderMax(2400)
        .build()
    );

    public AutoSell() {
        super(Powhax.CATEGORY, "auto-sell", "Automatically sells a stack in Tradeview");
    }

    int timer = 0;

    @EventHandler
    private void onTick(TickEvent.Post event) {
        MinecraftClient client = MinecraftClient.getInstance(); // this is prob inefficient af.
        ClientPlayerEntity player = client.player;
        if (client.currentScreen == null) return;
        if (!client.currentScreen.getTitle().getString().equals("Tradeview") || player.currentScreenHandler.slots.size() != 54) return; // 63 // 54
        if (player == null || player.currentScreenHandler == null) return;
        if (timer <= SlotDelay.get()) {
            timer++;
            return;
        }

        client.interactionManager.clickSlot(
            player.currentScreenHandler.syncId,
            8,
            0,
            SlotActionType.PICKUP,
            player
        );
        timer = 0;
    }
}
