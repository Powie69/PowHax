package powie.powhax.modules;

import meteordevelopment.meteorclient.utils.player.Rotations;
import powie.powhax.Powhax;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import org.joml.Vector3d;

import java.util.Set;

public class SmiteAura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Entities to aim at.")
        .defaultValue(EntityType.PLAYER)
        .build()
    );

    private final Setting<SmiteAura.Command> command = sgGeneral.add(new EnumSetting.Builder<SmiteAura.Command>()
        .name("command")
        .description("what command to use for smiting")
        .defaultValue(SmiteAura.Command.thor)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("The range at which an entity can be targeted.")
        .defaultValue(50)
        .min(1)
        .sliderMin(1)
        .max(200)
        .sliderMax(200)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("Delay")
        .description("the delay before smiting in minecraft ticks")
        .defaultValue(5)
        .min(0)
        .sliderMin(0)
        .max(1200)
        .sliderMax(20)
        .build()
    );

    private final Setting<SortPriority> priority = sgGeneral.add(new EnumSetting.Builder<SortPriority>()
        .name("priority")
        .description("How to filter targets within range.")
        .defaultValue(SortPriority.LowestHealth)
        .build()
    );

    private final Vector3d vec3d1 = new Vector3d();
    private Entity target;
    private int timer = 0;

    public SmiteAura() {
        super(Powhax.CATEGORY, "Smite Aura", "Automatically strikes lighting on specified entities around you");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (timer <= delay.get()) {
            timer++;
            return;
        }
        target = TargetUtils.get(entity -> {
            if (!entity.isAlive()) return false;
            if (!PlayerUtils.isWithin(entity, range.get())) return false;
            if (!PlayerUtils.canSeeEntity(entity)) return false;
            if (!entities.get().contains(entity.getType())) return false;
            if (entity instanceof PlayerEntity) {
                return Friends.get().shouldAttack((PlayerEntity) entity);
            }

            return true;
        }, priority.get());

        timer = 0;
    }

    @EventHandler
    private void onRender(Render3DEvent event) { // this whole thing could probably be in 'onTick'. I just don't know how to pass 'event.tickDelta'
//        info(String.valueOf(target));
        if (target == null) return;
        if (target.equals(mc.player)) return;
        if (timer >= delay.get()) {
            aim(target, event.tickDelta);
            ChatUtils.sendPlayerMsg("/" + command.get());
        };
    }

    private void aim(Entity target, double delta) {
        Utils.set(vec3d1, target, delta);

        double deltaX = vec3d1.x - mc.player.getX();
        double deltaZ = vec3d1.z - mc.player.getZ();
        double deltaY = vec3d1.y - (mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()));

        // Yaw
        double angle = Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90;
        double deltaAngle;
        double toRotate;

        // Pitch
        double idk = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        double anglePitch = -Math.toDegrees(Math.atan2(deltaY, idk));

        Rotations.rotate((float) angle,(float) anglePitch);
    }

    @Override
    public String getInfoString() {
        return EntityUtils.getName(target);
    }

    public enum Command {
        elightning, shock, eshock, smite, esmite, strike, estrike, thor, ethor
    }
}
