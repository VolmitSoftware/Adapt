package art.arcane.adapt.api.mutation.sound;

import art.arcane.adapt.platform.value.Id;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The sound palette of every skill, keyed by skill id. Every id is a vanilla sound event registered on 1.20.1, 1.21.1,
 * 26.1.2, 26.2 and 26.3, so no resource pack is needed. Every file behind an id is at most 0.7 s long, ticks included,
 * and no id appears twice in the table.
 */
public final class SoundPalettes {
  /** The low hit every Convergence adds under its two strike cues. */
  public static final Id CONVERGENCE_HIT = Id.minecraft("entity.player.attack.knockback");

  private static final Map<String, SoundPalette> PALETTES = build();

  private SoundPalettes() {
  }

  public static SoundPalette of(String skill) {
    SoundPalette palette = PALETTES.get(skill);
    if (palette == null) {
      throw new IllegalArgumentException("No sound palette for skill '" + skill + "'");
    }
    return palette;
  }

  public static Collection<SoundPalette> all() {
    return PALETTES.values();
  }

  private static Map<String, SoundPalette> build() {
    Map<String, SoundPalette> palettes = new LinkedHashMap<>();
    add(palettes, "swords", "entity.player.attack.sweep", "block.grass.break", "entity.player.attack.crit", "block.pointed_dripstone.drip_water");
    add(palettes, "axes", "item.axe.strip", "block.wood.break", "item.armor.equip_leather", "block.wood.hit");
    add(palettes, "unarmed", "entity.player.attack.strong", "block.wool.break", "item.armor.equip_generic", "block.wool.hit");
    add(palettes, "ranged", "entity.arrow.shoot", "entity.item.pickup", "item.crossbow.quick_charge_3", "block.tripwire.click_on");
    add(palettes, "blocking", "item.shield.block", "item.armor.equip_iron", "item.armor.equip_netherite", "block.chain.step");
    add(palettes, "stealth", "entity.bat.takeoff", "block.wool.step", "entity.illusioner.ambient", "block.moss_carpet.step");
    add(palettes, "hunter", "entity.fox.bite", "entity.rabbit.hurt", "entity.goat.ram_impact", "block.note_block.basedrum");
    add(palettes, "tragoul", "entity.vex.hurt", "block.sculk.spread", "block.sculk.charge", "block.pointed_dripstone.drip_lava");
    add(palettes, "agility", "entity.horse.jump", "entity.horse.gallop", "item.armor.equip_elytra", "entity.horse.step");
    add(palettes, "kinetics", "block.piston.extend", "entity.slime.jump_small", "block.slime_block.fall", "entity.slime.squish_small");
    add(palettes, "rift", "entity.enderman.hurt", "block.ender_chest.close", "entity.ender_pearl.throw", "entity.endermite.step");
    add(palettes, "chronos", "block.note_block.hat", "block.note_block.cow_bell", "block.note_block.bell", "block.lever.click");
    add(palettes, "pickaxe", "block.stone.break", "block.ancient_debris.break", "block.lodestone.hit", "block.stone.hit");
    add(palettes, "excavation", "item.shovel.flatten", "block.gravel.break", "block.sand.fall", "block.mud.step");
    add(palettes, "herbalism", "block.vine.step", "item.hoe.till", "block.moss.break", "block.wet_grass.step");
    add(palettes, "architect", "block.scaffolding.place", "block.wood.place", "block.stone.place", "block.scaffolding.step");
    add(palettes, "crafting", "ui.stonecutter.take_result", "block.anvil.hit", "ui.loom.select_pattern", "ui.stonecutter.select_recipe");
    add(palettes, "enchanting", "enchant.thorns.hit", "item.book.put", "block.amethyst_block.place", "block.candle.ambient");
    add(palettes, "brewing", "entity.splash_potion.break", "item.bottle.fill", "entity.generic.drink", "block.bubble_column.bubble_pop");
    add(palettes, "discovery", "item.spyglass.use", "block.note_block.pling", "block.note_block.iron_xylophone", "block.note_block.harp");
    add(palettes, "taming", "entity.wolf.ambient", "entity.parrot.eat", "entity.parrot.fly", "entity.wolf.pant");
    add(palettes, "golemancy", "entity.iron_golem.attack", "entity.iron_golem.step", "item.armor.equip_chain", "block.netherite_block.step");
    add(palettes, "seaborne", "entity.dolphin.attack", "entity.fishing_bobber.retrieve", "entity.generic.swim", "entity.puffer_fish.blow_up");
    add(palettes, "nether", "entity.blaze.hurt", "block.nether_wart.break", "item.flintandsteel.use", "block.lava.pop");
    return Collections.unmodifiableMap(palettes);
  }

  private static void add(Map<String, SoundPalette> palettes, String skill, String fight, String work, String boon, String tick) {
    palettes.put(skill, new SoundPalette(skill, Id.minecraft(fight), Id.minecraft(work), Id.minecraft(boon), Id.minecraft(tick)));
  }
}
