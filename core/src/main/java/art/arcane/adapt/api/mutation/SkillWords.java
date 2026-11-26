package art.arcane.adapt.api.mutation;

import java.util.Map;

/** The noun a skill gives a mutation it leads and the adjective it gives one it joins as partner. */
public record SkillWords(String noun, String adjective) {
  private static final Map<String, SkillWords> BY_SKILL = Map.ofEntries(
      Map.entry("swords", new SkillWords("Duelist", "Bladed")),
      Map.entry("axes", new SkillWords("Reaver", "Cleaving")),
      Map.entry("unarmed", new SkillWords("Brawler", "Iron-Fisted")),
      Map.entry("ranged", new SkillWords("Marksman", "Far-Sighted")),
      Map.entry("blocking", new SkillWords("Bulwark", "Shielded")),
      Map.entry("stealth", new SkillWords("Shade", "Silent")),
      Map.entry("hunter", new SkillWords("Predator", "Feral")),
      Map.entry("tragoul", new SkillWords("Bloodbinder", "Sanguine")),
      Map.entry("agility", new SkillWords("Runner", "Fleet")),
      Map.entry("kinetics", new SkillWords("Meteor", "Kinetic")),
      Map.entry("rift", new SkillWords("Voidwalker", "Rifted")),
      Map.entry("chronos", new SkillWords("Timekeeper", "Timeless")),
      Map.entry("pickaxe", new SkillWords("Delver", "Deep")),
      Map.entry("excavation", new SkillWords("Dredger", "Earthen")),
      Map.entry("herbalism", new SkillWords("Warden", "Verdant")),
      Map.entry("architect", new SkillWords("Mason", "Stonewrought")),
      Map.entry("crafting", new SkillWords("Artisan", "Forged")),
      Map.entry("enchanting", new SkillWords("Runesmith", "Runic")),
      Map.entry("brewing", new SkillWords("Alchemist", "Distilled")),
      Map.entry("discovery", new SkillWords("Seeker", "Wayfaring")),
      Map.entry("taming", new SkillWords("Packmaster", "Wild")),
      Map.entry("golemancy", new SkillWords("Artificer", "Clockwork")),
      Map.entry("seaborne", new SkillWords("Tidecaller", "Tidal")),
      Map.entry("nether", new SkillWords("Ashwalker", "Infernal")));

  public static SkillWords of(String skill) {
    return BY_SKILL.get(skill);
  }

  public static Map<String, SkillWords> all() {
    return BY_SKILL;
  }
}
