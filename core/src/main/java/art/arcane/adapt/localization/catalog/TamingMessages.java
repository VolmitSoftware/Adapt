package art.arcane.adapt.localization.catalog;

import art.arcane.volmlib.util.localization.LinesKey;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;

public final class TamingMessages {
  public static final TextKey PREFERENCE_SPEED = TextKey.of("taming.preferences.speed", "Speed bonus");
  public static final TextKey PREFERENCE_VISUALS = TextKey.of("taming.preferences.visuals", "Bond particles and glow");
  public static final TextKey PREFERENCE_SITTING = TextKey.of("taming.preferences.sitting", "Include sitting pets");
  public static final TextKey PREFERENCE_HANDLING = TextKey.of("taming.preferences.handling", "Mount speed and jump");
  public static final TextKey PREFERENCE_COMBAT = TextKey.of("taming.preferences.combat", "Mounted combat bonuses");
  public static final TextKey PREFERENCE_REGENERATION = TextKey.of("taming.preferences.regeneration", "Pack regeneration");
  public static final TextKey PREFERENCE_TAMING = TextKey.of("taming.preferences.taming", "Improved taming");
  public static final TextKey PREFERENCE_PACIFY = TextKey.of("taming.preferences.pacify", "Pacify neutral mobs");
  public static final TextKey PREFERENCE_PETS = TextKey.of("taming.preferences.pets", "Eligible pet types");
  public static final TextKey PREFERENCE_ALL = TextKey.of("taming.preferences.all", "All permitted pets");
  public static final TextKey PREFERENCE_WOLVES = TextKey.of("taming.preferences.wolves", "Wolves only");
  public static final TextKey PREFERENCE_CATS = TextKey.of("taming.preferences.cats", "Cats only");
  public static final TextKey PREFERENCE_HORSES = TextKey.of("taming.preferences.horses", "Horses and equines");
  public static final TextKey PREFERENCE_TARGETS = TextKey.of("taming.preferences.targets", "Command targets");
  public static final TextKey PREFERENCE_ALL_TARGETS = TextKey.of("taming.preferences.all_targets", "All permitted targets");
  public static final TextKey PREFERENCE_HOSTILE = TextKey.of("taming.preferences.hostile", "Hostile mobs");
  public static final TextKey PREFERENCE_NON_PLAYERS = TextKey.of("taming.preferences.non_players", "Non-player targets");
  public static final TextKey PREFERENCE_BONES = TextKey.of("taming.preferences.bones", "Bones remaining after command");
  public static final TextKey PREFERENCE_NONE = TextKey.of("taming.preferences.none", "No extra reserve");
  public static final TextKey PREFERENCE_FOUR = TextKey.of("taming.preferences.four", "Keep 4");
  public static final TextKey PREFERENCE_EIGHT = TextKey.of("taming.preferences.eight", "Keep 8");
  public static final TextKey PREFERENCE_HUNGER = TextKey.of("taming.preferences.hunger", "Hunger remaining after recall");
  public static final TextKey PREFERENCE_HEALTH = TextKey.of("taming.preferences.health", "Pet health remaining after protection");
  public static final TextKey PREFERENCE_SERVER_HEALTH = TextKey.of("taming.preferences.server_health", "Server minimum");
  public static final TextKey PREFERENCE_HALF = TextKey.of("taming.preferences.half", "At least 50% health");
  public static final TextKey PREFERENCE_THREE_QUARTERS = TextKey.of("taming.preferences.three_quarters", "At least 75% health");
  public static final TextKey PREFERENCE_ITEMS = TextKey.of("taming.preferences.items", "Items to fetch");
  public static final TextKey PREFERENCE_ALL_ITEMS = TextKey.of("taming.preferences.all_items", "All permitted items");
  public static final TextKey PREFERENCE_FOOD = TextKey.of("taming.preferences.food", "Food only");
  public static final TextKey PREFERENCE_BLOCKS = TextKey.of("taming.preferences.blocks", "Blocks only");
  public static final TextKey PREFERENCE_VALUABLES = TextKey.of("taming.preferences.valuables", "Ores and minerals");
  public static final TextKey PREFERENCE_COLLAR = TextKey.of("taming.preferences.collar", "Wolves allowed to fetch");
  public static final TextKey PREFERENCE_ALL_COLLARS = TextKey.of("taming.preferences.all_collars", "All collar colors");
  public static final TextKey PREFERENCE_RED = TextKey.of("taming.preferences.red", "Red collars");
  public static final TextKey PREFERENCE_BLUE = TextKey.of("taming.preferences.blue", "Blue collars");
  public static final TextKey PREFERENCE_YELLOW = TextKey.of("taming.preferences.yellow", "Yellow collars");
  private static final List<TextKey> PREFERENCE_KEYS = List.of(
      PREFERENCE_SPEED,
      PREFERENCE_VISUALS,
      PREFERENCE_SITTING,
      PREFERENCE_HANDLING,
      PREFERENCE_COMBAT,
      PREFERENCE_REGENERATION,
      PREFERENCE_TAMING,
      PREFERENCE_PACIFY,
      PREFERENCE_PETS,
      PREFERENCE_ALL,
      PREFERENCE_WOLVES,
      PREFERENCE_CATS,
      PREFERENCE_HORSES,
      PREFERENCE_TARGETS,
      PREFERENCE_ALL_TARGETS,
      PREFERENCE_HOSTILE,
      PREFERENCE_NON_PLAYERS,
      PREFERENCE_BONES,
      PREFERENCE_NONE,
      PREFERENCE_FOUR,
      PREFERENCE_EIGHT,
      PREFERENCE_HUNGER,
      PREFERENCE_HEALTH,
      PREFERENCE_SERVER_HEALTH,
      PREFERENCE_HALF,
      PREFERENCE_THREE_QUARTERS,
      PREFERENCE_ITEMS,
      PREFERENCE_ALL_ITEMS,
      PREFERENCE_FOOD,
      PREFERENCE_BLOCKS,
      PREFERENCE_VALUABLES,
      PREFERENCE_COLLAR,
      PREFERENCE_ALL_COLLARS,
      PREFERENCE_RED,
      PREFERENCE_BLUE,
      PREFERENCE_YELLOW);
  public static final TextKey DAMAGE_NAME = TextKey.of("taming.damage.name", "Tame Damage");
  public static final TextKey DAMAGE_DESCRIPTION = TextKey.of("taming.damage.description", "Increase your tamed animal damage dealt.");
  public static final TextKey DAMAGE_LORE1 = TextKey.of("taming.damage.lore1", "Increased Damage");
  public static final LinesKey DAMAGE_LORE = LinesKey.of("taming.damage.lore", "Increased Damage");
  public static final TextKey HEALTH_NAME = TextKey.of("taming.health.name", "Tame Health");
  public static final TextKey HEALTH_DESCRIPTION = TextKey.of("taming.health.description", "Increase your tamed animal health.");
  public static final TextKey HEALTH_LORE1 = TextKey.of("taming.health.lore1", "Increased Health");
  public static final LinesKey HEALTH_LORE = LinesKey.of("taming.health.lore", "Increased Health");
  public static final TextKey REGENERATION_NAME = TextKey.of("taming.regeneration.name", "Tame Regeneration");
  public static final TextKey REGENERATION_DESCRIPTION = TextKey.of("taming.regeneration.description", "Increase your tamed animal regeneration.");
  public static final TextKey REGENERATION_LORE1 = TextKey.of("taming.regeneration.lore1", "HP/s");
  public static final LinesKey REGENERATION_LORE = LinesKey.of("taming.regeneration.lore", "HP/s");
  public static final TextKey PACK_LEADER_AURA_NAME = TextKey.of("taming.pack_leader_aura.name", "Pack Leader Aura");
  public static final TextKey PACK_LEADER_AURA_DESCRIPTION = TextKey.of("taming.pack_leader_aura.description", "Nearby tamed companions gain speed and regeneration near their owner.");
  public static final TextKey PACK_LEADER_AURA_LORE1 = TextKey.of("taming.pack_leader_aura.lore1", "Aura Radius");
  public static final TextKey PACK_LEADER_AURA_LORE2 = TextKey.of("taming.pack_leader_aura.lore2", "Aura Strength");
  public static final LinesKey PACK_LEADER_AURA_LORE = LinesKey.of("taming.pack_leader_aura.lore", "Aura Radius", "Aura Strength");
  public static final TextKey BEAST_RECALL_NAME = TextKey.of("taming.beast_recall.name", "Beast Recall");
  public static final TextKey BEAST_RECALL_DESCRIPTION = TextKey.of("taming.beast_recall.description", "Sneak-right-click with a lead to recall your nearest tamed companion to a safe nearby spot. Each recall costs hunger.");
  public static final TextKey BEAST_RECALL_LORE1 = TextKey.of("taming.beast_recall.lore1", "Recall Radius");
  public static final TextKey BEAST_RECALL_LORE2 = TextKey.of("taming.beast_recall.lore2", "Recall Cooldown");
  public static final LinesKey BEAST_RECALL_LORE = LinesKey.of("taming.beast_recall.lore", "Recall Radius", "Recall Cooldown");
  public static final TextKey BEAST_RECALL_LORE_COST_HUNGER = TextKey.of("taming.beast_recall.lore_cost_hunger", "Hunger cost per recall");
  public static final TextKey SHARED_PAIN_NAME = TextKey.of("taming.shared_pain.name", "Shared Pain");
  public static final TextKey SHARED_PAIN_DESCRIPTION = TextKey.of("taming.shared_pain.description", "Spread a portion of your incoming damage across nearby owned companions without reducing them below their health floor.");
  public static final TextKey SHARED_PAIN_LORE1 = TextKey.of("taming.shared_pain.lore1", "Shared Damage");
  public static final TextKey SHARED_PAIN_LORE2 = TextKey.of("taming.shared_pain.lore2", "Companion Health Floor");
  public static final LinesKey SHARED_PAIN_LORE = LinesKey.of("taming.shared_pain.lore", "Shared Damage", "Companion Health Floor");
  public static final TextKey MOUNTED_TACTICS_NAME = TextKey.of("taming.mounted_tactics.name", "Mounted Tactics");
  public static final TextKey MOUNTED_TACTICS_DESCRIPTION = TextKey.of("taming.mounted_tactics.description", "Gain mount-specific combat and handling bonuses while riding.");
  public static final TextKey MOUNTED_TACTICS_LORE1 = TextKey.of("taming.mounted_tactics.lore1", "Mounted Damage Bonus");
  public static final TextKey MOUNTED_TACTICS_LORE2 = TextKey.of("taming.mounted_tactics.lore2", "Mounted Damage Reduction");
  public static final LinesKey MOUNTED_TACTICS_LORE = LinesKey.of("taming.mounted_tactics.lore", "Mounted Damage Bonus", "Mounted Damage Reduction");
  public static final TextKey FETCH_NAME = TextKey.of("taming.fetch.name", "Fetch");
  public static final TextKey FETCH_DESCRIPTION = TextKey.of("taming.fetch.description", "Idle tamed wolves physically run to nearby drops, pick them up, and carry them back to you.");
  public static final TextKey FETCH_LORE1 = TextKey.of("taming.fetch.lore1", "Fetch Range");
  public static final TextKey FETCH_LORE2 = TextKey.of("taming.fetch.lore2", "Carry Chance");
  public static final LinesKey FETCH_LORE = LinesKey.of("taming.fetch.lore", "Fetch Range", "Carry Chance");
  public static final TextKey ALPHAS_COMMAND_NAME = TextKey.of("taming.alphas_command.name", "Alpha's Command");
  public static final TextKey ALPHAS_COMMAND_DESCRIPTION = TextKey.of("taming.alphas_command.description", "Sneak-left-click while holding a bone to raycast a target, mark it with a private red glow, and command nearby combat pets to focus it. Successful commands consume one bone.");
  public static final TextKey ALPHAS_COMMAND_LORE1 = TextKey.of("taming.alphas_command.lore1", "Command Range");
  public static final TextKey ALPHAS_COMMAND_LORE2 = TextKey.of("taming.alphas_command.lore2", "Focus Duration");
  public static final LinesKey ALPHAS_COMMAND_LORE = LinesKey.of("taming.alphas_command.lore", "Command Range", "Focus Duration");
  public static final TextKey GUARDIAN_INSTINCT_NAME = TextKey.of("taming.guardian_instinct.name", "Guardian Instinct");
  public static final TextKey GUARDIAN_INSTINCT_DESCRIPTION = TextKey.of("taming.guardian_instinct.description", "Nearby pets leap to intercept projectiles aimed at you, taking reduced damage themselves.");
  public static final TextKey GUARDIAN_INSTINCT_LORE1 = TextKey.of("taming.guardian_instinct.lore1", "Intercept Chance");
  public static final TextKey GUARDIAN_INSTINCT_LORE2 = TextKey.of("taming.guardian_instinct.lore2", "Pet Damage Reduction");
  public static final LinesKey GUARDIAN_INSTINCT_LORE = LinesKey.of("taming.guardian_instinct.lore", "Intercept Chance", "Pet Damage Reduction");
  public static final TextKey STABLE_HAND_NAME = TextKey.of("taming.stable_hand.name", "Stable Hand");
  public static final TextKey STABLE_HAND_DESCRIPTION = TextKey.of("taming.stable_hand.description", "Animals you tame or breed keep a permanent bias toward better speed, jump, health, and safe fall distance.");
  public static final TextKey STABLE_HAND_LORE1 = TextKey.of("taming.stable_hand.lore1", "Attribute Bias");
  public static final TextKey STABLE_HAND_LORE2 = TextKey.of("taming.stable_hand.lore2", "Safe Fall Blocks");
  public static final LinesKey STABLE_HAND_LORE = LinesKey.of("taming.stable_hand.lore", "Attribute Bias", "Safe Fall Blocks");
  public static final TextKey WILD_EMPATHY_NAME = TextKey.of("taming.wild_empathy.name", "Wild Empathy");
  public static final TextKey WILD_EMPATHY_DESCRIPTION = TextKey.of("taming.wild_empathy.description", "Taming succeeds more often, and neutral mobs are slower to anger at you.");
  public static final TextKey WILD_EMPATHY_LORE1 = TextKey.of("taming.wild_empathy.lore1", "Extra Taming Odds");
  public static final TextKey WILD_EMPATHY_LORE2 = TextKey.of("taming.wild_empathy.lore2", "Anger Resistance");
  public static final LinesKey WILD_EMPATHY_LORE = LinesKey.of("taming.wild_empathy.lore", "Extra Taming Odds", "Anger Resistance");
  public static final TextKey BATTLE_BOND_NAME = TextKey.of("taming.battle_bond.name", "Battle Bond");
  public static final TextKey BATTLE_BOND_DESCRIPTION = TextKey.of("taming.battle_bond.description", "When one of your pets lands a kill, you and the nearby pack gain brief strength, speed, and regeneration with a visible bond.");
  public static final TextKey BATTLE_BOND_LORE1 = TextKey.of("taming.battle_bond.lore1", "Buff Tier");
  public static final TextKey BATTLE_BOND_LORE2 = TextKey.of("taming.battle_bond.lore2", "Buff Duration");
  public static final LinesKey BATTLE_BOND_LORE = LinesKey.of("taming.battle_bond.lore", "Buff Tier", "Buff Duration");
  public static final TextKey LAST_BREATH_NAME = TextKey.of("taming.last_breath.name", "Last Breath");
  public static final TextKey LAST_BREATH_DESCRIPTION = TextKey.of("taming.last_breath.description", "On a lethal hit a pet drops to 1 HP, is briefly invulnerable, and recalls to your side.");
  public static final TextKey LAST_BREATH_LORE1 = TextKey.of("taming.last_breath.lore1", "Per-Pet Cooldown");
  public static final TextKey LAST_BREATH_LORE2 = TextKey.of("taming.last_breath.lore2", "Invulnerability");
  public static final TextKey ALPHA_LORE1 = TextKey.of("upgrade.taming.alpha.lore1", "Pet Damage Bonus");
  public static final TextKey ALPHA_LORE2 = TextKey.of("upgrade.taming.alpha.lore2", "Pet Maximum Health Bonus");
  public static final TextKey ALPHA_LORE3 = TextKey.of("upgrade.taming.alpha.lore3", "Aura Radius");
  public static final TextKey ONE_MIND_LORE1 = TextKey.of("upgrade.taming.one_mind.lore1", "Marking Range");
  public static final TextKey ONE_MIND_LORE2 = TextKey.of("upgrade.taming.one_mind.lore2", "Recall Radius");
  public static final TextKey ONE_MIND_LORE3 = TextKey.of("upgrade.taming.one_mind.lore3", "Recall Cooldown");
  public static final TextKey ONE_MIND_LORE4 = TextKey.of("upgrade.taming.one_mind.lore4", "Roles for Named Pets");
  public static final TextKey ALPHA_LORE4 = TextKey.of("upgrade.taming.alpha.lore4", "Bonded Mount Growth Cap");
  public static final TextKey ALPHA_LORE5 = TextKey.of("upgrade.taming.alpha.lore5", "Bonded Mount Call Range");
  public static final TextKey WHISTLE_NAME = TextKey.of("tool.taming.whistle.name", "Beastmaster's Whistle");
  public static final TextKey WHISTLE_LORE1 = TextKey.of("tool.taming.whistle.lore1", "Right-click: Send Pets");
  public static final TextKey WHISTLE_LORE2 = TextKey.of("tool.taming.whistle.lore2", "Sneak + Right-click: Recall");
  public static final TextKey WHISTLE_LORE3 = TextKey.of("tool.taming.whistle.lore3", "Left-click: Menu");
  public static final TextKey WHISTLE_FETCH_ON = TextKey.of("tool.taming.whistle.fetch_on", "Fetch: On");
  public static final TextKey WHISTLE_FETCH_OFF = TextKey.of("tool.taming.whistle.fetch_off", "Fetch: Off");
  public static final TextKey WHISTLE_BOND = TextKey.of("tool.taming.whistle.bond", "Bond This Mount");
  public static final TextKey WHISTLE_BONDED = TextKey.of("tool.taming.whistle.bonded", "Bonded Mount");
  public static final TextKey WHISTLE_ROLE = TextKey.of("tool.taming.whistle.role", "Role: {role}");
  public static final TextKey WHISTLE_ROLE_NONE = TextKey.of("tool.taming.whistle.role_none", "None");
  public static final TextKey WHISTLE_ROLE_GUARD_OWNER = TextKey.of("tool.taming.whistle.role_guard_owner", "Guard You");
  public static final TextKey WHISTLE_ROLE_GUARD_SPOT = TextKey.of("tool.taming.whistle.role_guard_spot", "Guard This Spot");
  public static final TextKey WHISTLE_ROLE_FETCH = TextKey.of("tool.taming.whistle.role_fetch", "Fetch");
  public static final TextKey WHISTLE_CANNOT_BUILD = TextKey.of("tool.taming.whistle.cannot_build", "You cannot build here");
  public static final TextKey BLOOD_BOND_LORE1 = TextKey.of("upgrade.taming.blood_bond.lore1", "Damage Shared with Pets");
  public static final TextKey BLOOD_BOND_LORE2 = TextKey.of("upgrade.taming.blood_bond.lore2", "Shot Intercept Chance");
  public static final TextKey BLOOD_BOND_LORE3 = TextKey.of("upgrade.taming.blood_bond.lore3", "Per-Pet Save Cooldown");
  public static final LinesKey LAST_BREATH_LORE = LinesKey.of("taming.last_breath.lore", "Per-Pet Cooldown", "Invulnerability");

