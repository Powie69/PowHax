package powie.powhax.modules;

import meteordevelopment.meteorclient.settings.SettingGroup;
import powie.powhax.Powhax;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;

import java.util.ArrayList;
import java.util.List;

public class DimensionGodMode extends Module {
    private List<TeleportConfirmC2SPacket> packets;


    // Constructor

    public DimensionGodMode() {
        super(Powhax.CATEGORY, "Dimension God Mode", "Makes you invincible after changing Dimensions");
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList l = theme.verticalList();
        l.add(theme.label("When turned on, most of the things that you're doing is client side"));
        l.add(theme.label("Once you have turned off the module. The client SHOULD sync back with the server"));
        return l;
    }

    @Override
    public void onActivate() {
        this.packets = new ArrayList<>();
    }

    @Override
    public void onDeactivate() {
        if (!this.packets.isEmpty()) {
            mc.getNetworkHandler().sendPacket(this.packets.get(this.packets.size() - 1));
        }
    }

    // Packet Event

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (event.packet instanceof TeleportConfirmC2SPacket packet) {
            this.packets.add(packet);
            event.setCancelled(true);
        }
    }
}
