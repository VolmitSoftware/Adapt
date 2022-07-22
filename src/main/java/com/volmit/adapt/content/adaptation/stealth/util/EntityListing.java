package com.volmit.adapt.content.adaptation.stealth.util;

import lombok.Getter;
import org.bukkit.entity.EntityType;

import java.util.List;

public class EntityListing {

    @Getter
    public static List<EntityType> aggroMobs = List.of(
            EntityType.EVOKER,
            EntityType.VINDICATOR,
            EntityType.PILLAGER,
            EntityType.RAVAGER,
            EntityType.VEX,
            EntityType.ENDERMITE,
            EntityType.GUARDIAN,
            EntityType.ELDER_GUARDIAN,
            EntityType.SKELETON,
            EntityType.SHULKER,
            EntityType.SKELETON_HORSE,
            EntityType.HUSK,
            EntityType.STRAY,
            EntityType.PHANTOM,
            EntityType.BLAZE,
            EntityType.CREEPER,
            EntityType.GHAST,
            EntityType.MAGMA_CUBE,
            EntityType.SILVERFISH,
            EntityType.SLIME,
            EntityType.SPIDER,
            EntityType.CAVE_SPIDER,
            EntityType.ZOMBIE,
            EntityType.ZOMBIE_HORSE,
            EntityType.ZOMBIE_VILLAGER,
            EntityType.DROWNED,
            EntityType.WITHER_SKELETON,
            EntityType.WITCH,
            EntityType.HOGLIN,
            EntityType.ZOGLIN,
            EntityType.PIGLIN,
            EntityType.PIGLIN_BRUTE,
            EntityType.ENDERMAN
    );


}
