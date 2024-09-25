package powie.powhax.modules;

import powie.powhax.Powhax;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;

public class AutoLogin extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> command = sgGeneral.add(new StringSetting.Builder()
        .name("command")
        .description("The Command to execute")
        .defaultValue("/l")
        .build());

    private final Setting<String> password = sgGeneral.add(new StringSetting.Builder()
        .name("password")
        .description("The password to log in with")
        .defaultValue("12345")
        .build());


    public AutoLogin() {
        super(Powhax.CATEGORY, "auto-login", "Runs /login command when you join a server.");
        runInMainMenu = true;
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        ChatUtils.sendPlayerMsg(command.get() + " " + password.get());
    }

}
