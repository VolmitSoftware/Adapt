package art.arcane.adapt.localization.catalog;

import art.arcane.volmlib.util.localization.LinesKey;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;

public final class RangedMessages {
  public static final TextKey PREFERENCE_SNEAK = TextKey.of("ranged.preferences.sneak", "Require sneaking");
  public static final TextKey PREFERENCE_GLOW = TextKey.of("ranged.preferences.glow", "Private target glow");
  public static final TextKey PREFERENCE_IGNORE_PASSIVE = TextKey.of("ranged.preferences.ignore_passive", "Ignore passive mobs");
  public static final TextKey PREFERENCE_DAMPEN = TextKey.of("ranged.preferences.dampen", "Slow target momentum");
  public static final TextKey PREFERENCE_IMPACT = TextKey.of("ranged.preferences.impact", "Impact marker");
  public static final TextKey PREFERENCE_TRAIL = TextKey.of("ranged.preferences.trail", "Trajectory trail");
  public static final TextKey PREFERENCE_ALL_PROJECTILES = TextKey.of("ranged.preferences.all_projectiles", "Include permitted thrown projectiles");
  public static final TextKey PREFERENCE_PROJECTILES = TextKey.of("ranged.preferences.projectiles", "Eligible projectiles");
  public static final TextKey PREFERENCE_ALL = TextKey.of("ranged.preferences.all", "All permitted");
  public static final TextKey PREFERENCE_BOW = TextKey.of("ranged.preferences.bow", "Bow arrows");
  public static final TextKey PREFERENCE_CROSSBOW = TextKey.of("ranged.preferences.crossbow", "Crossbow arrows");
  public static final TextKey PREFERENCE_ARROWS = TextKey.of("ranged.preferences.arrows", "All arrows");
  public static final TextKey PREFERENCE_TRIDENTS = TextKey.of("ranged.preferences.tridents", "Tridents");
  public static final TextKey PREFERENCE_THROWN = TextKey.of("ranged.preferences.thrown", "Thrown projectiles");
  public static final TextKey PREFERENCE_TARGETS = TextKey.of("ranged.preferences.targets", "Eligible targets");
  public static final TextKey PREFERENCE_HOSTILE = TextKey.of("ranged.preferences.hostile", "Hostile mobs only");
  public static final TextKey PREFERENCE_NON_PLAYERS = TextKey.of("ranged.preferences.non_players", "Exclude players");
  public static final TextKey PREFERENCE_ITEMS = TextKey.of("ranged.preferences.items", "Collected items");
  public static final TextKey PREFERENCE_BLOCKS = TextKey.of("ranged.preferences.blocks", "Building blocks");
  public static final TextKey PREFERENCE_VALUABLES = TextKey.of("ranged.preferences.valuables", "Ores and valuables");
  public static final TextKey PREFERENCE_FOOD = TextKey.of("ranged.preferences.food", "Food");
  public static final TextKey PREFERENCE_PREVIEW = TextKey.of("ranged.preferences.preview", "Preview while");
  public static final TextKey PREFERENCE_BOTH = TextKey.of("ranged.preferences.both", "Drawing or sneaking");
  public static final TextKey PREFERENCE_DRAWING = TextKey.of("ranged.preferences.drawing", "Drawing only");
  public static final TextKey PREFERENCE_SNEAKING = TextKey.of("ranged.preferences.sneaking", "Sneaking only");
  private static final List<TextKey> PREFERENCE_KEYS = List.of(
      PREFERENCE_SNEAK,
      PREFERENCE_GLOW,
      PREFERENCE_IGNORE_PASSIVE,
      PREFERENCE_DAMPEN,
      PREFERENCE_IMPACT,
      PREFERENCE_TRAIL,
      PREFERENCE_ALL_PROJECTILES,
      PREFERENCE_PROJECTILES,
      PREFERENCE_ALL,
      PREFERENCE_BOW,
      PREFERENCE_CROSSBOW,
      PREFERENCE_ARROWS,
      PREFERENCE_TRIDENTS,
      PREFERENCE_THROWN,
      PREFERENCE_TARGETS,
      PREFERENCE_HOSTILE,
      PREFERENCE_NON_PLAYERS,
      PREFERENCE_ITEMS,
      PREFERENCE_BLOCKS,
      PREFERENCE_VALUABLES,
      PREFERENCE_FOOD,
      PREFERENCE_PREVIEW,
      PREFERENCE_BOTH,
      PREFERENCE_DRAWING,
      PREFERENCE_SNEAKING);
  public static final TextKey ARROW_RECOVERY_NAME = TextKey.of("ranged.arrow_recovery.name", "Arrow Recovery");
  public static final TextKey ARROW_RECOVERY_DESCRIPTION = TextKey.of("ranged.arrow_recovery.description", "Recover Arrows after you have killed an enemy.");
  public static final TextKey ARROW_RECOVERY_LORE1 = TextKey.of("ranged.arrow_recovery.lore1", "Chance to Recover Arrows on Hit/Kill");
  public static final TextKey ARROW_RECOVERY_LORE2 = TextKey.of("ranged.arrow_recovery.lore2", "Chance: ");
  public static final TextKey ARROW_RECOVERY_CHANCE = TextKey.of("ranged.arrow_recovery.chance", "Chance: {chance}");
  public static final LinesKey ARROW_RECOVERY_LORE = LinesKey.of("ranged.arrow_recovery.lore", "Chance to Recover Arrows on Hit/Kill", "Chance: ");
  public static final TextKey WEB_SHOT_NAME = TextKey.of("ranged.web_shot.name", "Web Snare");
  public static final TextKey WEB_SHOT_DESCRIPTION = TextKey.of("ranged.web_shot.description", "Surround cobwebs around your target when you hit them!");
  public static final TextKey WEB_SHOT_LORE1 = TextKey.of("ranged.web_shot.lore1", "8 Cobwebs around a Snowball, and throw!");
  public static final TextKey WEB_SHOT_LORE2 = TextKey.of("ranged.web_shot.lore2", "seconds of a cage, roughly.");
  public static final LinesKey WEB_SHOT_LORE = LinesKey.of("ranged.web_shot.lore", "8 Cobwebs around a Snowball, and throw!", "seconds of a cage, roughly.");
  public static final TextKey FORCE_SHOT_NAME = TextKey.of("ranged.force_shot.name", "Force Shot");
  public static final TextKey FORCE_SHOT_DESCRIPTION = TextKey.of("ranged.force_shot.description", "Shoot projectiles further, faster!");
  public static final TextKey FORCE_SHOT_ADVANCEMENTNAME = TextKey.of("ranged.force_shot.advancementname", "Long Shot");
  public static final TextKey FORCE_SHOT_ADVANCEMENTLORE = TextKey.of("ranged.force_shot.advancementlore", "Land a shot from over 30 blocks away!");
  public static final TextKey FORCE_SHOT_LORE1 = TextKey.of("ranged.force_shot.lore1", "Projectile Speed");
  public static final LinesKey FORCE_SHOT_LORE = LinesKey.of("ranged.force_shot.lore", "Projectile Speed");
  public static final TextKey TRAJECTORY_SIGHT_NAME = TextKey.of("ranged.trajectory_sight.name", "Trajectory Sight");
  public static final TextKey TRAJECTORY_SIGHT_DESCRIPTION = TextKey.of("ranged.trajectory_sight.description", "Sneak or draw a ranged weapon to preview projectile flight as a clear dotted line with a ringed impact marker. With a Heartseeker lock active, the preview shows the arrow's curved seeking path to the mark instead.");
  public static final TextKey TRAJECTORY_SIGHT_LORE1 = TextKey.of("ranged.trajectory_sight.lore1", "Prediction Range");
  public static final TextKey TRAJECTORY_SIGHT_LORE2 = TextKey.of("ranged.trajectory_sight.lore2", "Prediction Detail");
  public static final LinesKey TRAJECTORY_SIGHT_LORE = LinesKey.of("ranged.trajectory_sight.lore", "Prediction Range", "Prediction Detail");
  public static final TextKey LUNGE_SHOT_NAME = TextKey.of("ranged.lunge_shot.name", "Lunge Shot");
  public static final TextKey LUNGE_SHOT_DESCRIPTION = TextKey.of("ranged.lunge_shot.description", "While airborne, firing arrows kicks you backward, away from your aim");
  public static final TextKey LUNGE_SHOT_LORE1 = TextKey.of("ranged.lunge_shot.lore1", "Recoil Burst Speed");
  public static final LinesKey LUNGE_SHOT_LORE = LinesKey.of("ranged.lunge_shot.lore", "Recoil Burst Speed");
  public static final TextKey ARROW_PIERCING_NAME = TextKey.of("ranged.arrow_piercing.name", "Arrow Piercing");
  public static final TextKey ARROW_PIERCING_DESCRIPTION = TextKey.of("ranged.arrow_piercing.description", "Adds Piercing to projectiles! Shoot through things!");
  public static final TextKey ARROW_PIERCING_LORE1 = TextKey.of("ranged.arrow_piercing.lore1", "Pierce Targets");
  public static final LinesKey ARROW_PIERCING_LORE = LinesKey.of("ranged.arrow_piercing.lore", "Pierce Targets");
  public static final TextKey FLOATERS_NAME = TextKey.of("ranged.floaters.name", "Floaters");
  public static final TextKey FLOATERS_DESCRIPTION = TextKey.of("ranged.floaters.description", "Projectiles have a chance to apply levitation and hold targets in the air.");
  public static final TextKey FLOATERS_LORE1 = TextKey.of("ranged.floaters.lore1", "Levitation Chance");
  public static final TextKey FLOATERS_LORE2 = TextKey.of("ranged.floaters.lore2", "Levitation Duration");
  public static final TextKey FLOATERS_LORE3 = TextKey.of("ranged.floaters.lore3", "Levitation Strength");
  public static final LinesKey FLOATERS_LORE = LinesKey.of("ranged.floaters.lore", "Levitation Chance", "Levitation Duration", "Levitation Strength");
  public static final TextKey PINNING_SHOT_NAME = TextKey.of("ranged.pinning_shot.name", "Pinning Shot");
  public static final TextKey PINNING_SHOT_DESCRIPTION = TextKey.of("ranged.pinning_shot.description", "Any player-fired projectile can pin targets with heavy slowness.");
  public static final TextKey PINNING_SHOT_LORE1 = TextKey.of("ranged.pinning_shot.lore1", "Pin Chance");
  public static final TextKey PINNING_SHOT_LORE2 = TextKey.of("ranged.pinning_shot.lore2", "Pin Duration");
  public static final TextKey PINNING_SHOT_LORE3 = TextKey.of("ranged.pinning_shot.lore3", "Reapply Cooldown");
  public static final LinesKey PINNING_SHOT_LORE = LinesKey.of("ranged.pinning_shot.lore", "Pin Chance", "Pin Duration", "Reapply Cooldown");
  public static final TextKey RICOCHET_BOLT_NAME = TextKey.of("ranged.ricochet_bolt.name", "Ricochet Bolt");
  public static final TextKey RICOCHET_BOLT_DESCRIPTION = TextKey.of("ranged.ricochet_bolt.description", "Projectiles ricochet off solid blocks with chained bounces.");
  public static final TextKey RICOCHET_BOLT_LORE1 = TextKey.of("ranged.ricochet_bolt.lore1", "Max Ricochets");
  public static final TextKey RICOCHET_BOLT_LORE2 = TextKey.of("ranged.ricochet_bolt.lore2", "Speed Bonus Per Ricochet");
  public static final TextKey RICOCHET_BOLT_LORE3 = TextKey.of("ranged.ricochet_bolt.lore3", "Bonus Damage Per Ricochet");
  public static final LinesKey RICOCHET_BOLT_LORE = LinesKey.of("ranged.ricochet_bolt.lore", "Max Ricochets", "Speed Bonus Per Ricochet", "Bonus Damage Per Ricochet");
  public static final TextKey FETCH_SHOT_NAME = TextKey.of("ranged.fetch_shot.name", "Fetch Shot");
  public static final TextKey FETCH_SHOT_DESCRIPTION = TextKey.of("ranged.fetch_shot.description", "Shoot dropped items with projectiles to pull them straight into your inventory.");
  public static final TextKey FETCH_SHOT_LORE1 = TextKey.of("ranged.fetch_shot.lore1", "Fetch Radius");
  public static final LinesKey FETCH_SHOT_LORE = LinesKey.of("ranged.fetch_shot.lore", "Fetch Radius");
  public static final TextKey HEAVY_DRAW_NAME = TextKey.of("ranged.heavy_draw.name", "Heavy Draw");
  public static final TextKey HEAVY_DRAW_DESCRIPTION = TextKey.of("ranged.heavy_draw.description", "Heavier projectiles fly slower but hit far harder.");
  public static final TextKey HEAVY_DRAW_LORE1 = TextKey.of("ranged.heavy_draw.lore1", "Bonus Damage");
  public static final TextKey HEAVY_DRAW_LORE2 = TextKey.of("ranged.heavy_draw.lore2", "Projectile Speed");
  public static final LinesKey HEAVY_DRAW_LORE = LinesKey.of("ranged.heavy_draw.lore", "Bonus Damage", "Projectile Speed");
  public static final TextKey HEARTSEEKER_NAME = TextKey.of("ranged.heartseeker.name", "Heartseeker");
  public static final TextKey HEARTSEEKER_DESCRIPTION = TextKey.of("ranged.heartseeker.description", "Draw a bow while looking at a creature to lock on - it glows red for you, and your arrow whistles and curves through the air to find it no matter where you aim, weaving around obstacles. Piercing levels and Ricochet Bolt's available bounce capacity add seeking passes: the arrow punches through its target, exits the far side, then bends toward a fresh nearby target; without a new target it keeps flying forward. Ricochet passes preserve their reflection, speed, damage, and rewards when seeking arrows strike blocks. Every seeking shot puts your bow on a short cooldown.");
  public static final TextKey HEARTSEEKER_LORE1 = TextKey.of("ranged.heartseeker.lore1", "Draw a bow while looking at a creature to lock on");
  public static final TextKey HEARTSEEKER_LORE2 = TextKey.of("ranged.heartseeker.lore2", "Bow cooldown after a seeking shot");
  public static final TextKey HEARTSEEKER_LORE3 = TextKey.of("ranged.heartseeker.lore3", "Piercing and Ricochet Bolt bounce capacity add seeking passes");
  public static final LinesKey HEARTSEEKER_LORE = LinesKey.of("ranged.heartseeker.lore", "Draw a bow while looking at a creature to lock on", "Bow cooldown after a seeking shot", "Piercing and Ricochet Bolt bounce capacity add seeking passes");

