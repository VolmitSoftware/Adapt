package art.arcane.adapt.clientui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Gameplay input at the key-mapping layer. The hidden client never grabs the pointer, so mouse callbacks only grab and
 * return; held actions here set the mappings directly every tick and let continueAttack run without a grabbed pointer.
 */
public final class Gameplay {
    private static final Map<Action, Long> HELD = new EnumMap<>(Action.class);
    private static long tick;
    private static JumpSequence jumps;
    private static JsonArray jumpTrace = new JsonArray();
    private static JsonArray entityTrace = new JsonArray();
    private static JsonArray effectTrace = new JsonArray();
    private static long entityTraceEnd;
    private static double entityTraceRadius;

    private Gameplay() {
    }

    enum Action {
        ATTACK, USE, FORWARD, BACK, LEFT, RIGHT, JUMP, SNEAK, SPRINT;

        KeyMapping mapping(Options options) {
            return switch (this) {
                case ATTACK -> options.keyAttack;
                case USE -> options.keyUse;
                case FORWARD -> options.keyUp;
                case BACK -> options.keyDown;
                case LEFT -> options.keyLeft;
                case RIGHT -> options.keyRight;
                case JUMP -> options.keyJump;
                case SNEAK -> options.keyShift;
                case SPRINT -> options.keySprint;
            };
        }

        static Action of(String name) {
            try {
                return valueOf(name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException failure) {
                throw new IllegalArgumentException("action must be attack, use, forward, back, left, right, jump, sneak or sprint");
            }
        }
    }

    static void hold(Minecraft client, String name, boolean down, long ticks) {
        Action action = Action.of(name);
        KeyMapping mapping = action.mapping(client.options);
        if (down) {
            HELD.put(action, ticks > 0 ? tick + ticks : Long.MAX_VALUE);
            mapping.setDown(true);
        } else {
            HELD.remove(action);
            mapping.setDown(false);
        }
    }

    static void releaseAll(Minecraft client) {
        for (Action action : HELD.keySet()) {
            action.mapping(client.options).setDown(false);
        }
        HELD.clear();
    }

    /**
     * Presses jump for {@code tap} ticks at each offset of {@code presses} (client ticks after the call, the first
     * normally 0) and records every tick until the player lands after the last press, at most 80 ticks.
     */
    static void jumpSequence(long[] presses, long tap) {
        jumps = new JumpSequence(presses, Math.max(1L, tap));
        jumpTrace = new JsonArray();
    }

    /**
     * Records every client tick for {@code ticks} ticks the arrows and living entities within {@code radius} blocks of
     * the player as the client simulates them: position, motion and hurt time.
     */
    static void traceEntities(long ticks, double radius) {
        entityTrace = new JsonArray();
        effectTrace = new JsonArray();
        entityTraceEnd = tick + Math.max(1L, ticks);
        entityTraceRadius = radius;
    }

    static void tick(Minecraft client) {
        tick++;
        if (jumps != null) {
            jumps.tick(client);
        }
        if (tick <= entityTraceEnd) {
            sampleEntities(client);
            if (client.player != null) {
                effectTrace.add(Effects.sample(client.player, tick));
            }
        }
        if (HELD.isEmpty()) {
            return;
        }
        HELD.entrySet().removeIf(entry -> {
            KeyMapping mapping = entry.getKey().mapping(client.options);
            if (tick >= entry.getValue()) {
                mapping.setDown(false);
                return true;
            }
            mapping.setDown(true);
            return false;
        });
    }

    public static boolean attacking() {
        return HELD.containsKey(Action.ATTACK);
    }

    static void look(Minecraft client, float yaw, float pitch) {
        LocalPlayer player = client.player;
        if (player == null) {
            throw new IllegalStateException("Not connected to a server");
        }
        float clamped = Math.max(-90.0F, Math.min(90.0F, pitch));
        player.setYRot(yaw);
        player.setXRot(clamped);
        player.yRotO = yaw;
        player.xRotO = clamped;
    }

    static void describe(Minecraft client, JsonObject player) {
        if (client.player != null) {
            player.addProperty("yaw", client.player.getYRot());
            player.addProperty("pitch", client.player.getXRot());
        }
        JsonArray held = new JsonArray();
        for (Action action : HELD.keySet()) {
            held.add(action.name().toLowerCase(Locale.ROOT));
        }
        player.add("held", held);
        player.add("jumpTrace", jumpTrace);
        player.add("entityTrace", entityTrace);
        player.add("effectTrace", effectTrace);
        player.addProperty("entityTraceRunning", tick <= entityTraceEnd);
        player.addProperty("jumpSequenceRunning", jumps != null);
        HitResult hit = client.hitResult;
        JsonObject target = new JsonObject();
        target.addProperty("type", hit == null ? "NONE" : hit.getType().name());
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = block.getBlockPos();
            target.addProperty("x", pos.getX());
            target.addProperty("y", pos.getY());
            target.addProperty("z", pos.getZ());
            if (client.level != null) {
                target.addProperty("block", client.level.getBlockState(pos).getBlock().getDescriptionId());
            }
        } else if (hit instanceof EntityHitResult entity) {
            target.addProperty("entity", entity.getEntity().getType().getDescriptionId());
        }
        player.add("target", target);
    }

    private static void sampleEntities(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            return;
        }
        double limit = entityTraceRadius * entityTraceRadius;
        for (Entity entity : client.level.entitiesForRendering()) {
            boolean arrow = entity.getType() == EntityType.ARROW;
            if ((!arrow && !(entity instanceof LivingEntity)) || entity == player || entity.distanceToSqr(player) > limit) {
                continue;
            }
            Vec3 motion = entity.getDeltaMovement();
            JsonObject entry = new JsonObject();
            entry.addProperty("t", tick);
            entry.addProperty("id", entity.getId());
            entry.addProperty("arrow", arrow);
            entry.addProperty("x", entity.getX());
            entry.addProperty("y", entity.getY());
            entry.addProperty("z", entity.getZ());
            entry.addProperty("vx", motion.x);
            entry.addProperty("vy", motion.y);
            entry.addProperty("vz", motion.z);
            entry.addProperty("glowing", entity.isCurrentlyGlowing());
            if (entity instanceof LivingEntity living) {
                entry.addProperty("hurt", living.hurtTime);
                entry.addProperty("health", living.getHealth());
            }
            entityTrace.add(entry);
        }
    }

    private static final class JumpSequence {
        private final long[] presses;
        private final long tap;
        private long index;
        private boolean wasAirborne;

        private JumpSequence(long[] presses, long tap) {
            this.presses = presses;
            this.tap = tap;
        }

        private void tick(Minecraft client) {
            LocalPlayer player = client.player;
            if (player == null) {
                jumps = null;
                return;
            }
            boolean down = false;
            long last = 0L;
            for (long press : presses) {
                down |= index >= press && index < press + tap;
                last = Math.max(last, press + tap);
            }
            client.options.keyJump.setDown(down);
            JsonObject entry = new JsonObject();
            entry.addProperty("i", index);
            entry.addProperty("jump", down);
            entry.addProperty("ground", player.onGround());
            entry.addProperty("x", player.getX());
            entry.addProperty("y", player.getY());
            entry.addProperty("z", player.getZ());
            entry.addProperty("mayfly", player.getAbilities().mayfly);
            entry.addProperty("flying", player.getAbilities().flying);
            jumpTrace.add(entry);
            wasAirborne |= !player.onGround();
            index++;
            if (index > 80 || index > last && wasAirborne && player.onGround()) {
                client.options.keyJump.setDown(false);
                jumps = null;
            }
        }
    }
}
