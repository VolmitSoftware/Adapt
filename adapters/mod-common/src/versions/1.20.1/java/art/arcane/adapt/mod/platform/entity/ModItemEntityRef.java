package art.arcane.adapt.mod.platform.entity;

import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.mod.platform.mixin.ItemEntityAccess;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.item.ItemRef;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.UUID;

public class ModItemEntityRef extends ModEntityRef implements ItemEntityRef {
    /** Entity tag marking an item mobs must not pick up; checked by {@code EntityHooks.mobPickingUp}. */
    public static final String NO_MOB_PICKUP_TAG = "adapt_no_mob_pickup";
    /** Entity tag marking an item players must not pick up; checked by {@code EntityHooks.playerPickingUp}. */
    public static final String NO_PLAYER_PICKUP_TAG = "adapt_no_player_pickup";
    private static final int NEVER_PICK_UP_DELAY = 32767;

    public ModItemEntityRef(ItemEntity item) {
        super(item);
    }

    public ItemEntity itemEntity() {
        return (ItemEntity) handle();
    }

    @Override
    public ItemRef item() {
        return ModRefs.item(itemEntity().getItem());
    }

    @Override
    public void setItem(ItemRef item) {
        itemEntity().setItem(ModRefs.item(item).copy());
    }

    @Override
    public int pickupDelay() {
        return ((ItemEntityAccess) itemEntity()).adapt$pickupDelay();
    }

    @Override
    public void setPickupDelay(int ticks) {
        itemEntity().setPickUpDelay(ticks);
    }

    @Override
    public UUID thrower() {
        return ((ItemEntityAccess) itemEntity()).adapt$thrower();
    }

    @Override
    public void setThrower(UUID thrower) {
        ((ItemEntityAccess) itemEntity()).adapt$setThrower(thrower);
    }

    @Override
    public UUID owner() {
        return ((ItemEntityAccess) itemEntity()).adapt$target();
    }

    @Override
    public void setOwner(UUID owner) {
        itemEntity().setTarget(owner);
    }

    @Override
    public boolean canPlayerPickup() {
        return !itemEntity().getTags().contains(NO_PLAYER_PICKUP_TAG) && pickupDelay() != NEVER_PICK_UP_DELAY;
    }

    @Override
    public boolean canMobPickup() {
        return !itemEntity().getTags().contains(NO_MOB_PICKUP_TAG);
    }

    @Override
    public void setCanMobPickup(boolean pickup) {
        if (pickup) {
            itemEntity().removeTag(NO_MOB_PICKUP_TAG);
        } else {
            itemEntity().addTag(NO_MOB_PICKUP_TAG);
        }
    }

    @Override
    public void setUnlimitedLifetime(boolean unlimited) {
        if (unlimited) {
            itemEntity().setUnlimitedLifetime();
        } else {
            ((ItemEntityAccess) itemEntity()).adapt$setAge(0);
        }
    }
}
