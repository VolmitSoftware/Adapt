package art.arcane.adapt.client.screen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The real catalog's skills by id with their adaptations' English names, grouped as the server groups them, and the
 * English names of those groups; a skill the server does not group is one list.
 */
final class RealSkills {
    record Skill(String name, List<String> labels, List<List<String>> groups) {
        int size() {
            int size = 0;
            for (List<String> group : groups) {
                size += group.size();
            }
            return size;
        }
    }

    static final Map<String, Skill> SKILLS = skills();

    private RealSkills() {
    }

    private static Map<String, Skill> skills() {
        Map<String, Skill> skills = new LinkedHashMap<>();
        skills.put("agility", new Skill("Agility", List.of(), List.of(
                List.of("Featherfoot", "Ladder Slide", "Marathoner", "Vault", "Armor-Up", "Kip-Up", "Roll Landing", "Slipstream Slide", "Air Dash", "Cat Reflexes", "Super Jump", "Wall Jump", "Wind Up"))));
        skills.put("architect", new Skill("Architect", List.of(), List.of(
                List.of("Smart Shape", "Stonecutter Savant", "Chalk Line", "Mason's Eraser", "Scaffolder", "Silk-Touch Glass", "Steady Hands", "Brickbat", "Elevator", "Magic Foundation", "Redstone Remote", "Supply Line", "Builder's Wand"))));
        skills.put("axes", new Skill("Axes", List.of(), List.of(
                List.of("Drop to Inventory", "Lucy's Log-Swapper", "Bark Hide", "Leaf-Miner", "Shield Splitter", "Cleave", "Sunder", "Throwing Axe", "Chop", "Ground Smash", "Wood-Miner"))));
        skills.put("blocking", new Skill("Blocking", List.of("Guarding", "Counters", "Gear"), List.of(
                List.of("Bastion Stance", "Shield Wall", "Interpose", "Tempered Guard", "Shieldbearer's Resolve", "Perfect Guard"),
                List.of("Counter Guard", "Mirror Block", "Bulwark Bash"),
                List.of("Chains of Mephistopheles", "Craftable Horse Armor", "Craftable Saddle", "Phalanx Crafter", "Multi-Armor"))));
        skills.put("brewing", new Skill("Brewing", List.of(), List.of(
                List.of("Bottled Blindness", "Bottled Darkness", "Bottled Decay", "Bottled Fatigue", "Bottled Hunger", "Bottled Nausea", "Bottled Absorption", "Bottled Haste", "Bottled Life", "Bottled Resistance", "Bottled Saturation", "Lingering Brew", "Super Heated Brew"))));
        skills.put("chronos", new Skill("Chronos", List.of(), List.of(
                List.of("Deja Vu", "Pocket Watch", "Aberrant Touch", "Accelerate", "Borrowed Time", "Overtime", "Temporal Echo", "Instant Recall", "Rewind", "Stasis Field", "Time Bomb", "Time in a Bottle", "Hourglass Guard"))));
        skills.put("crafting", new Skill("Crafting", List.of(), List.of(
                List.of("Bulk Artisan", "Compactor", "Craftable Leather", "Crafting XP", "Craftable Skulls", "Provisioner", "Thrifty Hands", "Masterwork", "Ore Reconstruction", "Portable Tables", "Tinkerer", "A Boutilier's Backpacks", "Deconstruction"))));
        skills.put("discovery", new Skill("Discovery", List.of("Exploring", "Experience"), List.of(
                List.of("Insight", "Keen Eye", "Sixth Sense", "Cartographer Pulse", "Trailblazer", "Archaeologist", "Relic Appraiser", "World Armor"),
                List.of("Field Notes", "Villager Attraction", "Better Mending", "Experimental Resistance", "Polymath", "Experimental Unity"))));
        skills.put("enchanting", new Skill("Enchanting", List.of("Table", "Anvil & Grindstone", "Books"), List.of(
                List.of("Lapis Return", "Rune Sight", "Bookshelf Attunement", "Offer Reroll", "XP Return"),
                List.of("Anvil Savant", "Grindstone Recovery", "Curse Cleansing", "Tome Rebinding", "Infusion Transfer", "Soul Link"),
                List.of("Arcane Siphon", "Echo of Knowledge", "Quick-Click Enchant"))));
        skills.put("excavation", new Skill("Excavation", List.of(), List.of(
                List.of("Drop to Inventory", "Grave Digger", "Hasty Excavator", "Mudlark", "Soft Fall", "Burrow", "Clod Hurler", "Earth Mover", "Seismic Ping", "Treasure Hunter", "OMNI - T.O.O.L.", "Super-Seeing Spelunker", "Tunneler"))));
        skills.put("golemancy", new Skill("Golemancy", List.of("Binding", "Frames", "Cores", "Command & Care"), List.of(
                List.of("Animus Binding", "Node Weaving"),
                List.of("Frost Frame", "Copper Frame", "Iron Frame", "Bone Frame"),
                List.of("Guardian Core", "Gatherer Core", "Harvester Core", "Lumber Core", "Breaker Core", "Rancher Core", "Sorter Core", "Angler Core"),
                List.of("Runic Augments", "Masterwork", "Overseer", "Golem Mending", "Stasis"))));
        skills.put("herbalism", new Skill("Herbalism", List.of("Farming", "Survival", "Recipes"), List.of(
                List.of("Harvest & Replant", "Seed Sower", "Compost Cascade", "Bee Shepherd", "Growth Aura", "Spore Bloom", "Drop to Inventory"),
                List.of("Hippo", "Luck", "Rooted Footing", "Hungry Shield"),
                List.of("Myconid", "Terralid", "Mushroom Maker", "Webby Creator"))));
        skills.put("hunter", new Skill("Hunter", List.of("When Struck", "The Hunt"), List.of(
                List.of("Adrenaline", "Regen", "Resistance", "Strength", "Speed", "Luck", "Heights", "Vanishing Step"),
                List.of("Blood Trail", "Predator Focus", "Big Game Hunter", "Snare Line", "Trophy Skinner", "Drop to Inventory"))));
        skills.put("kinetics", new Skill("Kinetics", List.of("Spear", "Mace", "Movement"), List.of(
                List.of("Phalanx Reach", "Charge Lance", "Lunge Conductor", "Impale Pin", "Mounted Shock", "Dead Zone"),
                List.of("Meteor Cadence", "Breachwright", "Windburst", "Quake Guard", "Rebound Anvil", "Heavy Frame"),
                List.of("Moon Jump", "Rubber Soul", "Soft Catch", "Surface Skate", "Terminal Toggle", "Mass Shift"))));
        skills.put("nether", new Skill("Nether", List.of("Fire", "Wither", "Exploring"), List.of(
                List.of("Fire Resistance", "Ashwalker", "Magma Skin", "Blaze Leech", "Lava Walker", "Crimson Feast"),
                List.of("Wither Resistance", "Ghast Ward", "Wither Harvest", "Wither Skull Throw"),
                List.of("Strider Bond", "Soul Strider", "Netherrack Mason", "Piglin Broker"))));
        skills.put("pickaxe", new Skill("Pickaxe", List.of(), List.of(
                List.of("Drop to Inventory", "Trophy Polish", "Deep Core", "Obsidian Rush", "Stone Skin", "Ore Chisel", "Repair Rhythm", "Autosmelt", "Quarry Sense", "Tunnel Bore", "Unbreakable Pact", "Silk-Spawner", "Veinminer"))));
        skills.put("ranged", new Skill("Ranged", List.of(), List.of(
                List.of("Fetch Shot", "Trajectory Sight", "Arrow Recovery", "Force Shot", "Lunge Shot", "Arrow Piercing", "Floaters", "Heavy Draw", "Pinning Shot", "Ricochet Bolt", "Web Snare", "Heartseeker"))));
        skills.put("rift", new Skill("Rift", List.of(), List.of(
                List.of("Anti-Levitation", "Easy Enderchest", "Pearl Rebound", "Resistance", "Rift Visage", "Void Magnet", "Ender Taglock", "Inflated Pocket Dimension", "Remote Access", "Rift Conduit", "Blink", "Rift Gate", "Void Skin"))));
        skills.put("seaborne", new Skill("Seaborne", List.of("Swimming", "Combat", "Sea Harvest"), List.of(
                List.of("Organic Oxygen Tank", "Dolphin's Grace", "Hydro Jet", "Tidecaller", "Turtle Miner", "Rain-Fed"),
                List.of("Brine Skin", "Tidal Ward", "Ink Veil", "Trident Mastery", "Storm Strike"),
                List.of("Fish Whisperer", "Fisher's Fantasy", "Deep Salvager", "Coral Gardener"))));
        skills.put("stealth", new Skill("Stealth", List.of("Concealment", "Tricks", "Ambush"), List.of(
                List.of("Silent Step", "Sneak Speed", "Shadowmeld", "Smoke Pellet", "Enderveil", "Ghost's Armor"),
                List.of("Stealth Vision", "Trap Sense", "Item Snatch", "Shadow Decoy", "Decoy Swap"),
                List.of("Cutpurse", "Umbral Recovery", "Assassinate"))));
        skills.put("swords", new Skill("Swords", List.of("Technique", "Wounds", "Blades"), List.of(
                List.of("Lunge Strike", "Riposte Window", "Blade Flow", "Duelist's Focus", "Hamstring", "Executioner's Edge", "Crescent Guard"),
                List.of("Bloody Blade", "Poisoned Blade", "Crimson Cyclone"),
                List.of("Machete", "Whetstone Ritual", "Dual Wield Stance", "Heirloom Edge"))));
        skills.put("taming", new Skill("Taming", List.of("Care", "Battle"), List.of(
                List.of("Wild Empathy", "Fetch", "Stable Hand", "Tame Health", "Tame Regeneration", "Pack Leader Aura", "Beast Recall"),
                List.of("Tame Damage", "Battle Bond", "Alpha's Command", "Guardian Instinct", "Shared Pain", "Mounted Tactics", "Last Breath"))));
        skills.put("tragoul", new Skill("TragOul", List.of("Pain", "Corpses", "Blood"), List.of(
                List.of("Thorns", "Curse of Frailty", "Blood Pact", "Marrow Armor", "Will of Pain", "Last Rites"),
                List.of("Corpse Explosion", "Corpse Lances", "Bone Harvest", "Plague Bearer", "Skeletal Servant"),
                List.of("Death Sense", "Globe of Pain", "Soul Siphon"))));
        skills.put("unarmed", new Skill("Unarmed", List.of(), List.of(
                List.of("Iron Fists", "Meditation", "Pressure Point", "Second Wind", "Sucker Punch", "Battering Charge", "Combo Chain", "Disarm", "Grapple", "Shockwave Clap", "Unarmed Power", "Glass Cannon"))));
        return skills;
    }
}