  private RangedMessages() {
  }

  public static void addTo(MessageCatalog.Builder builder) {
    PREFERENCE_KEYS.forEach(builder::add);
    builder.add(ARROW_RECOVERY_NAME);
    builder.add(ARROW_RECOVERY_DESCRIPTION);
    builder.add(ARROW_RECOVERY_LORE1);
    builder.add(ARROW_RECOVERY_LORE2);
    builder.add(ARROW_RECOVERY_CHANCE);
    builder.add(ARROW_RECOVERY_LORE);
    builder.add(WEB_SHOT_NAME);
    builder.add(WEB_SHOT_DESCRIPTION);
    builder.add(WEB_SHOT_LORE1);
    builder.add(WEB_SHOT_LORE2);
    builder.add(WEB_SHOT_LORE);
    builder.add(FORCE_SHOT_NAME);
    builder.add(FORCE_SHOT_DESCRIPTION);
    builder.add(FORCE_SHOT_ADVANCEMENTNAME);
    builder.add(FORCE_SHOT_ADVANCEMENTLORE);
    builder.add(FORCE_SHOT_LORE1);
    builder.add(FORCE_SHOT_LORE);
    builder.add(TRAJECTORY_SIGHT_NAME);
    builder.add(TRAJECTORY_SIGHT_DESCRIPTION);
    builder.add(TRAJECTORY_SIGHT_LORE1);
    builder.add(TRAJECTORY_SIGHT_LORE2);
    builder.add(TRAJECTORY_SIGHT_LORE);
    builder.add(LUNGE_SHOT_NAME);
    builder.add(LUNGE_SHOT_DESCRIPTION);
    builder.add(LUNGE_SHOT_LORE1);
    builder.add(LUNGE_SHOT_LORE);
    builder.add(ARROW_PIERCING_NAME);
    builder.add(ARROW_PIERCING_DESCRIPTION);
    builder.add(ARROW_PIERCING_LORE1);
    builder.add(ARROW_PIERCING_LORE);
    builder.add(FLOATERS_NAME);
    builder.add(FLOATERS_DESCRIPTION);
    builder.add(FLOATERS_LORE1);
    builder.add(FLOATERS_LORE2);
    builder.add(FLOATERS_LORE3);
    builder.add(FLOATERS_LORE);
    builder.add(PINNING_SHOT_NAME);
    builder.add(PINNING_SHOT_DESCRIPTION);
    builder.add(PINNING_SHOT_LORE1);
    builder.add(PINNING_SHOT_LORE2);
    builder.add(PINNING_SHOT_LORE3);
    builder.add(PINNING_SHOT_LORE);
    builder.add(RICOCHET_BOLT_NAME);
    builder.add(RICOCHET_BOLT_DESCRIPTION);
    builder.add(RICOCHET_BOLT_LORE1);
    builder.add(RICOCHET_BOLT_LORE2);
    builder.add(RICOCHET_BOLT_LORE3);
    builder.add(RICOCHET_BOLT_LORE);
    builder.add(FETCH_SHOT_NAME);
    builder.add(FETCH_SHOT_DESCRIPTION);
    builder.add(FETCH_SHOT_LORE1);
    builder.add(FETCH_SHOT_LORE);
    builder.add(HEAVY_DRAW_NAME);
    builder.add(HEAVY_DRAW_DESCRIPTION);
    builder.add(HEAVY_DRAW_LORE1);
    builder.add(HEAVY_DRAW_LORE2);
    builder.add(HEAVY_DRAW_LORE);
    builder.add(HEARTSEEKER_NAME);
    builder.add(HEARTSEEKER_DESCRIPTION);
    builder.add(HEARTSEEKER_LORE1);
    builder.add(HEARTSEEKER_LORE2);
    builder.add(HEARTSEEKER_LORE3);
    builder.add(HEARTSEEKER_LORE);
  }
}
