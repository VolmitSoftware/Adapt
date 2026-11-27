package art.arcane.adapt.api.fx;

import art.arcane.adapt.platform.fx.ParticleData;
import art.arcane.adapt.platform.fx.ParticleSpec;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.BlockStateRef;

import org.jetbrains.annotations.ApiStatus;
/**
 * Vanilla particle specs named like the Bukkit {@code Particle} constants.
 * Colour particles ({@link #DUST}, {@link #DUST_COLOR_TRANSITION}, {@link #FLASH}) carry no data:
 * {@link FxEmitter} fills them with the skill colour. Outside an emitter, and for block or item particles, use the
 * factories.
 */
@ApiStatus.AvailableSince("1.0.0")
public final class Particles {
  public static final ParticleSpec ANGRY_VILLAGER = ParticleSpec.of(Id.minecraft("angry_villager"));
  public static final ParticleSpec ASH = ParticleSpec.of(Id.minecraft("ash"));
  public static final ParticleSpec BUBBLE = ParticleSpec.of(Id.minecraft("bubble"));
  public static final ParticleSpec BUBBLE_POP = ParticleSpec.of(Id.minecraft("bubble_pop"));
  public static final ParticleSpec CAMPFIRE_COSY_SMOKE = ParticleSpec.of(Id.minecraft("campfire_cosy_smoke"));
  public static final ParticleSpec CLOUD = ParticleSpec.of(Id.minecraft("cloud"));
  public static final ParticleSpec COMPOSTER = ParticleSpec.of(Id.minecraft("composter"));
  public static final ParticleSpec CRIMSON_SPORE = ParticleSpec.of(Id.minecraft("crimson_spore"));
  public static final ParticleSpec CRIT = ParticleSpec.of(Id.minecraft("crit"));
  public static final ParticleSpec DAMAGE_INDICATOR = ParticleSpec.of(Id.minecraft("damage_indicator"));
  public static final ParticleSpec DRAGON_BREATH = ParticleSpec.of(Id.minecraft("dragon_breath"));
  public static final ParticleSpec DUST = ParticleSpec.of(Id.minecraft("dust"));
  public static final ParticleSpec DUST_COLOR_TRANSITION = ParticleSpec.of(Id.minecraft("dust_color_transition"));
  public static final ParticleSpec ELECTRIC_SPARK = ParticleSpec.of(Id.minecraft("electric_spark"));
  public static final ParticleSpec ENCHANT = ParticleSpec.of(Id.minecraft("enchant"));
  public static final ParticleSpec END_ROD = ParticleSpec.of(Id.minecraft("end_rod"));
  public static final ParticleSpec EXPLOSION = ParticleSpec.of(Id.minecraft("explosion"));
  public static final ParticleSpec FIREWORK = ParticleSpec.of(Id.minecraft("firework"));
  public static final ParticleSpec FLAME = ParticleSpec.of(Id.minecraft("flame"));
  public static final ParticleSpec FLASH = ParticleSpec.of(Id.minecraft("flash"));
  public static final ParticleSpec GLOW = ParticleSpec.of(Id.minecraft("glow"));
  public static final ParticleSpec GUST = ParticleSpec.of(Id.minecraft("gust"));
  public static final ParticleSpec HAPPY_VILLAGER = ParticleSpec.of(Id.minecraft("happy_villager"));
  public static final ParticleSpec HEART = ParticleSpec.of(Id.minecraft("heart"));
  public static final ParticleSpec ITEM_SLIME = ParticleSpec.of(Id.minecraft("item_slime"));
  public static final ParticleSpec LAVA = ParticleSpec.of(Id.minecraft("lava"));
  public static final ParticleSpec POOF = ParticleSpec.of(Id.minecraft("poof"));
  public static final ParticleSpec PORTAL = ParticleSpec.of(Id.minecraft("portal"));
  public static final ParticleSpec REVERSE_PORTAL = ParticleSpec.of(Id.minecraft("reverse_portal"));
  public static final ParticleSpec SCRAPE = ParticleSpec.of(Id.minecraft("scrape"));
  public static final ParticleSpec SCULK_SOUL = ParticleSpec.of(Id.minecraft("sculk_soul"));
  public static final ParticleSpec SMALL_FLAME = ParticleSpec.of(Id.minecraft("small_flame"));
  public static final ParticleSpec SMOKE = ParticleSpec.of(Id.minecraft("smoke"));
  public static final ParticleSpec SNEEZE = ParticleSpec.of(Id.minecraft("sneeze"));
  public static final ParticleSpec SNOWFLAKE = ParticleSpec.of(Id.minecraft("snowflake"));
  public static final ParticleSpec SOUL = ParticleSpec.of(Id.minecraft("soul"));
  public static final ParticleSpec SOUL_FIRE_FLAME = ParticleSpec.of(Id.minecraft("soul_fire_flame"));
  public static final ParticleSpec SPLASH = ParticleSpec.of(Id.minecraft("splash"));
  public static final ParticleSpec SPORE_BLOSSOM_AIR = ParticleSpec.of(Id.minecraft("spore_blossom_air"));
  public static final ParticleSpec SQUID_INK = ParticleSpec.of(Id.minecraft("squid_ink"));
  public static final ParticleSpec SWEEP_ATTACK = ParticleSpec.of(Id.minecraft("sweep_attack"));
  /** The totem of undying particle, {@code minecraft:totem_of_undying}. */
  public static final ParticleSpec TOTEM = ParticleSpec.of(Id.minecraft("totem_of_undying"));
  /** Legacy alias of {@link #HAPPY_VILLAGER}. */
  public static final ParticleSpec VILLAGER_HAPPY = ParticleSpec.of(Id.minecraft("happy_villager"));
  public static final ParticleSpec WARPED_SPORE = ParticleSpec.of(Id.minecraft("warped_spore"));
  public static final ParticleSpec WAX_OFF = ParticleSpec.of(Id.minecraft("wax_off"));
  public static final ParticleSpec WAX_ON = ParticleSpec.of(Id.minecraft("wax_on"));
  public static final ParticleSpec WHITE_ASH = ParticleSpec.of(Id.minecraft("white_ash"));
  public static final ParticleSpec WITCH = ParticleSpec.of(Id.minecraft("witch"));

  private Particles() {
  }

  /** Dust in {@code color}, {@code size} 1 being the vanilla size. */
  public static ParticleSpec dust(Rgb color, float size) {
    return ParticleSpec.dust(color, size);
  }

  /** Dust that fades from {@code from} to {@code to}. */
  public static ParticleSpec dustTransition(Rgb from, Rgb to, float size) {
    return new ParticleSpec(DUST_COLOR_TRANSITION.type(), new ParticleData.DustTransition(from, to, size));
  }

  /** A flash in {@code color}. */
  public static ParticleSpec flash(Rgb color) {
    return new ParticleSpec(FLASH.type(), new ParticleData.Color(color));
  }

  /** Fragments of the block {@code state}, as when it breaks. */
  public static ParticleSpec blockCrack(BlockStateRef state) {
    return ParticleSpec.block(state);
  }

  /** Fragments of {@code item}, as when it breaks or is eaten. */
  public static ParticleSpec itemCrack(ItemRef item) {
    return new ParticleSpec(Id.minecraft("item"), new ParticleData.Item(item));
  }
}
