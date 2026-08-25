package powie.powhax.modules;

import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import powie.powhax.Powhax;

public class FlySpeed extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("Speed")
        .description("how fast u wanna go?")
        .defaultValue(0.250)
        .sliderMax(3)
        .min(0)
        .onChanged((v) -> {
            if (mc.player == null) return;
            setSpeed(v.floatValue());
        })
        .build()
    );

    public FlySpeed() {
        super(Powhax.CATEGORY, "Fly-Speed", "Sets the flight speed for abilities");
    }

    @Override
    public void onActivate() {
        setSpeed(speed.get().floatValue());
    }

    /**
     * 0.05 is the default speed
     * @see net.minecraft.world.entity.player.Abilities
     */
    @Override
    public void onDeactivate() {
        setSpeed(0.05f);
    }

    private void setSpeed(float speed) {
        mc.player.getAbilities().setFlyingSpeed(speed);
    }
}
