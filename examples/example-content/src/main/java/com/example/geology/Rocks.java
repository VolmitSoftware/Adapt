package com.example.geology;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.RegistryLookup;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

/** Block and tool tests shared by the geology content. */
public final class Rocks {
  private static final Id STONE_TAG = Id.minecraft("base_stone_overworld");
  private static final Id PICKAXE_TAG = Id.minecraft("pickaxes");

  private Rocks() {
  }

  public static boolean isStone(Id block) {
    return registries().isTagged(RegistryKind.BLOCK, STONE_TAG, block);
  }

  public static boolean isOre(Id block) {
    return block.isMinecraft() && (block.path().endsWith("_ore") || block.path().equals("ancient_debris"));
  }

  public static boolean isPickaxe(ItemRef item) {
    return item != null && !item.isEmpty() && registries().isTagged(RegistryKind.ITEM, PICKAXE_TAG, item.type());
  }

  private static RegistryLookup registries() {
    return AdaptApi.platform().host().registries();
  }
}
