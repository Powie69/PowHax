package powie.powhax.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.Target;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import powie.powhax.Powhax;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SmiteAura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("Targeting");

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("Delay")
        .description("the delay before smiting in minecraft ticks")
        .defaultValue(15)
        .min(0)
        .sliderMin(10)
        .max(1200)
        .sliderMax(100)
        .build()
    );

    private final Setting<SmiteAura.Command> command = sgGeneral.add(new EnumSetting.Builder<SmiteAura.Command>()
        .name("command")
        .description("what command to use for smiting")
        .defaultValue(Command.thor)
        .build()
    );

    private final Setting<String> customCommand = sgGeneral.add(new StringSetting.Builder()
        .name("custom-command")
        .description("The Command to use")
        .defaultValue("/beezooka")
        .visible(() -> command.get() == Command.custom)
        .build()
    );

    private final Setting<Boolean> pauseOnLag = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-on-lag")
        .description("Does not smite if the server is lagging.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseOnUse = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-on-use")
        .description("Does not smite while using an item.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> pauseOnBreakingBlock = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-on-breaking-block")
        .description("Does not smite while breaking a block.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> pauseOnMove = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-on-move")
        .description("Will not smite while moving.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> pauseOnMoveSpeedThreshold = sgGeneral.add(new DoubleSetting.Builder()
        .name("pause-on-move-speed-threshold")
        .description("If this value is higher than the player's speed, it won't smite.")
        .defaultValue(2)
        .min(0)
        .sliderMin(0)
        .sliderMax(40)
        .visible(pauseOnMove::get)
        .build()
    );

    // Targeting

    private final Setting<Set<EntityType<?>>> entities = sgTargeting.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Entities to attack.")
        .onlyAttackable()
        .defaultValue(EntityTypes.PLAYER)
        .build()
    );

    private final Setting<SortPriority> priority = sgTargeting.add(new EnumSetting.Builder<SortPriority>()
        .name("priority")
        .description("How to filter targets within range.")
        .defaultValue(SortPriority.ClosestAngle)
        .build()
    );

    private final Setting<Double> range = sgTargeting.add(new DoubleSetting.Builder()
        .name("range")
        .description("The maximum range the entity can be to attack it.")
        .defaultValue(80)
        .min(1)
        .sliderMin(1)
        .max(80)
        .sliderMax(80)
        .build()
    );

    private final Setting<EntityAge> mobAgeFilter = sgTargeting.add(new EnumSetting.Builder<EntityAge>()
        .name("mob-age-filter")
        .description("Determines the age of the mobs to target (baby, adult, or both).")
        .defaultValue(EntityAge.Adult)
        .build()
    );

    private final Setting<Boolean> ignoreNamed = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-named")
        .description("Whether or not to attack mobs with a name.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> ignorePassive = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-passive")
        .description("Will only attack sometimes passive mobs if they are targeting you.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreTamed = sgTargeting.add(new BoolSetting.Builder()
        .name("ignore-tamed")
        .description("Will avoid attacking mobs you tamed.")
        .defaultValue(false)
        .build()
    );

    private final List<Entity> targets = new ArrayList<>();
    private final Vec3 rayStart = new Vec3(0, 0, 0);
    private final Vec3 rayEnd = new Vec3(0, 0, 0);
    private int timer = 0;
    public boolean attacking;
    private Entity target;

    public SmiteAura() {
        super(Powhax.CATEGORY, "smite-aura", "Automatically strikes lighting on specified entities around you");
    }

    @Override
    public void onDeactivate() {
        targets.clear();
        attacking = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (timer <= delay.get()) {
            timer++;
            return;
        }
        if (!mc.player.isAlive() || PlayerUtils.getGameMode() == GameType.SPECTATOR) return;
        if (pauseOnUse.get() && mc.player.isUsingItem()) return;
        if (pauseOnBreakingBlock.get() && mc.gameMode.isDestroying()) return;
        if (pauseOnMove.get() && Utils.getPlayerSpeed().horizontalDistance() >= pauseOnMoveSpeedThreshold.get()) return;
        if (TickRate.INSTANCE.getTimeSinceLastTick() >= 1f && pauseOnLag.get()) return;

        targets.clear();
        TargetUtils.getList(targets, this::entityCheck, priority.get(), 1);

        if (targets.isEmpty()) {
            attacking = false;
            target = null;
            return;
        }

        Entity primary = targets.getFirst();
        target = primary;

        attacking = true;
        targets.forEach(this::attack);

        timer = 0;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (target == null) return;
        if (pauseOnMove.get() && Utils.getPlayerSpeed().horizontalDistance() >= pauseOnMoveSpeedThreshold.get()) return;
        Rotations.rotate(Rotations.getYaw(target), Rotations.getPitch(target, Target.Feet));
    }

    private void attack(Entity target) {
        if (command.get() != Command.custom) {
            ChatUtils.sendPlayerMsg("/" + command.get());
        } else {
            ChatUtils.sendPlayerMsg(customCommand.get());
        }
    }

    private boolean entityCheck(Entity entity) {
        if (entity.equals(mc.player) || entity.equals(mc.getCameraEntity())) return false;
        if ((entity instanceof LivingEntity livingEntity && livingEntity.isDeadOrDying()) || !entity.isAlive() || !entity.onGround())
            return false;

        AABB hitbox = entity.getBoundingBox();
        if (!PlayerUtils.isWithin(
            Mth.clamp(mc.player.getX(), hitbox.minX, hitbox.maxX),
            Mth.clamp(mc.player.getY(), hitbox.minY, hitbox.maxY),
            Mth.clamp(mc.player.getZ(), hitbox.minZ, hitbox.maxZ),
            range.get()
        )) return false;

        if (!entities.get().contains(entity.getType())) return false;
        if (!canSeeEntityFeet(entity)) return false;
        if (ignoreNamed.get() && entity.hasCustomName()) return false;
        if (ignoreTamed.get()) {
            if (entity instanceof OwnableEntity tameable
                && tameable.getOwner() != null
                && tameable.getOwner().equals(mc.player)
            ) return false;
        }
        if (ignorePassive.get()) {
            if (entity instanceof EnderMan enderman && !enderman.isAngry()) return false;
            if (entity instanceof ZombifiedPiglin piglin && !piglin.isAggressive()) return false;
            if (entity instanceof Wolf wolf && !wolf.isAggressive()) return false;
        }
        if (entity instanceof Player player) {
            if (player.isCreative()) return false;
            if (!Friends.get().shouldAttack(player)) return false;
        }
        if (entity instanceof Animal animal) {
            return switch (mobAgeFilter.get()) {
                case Baby -> animal.isBaby();
                case Adult -> !animal.isBaby();
                case Both -> true;
            };
        }
        return true;
    }

    public boolean canSeeEntityFeet(Entity entity) {
        ((IVec3) rayStart).meteor$set(mc.player.getX(), mc.player.getY() + mc.player.getEyeHeight(), mc.player.getZ());
        ((IVec3) rayEnd).meteor$set(entity.getX(), entity.getY(), entity.getZ());

        return mc.level.clip(new ClipContext(rayStart, rayEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player)).getType() == HitResult.Type.MISS;
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

    private enum EntityAge {
        Baby,
        Adult,
        Both
    }

    private enum Command {
        lightning, shock, smite, strike, thor, custom
    }
}
