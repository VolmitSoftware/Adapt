package art.arcane.adapt.mod.platform;

import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import java.util.stream.Stream;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;

/** Calls whose vanilla signature differs between supported Minecraft versions; overlays replace this class. */
public final class Compat {
    private Compat() {
    }

    public static Entity lootEntity(LootParams params) {
        return params.getParamOrNull(LootContextParams.THIS_ENTITY);
    }

    public static Vec3 lootOrigin(LootParams params) {
        return params.getParamOrNull(LootContextParams.ORIGIN);
    }

    public static AdvancementType advancementType(DisplayInfo display) {
        return display.getType();
    }

    public static void swing(LivingEntity entity, InteractionHand hand) {
        entity.swing(hand, true);
    }

    /** Sends the entity's velocity to clients on the next tracker update. */
    public static void markVelocityChanged(Entity entity) {
        entity.hurtMarked = true;
    }

    public static boolean isInvulnerable(Entity entity) {
        return entity.isInvulnerable();
    }

    public static void setInvulnerable(Entity entity, boolean invulnerable) {
        entity.setInvulnerable(invulnerable);
    }

    public static int damageCooldown(LivingEntity entity) {
        return entity.invulnerableTime;
    }

    public static void setDamageCooldown(LivingEntity entity, int ticks) {
        entity.invulnerableTime = ticks;
    }

    public static EntityType<LightningBolt> lightningBolt() {
        return EntityType.LIGHTNING_BOLT;
    }

    public static boolean isSlime(Entity entity) {
        return entity instanceof Slime;
    }

    public static Stream<ItemStack> bundleItems(BundleContents bundle) {
        return bundle.itemCopyStream();
    }

    public static Stream<ItemStack> containerItems(ItemContainerContents contents) {
        return contents.stream();
    }

    public static boolean isInstantaneous(MobEffect effect) {
        return effect.isInstantenous();
    }
}
