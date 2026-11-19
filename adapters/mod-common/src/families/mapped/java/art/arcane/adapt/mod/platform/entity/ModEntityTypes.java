package art.arcane.adapt.mod.platform.entity;

import art.arcane.adapt.mod.platform.ModContentAliases;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.value.Id;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.item.Items;

/**
 * An entity's type under core's 26.x id. This version registers one type for every boat wood and one for both thrown
 * potions, so those take the 26.x type the entity's wood or potion item stands for.
 */
public final class ModEntityTypes {
    private ModEntityTypes() {
    }

    public static Id id(Entity entity) {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        String vanilla = "minecraft".equals(key.getNamespace()) ? key.getPath() : "";
        if (vanilla.equals("potion") && entity instanceof ThrownPotion potion) {
            return Id.minecraft(potion.getItem().is(Items.LINGERING_POTION) ? "lingering_potion" : "splash_potion");
        }
        if ((vanilla.equals("boat") || vanilla.equals("chest_boat")) && entity instanceof Boat boat) {
            boolean chest = boat instanceof ChestBoat;
            if (boat.getVariant() == Boat.Type.BAMBOO) {
                return Id.minecraft(chest ? "bamboo_chest_raft" : "bamboo_raft");
            }
            return Id.minecraft(boat.getVariant().getName() + (chest ? "_chest_boat" : "_boat"));
        }
        return ModContentAliases.modern(RegistryKind.ENTITY_TYPE, key);
    }
}
