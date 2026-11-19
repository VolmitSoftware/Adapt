package art.arcane.adapt.mod.event;

import art.arcane.adapt.mod.platform.ModConversions;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.mod.platform.entity.ModItemEntityRef;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.block.BlockExplodeEvent;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.entity.EntityExplodeEvent;
import art.arcane.adapt.platform.event.entity.EntityPickupItemEvent;
import art.arcane.adapt.platform.event.entity.EntityShootBowEvent;
import art.arcane.adapt.platform.event.entity.ProjectileHitEvent;
import art.arcane.adapt.platform.event.entity.ProjectileLaunchEvent;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.world.BlockRef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Deaths and their drops, projectiles, explosions and item pickup. */
public final class EntityHooks {
    private static final Map<UUID, Respawn> RESPAWNS = new HashMap<>();
    private static final Map<LivingEntity, List<ItemStack>> INVENTORY_SNAPSHOTS = new IdentityHashMap<>();
    private static boolean shotCancelled;

    private EntityHooks() {
    }

    /** Before vanilla drops loot, equipment and experience: everything it spawns is held back for the death event. */
    public static void deathLootStarting(LivingEntity entity, ServerLevel level) {
        boolean player = entity instanceof ServerPlayer;
        if (!ModEvents.listening(player ? PlayerDeathEvent.class : EntityDeathEvent.class)) {
            return;
        }
        DropCapture.open(level, entity);
        if (player && !keepInventoryRule((ServerPlayer) entity, level)) {
            Inventory inventory = ((ServerPlayer) entity).getInventory();
            List<ItemStack> snapshot = new ArrayList<>(inventory.getContainerSize());
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                snapshot.add(inventory.getItem(slot).copy());
            }
            INVENTORY_SNAPSHOTS.put(entity, snapshot);
        }
    }

    /** True when the experience was held back for the death event instead of spawning. */
    public static boolean deathExperience(LivingEntity entity, int amount) {
        DropCapture.Frame frame = DropCapture.find(entity);
        if (frame == null) {
            return false;
        }
        frame.experience += amount;
        return true;
    }

    /** After vanilla decided the drops: posts the death event, then spawns what the handlers left. */
    public static void deathLootDone(LivingEntity entity, ServerLevel level, DamageSource source) {
        DropCapture.Frame frame = DropCapture.close(entity);
        List<ItemStack> snapshot = INVENTORY_SNAPSHOTS.remove(entity);
        if (frame == null) {
            return;
        }
        List<ItemRef> drops = new ArrayList<>(frame.items.size());
        Map<ItemRef, ItemEntity> origins = new IdentityHashMap<>();
        for (ItemEntity item : frame.items) {
            ItemRef ref = ModRefs.item(item.getItem());
            drops.add(ref);
            origins.put(ref, item);
        }
        PlayerRef killer = entity.getKillCredit() instanceof ServerPlayer serverKiller ? ModRefs.player(serverKiller) : null;
        Entity direct = source.getDirectEntity();
        Entity causing = source.getEntity();
        EntityRef directRef = direct == null ? null : ModRefs.entity(direct);
        EntityRef causingRef = causing == null ? null : ModRefs.entity(causing);
        DamageCause cause = BlockDamage.cause(entity, source, false);
        EntityDeathEvent event;
        boolean ruleKeep = false;
        if (entity instanceof ServerPlayer player) {
            ruleKeep = keepInventoryRule(player, level);
            event = new PlayerDeathEvent(ModRefs.player(player), killer, directRef, causingRef, cause, drops, frame.experience,
                ruleKeep, ruleKeep, 0, 0, 0);
        } else {
            event = new EntityDeathEvent(ModRefs.living(entity), killer, directRef, causingRef, cause, drops, frame.experience);
        }
        ModEvents.post(event);
        for (ItemRef ref : event.drops()) {
            if (ref == null) {
                continue;
            }
            ItemStack stack = ModRefs.item(ref);
            if (stack.isEmpty()) {
                continue;
            }
            ItemEntity origin = origins.get(ref);
            if (origin != null) {
                if (origin.getItem() != stack) {
                    origin.setItem(stack);
                }
                level.addFreshEntity(origin);
            } else {
                entity.spawnAtLocation(stack.copy());
            }
        }
        if (event.droppedExp() > 0) {
            ExperienceOrb.award(level, entity.position(), event.droppedExp());
        }
        if (event instanceof PlayerDeathEvent playerEvent && entity instanceof ServerPlayer player) {
            settlePlayerDeath(player, playerEvent, ruleKeep, snapshot);
        }
    }

    private static void settlePlayerDeath(ServerPlayer player, PlayerDeathEvent event, boolean ruleKeep, List<ItemStack> snapshot) {
        Inventory inventory = player.getInventory();
        if (!ruleKeep && event.keepInventory() && snapshot != null) {
            for (int slot = 0; slot < snapshot.size() && slot < inventory.getContainerSize(); slot++) {
                inventory.setItem(slot, snapshot.get(slot));
            }
        } else if (ruleKeep && !event.keepInventory()) {
            inventory.dropAll();
        }
        Respawn respawn = new Respawn(event.keepInventory(), event.keepLevel(), event.newLevel(), event.newExp(), event.newTotalExp());
        if (respawn.keepInventory() != ruleKeep || respawn.keepLevel() != ruleKeep || respawn.newLevel() != 0 || respawn.newExp() != 0
            || respawn.newTotalExp() != 0) {
            RESPAWNS.put(player.getUUID(), respawn);
        } else {
            RESPAWNS.remove(player.getUUID());
        }
    }

    /** Applies keep-inventory, keep-level and new experience from the death event to the respawned player. */
    public static void restored(ServerPlayer player, ServerPlayer old, boolean restoreAll) {
        Respawn respawn = RESPAWNS.remove(old.getUUID());
        if (respawn == null || restoreAll) {
            return;
        }
        boolean ruleKeep = keepInventoryRule(old, old.serverLevel());
        if (respawn.keepInventory() && !ruleKeep) {
            player.getInventory().replaceWith(old.getInventory());
        } else if (!respawn.keepInventory() && ruleKeep) {
            player.getInventory().clearContent();
        }
        if (respawn.keepLevel()) {
            if (!ruleKeep) {
                player.experienceLevel = old.experienceLevel;
                player.totalExperience = old.totalExperience;
                player.experienceProgress = old.experienceProgress;
            }
            return;
        }
        player.experienceLevel = respawn.newLevel();
        player.totalExperience = respawn.newTotalExp();
        player.experienceProgress = 0.0F;
        if (respawn.newExp() > 0) {
            player.giveExperiencePoints(respawn.newExp());
        }
    }

    private static boolean keepInventoryRule(ServerPlayer player, ServerLevel level) {
        return level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY) || player.isSpectator();
    }

    /**
     * True when a handler cancelled; the projectile then passes as if nothing was hit. Before 1.21.2 a projectile handles
     * its hit before moving, so for a block hit it stands where the tick began; 26.x moves it to the contact point
     * first. Handlers see it at the contact point, and vanilla's own hit handling still starts from where it was.
     */
    public static boolean projectileHit(Projectile projectile, HitResult hit) {
        if (hit == null || hit.getType() == HitResult.Type.MISS || !ModEvents.server(projectile)
            || !ModEvents.listening(ProjectileHitEvent.class)) {
            return false;
        }
        EntityRef hitEntity = null;
        BlockRef hitBlock = null;
        art.arcane.adapt.platform.value.BlockFace face = null;
        if (hit instanceof EntityHitResult entityHit) {
            hitEntity = ModRefs.entity(entityHit.getEntity());
        } else if (hit instanceof BlockHitResult blockHit) {
            hitBlock = ModRefs.block((ServerLevel) projectile.level(), blockHit.getBlockPos());
            face = ModConversions.face(blockHit.getDirection());
        } else {
            return false;
        }
        Vec3 tickStart = projectile.position();
        Vec3 contact = hit.getLocation();
        boolean atContact = hitBlock != null;
        if (atContact) {
            projectile.setPos(contact);
        }
        boolean cancelled = ModEvents.post(new ProjectileHitEvent(ModEvents.projectile(projectile), hitEntity, hitBlock, face)).isCancelled();
        if (atContact && projectile.position().equals(contact)) {
            projectile.setPos(tickStart);
        }
        return cancelled;
    }

    /** Called for every entity entering a level. True when it must not be added. */
    public static boolean adding(ServerLevel level, Entity entity) {
        if (DropCapture.active() && DropCapture.capture(level, entity)) {
            return true;
        }
        if (art.arcane.adapt.mod.event.entity.SpawnHooks.spawning(level, entity)) {
            return true;
        }
        if (!(entity instanceof Projectile projectile) || !ModEvents.listening(ProjectileLaunchEvent.class)) {
            return false;
        }
        return ModEvents.post(new ProjectileLaunchEvent(ModEvents.projectile(projectile))).isCancelled();
    }

    /**
     * A bow, crossbow or other projectile weapon is about to spawn one projectile. True when a handler
     * cancelled; the projectile is then not spawned and its ammunition is returned.
     */
    public static boolean shooting(LivingEntity shooter, ItemStack weapon, ItemStack ammo, Projectile projectile, InteractionHand hand,
                                   float power) {
        shotCancelled = false;
        if (!ModEvents.server(shooter) || !ModEvents.listening(EntityShootBowEvent.class)) {
            return false;
        }
        boolean consumed = !ammo.has(DataComponents.INTANGIBLE_PROJECTILE) && !shooter.hasInfiniteMaterials();
        float force = weapon.getItem() instanceof BowItem ? Math.min(1.0F, power / 3.0F) : 1.0F;
        EntityShootBowEvent event = new EntityShootBowEvent(ModRefs.living(shooter), ModRefs.item(weapon), ModRefs.item(ammo.copy()),
            ModRefs.entity(projectile), ModConversions.hand(hand), force, consumed);
        ModEvents.post(event);
        if (event.isCancelled()) {
            if (consumed) {
                refund(shooter, ammo);
            }
            shotCancelled = true;
            return true;
        }
        if (consumed && !event.consumeItem()) {
            refund(shooter, ammo);
            if (projectile instanceof AbstractArrow arrow) {
                arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            }
        }
        return false;
    }

    /** True once after the projectile just offered to {@link #shooting} was cancelled. */
    public static boolean shotCancelled() {
        boolean cancelled = shotCancelled;
        shotCancelled = false;
        return cancelled;
    }

    private static void refund(LivingEntity shooter, ItemStack ammo) {
        if (shooter instanceof ServerPlayer player && !ammo.isEmpty()) {
            if (!player.getInventory().add(ammo.copy())) {
                player.spawnAtLocation(ammo.copy());
            }
            player.containerMenu.sendAllDataToRemote();
        }
    }

    /**
     * True when a handler cancelled the block damage of this explosion. The list is edited in place. The explosion's
     * level, centre and radius are private on this version, so the explosion mixin passes them.
     */
    public static boolean exploding(Explosion explosion, ServerLevel level, Vec3 center, float radius, List<BlockPos> blocks) {
        Entity source = explosion.getDirectSourceEntity();
        Class<? extends art.arcane.adapt.platform.event.AdaptEvent> type = source != null ? EntityExplodeEvent.class : BlockExplodeEvent.class;
        if (!ModEvents.listening(type)) {
            return false;
        }
        List<BlockRef> refs = new ArrayList<>(blocks.size());
        for (BlockPos pos : blocks) {
            refs.add(ModRefs.block(level, pos));
        }
        float yield = radius <= 0.0F ? 1.0F : 1.0F / radius;
        List<BlockRef> kept;
        float chosenYield;
        if (source != null) {
            EntityExplodeEvent event = ModEvents.post(new EntityExplodeEvent(ModRefs.entity(source),
                ModRefs.loc(level, center, 0.0F, 0.0F), refs, yield));
            if (event.isCancelled()) {
                blocks.clear();
                return true;
            }
            kept = event.blocks();
            chosenYield = event.yield();
        } else {
            net.minecraft.world.level.block.state.BlockState removed = art.arcane.adapt.mod.event.entity.ExplodingBlocks.current();
            BlockExplodeEvent event = ModEvents.post(new BlockExplodeEvent(ModRefs.block(level, BlockPos.containing(center)),
                removed == null ? null : ModRefs.state(removed), refs, yield));
            if (event.isCancelled()) {
                blocks.clear();
                return true;
            }
            kept = event.blocks();
            chosenYield = event.yield();
        }
        if (chosenYield != yield) {
            ((ExplosionYield) explosion).adapt$yield(chosenYield);
        }
        Set<BlockPos> result = new LinkedHashSet<>(kept.size());
        for (BlockRef ref : kept) {
            if (ref != null && ModRefs.world(ref.world()) == level) {
                result.add(ModRefs.pos(ref.pos()));
            }
        }
        blocks.clear();
        blocks.addAll(result);
        return false;
    }

    /** The radius loot tables use for an exploded block's survival chance: 1 / yield when a handler changed the yield. */
    public static float dropRadius(Explosion explosion, float radius) {
        if (!(explosion instanceof ExplosionYield chosen)) {
            return radius;
        }
        float yield = chosen.adapt$yield();
        if (Float.isNaN(yield)) {
            return radius;
        }
        return yield <= 0.0F ? Float.MAX_VALUE : 1.0F / Math.min(1.0F, yield);
    }

    /** True when the item is marked against player pickup or a handler cancelled it. Only posted when some of the stack fits. */
    public static boolean playerPickingUp(ServerPlayer player, ItemEntity item) {
        if (item.getTags().contains(ModItemEntityRef.NO_PLAYER_PICKUP_TAG)) {
            return true;
        }
        if (!ModEvents.listening(EntityPickupItemEvent.class)) {
            return false;
        }
        ItemStack stack = item.getItem();
        int space = space(player.getInventory(), stack);
        if (space <= 0) {
            return false;
        }
        int remaining = Math.max(0, stack.getCount() - space);
        return ModEvents.post(new EntityPickupItemEvent(ModRefs.living(player), ModEvents.itemEntity(item), remaining)).isCancelled();
    }

    /** True when a handler cancelled a mob's pickup. */
    public static boolean mobPickingUp(Mob mob, ItemEntity item) {
        if (item.getTags().contains(ModItemEntityRef.NO_MOB_PICKUP_TAG)) {
            return true;
        }
        if (!ModEvents.server(mob) || !ModEvents.listening(EntityPickupItemEvent.class)) {
            return false;
        }
        return ModEvents.post(new EntityPickupItemEvent(ModRefs.living(mob), ModEvents.itemEntity(item), 0)).isCancelled();
    }

    private static int space(Inventory inventory, ItemStack stack) {
        int max = Math.min(stack.getMaxStackSize(), inventory.getMaxStackSize(stack));
        int space = 0;
        for (int slot = 0; slot < 36; slot++) {
            space += room(inventory.getItem(slot), stack, max);
        }
        space += room(inventory.getItem(Inventory.SLOT_OFFHAND), stack, max);
        return space;
    }

    private static int room(ItemStack existing, ItemStack stack, int max) {
        if (existing.isEmpty()) {
            return max;
        }
        if (ItemStack.isSameItemSameComponents(existing, stack)) {
            return Math.max(0, max - existing.getCount());
        }
        return 0;
    }

    private record Respawn(boolean keepInventory, boolean keepLevel, int newLevel, int newExp, int newTotalExp) {
    }
}
