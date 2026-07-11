package art.arcane.adapt.api.mutation;

import org.bukkit.Material;

import java.util.Locale;

public enum MutationType {
  GALE_LUNG(
      "gale-lung", "Gale Lung", MutationDomain.BODY, MutationDomain.HUNT, Material.FEATHER,
      "Moving fills Momentum. At full Momentum, your next hit pushes or repositions the target.",
      "Enemies push you harder while Momentum is full. Blocking empties it.",
      "Enemies no longer push you harder. Blocking still empties Momentum.",
      "Teal wind gathers around your feet and weapon.",
      "Keep moving to fill Momentum, then land a hit.", true),
  BASTION_SPINE(
      "bastion-spine", "Bastion Spine", MutationDomain.BODY, MutationDomain.INDUSTRY, Material.DEEPSLATE_BRICKS,
      "Standing still builds Stability. Your next shield, axe, or unarmed hit sends out a short push.",
      "While braced, you cannot sprint or jump. A hit from behind breaks the stance.",
      "You can sprint and jump while braced. Hits from behind can still break it.",
      "Stone ribs and bright cracks show your stored force.",
      "Stand still on the ground, then strike with a shield, axe, or empty hand.", true),
  VERDANT_MOLT(
      "verdant-molt", "Verdant Molt", MutationDomain.BODY, MutationDomain.WILD, Material.MOSS_BLOCK,
      "Sneak without moving on natural ground to clear harmful effects.",
      "It also clears helpful effects, costs hunger, and briefly blocks new effects.",
      "Helpful effects and hunger are no longer lost. The short effect block stays.",
      "Leaves, scales, or spores burst away from you.",
      "Hold sneak and stay still on natural ground.", false),
  TEMPERBOUND(
      "temperbound", "Temperbound", MutationDomain.BODY, MutationDomain.CRAFT, Material.ANVIL,
      "Link one armor set so its pieces share durability. A piece that would break becomes Cracked instead.",
      "Removing or swapping a linked piece disables the set for a short time.",
      "Swapping armor no longer disables the set. Only one set can stay linked.",
      "Glowing lines connect the linked armor pieces.",
      "At an Adapt bookshelf, wear four crafted armor pieces and choose Link Current Armor.", false),
  PARADOX_SCAR(
      "paradox-scar", "Paradox Scar", MutationDomain.BODY, MutationDomain.ANOMALY, Material.RECOVERY_COMPASS,
      "A long move or teleport leaves a return point. You can jump back to it once.",
      "The return point is visible, blocks another one from forming, and enemies can break it.",
      "It no longer blocks other Mutation return effects. Enemies can still break it.",
      "A bright afterimage marks the return point.",
      "After a long move or teleport, sneak and swap hands to return.", true),
  ARSENAL_CORTEX(
      "arsenal-cortex", "Arsenal Cortex", MutationDomain.HUNT, MutationDomain.INDUSTRY, Material.SMITHING_TABLE,
      "Changing weapon or tool types between hits carries one control effect into the next hit.",
      "Using the same type twice locks the combo for a short time.",
      "Repeating a type no longer locks the combo. Switching types is still needed to build it.",
      "A changing symbol spins around the held item.",
      "Land hits with different weapon or tool types.", true),
  PACKMIND(
      "packmind", "Packmind", MutationDomain.HUNT, MutationDomain.WILD, Material.LEAD,
      "Your first hit marks a target. Pets and opted-in allies build Tempo by hitting it too.",
      "Your damage is lower until someone else joins in. Solo Tempo quickly fades.",
      "Your damage is no longer lowered while waiting. Tempo still needs another participant.",
      "Amber lines connect the group to the marked target.",
      "Hit a target, then have a pet or opted-in ally attack it too.", true),
  TROPHY_CRUCIBLE(
      "trophy-crucible", "Trophy Crucible", MutationDomain.HUNT, MutationDomain.CRAFT, Material.SKELETON_SKULL,
      "Natural mobs can drop trophies. Use one to prepare a control effect against that mob type.",
      "That mob type spots you more easily and sees through Mutation stealth.",
      "The extra detection is removed. You can still store only one trophy effect.",
      "A mask showing the stored mob type appears behind you.",
      "Sneak-right-click a crafting table while holding a natural trophy.", true),
  UMBRAL_ECHO(
      "umbral-echo", "Umbral Echo", MutationDomain.HUNT, MutationDomain.ANOMALY, Material.ECHO_SHARD,
      "Attack from a new angle or with a new weapon type to repeat a weaker control effect after a delay.",
      "Repeating the same approach reveals you and briefly shuts down stealth.",
      "Repeating an approach no longer reveals you. New angles or weapon types are still required.",
      "A dark purple afterimage repeats the control effect.",
      "Change your attack angle or weapon type between hits.", true),
  LIVING_LATTICE(
      "living-lattice", "Living Lattice", MutationDomain.INDUSTRY, MutationDomain.WILD, Material.MANGROVE_ROOTS,
      "Harvesting and replanting earns Root Charge. Spend it to grow a short temporary path.",
      "Fire and lava can wipe your charge, and a forced collapse costs hunger.",
      "Fire and lava no longer wipe charge. The paths still disappear.",
      "Green roots turn brown before the path disappears.",
      "Harvest and replant, then sneak-right-click while holding a sapling.", false),
  MASTERWORK_BOND(
      "masterwork-bond", "Masterwork Bond", MutationDomain.INDUSTRY, MutationDomain.CRAFT, Material.NETHERITE_PICKAXE,
      "Bind one tool you crafted. It stops at one durability instead of breaking and must be repaired before use.",
      "Only the bound tool gets protection. If it is lost, you must wait before binding another.",
      "Other tools work normally with Mutation effects. Only the Masterwork avoids breaking.",
      "Runes on the tool crack as it nears breaking.",
      "At an Adapt bookshelf, hold a tool you crafted and choose Bind Held Tool.", false),
  DEEPBLOOD(
      "deepblood", "Deepblood", MutationDomain.INDUSTRY, MutationDomain.ANOMALY, Material.DEEPSLATE_DIAMOND_ORE,
      "Mining natural blocks deep underground builds Deep Charge. It powers healing and saves one bound tool.",
      "Underground healing spends Deep Charge, and the charge slowly drains above ground.",
      "Underground healing works at zero charge. Saving the bound tool still costs charge.",
      "Red cracks spread across you and the bound tool as charge builds.",
      "Mine natural deep blocks and bind one tool at an Adapt bookshelf.", false),
  MYCELIAL_NERVE(
      "mycelial-nerve", "Mycelial Nerve", MutationDomain.WILD, MutationDomain.CRAFT, Material.SPORE_BLOSSOM,
      "Helpful effects you give yourself spread in weaker form to nearby opted-in players and pets.",
      "Your own effect lasts less time, and taking fire damage breaks the link.",
      "Your effect keeps its full time and fire no longer breaks the link.",
      "Spore trails carry copied effects to nearby allies.",
      "Give yourself a helpful effect near opted-in players or owned pets.", false),
  GRAVEBLOOM(
      "gravebloom", "Gravebloom", MutationDomain.WILD, MutationDomain.ANOMALY, Material.WITHER_ROSE,
      "Killing a natural hostile mob can create a short-lived bloom that grows crops and heals your pets.",
      "Active blooms weaken your natural healing, and older blooms attract hostile mobs.",
      "Blooms no longer weaken your healing. They still disappear after a short time.",
      "Pale flowers and soul particles rise from the death spot.",
      "Kill a natural hostile mob on natural ground.", false),
  RESONANT_FORMULA(
      "resonant-formula", "Resonant Formula", MutationDomain.CRAFT, MutationDomain.ANOMALY, Material.ENCHANTED_BOOK,
      "Craft, brew, and enchant once each to prepare a combo. Your next non-damaging Anomaly effect repeats at half strength.",
      "Repeating the same prep step breaks the combo and removes your oldest helpful effect.",
      "Repeating a step no longer breaks the combo. You still need all three different steps.",
      "Three symbols join together when the combo is ready.",
      "Craft, brew, and enchant once each, in any order.", true);