  private TamingMessages() {
  }

  public static void addTo(MessageCatalog.Builder builder) {
    PREFERENCE_KEYS.forEach(builder::add);
    builder.add(DAMAGE_NAME);
    builder.add(DAMAGE_DESCRIPTION);
    builder.add(DAMAGE_LORE1);
    builder.add(DAMAGE_LORE);
    builder.add(HEALTH_NAME);
    builder.add(HEALTH_DESCRIPTION);
    builder.add(HEALTH_LORE1);
    builder.add(HEALTH_LORE);
    builder.add(REGENERATION_NAME);
    builder.add(REGENERATION_DESCRIPTION);
    builder.add(REGENERATION_LORE1);
    builder.add(REGENERATION_LORE);
    builder.add(PACK_LEADER_AURA_NAME);
    builder.add(PACK_LEADER_AURA_DESCRIPTION);
    builder.add(PACK_LEADER_AURA_LORE1);
    builder.add(PACK_LEADER_AURA_LORE2);
    builder.add(PACK_LEADER_AURA_LORE);
    builder.add(BEAST_RECALL_NAME);
    builder.add(BEAST_RECALL_DESCRIPTION);
    builder.add(BEAST_RECALL_LORE1);
    builder.add(BEAST_RECALL_LORE2);
    builder.add(BEAST_RECALL_LORE);
    builder.add(BEAST_RECALL_LORE_COST_HUNGER);
    builder.add(SHARED_PAIN_NAME);
    builder.add(SHARED_PAIN_DESCRIPTION);
    builder.add(SHARED_PAIN_LORE1);
    builder.add(SHARED_PAIN_LORE2);
    builder.add(SHARED_PAIN_LORE);
    builder.add(MOUNTED_TACTICS_NAME);
    builder.add(MOUNTED_TACTICS_DESCRIPTION);
    builder.add(MOUNTED_TACTICS_LORE1);
    builder.add(MOUNTED_TACTICS_LORE2);
    builder.add(MOUNTED_TACTICS_LORE);
    builder.add(FETCH_NAME);
    builder.add(FETCH_DESCRIPTION);
    builder.add(FETCH_LORE1);
    builder.add(FETCH_LORE2);
    builder.add(FETCH_LORE);
    builder.add(ALPHAS_COMMAND_NAME);
    builder.add(ALPHAS_COMMAND_DESCRIPTION);
    builder.add(ALPHAS_COMMAND_LORE1);
    builder.add(ALPHAS_COMMAND_LORE2);
    builder.add(ALPHAS_COMMAND_LORE);
    builder.add(GUARDIAN_INSTINCT_NAME);
    builder.add(GUARDIAN_INSTINCT_DESCRIPTION);
    builder.add(GUARDIAN_INSTINCT_LORE1);
    builder.add(GUARDIAN_INSTINCT_LORE2);
    builder.add(GUARDIAN_INSTINCT_LORE);
    builder.add(STABLE_HAND_NAME);
    builder.add(STABLE_HAND_DESCRIPTION);
    builder.add(STABLE_HAND_LORE1);
    builder.add(STABLE_HAND_LORE2);
    builder.add(STABLE_HAND_LORE);
    builder.add(WILD_EMPATHY_NAME);
    builder.add(WILD_EMPATHY_DESCRIPTION);
    builder.add(WILD_EMPATHY_LORE1);
    builder.add(WILD_EMPATHY_LORE2);
    builder.add(WILD_EMPATHY_LORE);
    builder.add(BATTLE_BOND_NAME);
    builder.add(BATTLE_BOND_DESCRIPTION);
    builder.add(BATTLE_BOND_LORE1);
    builder.add(BATTLE_BOND_LORE2);
    builder.add(BATTLE_BOND_LORE);
    builder.add(LAST_BREATH_NAME);
    builder.add(LAST_BREATH_DESCRIPTION);
    builder.add(LAST_BREATH_LORE1);
    builder.add(LAST_BREATH_LORE2);
    builder.add(LAST_BREATH_LORE);
    builder.add(ALPHA_LORE1);
    builder.add(ALPHA_LORE2);
    builder.add(ALPHA_LORE3);
    builder.add(ONE_MIND_LORE1);
    builder.add(ONE_MIND_LORE2);
    builder.add(ONE_MIND_LORE3);
    builder.add(ONE_MIND_LORE4);
    builder.add(ALPHA_LORE4);
    builder.add(ALPHA_LORE5);
    builder.add(WHISTLE_NAME);
    builder.add(WHISTLE_LORE1);
    builder.add(WHISTLE_LORE2);
    builder.add(WHISTLE_LORE3);
    builder.add(WHISTLE_FETCH_ON);
    builder.add(WHISTLE_FETCH_OFF);
    builder.add(WHISTLE_BOND);
    builder.add(WHISTLE_BONDED);
    builder.add(WHISTLE_ROLE);
    builder.add(WHISTLE_ROLE_NONE);
    builder.add(WHISTLE_ROLE_GUARD_OWNER);
    builder.add(WHISTLE_ROLE_GUARD_SPOT);
    builder.add(WHISTLE_ROLE_FETCH);
    builder.add(WHISTLE_CANNOT_BUILD);
    builder.add(BLOOD_BOND_LORE1);
    builder.add(BLOOD_BOND_LORE2);
    builder.add(BLOOD_BOND_LORE3);
  }
}
