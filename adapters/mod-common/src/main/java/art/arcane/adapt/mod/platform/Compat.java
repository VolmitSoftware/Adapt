package art.arcane.adapt.mod.platform;

import java.util.stream.Stream;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;

/** Calls whose vanilla signature differs between supported Minecraft versions; overlays replace this class. */
public final class Compat {
    private Compat() {
    }

    public static void swing(LivingEntity entity, InteractionHand hand) {
        entity.swing(hand, SwingAnimation.DEFAULT, true);
    }

    /** Sends the entity's velocity to clients on the next tracker update. */
    public static void markVelocityChanged(Entity entity) {
        entity.syncVelocity = true;
    }

    public static boolean isInvulnerable(Entity entity) {
        return entity.isPermanentlyInvulnerable();
    }

    public static void setInvulnerable(Entity entity, boolean invulnerable) {
        entity.setPermanentlyInvulnerable(invulnerable);
    }

    public static int damageCooldown(LivingEntity entity) {
        return entity.damageCooldownTime;
    }

    public static void setDamageCooldown(LivingEntity entity, int ticks) {
        entity.damageCooldownTime = ticks;
    }

    public static EntityType<LightningBolt> lightningBolt() {
        return EntityTypes.LIGHTNING_BOLT;
    }

    public static boolean isSlime(Entity entity) {
        return entity instanceof Slime;
    }

    public static Stream<ItemStack> bundleItems(BundleContents bundle) {
        return bundle.itemCopies();
    }

    public static Stream<ItemStack> containerItems(ItemContainerContents contents) {
        return contents.itemCopies();
    }

    public static boolean isInstantaneous(MobEffect effect) {
        return effect.isInstantaneous();
    }
}
