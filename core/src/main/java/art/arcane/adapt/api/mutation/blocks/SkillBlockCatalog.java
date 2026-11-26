package art.arcane.adapt.api.mutation.blocks;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The words, blocks and upgrades of all 24 skills, in the order of the mutation plan.
 */
public final class SkillBlockCatalog {
  private static final List<SkillBlocks> SKILLS = List.of(
      skill("swords", "Swords", "Duelist", "Bladed", TriggerKind.FIGHT,
          "land a sword critical hit",
          "the target bleeds for 4 s",
          "grass, leaves and crops in a 3-block arc are cut and collected",
          "+15% attack speed for 5 s",
          "5 durability from your sword",
          "you hit an enemy with a sword in the last 4 s",
          List.of(
            upgrade("Witherblade", "Sword hits apply Wither that also bleeds; the target cannot regenerate while it lasts.",
                "Poisoned Blade", "Bloody Blade"),
            toolUpgrade("Tempest Flow", "Hits build attack speed, a full-stack crit releases the area slash, and a Whetstone grinds swords Serrated, Weighted or Keen.",
                "Blade Flow", "Crimson Cyclone"),
            upgrade("Duelist's Verdict", "Against one engaged foe you deal more and take less, finishing under 30% health, and named swords awaken through natural kills.",
                "Duelist's Focus", "Executioner's Edge"))),
      skill("axes", "Axes", "Reaver", "Cleaving", TriggerKind.FIGHT,
          "land a fully charged axe hit",
          "the target's armor is cut by 15% for 6 s",
          "the next tree you fell drops stripped logs or planks and replants itself",
          "2 absorption hearts for 6 s",
          "5 durability from your axe",
          "you chopped wood or hit with an axe in the last 4 s",
          List.of(
            upgrade("Timberfall", "One swing fells the whole tree, leaves included, straight to your inventory.",
                "Axe Chop", "Wood-Miner", "Leaf-Miner"),
            upgrade("Sundering Cleave", "Swings hit everything in a frontal arc and shred armor on all of it.",
                "Sunder", "Cleave"),
            upgrade("Worldsplitter", "A thrown axe smashes the ground where it lands, then flies back through everything in its way.",
                "Axe Ground Smash", "Throwing Axe"))),
      skill("unarmed", "Unarmed", "Brawler", "Iron-Fisted", TriggerKind.FIGHT,
          "land a third bare-hand hit in a row",
          "the target is knocked back and weakened for 3 s",
          "sneaking still for 3 s within 30 s gives absorption hearts",
          "regeneration for 3 s, and bare-hand kills restore food for 15 s",
          "1 point of hunger",
          "your hands are empty and you are in combat",
          List.of(
            upgrade("Titan Fists", "Fists hit like the maxed Unarmed originals and more, and a sprint punch breaks through soft blocks.",
                "Unarmed Power", "Iron Fists", "Glass Cannon", "Sucker Punch", "Battering Charge"),
            upgrade("Unbroken Combo", "Combo stacks survive being hit, each stack slows the target, and a sneaking hit at full stacks spends them all.",
                "Combo Chain", "Pressure Point", "Disarm"),
            upgrade("Seismic Throw", "A thrown enemy lands with a shockwave.",
                "Grapple", "Shockwave Clap"))),
      skill("ranged", "Ranged", "Marksman", "Far-Sighted", TriggerKind.FIGHT,
          "hit with a projectile from 15 or more blocks",
          "a spectral arrow fires at the target",
          "drops within 8 blocks fly to you",
          "an instant draw on your next shot",
          "1 arrow",
          "you are at full draw",
          List.of(
            toolUpgrade("Stormshot", "Arrows fly faster, hit harder and pierce, and a Quiver stores and recovers them.",
                "Force Shot", "Heavy Draw", "Arrow Piercing"),
            upgrade("Hunter's Web", "Snare Arrows cage what they hit in webs and hold it off the ground.",
                "Web Snare", "Pinning Shot", "Floaters"),
            upgrade("Trueflight", "Locked arrows curve to the target, Ricochet Arrows bounce on, and a Volley locks several.",
                "Heartseeker", "Ricochet Bolt", "Trajectory Sight"))),
      skill("blocking", "Blocking", "Bulwark", "Shielded", TriggerKind.FIGHT,
          "block a hit with your shield",
          "the target is staggered and cannot block for 2 s",
          "the next hit you take within 10 s is halved",
          "30% knockback and projectile resistance for 5 s",
          "10 shield durability",
          "your shield is raised",
          List.of(
            upgrade("Mirror Guard", "A timed block negates the hit and reflects it, melee or projectile.",
                "Perfect Guard", "Mirror Block", "Counter Guard"),
            upgrade("Living Wall", "Plant your shield as a timed wall that shelters everyone behind it and slows hostile mobs.",
                "Shield Wall", "Interpose", "Bastion Stance"),
            upgrade("Tempered Aegis", "Blocked hits from real foes repair shield and armor, axes cannot disable your shield, and a sprint-jump bash slams a foe.",
                "Tempered Guard", "Shieldbearer's Resolve"))),
      skill("stealth", "Stealth", "Shade", "Silent", TriggerKind.FIGHT,
          "strike a target that is not looking at you",
          "mobs within 8 blocks lose track of you for 3 s",
          "your steps and block sounds are silent and mobs ignore you for 5 s",
          "invisibility for 3 s",
          "your cover and 2 points of hunger (you are revealed)",
          "you are sneaking and no mob targets you",
          List.of(
            upgrade("Umbral Form", "Unseen sneaking makes you invisible at full walk speed and builds an armor layer that softens the next hit and breaks.",
                "Shadowmeld", "Ghost's Armor", "Sneak Speed"),
            upgrade("Death Mark", "Your opening strike from stealth kills eligible non-boss mobs outright and steals from those it cannot kill.",
                "Assassinate", "Cutpurse"),
            upgrade("Phantom Decoy", "Smoke Pellets leave a decoy you can swap places with, or put you behind a creature unseen.",
                "Shadow Decoy", "Decoy Swap", "Smoke Pellet"))),
      skill("hunter", "Hunter", "Predator", "Feral", TriggerKind.FIGHT,
          "kill a natural mob",
          "the target is marked and takes 15% more damage from you for 6 s",
          "animals and mobs within 16 blocks glow for you for 5 s",
          "speed for 5 s",
          "2 points of hunger",
          "you are below half health",
          List.of(
            upgrade("Blood Frenzy", "Being struck grants strength, speed and regeneration together, stronger at low health, for one hunger cost.",
                "Adrenaline", "Hunter's Strength", "Hunter's Speed", "Hunter's Regen"),
            toolUpgrade("Apex Predator", "Damage ramps on one target, big game drops more, and a compass tracks your quarry.",
                "Predator Focus", "Big Game Hunter"),
            upgrade("Stalker's Trail", "Wounded prey leaves a trail, snares catch animals you can lead home, and trophy kills drop more.",
                "Blood Trail", "Snare Line", "Trophy Skinner"))),
      skill("tragoul", "TragOul", "Bloodbinder", "Sanguine", TriggerKind.FIGHT,
          "lose 2 or more hearts in one hit",
          "you drain 1 heart from the target",
          "1 point of hunger becomes half a heart",
          "1.5 hearts of healing",
          "1 heart of your own health",
          "you are at 3 hearts or less",
          List.of(
            upgrade("Crimson Tide", "All damage you deal heals you, overflow fills a blood reservoir, and attackers lose life to you.",
                "Soul Siphon", "Will of Pain", "Thorns"),
            upgrade("Plague Nova", "Kills burst, spread their afflictions and fire lances.",
                "Corpse Explosion", "Plague Bearer", "Corpse Lances"),
            upgrade("Deathless", "Bones absorb hits, a death save leaves you at 1 health, and skeletal servants rise for you.",
                "Last Rites", "Marrow Armor"))),
      skill("agility", "Agility", "Runner", "Fleet", TriggerKind.EITHER,
          "sprint-jump",
          "you dash 3 blocks where you look",
          "you gain speed II for 4 s",
          "jump boost and a safe landing for 4 s",
          "your momentum and 2 points of hunger (you stop)",
          "you have sprinted for 3 s without stopping",
          List.of(
            upgrade("Slipstream", "Sprinting builds speed up to a cap, slides keep it, and sprinting costs no hunger.",
                "Wind Up", "Slipstream Slide", "Marathoner"),
            toolUpgrade("Skyrunner", "Wall latch, high jump, air dash and a Grappling Line share mid-air charges that walls refresh.",
                "Wall Jump", "Super Jump", "Air Dash"),
            upgrade("Untouchable", "Dodge projectiles while sprinting, roll out of falls up to a set height, and turn hits into speed.",
                "Cat Reflexes", "Roll Landing", "Kip-Up"))),
      skill("kinetics", "Kinetics", "Meteor", "Kinetic", TriggerKind.EITHER,
          "land from a fall of 4 or more blocks",
          "the target is launched upward",
          "your next jump within 5 s is 3 blocks high",
          "slow falling for 3 s",
          "your fall height and 2 points of hunger (the landing is cancelled)",
          "you are airborne",
          List.of(
            upgrade("Comet", "Dive smashes hit a wide radius and shred armor, and you can grow to Titan or shrink to Pocket form.",
                "Meteor Cadence", "Windburst", "Breachwright"),
            upgrade("Lancer", "Spear or trident hits reach farther, scale with your speed, and pin.",
                "Charge Lance", "Phalanx Reach", "Impale Pin"),
            upgrade("Featherweight", "Higher jumps, springier landings, and no fall damage on any block.",
                "Moon Jump", "Rubber Soul", "Soft Catch"))),
      skill("rift", "Rift", "Voidwalker", "Rifted", TriggerKind.EITHER,
          "teleport by pearl, blink or chorus fruit",
          "the target is blinked 4 blocks away from you",
          "drops within 6 blocks go to your ender chest",
          "resistance for 3 s",
          "1 ender pearl",
          "you teleported in the last 5 s",
          List.of(
            upgrade("Phase Step", "Blink through walls for a pearl or half a heart, swap with tagged targets, and bounce pearls once.",
                "Rift Blink", "Pearl Rebound", "Rift Resistance"),
            toolUpgrade("Pocket Realm", "Your ender chest opens anywhere, pulls in drops and refills your hand, and Rift Anchors link your bases.",
                "Inflated Pocket Dimension", "Void Magnet", "Easy Enderchest"),
            upgrade("Voidborn", "Lethal damage blinks you to safety, endermen ignore you, and levitation obeys you.",
                "Void Skin", "Rift Visage", "Anti-Levitation"))),
      skill("chronos", "Chronos", "Timekeeper", "Timeless", TriggerKind.EITHER,
          "sneak and swap hands while carrying a clock",
          "the target is slowed for 2 s",
          "crops, furnaces and brewing stands within 4 blocks advance 10 s",
          "5 s added to your active potion effects",
          "a 20 s cooldown on your clock",
          "a clock is in your off hand",
          List.of(
            upgrade("Second Chance", "A rewind you trigger yourself or that fires on a killing blow, and part of each hit is paid later.",
                "Rewind", "Instant Recall", "Hourglass Guard"),
            upgrade("Time Lord", "The bottle stores more, the aura speeds crops and stations, buffs last longer, and a clock restores blast damage.",
                "Time in a Bottle", "Accelerate", "Overtime"),
            upgrade("Frozen Moment", "A stasis field you can throw; your hits inside it root targets.",
                "Stasis Field", "Time Bomb", "Aberrant Touch"))),
      skill("pickaxe", "Pickaxes", "Delver", "Deep", TriggerKind.WORK,
          "mine an ore",
          "the target is stunned by a burst of rock for 1 s",
          "ores within 6 blocks glow for you for 5 s",
          "haste for 6 s",
          "5 durability from your pickaxe",
          "you mined a block in the last 4 s",
          List.of(
            upgrade("Motherlode", "Whole veins break at once, smelted, with a bonus ore, and spawners drop with their mob.",
                "Veinminer", "Autosmelt", "Ore Chisel"),
            toolUpgrade("Earthshaker", "Bore a 3 by 3 face through stone, deepslate and obsidian at stone speed, and blast ore with Prospector's Charges.",
                "Tunnel Bore", "Deep Core", "Obsidian Rush"),
            upgrade("Eternal Pick", "The pickaxe never breaks, repairs as you mine, and mining hardens you.",
                "Unbreakable Pact", "Repair Rhythm", "Stone Skin"))),
      skill("excavation", "Excavation", "Dredger", "Earthen", TriggerKind.WORK,
          "dig 8 soft blocks within 5 s",
          "the ground under the target turns to mud, slowing it for 3 s",
          "the 3 by 3 of soft blocks around your last dig breaks and drops",
          "a safe landing on your next fall",
          "8 dirt, sand or gravel",
          "you dug a soft block in the last 4 s",
          List.of(
            toolUpgrade("Landshaper", "Carve planes or shafts in soft ground, slam a line of earth, and level ground with Grading Stakes.",
                "Tunneler", "Burrow", "Hasty Excavator"),
            upgrade("Deep Sight", "Ores show through the ground while you dig, no glow berries needed.",
                "Super-Seeing Spelunker", "Seismic Ping"),
            upgrade("Gravekeeper", "Digging turns up treasure, bones and bonus drops.",
                "Treasure Hunter", "Grave Digger", "Mudlark"))),
      skill("herbalism", "Herbalism", "Warden", "Verdant", TriggerKind.WORK,
          "harvest a mature crop",
          "the target is rooted by vines for 2 s",
          "crops and saplings within 4 blocks grow one stage",
          "2 points of hunger restored",
          "3 seeds or crops",
          "you stand in crops, grass or flowers",
          List.of(
            upgrade("Wildgrowth", "A wide growth aura that also plants your seeds on empty farmland.",
                "Growth Aura", "Bee Shepherd", "Seed Sower"),
            toolUpgrade("Harvest Moon", "One right-click harvests and replants a whole field, and Scarecrows tend crops while you are near.",
                "Harvest & Replant", "Compost Cascade", "Herbalist's Luck"),
            upgrade("Iron Stomach", "Hunger absorbs 50 to 80% of every damage type first, and food fills twice as much.",
                "Hungry Shield", "Herbalist's Hippo"))),
      skill("architect", "Architect", "Mason", "Stonewrought", TriggerKind.WORK,
          "place 8 blocks within 5 s",
          "a temporary 2-high wall rises between you and the target",
          "for 5 s your placements reach 3 more blocks and snap to your chalk guide",
          "your next 8 placements are blast and fire proof for 60 s",
          "4 blocks from your hand",
          "you placed a block in the last 4 s",
          List.of(
            toolUpgrade("Master Builder", "Wide wand faces, supplies from carried containers, and chalk guides filled with blocks.",
                "Builder's Wand", "Supply Line", "Chalk Line"),
            toolUpgrade("Skyhook", "Temporary footing with no knockback or falls, and a Mason's Template that copies and stamps builds.",
                "Magic Foundation", "Scaffolder", "Steady Hands"),
            toolUpgrade("Wrecking Hand", "Erase your placements, undo recent builds, and replace blocks across a build.",
                "Mason's Eraser", "Silk-Touch Glass", "Smart Shape"))),
      skill("crafting", "Crafting", "Artisan", "Forged", TriggerKind.WORK,
          "craft a tool, weapon or armor piece",
          "your next hit within 5 s ignores 20% of the target's armor",
          "the tool in your hand gets Efficiency +1 for 20 s",
          "6 durability on the item in your hand",
          "1 iron nugget",
          "you crafted something in the last 10 s",
          List.of(
            upgrade("Grand Artisan", "Crafted gear rolls better bonuses, combines enchantments, refunds an ingredient, and reforges at a smithing table.",
                "Masterwork", "Tinkerer", "Thrifty Hands"),
            upgrade("Unmaker", "Break anything into parts, rebuild ores, and compact stacks anywhere.",
                "Deconstruction", "Ore Reconstruction", "Compactor"),
            toolUpgrade("Field Workshop", "Craft stored recipes and open workstations from an Artisan's Ledger, and craft whole stacks at once.",
                "Portable Tables", "A Boutilier's Backpacks", "Bulk Artisan"))),
      skill("enchanting", "Enchanting", "Runesmith", "Runic", TriggerKind.WORK,
          "enchant an item, or collect 10 vanilla XP at once",
          "the target glows, and a natural mob killed within 5 s drops half again as much XP",
          "your next block break rolls Fortune one level higher",
          "luck for 8 s",
          "1 vanilla XP level",
          "you carry 20 or more vanilla XP levels",
          List.of(
            upgrade("Arcane Hand", "Move, split and apply enchantments by hand with no anvil, and pull one onto a book at a grindstone.",
                "Quick-Click Enchant", "Infusion Transfer", "Tome Rebinding"),
            upgrade("Fate Weaver", "See every offer and reroll at a higher shelf power.",
                "Offer Reroll", "Rune Sight", "Bookshelf Attunement"),
            upgrade("Soulforged", "Linked gear survives death, grows as you gain XP, sheds curses, and ascends past its maximum.",
                "Soul Link", "Echo of Knowledge", "Curse Cleansing"))),
      skill("brewing", "Brewing", "Alchemist", "Distilled", TriggerKind.WORK,
          "drink or throw a potion",
          "the potion in your Bandolier or off hand splashes on the target, using a quarter bottle",
          "the potion in your Bandolier or off hand applies to you at half duration, using a quarter bottle",
          "a random helpful potion effect for 6 s",
          "a quarter of the potion in your Bandolier or off hand",
          "a potion you drank is active",
          List.of(
            toolUpgrade("Master Distiller", "Potions brew faster and last twice as long, and a Bandolier carries the potions your doses use.",
                "Lingering Brew", "Super Heated Brew"),
            upgrade("Witch's Shelf", "All six hostile brews in one unlock, with a wider splash and mixed hostile splashes.",
                "Bottled Blindness", "Bottled Darkness", "Bottled Decay", "Bottled Fatigue", "Bottled Hunger", "Bottled Nausea"),
            upgrade("Apothecary", "The four defensive brews and Haste in one unlock, plus an elixir and mixed potions.",
                "Bottled Absorption", "Bottled Life", "Bottled Resistance", "Bottled Saturation"))),
      skill("discovery", "Discovery", "Seeker", "Wayfaring", TriggerKind.WORK,
          "discover something new",
          "the target's stats are revealed and your next hit on it deals 20% more",
          "the nearest chest, spawner or structure within 24 blocks pings for you",
          "night vision and glowing chests for 8 s",
          "1 paper or map",
          "you are in a chunk you had never entered",
          List.of(
            toolUpgrade("Omniscience", "Creature stats, nearby chests, spawners and structures on one readout, and an Atlas of your finds.",
                "Insight", "Keen Eye", "Sixth Sense"),
            upgrade("Unity Prime", "Vanilla XP feeds your lowest skills, every levelled skill raises all gains, and World Armor and Better Mending run.",
                "Experimental Unity", "Polymath"),
            upgrade("Explorer's Luck", "First visits, first kills and relics pay more and bank permanent bonuses.",
                "Trailblazer", "Field Notes", "Relic Appraiser"))),
      skill("taming", "Taming", "Packmaster", "Wild", TriggerKind.FIGHT,
          "have a pet land a hit",
          "your nearest pet leaps at the target",
          "pets within 8 blocks heal 1 heart and keep pace with you for 5 s",
          "2 hearts of healing for each pet nearby, at most 3 hearts",
          "1 bone or piece of meat",
          "a pet of yours is fighting",
          List.of(
            upgrade("Alpha", "Pets are stronger, tougher and regenerate, more so near you, and a bonded mount grows as you ride.",
                "Tame Damage", "Tame Health", "Tame Regeneration", "Pack Leader Aura"),
            toolUpgrade("One Mind", "A whistle sends, recalls and fetches with your pets, and named pets take guard roles.",
                "Alpha's Command", "Beast Recall", "Fetch"),
            upgrade("Blood Bond", "You and your pets share damage, they intercept shots, and a pet that would die returns to you at 1 HP.",
                "Shared Pain", "Guardian Instinct", "Last Breath"))),
      skill("golemancy", "Golemancy", "Artificer", "Clockwork", TriggerKind.WORK,
          "have a golem you own finish a task",
          "your nearest golem turns on the target",
          "your nearest golem repeats the job you just did on the next matching block",
          "50% faster golem work for 10 s",
          "1 copper or iron nugget",
          "a golem of yours is working within 16 blocks",
          List.of(
            upgrade("Grand Overseer", "Command, heal and pack your golems from range.",
                "Overseer", "Golem Mending", "Stasis"),
            toolUpgrade("Forgemaster", "Bind golems, weave nodes and run every core at full strength; frames gain levels up to their top at level 3.",
                "Animus Binding", "Node Weaving", "Guardian Core", "Gatherer Core", "Harvester Core", "Lumber Core",
                "Breaker Core", "Rancher Core", "Sorter Core", "Angler Core", "Frost Frame", "Copper Frame",
                "Iron Frame", "Bone Frame"),
            upgrade("Runeforged", "An extra rune slot, stronger runes, and twin cores in any golem.",
                "Runic Augments", "Golem Masterwork"))),
      skill("seaborne", "Seaborne", "Tidecaller", "Tidal", TriggerKind.EITHER,
          "dive into water, or get hit while wet",
          "a water burst pushes the target back and puts out fire",
          "fire within 4 blocks goes out and farmland within 4 blocks is watered",
          "water breathing and fast swimming for 6 s",
          "3 air bubbles and 2 points of hunger",
          "you are wet",
          List.of(
            toolUpgrade("Leviathan", "Breathe, swim fast, hold a tidal ward, regenerate and stay fed while wet, and raise a Tide Shrine.",
                "Organic Oxygen Tank", "Dolphin's Grace", "Tidal Ward", "Brine Skin", "Rain-Fed"),
            upgrade("Maelstrom", "Surge and jet share charges; tridents hit harder and return at once, and storms answer your hits.",
                "Tidecaller", "Hydro Jet", "Trident Mastery", "Storm Strike"),
            upgrade("Deep Fortune", "Better catches, sea life helps you, sunken chests glow, and wrecks and ruins can be salvaged.",
                "Fisher's Fantasy", "Fish Whisperer", "Deep Salvager"))),
      skill("nether", "Nether", "Ashwalker", "Infernal", TriggerKind.EITHER,
          "take fire damage",
          "the target burns for 4 s",
          "the next 3 blocks you break are smelted",
          "fire resistance for 6 s",
          "1 blaze powder",
          "you are in the Nether or burning",
          List.of(
            upgrade("Hellwalker", "Stride over Nether lava, swim through any lava with clear sight, ignore fire, and keep dropped items safe.",
                "Lava Walker", "Fire Resistance", "Ashwalker", "Soul Strider"),
            upgrade("Wither King", "Throw wither skulls, shrug off wither, and take more from wither skeletons.",
                "Wither Skull Throw", "Wither Resistance", "Wither Harvest"),
            upgrade("Brimstone", "Attackers ignite, fire feeds you, blasts barely hurt, and piglins barter and keep a truce.",
                "Magma Skin", "Blaze Leech", "Ghast Ward"))));
  private static final Map<String, SkillBlocks> BY_ID = index();