  private final String id;
  private final String displayName;
  private final MutationDomain firstDomain;
  private final MutationDomain secondDomain;
  private final Material icon;
  private final String benefit;
  private final String burden;
  private final String perfectResult;
  private final String tell;
  private final String control;
  private final boolean pvpRelevant;
  private final String permission;

  MutationType(
      String id,
      String displayName,
      MutationDomain firstDomain,
      MutationDomain secondDomain,
      Material icon,
      String benefit,
      String burden,
      String perfectResult,
      String tell,
      String control,
      boolean pvpRelevant
  ) {
    this.id = id;
    this.displayName = displayName;
    this.firstDomain = firstDomain;
    this.secondDomain = secondDomain;
    this.icon = icon;
    this.benefit = benefit;
    this.burden = burden;
    this.perfectResult = perfectResult;
    this.tell = tell;
    this.control = control;
    this.pvpRelevant = pvpRelevant;
    permission = "adapt.use.mutation." + id;
  }

  public String id() {
    return id;
  }

  public String displayName() {
    return displayName;
  }

  public MutationDomain firstDomain() {
    return firstDomain;
  }

  public MutationDomain secondDomain() {
    return secondDomain;
  }

  public MutationLineage lineage() {
    return MutationLineage.of(firstDomain, secondDomain);
  }

  public Material icon() {
    return icon;
  }

  public String benefit() {
    return benefit;
  }

  public String burden() {
    return burden;
  }

  public String perfectResult() {
    return perfectResult;
  }

  public String tell() {
    return tell;
  }

  public String control() {
    return control;
  }

  public boolean pvpRelevant() {
    return pvpRelevant;
  }

  public String permission() {
    return permission;
  }

  public static MutationType find(String id) {
    if (id == null || id.isBlank()) {
      return null;
    }
    String normalized = id.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    for (MutationType type : values()) {
      if (type.id.equals(normalized)) {
        return type;
      }
    }
    return null;
  }
}
