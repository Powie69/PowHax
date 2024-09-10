package powie.powhax.modules;

import powie.powhax.Powhax;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.Target;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.*;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BlazeFarm extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("Targeting");
    private final SettingGroup sgTiming = settings.createGroup("Timing");

    // General

    private final Setting<Weapon> weapon = sgGeneral.add(new EnumSetting.Builder<Weapon>()
        .name("weapon")
        .description("Only attacks an entity when a specified weapon is in your hand.")
        .defaultValue(Weapon.Sword)
        .build()
    );

    private final Setting<RotationMode> rotation = sgGeneral.add(new EnumSetting.Builder<RotationMode>()
        .name("rotate")
        .description("Determines when you should rotate towards the target.")
        .defaultValue(RotationMode.OnHit)
        .build()
    );

    // Targeting

    private final Setting<SortPriority> priority = sgGeneral.add(new EnumSetting.Builder<SortPriority>()
        .name("priority")
        .description("How to filter targets within range.")
        .defaultValue(SortPriority.LowestDistance)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("The maximum range the entity can be to attack it.")
        .defaultValue(4.5)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<Double> wallsRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("walls-range")
        .description("The maximum range the entity can be attacked through walls.")
        .defaultValue(3.5)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<Boolean> pauseOnLag = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-on-lag")
        .description("Pauses if the server is lagging.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> tpsSync = sgGeneral.add(new BoolSetting.Builder()
        .name("TPS-sync")
        .description("Tries to sync attack delay with the server's TPS.")
        .defaultValue(true)
        .build()
    );

    private final List<Entity> targets = new ArrayList<>();
    private int switchTimer, hitTimer;
    private boolean wasPathing = false;
    public boolean attacking;

    public BlazeFarm() {
        super(Powhax.CATEGORY, "Blaze Farm", "Attacks specified entities around you.");
    }

    @Override
    public void onDeactivate() {
        targets.clear();
        attacking = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!mc.player.isAlive() || PlayerUtils.getGameMode() == GameMode.SPECTATOR) return;
        if (TickRate.INSTANCE.getTimeSinceLastTick() >= 1f && pauseOnLag.get()) return;


        targets.clear();
        TargetUtils.getList(targets, this::entityCheck, priority.get(), 1);

        if (targets.isEmpty()) {
            attacking = false;
            if (wasPathing) {
                PathManagers.get().resume();
                wasPathing = false;
            }
            return;
        }

        Entity primary = targets.getFirst();


        if (!itemInHand()) return;

        attacking = true;
        if (rotation.get() == RotationMode.Always) Rotations.rotate(Rotations.getYaw(primary), Rotations.getPitch(primary, Target.Body));

        if (delayCheck()) targets.forEach(this::attack);
    }

    private boolean entityCheck(Entity entity) {
        if (entity.equals(mc.player) || entity.equals(mc.cameraEntity)) return false;
        if ((entity instanceof LivingEntity livingEntity && livingEntity.isDead()) || !entity.isAlive()) return false;

        Box hitbox = entity.getBoundingBox();
        if (!PlayerUtils.isWithin(
            MathHelper.clamp(mc.player.getX(), hitbox.minX, hitbox.maxX),
            MathHelper.clamp(mc.player.getY(), hitbox.minY, hitbox.maxY),
            MathHelper.clamp(mc.player.getZ(), hitbox.minZ, hitbox.maxZ),
            range.get()
        )) return false;
        if (entity.getType() != EntityType.BLAZE) return false;
        if (!PlayerUtils.canSeeEntity(entity) && !PlayerUtils.isWithin(entity, wallsRange.get())) return false;
        return true;
    }

    private boolean delayCheck() {
        if (switchTimer > 0) {
            switchTimer--;
            return false;
        }

        float delay = 0.5f;
        if (tpsSync.get()) delay /= (TickRate.INSTANCE.getTickRate() / 20);


        return mc.player.getAttackCooldownProgress(delay) >= 1;
    }

    private void attack(Entity target) {
        if (rotation.get() == RotationMode.OnHit) Rotations.rotate(Rotations.getYaw(target), Rotations.getPitch(target, Target.Body));

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);

        hitTimer = 0;
    }

    private boolean itemInHand() {

        return switch (weapon.get()) {
            case Axe -> mc.player.getMainHandStack().getItem() instanceof AxeItem;
            case Sword -> mc.player.getMainHandStack().getItem() instanceof SwordItem;
            case Both -> mc.player.getMainHandStack().getItem() instanceof AxeItem || mc.player.getMainHandStack().getItem() instanceof SwordItem;
            default -> true;
        };
    }

    public Entity getTarget() {
        if (!targets.isEmpty()) return targets.getFirst();
        return null;
    }

    @Override
    public String getInfoString() {
        if (!targets.isEmpty()) return EntityUtils.getName(getTarget());
        return null;
    }

    public enum Weapon {
        Sword,
        Axe,
        Both,
        Any
    }

    public enum RotationMode {
        Always,
        OnHit,
        None
    }

}