  private SkillBlockCatalog() {
  }

  public static List<SkillBlocks> all() {
    return SKILLS;
  }

  public static Optional<SkillBlocks> byId(String skillId) {
    return Optional.ofNullable(BY_ID.get(skillId));
  }

  public static SkillBlocks require(String skillId) {
    SkillBlocks skill = BY_ID.get(skillId);
    if (skill == null) {
      throw new IllegalArgumentException("No mutation blocks for skill " + skillId);
    }
    return skill;
  }

  private static Map<String, SkillBlocks> index() {
    Map<String, SkillBlocks> index = new LinkedHashMap<>();
    for (SkillBlocks skill : SKILLS) {
      index.put(skill.skillId(), skill);
    }
    return Map.copyOf(index);
  }

  private static SkillBlocks skill(String skillId, String skillName, String leadNoun, String partnerAdjective,
                                   TriggerKind triggerKind, String trigger, String strikeFight, String strikeWork,
                                   String boon, String fuel, String state, List<UpgradedAdaptation> upgrades) {
    return new SkillBlocks(skillId, skillName, leadNoun, partnerAdjective, triggerKind, trigger, strikeFight, strikeWork,
        boon, fuel, state, upgrades);
  }

  private static UpgradedAdaptation upgrade(String name, String description, String... sources) {
    return new UpgradedAdaptation(name, List.of(sources), description, MutationPrice.UPGRADED);
  }

  /** An upgrade that hands the player a craftable tool or deployable, priced at {@link MutationPrice#TOOL} (ECO-1). */
  private static UpgradedAdaptation toolUpgrade(String name, String description, String... sources) {
    return new UpgradedAdaptation(name, List.of(sources), description, MutationPrice.TOOL);
  }
}
