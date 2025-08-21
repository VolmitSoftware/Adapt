package com.volmit.adapt.api.version.v1_20_2;

import com.volmit.adapt.api.version.IAttribute;
import com.volmit.adapt.api.version.IBindings;
import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Optional;

public class Bindings implements IBindings {

    @Override
    public IAttribute getAttribute(Attributable attributable, Attribute modifier) {
        return Optional.ofNullable(attributable.getAttribute(modifier))
                .map(AttributeImpl::new)
                .orElse(null);
    }

    @Override
    @Unmodifiable
    public List<EntityType> getInvalidDamageableEntities() {
        return List.of(
                EntityType.ARMOR_STAND,
                EntityType.BOAT,
                EntityType.ITEM_FRAME,
                EntityType.MINECART,
                EntityType.MINECART_CHEST,
                EntityType.MINECART_COMMAND,
                EntityType.MINECART_FURNACE,
                EntityType.MINECART_HOPPER,
                EntityType.MINECART_MOB_SPAWNER,
                EntityType.MINECART_TNT,
                EntityType.PAINTING,
                EntityType.CHEST_BOAT,
                EntityType.LEASH_HITCH,
                EntityType.EVOKER_FANGS,
                EntityType.MARKER
        );
    }
}
