package art.arcane.adapt.localization.catalog;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.util.config.ConfigDocumentation;
import art.arcane.volmlib.util.localization.LinesKey;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ConfigFieldMessages {
  private static final Map<String, FieldMessages> FIELDS = createFields();

  private ConfigFieldMessages() {
  }

  public static void addTo(MessageCatalog.Builder builder) {
    for (FieldMessages field : FIELDS.values()) {
      builder.add(field.label());
      builder.add(field.description());
    }
  }

  public static boolean has(String path) {
    return FIELDS.containsKey(path);
  }

  public static FieldMessages field(String path) {
    FieldMessages messages = FIELDS.get(path);
    if (messages == null) {
      throw new IllegalArgumentException("Unknown configuration field: " + path);
    }
    return messages;
  }

  private static Map<String, FieldMessages> createFields() {
    Map<String, FieldMessages> fields = new LinkedHashMap<>();
    addFields(fields, "core", "core-config", AdaptConfig.class);
    addConfiguration(fields, "skills.agility", "skill:agility", "art.arcane.adapt.content.skill.SkillAgility");
    addConfiguration(fields, "skills.architect", "skill:architect", "art.arcane.adapt.content.skill.SkillArchitect");
    addConfiguration(fields, "skills.axes", "skill:axes", "art.arcane.adapt.content.skill.SkillAxes");
    addConfiguration(fields, "skills.blocking", "skill:blocking", "art.arcane.adapt.content.skill.SkillBlocking");
    addConfiguration(fields, "skills.brewing", "skill:brewing", "art.arcane.adapt.content.skill.SkillBrewing");
    addConfiguration(fields, "skills.chronos", "skill:chronos", "art.arcane.adapt.content.skill.SkillChronos");
    addConfiguration(fields, "skills.crafting", "skill:crafting", "art.arcane.adapt.content.skill.SkillCrafting");
    addConfiguration(fields, "skills.discovery", "skill:discovery", "art.arcane.adapt.content.skill.SkillDiscovery");
    addConfiguration(fields, "skills.enchanting", "skill:enchanting", "art.arcane.adapt.content.skill.SkillEnchanting");
    addConfiguration(fields, "skills.excavation", "skill:excavation", "art.arcane.adapt.content.skill.SkillExcavation");
    addConfiguration(fields, "skills.golemancy", "skill:golemancy", "art.arcane.adapt.content.skill.SkillGolemancy");
    addConfiguration(fields, "skills.herbalism", "skill:herbalism", "art.arcane.adapt.content.skill.SkillHerbalism");
    addConfiguration(fields, "skills.hunter", "skill:hunter", "art.arcane.adapt.content.skill.SkillHunter");
    addConfiguration(fields, "skills.kinetics", "skill:kinetics", "art.arcane.adapt.content.skill.SkillKinetics");
    addConfiguration(fields, "skills.nether", "skill:nether", "art.arcane.adapt.content.skill.SkillNether");
    addConfiguration(fields, "skills.pickaxe", "skill:pickaxe", "art.arcane.adapt.content.skill.SkillPickaxes");
    addConfiguration(fields, "skills.ranged", "skill:ranged", "art.arcane.adapt.content.skill.SkillRanged");
    addConfiguration(fields, "skills.rift", "skill:rift", "art.arcane.adapt.content.skill.SkillRift");
    addConfiguration(fields, "skills.seaborne", "skill:seaborne", "art.arcane.adapt.content.skill.SkillSeaborne");
    addConfiguration(fields, "skills.stealth", "skill:stealth", "art.arcane.adapt.content.skill.SkillStealth");
    addConfiguration(fields, "skills.swords", "skill:swords", "art.arcane.adapt.content.skill.SkillSwords");
    addConfiguration(fields, "skills.taming", "skill:taming", "art.arcane.adapt.content.skill.SkillTaming");
    addConfiguration(fields, "skills.tragoul", "skill:tragoul", "art.arcane.adapt.content.skill.SkillTragOul");
    addConfiguration(fields, "skills.unarmed", "skill:unarmed", "art.arcane.adapt.content.skill.SkillUnarmed");
    addConfiguration(fields, "adaptations.agility-air-dash", "adaptation:agility-air-dash", "art.arcane.adapt.content.adaptation.agility.AgilityAirDash");
    addConfiguration(fields, "adaptations.agility-armor-up", "adaptation:agility-armor-up", "art.arcane.adapt.content.adaptation.agility.AgilityArmorUp");
    addConfiguration(fields, "adaptations.agility-cat-reflexes", "adaptation:agility-cat-reflexes", "art.arcane.adapt.content.adaptation.agility.AgilityCatReflexes");
    addConfiguration(fields, "adaptations.agility-featherfoot", "adaptation:agility-featherfoot", "art.arcane.adapt.content.adaptation.agility.AgilityFeatherfoot");
    addConfiguration(fields, "adaptations.agility-kip-up", "adaptation:agility-kip-up", "art.arcane.adapt.content.adaptation.agility.AgilityKipUp");
    addConfiguration(fields, "adaptations.agility-ladder-slide", "adaptation:agility-ladder-slide", "art.arcane.adapt.content.adaptation.agility.AgilityLadderSlide");
    addConfiguration(fields, "adaptations.agility-marathoner", "adaptation:agility-marathoner", "art.arcane.adapt.content.adaptation.agility.AgilityMarathoner");
    addConfiguration(fields, "adaptations.agility-roll-landing", "adaptation:agility-roll-landing", "art.arcane.adapt.content.adaptation.agility.AgilityRollLanding");
    addConfiguration(fields, "adaptations.agility-slipstream-slide", "adaptation:agility-slipstream-slide", "art.arcane.adapt.content.adaptation.agility.AgilitySlipstreamSlide");
    addConfiguration(fields, "adaptations.agility-super-jump", "adaptation:agility-super-jump", "art.arcane.adapt.content.adaptation.agility.AgilitySuperJump");
    addConfiguration(fields, "adaptations.agility-vault", "adaptation:agility-vault", "art.arcane.adapt.content.adaptation.agility.AgilityVault");
    addConfiguration(fields, "adaptations.agility-wall-jump", "adaptation:agility-wall-jump", "art.arcane.adapt.content.adaptation.agility.AgilityWallJump");
    addConfiguration(fields, "adaptations.agility-wind-up", "adaptation:agility-wind-up", "art.arcane.adapt.content.adaptation.agility.AgilityWindUp");
    addConfiguration(fields, "adaptations.architect-chalk-line", "adaptation:architect-chalk-line", "art.arcane.adapt.content.adaptation.architect.ArchitectChalkLine");
    addConfiguration(fields, "adaptations.architect-demolition", "adaptation:architect-demolition", "art.arcane.adapt.content.adaptation.architect.ArchitectDemolition");
    addConfiguration(fields, "adaptations.architect-elevator", "adaptation:architect-elevator", "art.arcane.adapt.content.adaptation.architect.ArchitectElevator");
    addConfiguration(fields, "adaptations.architect-foundation", "adaptation:architect-foundation", "art.arcane.adapt.content.adaptation.architect.ArchitectFoundation");
    addConfiguration(fields, "adaptations.architect-glass", "adaptation:architect-glass", "art.arcane.adapt.content.adaptation.architect.ArchitectGlass");
    addConfiguration(fields, "adaptations.architect-placement", "adaptation:architect-placement", "art.arcane.adapt.content.adaptation.architect.ArchitectPlacement");
    addConfiguration(fields, "adaptations.architect-scaffolder", "adaptation:architect-scaffolder", "art.arcane.adapt.content.adaptation.architect.ArchitectScaffolder");
    addConfiguration(fields, "adaptations.architect-smart-shape", "adaptation:architect-smart-shape", "art.arcane.adapt.content.adaptation.architect.ArchitectSmartShape");
    addConfiguration(fields, "adaptations.architect-steady-hands", "adaptation:architect-steady-hands", "art.arcane.adapt.content.adaptation.architect.ArchitectSteadyHands");
    addConfiguration(fields, "adaptations.architect-stonecutter-savant", "adaptation:architect-stonecutter-savant", "art.arcane.adapt.content.adaptation.architect.ArchitectStonecutterSavant");
    addConfiguration(fields, "adaptations.architect-supply-line", "adaptation:architect-supply-line", "art.arcane.adapt.content.adaptation.architect.ArchitectSupplyLine");
    addConfiguration(fields, "adaptations.architect-wireless-redstone", "adaptation:architect-wireless-redstone", "art.arcane.adapt.content.adaptation.architect.ArchitectWirelessRedstone");
    addConfiguration(fields, "adaptations.axe-bark-hide", "adaptation:axe-bark-hide", "art.arcane.adapt.content.adaptation.axe.AxeBarkHide");
    addConfiguration(fields, "adaptations.axe-chop", "adaptation:axe-chop", "art.arcane.adapt.content.adaptation.axe.AxeChop");
    addConfiguration(fields, "adaptations.axe-cleave", "adaptation:axe-cleave", "art.arcane.adapt.content.adaptation.axe.AxeCleave");
    addConfiguration(fields, "adaptations.axe-logswap", "adaptation:axe-logswap", "art.arcane.adapt.content.adaptation.axe.AxeCraftLogSwap");
    addConfiguration(fields, "adaptations.axe-drop-to-inventory", "adaptation:axe-drop-to-inventory", "art.arcane.adapt.content.adaptation.axe.AxeDropToInventory");
    addConfiguration(fields, "adaptations.axe-ground-smash", "adaptation:axe-ground-smash", "art.arcane.adapt.content.adaptation.axe.AxeGroundSmash");
    addConfiguration(fields, "adaptations.axe-iris-feller", "adaptation:axe-iris-feller", "art.arcane.adapt.content.adaptation.axe.AxeIrisFeller");
    addConfiguration(fields, "adaptations.axe-leaf-veinminer", "adaptation:axe-leaf-veinminer", "art.arcane.adapt.content.adaptation.axe.AxeLeafVeinminer");
    addConfiguration(fields, "adaptations.axe-shield-splitter", "adaptation:axe-shield-splitter", "art.arcane.adapt.content.adaptation.axe.AxeShieldSplitter");
    addConfiguration(fields, "adaptations.axe-sunder", "adaptation:axe-sunder", "art.arcane.adapt.content.adaptation.axe.AxeSunder");
    addConfiguration(fields, "adaptations.axe-throwing-axe", "adaptation:axe-throwing-axe", "art.arcane.adapt.content.adaptation.axe.AxeThrowingAxe");
    addConfiguration(fields, "adaptations.axe-wood-veinminer", "adaptation:axe-wood-veinminer", "art.arcane.adapt.content.adaptation.axe.AxeWoodVeinminer");
    addConfiguration(fields, "adaptations.blocking-bastion-stance", "adaptation:blocking-bastion-stance", "art.arcane.adapt.content.adaptation.blocking.BlockingBastionStance");
    addConfiguration(fields, "adaptations.blocking-bulwark-bash", "adaptation:blocking-bulwark-bash", "art.arcane.adapt.content.adaptation.blocking.BlockingBulwarkBash");
    addConfiguration(fields, "adaptations.blocking-chainarmorer", "adaptation:blocking-chainarmorer", "art.arcane.adapt.content.adaptation.blocking.BlockingChainArmorer");
    addConfiguration(fields, "adaptations.blocking-counter-guard", "adaptation:blocking-counter-guard", "art.arcane.adapt.content.adaptation.blocking.BlockingCounterGuard");
    addConfiguration(fields, "adaptations.blocking-horsearmorer", "adaptation:blocking-horsearmorer", "art.arcane.adapt.content.adaptation.blocking.BlockingHorseArmorer");
    addConfiguration(fields, "adaptations.blocking-interpose", "adaptation:blocking-interpose", "art.arcane.adapt.content.adaptation.blocking.BlockingInterpose");
    addConfiguration(fields, "adaptations.blocking-mirror-block", "adaptation:blocking-mirror-block", "art.arcane.adapt.content.adaptation.blocking.BlockingMirrorBlock");
    addConfiguration(fields, "adaptations.blocking-multiarmor", "adaptation:blocking-multiarmor", "art.arcane.adapt.content.adaptation.blocking.BlockingMultiArmor");
    addConfiguration(fields, "adaptations.blocking-perfect-guard", "adaptation:blocking-perfect-guard", "art.arcane.adapt.content.adaptation.blocking.BlockingPerfectGuard");
    addConfiguration(fields, "adaptations.blocking-phalanx-crafter", "adaptation:blocking-phalanx-crafter", "art.arcane.adapt.content.adaptation.blocking.BlockingPhalanxCrafter");
    addConfiguration(fields, "adaptations.blocking-saddlecrafter", "adaptation:blocking-saddlecrafter", "art.arcane.adapt.content.adaptation.blocking.BlockingSaddlecrafter");
    addConfiguration(fields, "adaptations.blocking-shield-wall", "adaptation:blocking-shield-wall", "art.arcane.adapt.content.adaptation.blocking.BlockingShieldWall");
    addConfiguration(fields, "adaptations.blocking-shieldbearers-resolve", "adaptation:blocking-shieldbearers-resolve", "art.arcane.adapt.content.adaptation.blocking.BlockingShieldbearersResolve");
    addConfiguration(fields, "adaptations.blocking-tempered-guard", "adaptation:blocking-tempered-guard", "art.arcane.adapt.content.adaptation.blocking.BlockingTemperedGuard");
    addConfiguration(fields, "adaptations.brewing-absorption", "adaptation:brewing-absorption", "art.arcane.adapt.content.adaptation.brewing.BrewingAbsorption");
    addConfiguration(fields, "adaptations.brewing-blindness", "adaptation:brewing-blindness", "art.arcane.adapt.content.adaptation.brewing.BrewingBlindness");
    addConfiguration(fields, "adaptations.brewing-darkness", "adaptation:brewing-darkness", "art.arcane.adapt.content.adaptation.brewing.BrewingDarkness");
    addConfiguration(fields, "adaptations.brewing-decay", "adaptation:brewing-decay", "art.arcane.adapt.content.adaptation.brewing.BrewingDecay");
    addConfiguration(fields, "adaptations.brewing-fatigue", "adaptation:brewing-fatigue", "art.arcane.adapt.content.adaptation.brewing.BrewingFatigue");
    addConfiguration(fields, "adaptations.brewing-haste", "adaptation:brewing-haste", "art.arcane.adapt.content.adaptation.brewing.BrewingHaste");
    addConfiguration(fields, "adaptations.brewing-healthboost", "adaptation:brewing-healthboost", "art.arcane.adapt.content.adaptation.brewing.BrewingHealthBoost");
    addConfiguration(fields, "adaptations.brewing-hunger", "adaptation:brewing-hunger", "art.arcane.adapt.content.adaptation.brewing.BrewingHunger");
    addConfiguration(fields, "adaptations.brewing-lingering", "adaptation:brewing-lingering", "art.arcane.adapt.content.adaptation.brewing.BrewingLingering");
    addConfiguration(fields, "adaptations.brewing-nausea", "adaptation:brewing-nausea", "art.arcane.adapt.content.adaptation.brewing.BrewingNausea");
    addConfiguration(fields, "adaptations.brewing-resistance", "adaptation:brewing-resistance", "art.arcane.adapt.content.adaptation.brewing.BrewingResistance");
    addConfiguration(fields, "adaptations.brewing-saturation", "adaptation:brewing-saturation", "art.arcane.adapt.content.adaptation.brewing.BrewingSaturation");
    addConfiguration(fields, "adaptations.brewing-super-heated", "adaptation:brewing-super-heated", "art.arcane.adapt.content.adaptation.brewing.BrewingSuperHeated");
    addConfiguration(fields, "adaptations.chronos-aberrant-touch", "adaptation:chronos-aberrant-touch", "art.arcane.adapt.content.adaptation.chronos.ChronosAberrantTouch");
    addConfiguration(fields, "adaptations.chronos-accelerate", "adaptation:chronos-accelerate", "art.arcane.adapt.content.adaptation.chronos.ChronosAccelerate");
    addConfiguration(fields, "adaptations.chronos-borrowed-time", "adaptation:chronos-borrowed-time", "art.arcane.adapt.content.adaptation.chronos.ChronosBorrowedTime");
    addConfiguration(fields, "adaptations.chronos-deja-vu", "adaptation:chronos-deja-vu", "art.arcane.adapt.content.adaptation.chronos.ChronosDejaVu");
    addConfiguration(fields, "adaptations.chronos-hourglass-guard", "adaptation:chronos-hourglass-guard", "art.arcane.adapt.content.adaptation.chronos.ChronosHourglassGuard");
    addConfiguration(fields, "adaptations.chronos-overtime", "adaptation:chronos-overtime", "art.arcane.adapt.content.adaptation.chronos.ChronosOvertime");
    addConfiguration(fields, "adaptations.chronos-pocket-watch", "adaptation:chronos-pocket-watch", "art.arcane.adapt.content.adaptation.chronos.ChronosPocketWatch");
    addConfiguration(fields, "adaptations.chronos-rewind", "adaptation:chronos-rewind", "art.arcane.adapt.content.adaptation.chronos.ChronosRewind");
    addConfiguration(fields, "adaptations.chronos-stasis-field", "adaptation:chronos-stasis-field", "art.arcane.adapt.content.adaptation.chronos.ChronosStasisField");
    addConfiguration(fields, "adaptations.chronos-temporal-echo", "adaptation:chronos-temporal-echo", "art.arcane.adapt.content.adaptation.chronos.ChronosTemporalEcho");
    addConfiguration(fields, "adaptations.chronos-time-bomb", "adaptation:chronos-time-bomb", "art.arcane.adapt.content.adaptation.chronos.ChronosTimeBomb");
    addConfiguration(fields, "adaptations.chronos-time-bottle", "adaptation:chronos-time-bottle", "art.arcane.adapt.content.adaptation.chronos.ChronosTimeInABottle");
    addConfiguration(fields, "adaptations.crafting-backpacks", "adaptation:crafting-backpacks", "art.arcane.adapt.content.adaptation.crafting.CraftingBackpacks");
    addConfiguration(fields, "adaptations.crafting-bulk-artisan", "adaptation:crafting-bulk-artisan", "art.arcane.adapt.content.adaptation.crafting.CraftingBulkArtisan");
    addConfiguration(fields, "adaptations.crafting-compactor", "adaptation:crafting-compactor", "art.arcane.adapt.content.adaptation.crafting.CraftingCompactor");
    addConfiguration(fields, "adaptations.crafting-deconstruction", "adaptation:crafting-deconstruction", "art.arcane.adapt.content.adaptation.crafting.CraftingDeconstruction");
    addConfiguration(fields, "adaptations.crafting-leather", "adaptation:crafting-leather", "art.arcane.adapt.content.adaptation.crafting.CraftingLeather");
    addConfiguration(fields, "adaptations.crafting-masterwork", "adaptation:crafting-masterwork", "art.arcane.adapt.content.adaptation.crafting.CraftingMasterwork");
    addConfiguration(fields, "adaptations.crafting-provisioner", "adaptation:crafting-provisioner", "art.arcane.adapt.content.adaptation.crafting.CraftingProvisioner");
    addConfiguration(fields, "adaptations.crafting-reconstruction", "adaptation:crafting-reconstruction", "art.arcane.adapt.content.adaptation.crafting.CraftingReconstruction");
    addConfiguration(fields, "adaptations.crafting-skulls", "adaptation:crafting-skulls", "art.arcane.adapt.content.adaptation.crafting.CraftingSkulls");
    addConfiguration(fields, "adaptations.crafting-stations", "adaptation:crafting-stations", "art.arcane.adapt.content.adaptation.crafting.CraftingStations");
    addConfiguration(fields, "adaptations.crafting-thrifty-hands", "adaptation:crafting-thrifty-hands", "art.arcane.adapt.content.adaptation.crafting.CraftingThriftyHands");
    addConfiguration(fields, "adaptations.crafting-tinkerer", "adaptation:crafting-tinkerer", "art.arcane.adapt.content.adaptation.crafting.CraftingTinkerer");
    addConfiguration(fields, "adaptations.crafting-xp", "adaptation:crafting-xp", "art.arcane.adapt.content.adaptation.crafting.CraftingXP");
    addConfiguration(fields, "adaptations.discovery-archaeologist", "adaptation:discovery-archaeologist", "art.arcane.adapt.content.adaptation.discovery.DiscoveryArchaeologist");
    addConfiguration(fields, "adaptations.discovery-world-armor", "adaptation:discovery-world-armor", "art.arcane.adapt.content.adaptation.discovery.DiscoveryArmor");
    addConfiguration(fields, "adaptations.discovery-better-mending", "adaptation:discovery-better-mending", "art.arcane.adapt.content.adaptation.discovery.DiscoveryBetterMending");
    addConfiguration(fields, "adaptations.discovery-cartographer-pulse", "adaptation:discovery-cartographer-pulse", "art.arcane.adapt.content.adaptation.discovery.DiscoveryCartographerPulse");
    addConfiguration(fields, "adaptations.discovery-field-notes", "adaptation:discovery-field-notes", "art.arcane.adapt.content.adaptation.discovery.DiscoveryFieldNotes");
    addConfiguration(fields, "adaptations.discovery-insight", "adaptation:discovery-insight", "art.arcane.adapt.content.adaptation.discovery.DiscoveryInsight");
    addConfiguration(fields, "adaptations.discovery-keen-eye", "adaptation:discovery-keen-eye", "art.arcane.adapt.content.adaptation.discovery.DiscoveryKeenEye");
    addConfiguration(fields, "adaptations.discovery-polymath", "adaptation:discovery-polymath", "art.arcane.adapt.content.adaptation.discovery.DiscoveryPolymath");
    addConfiguration(fields, "adaptations.discovery-relic-appraiser", "adaptation:discovery-relic-appraiser", "art.arcane.adapt.content.adaptation.discovery.DiscoveryRelicAppraiser");
    addConfiguration(fields, "adaptations.discovery-sixth-sense", "adaptation:discovery-sixth-sense", "art.arcane.adapt.content.adaptation.discovery.DiscoverySixthSense");
    addConfiguration(fields, "adaptations.discovery-trailblazer", "adaptation:discovery-trailblazer", "art.arcane.adapt.content.adaptation.discovery.DiscoveryTrailblazer");
    addConfiguration(fields, "adaptations.discovery-unity", "adaptation:discovery-unity", "art.arcane.adapt.content.adaptation.discovery.DiscoveryUnity");
    addConfiguration(fields, "adaptations.discovery-villager-att", "adaptation:discovery-villager-att", "art.arcane.adapt.content.adaptation.discovery.DiscoveryVillagerAtt");
    addConfiguration(fields, "adaptations.discovery-xp-resist", "adaptation:discovery-xp-resist", "art.arcane.adapt.content.adaptation.discovery.DiscoveryXpResist");
    addConfiguration(fields, "adaptations.enchanting-anvil-savant", "adaptation:enchanting-anvil-savant", "art.arcane.adapt.content.adaptation.enchanting.EnchantingAnvilSavant");
    addConfiguration(fields, "adaptations.enchanting-arcane-siphon", "adaptation:enchanting-arcane-siphon", "art.arcane.adapt.content.adaptation.enchanting.EnchantingArcaneSiphon");
    addConfiguration(fields, "adaptations.enchanting-bookshelf-attunement", "adaptation:enchanting-bookshelf-attunement", "art.arcane.adapt.content.adaptation.enchanting.EnchantingBookshelfAttunement");
    addConfiguration(fields, "adaptations.enchanting-curse-cleansing", "adaptation:enchanting-curse-cleansing", "art.arcane.adapt.content.adaptation.enchanting.EnchantingCurseCleansing");
    addConfiguration(fields, "adaptations.enchanting-echo-of-knowledge", "adaptation:enchanting-echo-of-knowledge", "art.arcane.adapt.content.adaptation.enchanting.EnchantingEchoOfKnowledge");
    addConfiguration(fields, "adaptations.enchanting-grindstone-recovery", "adaptation:enchanting-grindstone-recovery", "art.arcane.adapt.content.adaptation.enchanting.EnchantingGrindstoneRecovery");
    addConfiguration(fields, "adaptations.enchanting-infusion-transfer", "adaptation:enchanting-infusion-transfer", "art.arcane.adapt.content.adaptation.enchanting.EnchantingInfusionTransfer");
    addConfiguration(fields, "adaptations.enchanting-lapis-return", "adaptation:enchanting-lapis-return", "art.arcane.adapt.content.adaptation.enchanting.EnchantingLapisReturn");
    addConfiguration(fields, "adaptations.enchanting-offer-reroll", "adaptation:enchanting-offer-reroll", "art.arcane.adapt.content.adaptation.enchanting.EnchantingOfferReroll");
    addConfiguration(fields, "adaptations.enchanting-quick-enchant", "adaptation:enchanting-quick-enchant", "art.arcane.adapt.content.adaptation.enchanting.EnchantingQuickEnchant");
    addConfiguration(fields, "adaptations.enchanting-rune-sight", "adaptation:enchanting-rune-sight", "art.arcane.adapt.content.adaptation.enchanting.EnchantingRuneSight");
    addConfiguration(fields, "adaptations.enchanting-soul-link", "adaptation:enchanting-soul-link", "art.arcane.adapt.content.adaptation.enchanting.EnchantingSoulLink");
    addConfiguration(fields, "adaptations.enchanting-tome-rebinding", "adaptation:enchanting-tome-rebinding", "art.arcane.adapt.content.adaptation.enchanting.EnchantingTomeRebinding");
    addConfiguration(fields, "adaptations.enchanting-xp-return", "adaptation:enchanting-xp-return", "art.arcane.adapt.content.adaptation.enchanting.EnchantingXPReturn");
    addConfiguration(fields, "adaptations.excavation-burrow", "adaptation:excavation-burrow", "art.arcane.adapt.content.adaptation.excavation.ExcavationBurrow");
    addConfiguration(fields, "adaptations.excavation-drop-to-inventory", "adaptation:excavation-drop-to-inventory", "art.arcane.adapt.content.adaptation.excavation.ExcavationDropToInventory");
    addConfiguration(fields, "adaptations.excavation-earth-mover", "adaptation:excavation-earth-mover", "art.arcane.adapt.content.adaptation.excavation.ExcavationEarthMover");
    addConfiguration(fields, "adaptations.excavation-grave-digger", "adaptation:excavation-grave-digger", "art.arcane.adapt.content.adaptation.excavation.ExcavationGraveDigger");
    addConfiguration(fields, "adaptations.excavation-haste", "adaptation:excavation-haste", "art.arcane.adapt.content.adaptation.excavation.ExcavationHaste");
    addConfiguration(fields, "adaptations.excavation-mudlark", "adaptation:excavation-mudlark", "art.arcane.adapt.content.adaptation.excavation.ExcavationMudlark");
    addConfiguration(fields, "adaptations.excavation-omnitool", "adaptation:excavation-omnitool", "art.arcane.adapt.content.adaptation.excavation.ExcavationOmniTool");
    addConfiguration(fields, "adaptations.excavation-seismic-ping", "adaptation:excavation-seismic-ping", "art.arcane.adapt.content.adaptation.excavation.ExcavationSeismicPing");
    addConfiguration(fields, "adaptations.excavation-soft-fall", "adaptation:excavation-soft-fall", "art.arcane.adapt.content.adaptation.excavation.ExcavationSoftFall");
    addConfiguration(fields, "adaptations.excavation-spelunker", "adaptation:excavation-spelunker", "art.arcane.adapt.content.adaptation.excavation.ExcavationSpelunker");
    addConfiguration(fields, "adaptations.excavation-treasure-hunter", "adaptation:excavation-treasure-hunter", "art.arcane.adapt.content.adaptation.excavation.ExcavationTreasureHunter");
    addConfiguration(fields, "adaptations.excavation-tunneler", "adaptation:excavation-tunneler", "art.arcane.adapt.content.adaptation.excavation.ExcavationTunneler");
    addConfiguration(fields, "adaptations.golem-angler-core", "adaptation:golem-angler-core", "art.arcane.adapt.content.adaptation.golemancy.GolemAnglerCore");
    addConfiguration(fields, "adaptations.golem-animus-binding", "adaptation:golem-animus-binding", "art.arcane.adapt.content.adaptation.golemancy.GolemAnimusBinding");
    addConfiguration(fields, "adaptations.golem-bone-frame", "adaptation:golem-bone-frame", "art.arcane.adapt.content.adaptation.golemancy.GolemBoneFrame");
    addConfiguration(fields, "adaptations.golem-breaker-core", "adaptation:golem-breaker-core", "art.arcane.adapt.content.adaptation.golemancy.GolemBreakerCore");
    addConfiguration(fields, "adaptations.golem-copper-frame", "adaptation:golem-copper-frame", "art.arcane.adapt.content.adaptation.golemancy.GolemCopperFrame");
    addConfiguration(fields, "adaptations.golem-frost-frame", "adaptation:golem-frost-frame", "art.arcane.adapt.content.adaptation.golemancy.GolemFrostFrame");
    addConfiguration(fields, "adaptations.golem-gatherer-core", "adaptation:golem-gatherer-core", "art.arcane.adapt.content.adaptation.golemancy.GolemGathererCore");
    addConfiguration(fields, "adaptations.golem-guardian-core", "adaptation:golem-guardian-core", "art.arcane.adapt.content.adaptation.golemancy.GolemGuardianCore");
    addConfiguration(fields, "adaptations.golem-harvester-core", "adaptation:golem-harvester-core", "art.arcane.adapt.content.adaptation.golemancy.GolemHarvesterCore");
    addConfiguration(fields, "adaptations.golem-iron-frame", "adaptation:golem-iron-frame", "art.arcane.adapt.content.adaptation.golemancy.GolemIronFrame");
    addConfiguration(fields, "adaptations.golem-lumber-core", "adaptation:golem-lumber-core", "art.arcane.adapt.content.adaptation.golemancy.GolemLumberCore");
    addConfiguration(fields, "adaptations.golem-masterwork", "adaptation:golem-masterwork", "art.arcane.adapt.content.adaptation.golemancy.GolemMasterwork");
    addConfiguration(fields, "adaptations.golem-mending", "adaptation:golem-mending", "art.arcane.adapt.content.adaptation.golemancy.GolemMending");
    addConfiguration(fields, "adaptations.golem-node-weaving", "adaptation:golem-node-weaving", "art.arcane.adapt.content.adaptation.golemancy.GolemNodeWeaving");
    addConfiguration(fields, "adaptations.golem-overseer", "adaptation:golem-overseer", "art.arcane.adapt.content.adaptation.golemancy.GolemOverseer");
    addConfiguration(fields, "adaptations.golem-rancher-core", "adaptation:golem-rancher-core", "art.arcane.adapt.content.adaptation.golemancy.GolemRancherCore");
    addConfiguration(fields, "adaptations.golem-runic-augments", "adaptation:golem-runic-augments", "art.arcane.adapt.content.adaptation.golemancy.GolemRunicAugments");
    addConfiguration(fields, "adaptations.golem-sorter-core", "adaptation:golem-sorter-core", "art.arcane.adapt.content.adaptation.golemancy.GolemSorterCore");
    addConfiguration(fields, "adaptations.golem-stasis", "adaptation:golem-stasis", "art.arcane.adapt.content.adaptation.golemancy.GolemStasis");
    addConfiguration(fields, "adaptations.herbalism-bee-shepherd", "adaptation:herbalism-bee-shepherd", "art.arcane.adapt.content.adaptation.herbalism.HerbalismBeeShepherd");
    addConfiguration(fields, "adaptations.herbalism-compost-cascade", "adaptation:herbalism-compost-cascade", "art.arcane.adapt.content.adaptation.herbalism.HerbalismCompostCascade");
    addConfiguration(fields, "adaptations.herbalism-cobweb", "adaptation:herbalism-cobweb", "art.arcane.adapt.content.adaptation.herbalism.HerbalismCraftableCobweb");
    addConfiguration(fields, "adaptations.herbalism-mushroom-blocks", "adaptation:herbalism-mushroom-blocks", "art.arcane.adapt.content.adaptation.herbalism.HerbalismCraftableMushroomBlocks");
    addConfiguration(fields, "adaptations.herbalism-drop-to-inventory", "adaptation:herbalism-drop-to-inventory", "art.arcane.adapt.content.adaptation.herbalism.HerbalismDropToInventory");
    addConfiguration(fields, "adaptations.herbalism-growth-aura", "adaptation:herbalism-growth-aura", "art.arcane.adapt.content.adaptation.herbalism.HerbalismGrowthAura");
    addConfiguration(fields, "adaptations.herbalism-hippo", "adaptation:herbalism-hippo", "art.arcane.adapt.content.adaptation.herbalism.HerbalismHungryHippo");
    addConfiguration(fields, "adaptations.herbalism-hungry-shield", "adaptation:herbalism-hungry-shield", "art.arcane.adapt.content.adaptation.herbalism.HerbalismHungryShield");
    addConfiguration(fields, "adaptations.herbalism-luck", "adaptation:herbalism-luck", "art.arcane.adapt.content.adaptation.herbalism.HerbalismLuck");
    addConfiguration(fields, "adaptations.herbalism-myconid", "adaptation:herbalism-myconid", "art.arcane.adapt.content.adaptation.herbalism.HerbalismMyconid");
    addConfiguration(fields, "adaptations.herbalism-replant", "adaptation:herbalism-replant", "art.arcane.adapt.content.adaptation.herbalism.HerbalismReplant");
    addConfiguration(fields, "adaptations.herbalism-rooted-footing", "adaptation:herbalism-rooted-footing", "art.arcane.adapt.content.adaptation.herbalism.HerbalismRootedFooting");
    addConfiguration(fields, "adaptations.herbalism-seed-sower", "adaptation:herbalism-seed-sower", "art.arcane.adapt.content.adaptation.herbalism.HerbalismSeedSower");
    addConfiguration(fields, "adaptations.herbalism-spore-bloom", "adaptation:herbalism-spore-bloom", "art.arcane.adapt.content.adaptation.herbalism.HerbalismSporeBloom");
    addConfiguration(fields, "adaptations.herbalism-terralid", "adaptation:herbalism-terralid", "art.arcane.adapt.content.adaptation.herbalism.HerbalismTerralid");
    addConfiguration(fields, "adaptations.hunter-adrenaline", "adaptation:hunter-adrenaline", "art.arcane.adapt.content.adaptation.hunter.HunterAdrenaline");
    addConfiguration(fields, "adaptations.hunter-big-game", "adaptation:hunter-big-game", "art.arcane.adapt.content.adaptation.hunter.HunterBigGameHunter");
    addConfiguration(fields, "adaptations.hunter-blood-trail", "adaptation:hunter-blood-trail", "art.arcane.adapt.content.adaptation.hunter.HunterBloodTrail");
    addConfiguration(fields, "adaptations.hunter-drop-to-inventory", "adaptation:hunter-drop-to-inventory", "art.arcane.adapt.content.adaptation.hunter.HunterDropToInventory");
    addConfiguration(fields, "adaptations.hunter-invis", "adaptation:hunter-invis", "art.arcane.adapt.content.adaptation.hunter.HunterInvis");
    addConfiguration(fields, "adaptations.hunter-jumpboost", "adaptation:hunter-jumpboost", "art.arcane.adapt.content.adaptation.hunter.HunterJumpBoost");
    addConfiguration(fields, "adaptations.hunter-luck", "adaptation:hunter-luck", "art.arcane.adapt.content.adaptation.hunter.HunterLuck");
    addConfiguration(fields, "adaptations.hunter-predator-focus", "adaptation:hunter-predator-focus", "art.arcane.adapt.content.adaptation.hunter.HunterPredatorFocus");
    addConfiguration(fields, "adaptations.hunter-regen", "adaptation:hunter-regen", "art.arcane.adapt.content.adaptation.hunter.HunterRegen");
    addConfiguration(fields, "adaptations.hunter-resistance", "adaptation:hunter-resistance", "art.arcane.adapt.content.adaptation.hunter.HunterResistance");
    addConfiguration(fields, "adaptations.hunter-snare-line", "adaptation:hunter-snare-line", "art.arcane.adapt.content.adaptation.hunter.HunterSnareLine");
    addConfiguration(fields, "adaptations.hunter-speed", "adaptation:hunter-speed", "art.arcane.adapt.content.adaptation.hunter.HunterSpeed");
    addConfiguration(fields, "adaptations.hunter-strength", "adaptation:hunter-strength", "art.arcane.adapt.content.adaptation.hunter.HunterStrength");
    addConfiguration(fields, "adaptations.hunter-trophy-skinner", "adaptation:hunter-trophy-skinner", "art.arcane.adapt.content.adaptation.hunter.HunterTrophySkinner");
    addConfiguration(fields, "adaptations.kinetics-breachwright", "adaptation:kinetics-breachwright", "art.arcane.adapt.content.adaptation.kinetics.KineticsBreachwright");
    addConfiguration(fields, "adaptations.kinetics-charge-lance", "adaptation:kinetics-charge-lance", "art.arcane.adapt.content.adaptation.kinetics.KineticsChargeLance");
    addConfiguration(fields, "adaptations.kinetics-dead-zone", "adaptation:kinetics-dead-zone", "art.arcane.adapt.content.adaptation.kinetics.KineticsDeadZone");
    addConfiguration(fields, "adaptations.kinetics-heavy-frame", "adaptation:kinetics-heavy-frame", "art.arcane.adapt.content.adaptation.kinetics.KineticsHeavyFrame");
    addConfiguration(fields, "adaptations.kinetics-impale-pin", "adaptation:kinetics-impale-pin", "art.arcane.adapt.content.adaptation.kinetics.KineticsImpalePin");
    addConfiguration(fields, "adaptations.kinetics-lunge-conductor", "adaptation:kinetics-lunge-conductor", "art.arcane.adapt.content.adaptation.kinetics.KineticsLungeConductor");
    addConfiguration(fields, "adaptations.kinetics-mass-shift", "adaptation:kinetics-mass-shift", "art.arcane.adapt.content.adaptation.kinetics.KineticsMassShift");
    addConfiguration(fields, "adaptations.kinetics-meteor-cadence", "adaptation:kinetics-meteor-cadence", "art.arcane.adapt.content.adaptation.kinetics.KineticsMeteorCadence");
    addConfiguration(fields, "adaptations.kinetics-moon-jump", "adaptation:kinetics-moon-jump", "art.arcane.adapt.content.adaptation.kinetics.KineticsMoonJump");
    addConfiguration(fields, "adaptations.kinetics-mounted-shock", "adaptation:kinetics-mounted-shock", "art.arcane.adapt.content.adaptation.kinetics.KineticsMountedShock");
    addConfiguration(fields, "adaptations.kinetics-phalanx-reach", "adaptation:kinetics-phalanx-reach", "art.arcane.adapt.content.adaptation.kinetics.KineticsPhalanxReach");
    addConfiguration(fields, "adaptations.kinetics-quake-guard", "adaptation:kinetics-quake-guard", "art.arcane.adapt.content.adaptation.kinetics.KineticsQuakeGuard");
    addConfiguration(fields, "adaptations.kinetics-rebound-anvil", "adaptation:kinetics-rebound-anvil", "art.arcane.adapt.content.adaptation.kinetics.KineticsReboundAnvil");
    addConfiguration(fields, "adaptations.kinetics-rubber-soul", "adaptation:kinetics-rubber-soul", "art.arcane.adapt.content.adaptation.kinetics.KineticsRubberSoul");
    addConfiguration(fields, "adaptations.kinetics-soft-catch", "adaptation:kinetics-soft-catch", "art.arcane.adapt.content.adaptation.kinetics.KineticsSoftCatch");
    addConfiguration(fields, "adaptations.kinetics-surface-skate", "adaptation:kinetics-surface-skate", "art.arcane.adapt.content.adaptation.kinetics.KineticsSurfaceSkate");
    addConfiguration(fields, "adaptations.kinetics-terminal-toggle", "adaptation:kinetics-terminal-toggle", "art.arcane.adapt.content.adaptation.kinetics.KineticsTerminalToggle");
    addConfiguration(fields, "adaptations.kinetics-windburst", "adaptation:kinetics-windburst", "art.arcane.adapt.content.adaptation.kinetics.KineticsWindburst");
    addConfiguration(fields, "adaptations.nether-ashwalker", "adaptation:nether-ashwalker", "art.arcane.adapt.content.adaptation.nether.NetherAshwalker");
    addConfiguration(fields, "adaptations.nether-blaze-leech", "adaptation:nether-blaze-leech", "art.arcane.adapt.content.adaptation.nether.NetherBlazeLeech");
    addConfiguration(fields, "adaptations.nether-crimson-feast", "adaptation:nether-crimson-feast", "art.arcane.adapt.content.adaptation.nether.NetherCrimsonFeast");
    addConfiguration(fields, "adaptations.nether-fire-resist", "adaptation:nether-fire-resist", "art.arcane.adapt.content.adaptation.nether.NetherFireResist");
    addConfiguration(fields, "adaptations.nether-ghast-ward", "adaptation:nether-ghast-ward", "art.arcane.adapt.content.adaptation.nether.NetherGhastWard");
    addConfiguration(fields, "adaptations.nether-lava-walker", "adaptation:nether-lava-walker", "art.arcane.adapt.content.adaptation.nether.NetherLavaWalker");
    addConfiguration(fields, "adaptations.nether-magma-skin", "adaptation:nether-magma-skin", "art.arcane.adapt.content.adaptation.nether.NetherMagmaSkin");
    addConfiguration(fields, "adaptations.nether-netherrack-mason", "adaptation:nether-netherrack-mason", "art.arcane.adapt.content.adaptation.nether.NetherNetherrackMason");
    addConfiguration(fields, "adaptations.nether-piglin-broker", "adaptation:nether-piglin-broker", "art.arcane.adapt.content.adaptation.nether.NetherPiglinBroker");
    addConfiguration(fields, "adaptations.nether-skull-toss", "adaptation:nether-skull-toss", "art.arcane.adapt.content.adaptation.nether.NetherSkullYeet");
    addConfiguration(fields, "adaptations.nether-soul-strider", "adaptation:nether-soul-strider", "art.arcane.adapt.content.adaptation.nether.NetherSoulStrider");
    addConfiguration(fields, "adaptations.nether-strider-bond", "adaptation:nether-strider-bond", "art.arcane.adapt.content.adaptation.nether.NetherStriderBond");
    addConfiguration(fields, "adaptations.nether-wither-harvest", "adaptation:nether-wither-harvest", "art.arcane.adapt.content.adaptation.nether.NetherWitherHarvest");
    addConfiguration(fields, "adaptations.nether-wither-resist", "adaptation:nether-wither-resist", "art.arcane.adapt.content.adaptation.nether.NetherWitherResist");
    addConfiguration(fields, "adaptations.pickaxe-autosmelt", "adaptation:pickaxe-autosmelt", "art.arcane.adapt.content.adaptation.pickaxe.PickaxeAutosmelt");
    addConfiguration(fields, "adaptations.pickaxe-chisel", "adaptation:pickaxe-chisel", "art.arcane.adapt.content.adaptation.pickaxe.PickaxeChisel");
    addConfiguration(fields, "adaptations.pickaxe-deep-core", "adaptation:pickaxe-deep-core", "art.arcane.adapt.content.adaptation.pickaxe.PickaxeDeepCore");
    addConfiguration(fields, "adaptations.pickaxe-drop-to-inventory", "adaptation:pickaxe-drop-to-inventory", "art.arcane.adapt.content.adaptation.pickaxe.PickaxeDropToInventory");
    addConfiguration(fields, "adaptations.pickaxe-gem-polish", "adaptation:pickaxe-gem-polish", "art.arcane.adapt.content.adaptation.pickaxe.PickaxeGemPolish");
    addConfiguration(fields, "adaptations.pickaxe-obsidian-rush", "adaptation:pickaxe-obsidian-rush", "art.arcane.adapt.content.adaptation.pickaxe.PickaxeObsidianRush");
    addConfiguration(fields, "adaptations.pickaxe-quarry-sense", "adaptation:pickaxe-quarry-sense", "art.arcane.adapt.content.adaptation.pickaxe.PickaxeQuarrySense");
    addConfiguration(fields, "adaptations.pickaxe-repair-rhythm", "adaptation:pickaxe-repair-rhythm", "art.arcane.adapt.content.adaptation.pickaxe.PickaxeRepairRhythm");
    addConfiguration(fields, "adaptations.pickaxe-silk-spawner", "adaptation:pickaxe-silk-spawner", "art.arcane.adapt.content.adaptation.pickaxe.PickaxeSilkSpawner");
    addConfiguration(fields, "adaptations.pickaxe-stone-skin", "adaptation:pickaxe-stone-skin", "art.arcane.adapt.content.adaptation.pickaxe.PickaxeStoneSkin");
    addConfiguration(fields, "adaptations.pickaxe-tunnel-bore", "adaptation:pickaxe-tunnel-bore", "art.arcane.adapt.content.adaptation.pickaxe.PickaxeTunnelBore");
    addConfiguration(fields, "adaptations.pickaxe-unbreakable-pact", "adaptation:pickaxe-unbreakable-pact", "art.arcane.adapt.content.adaptation.pickaxe.PickaxeUnbreakablePact");
    addConfiguration(fields, "adaptations.pickaxe-veinminer", "adaptation:pickaxe-veinminer", "art.arcane.adapt.content.adaptation.pickaxe.PickaxeVeinminer");
    addConfiguration(fields, "adaptations.ranged-recovery", "adaptation:ranged-recovery", "art.arcane.adapt.content.adaptation.ranged.RangedArrowRecovery");
    addConfiguration(fields, "adaptations.ranged-fetch-shot", "adaptation:ranged-fetch-shot", "art.arcane.adapt.content.adaptation.ranged.RangedFetchShot");
    addConfiguration(fields, "adaptations.ranged-floaters", "adaptation:ranged-floaters", "art.arcane.adapt.content.adaptation.ranged.RangedFloaters");
    addConfiguration(fields, "adaptations.ranged-force", "adaptation:ranged-force", "art.arcane.adapt.content.adaptation.ranged.RangedForce");
    addConfiguration(fields, "adaptations.ranged-heartseeker", "adaptation:ranged-heartseeker", "art.arcane.adapt.content.adaptation.ranged.RangedHeartseeker");
    addConfiguration(fields, "adaptations.ranged-heavy-draw", "adaptation:ranged-heavy-draw", "art.arcane.adapt.content.adaptation.ranged.RangedHeavyDraw");
    addConfiguration(fields, "adaptations.ranged-lunge-shot", "adaptation:ranged-lunge-shot", "art.arcane.adapt.content.adaptation.ranged.RangedLungeShot");
    addConfiguration(fields, "adaptations.ranged-piercing", "adaptation:ranged-piercing", "art.arcane.adapt.content.adaptation.ranged.RangedPiercing");
    addConfiguration(fields, "adaptations.ranged-pinning-shot", "adaptation:ranged-pinning-shot", "art.arcane.adapt.content.adaptation.ranged.RangedPinningShot");
    addConfiguration(fields, "adaptations.ranged-ricochet-bolt", "adaptation:ranged-ricochet-bolt", "art.arcane.adapt.content.adaptation.ranged.RangedRicochetBolt");
    addConfiguration(fields, "adaptations.ranged-trajectory-sight", "adaptation:ranged-trajectory-sight", "art.arcane.adapt.content.adaptation.ranged.RangedTrajectorySight");
    addConfiguration(fields, "adaptations.ranged-webshot", "adaptation:ranged-webshot", "art.arcane.adapt.content.adaptation.ranged.RangedWebBomb");
    addConfiguration(fields, "adaptations.rift-access", "adaptation:rift-access", "art.arcane.adapt.content.adaptation.rift.RiftAccess");
    addConfiguration(fields, "adaptations.rift-blink", "adaptation:rift-blink", "art.arcane.adapt.content.adaptation.rift.RiftBlink");
    addConfiguration(fields, "adaptations.rift-conduit", "adaptation:rift-conduit", "art.arcane.adapt.content.adaptation.rift.RiftConduit");
    addConfiguration(fields, "adaptations.rift-descent", "adaptation:rift-descent", "art.arcane.adapt.content.adaptation.rift.RiftDescent");
    addConfiguration(fields, "adaptations.rift-ender-taglock", "adaptation:rift-ender-taglock", "art.arcane.adapt.content.adaptation.rift.RiftEnderTaglock");
    addConfiguration(fields, "adaptations.rift-enderchest", "adaptation:rift-enderchest", "art.arcane.adapt.content.adaptation.rift.RiftEnderchest");
    addConfiguration(fields, "adaptations.rift-gate", "adaptation:rift-gate", "art.arcane.adapt.content.adaptation.rift.RiftGate");
    addConfiguration(fields, "adaptations.rift-inflated-pocket-dimension", "adaptation:rift-inflated-pocket-dimension", "art.arcane.adapt.content.adaptation.rift.RiftInflatedPocketDimension");
    addConfiguration(fields, "adaptations.rift-pearl-rebound", "adaptation:rift-pearl-rebound", "art.arcane.adapt.content.adaptation.rift.RiftPearlRebound");
    addConfiguration(fields, "adaptations.rift-resist", "adaptation:rift-resist", "art.arcane.adapt.content.adaptation.rift.RiftResist");
    addConfiguration(fields, "adaptations.rift-visage", "adaptation:rift-visage", "art.arcane.adapt.content.adaptation.rift.RiftVisage");
    addConfiguration(fields, "adaptations.rift-void-magnet", "adaptation:rift-void-magnet", "art.arcane.adapt.content.adaptation.rift.RiftVoidMagnet");
    addConfiguration(fields, "adaptations.rift-void-skin", "adaptation:rift-void-skin", "art.arcane.adapt.content.adaptation.rift.RiftVoidSkin");
    addConfiguration(fields, "adaptations.seaborne-brine-skin", "adaptation:seaborne-brine-skin", "art.arcane.adapt.content.adaptation.seaborrne.SeaborneBrineSkin");
    addConfiguration(fields, "adaptations.seaborne-coral-gardener", "adaptation:seaborne-coral-gardener", "art.arcane.adapt.content.adaptation.seaborrne.SeaborneCoralGardener");
    addConfiguration(fields, "adaptations.seaborne-deep-salvager", "adaptation:seaborne-deep-salvager", "art.arcane.adapt.content.adaptation.seaborrne.SeaborneDeepSalvager");
    addConfiguration(fields, "adaptations.seaborne-fish-whisperer", "adaptation:seaborne-fish-whisperer", "art.arcane.adapt.content.adaptation.seaborrne.SeaborneFishWhisperer");
    addConfiguration(fields, "adaptations.seaborne-fishers-fantasy", "adaptation:seaborne-fishers-fantasy", "art.arcane.adapt.content.adaptation.seaborrne.SeaborneFishersFantasy");
    addConfiguration(fields, "adaptations.seaborne-hydro-jet", "adaptation:seaborne-hydro-jet", "art.arcane.adapt.content.adaptation.seaborrne.SeaborneHydroJet");
    addConfiguration(fields, "adaptations.seaborne-ink-veil", "adaptation:seaborne-ink-veil", "art.arcane.adapt.content.adaptation.seaborrne.SeaborneInkVeil");
    addConfiguration(fields, "adaptations.seaborne-oxygen", "adaptation:seaborne-oxygen", "art.arcane.adapt.content.adaptation.seaborrne.SeaborneOxygen");
    addConfiguration(fields, "adaptations.seaborne-pressure-diver", "adaptation:seaborne-pressure-diver", "art.arcane.adapt.content.adaptation.seaborrne.SeabornePressureDiver");
    addConfiguration(fields, "adaptations.seaborne-speed", "adaptation:seaborne-speed", "art.arcane.adapt.content.adaptation.seaborrne.SeaborneSpeed");
    addConfiguration(fields, "adaptations.seaborne-tidecaller", "adaptation:seaborne-tidecaller", "art.arcane.adapt.content.adaptation.seaborrne.SeaborneTidecaller");
    addConfiguration(fields, "adaptations.seaborne-trident-mastery", "adaptation:seaborne-trident-mastery", "art.arcane.adapt.content.adaptation.seaborrne.SeaborneTridentMastery");
    addConfiguration(fields, "adaptations.seaborne-turtles-mining-speed", "adaptation:seaborne-turtles-mining-speed", "art.arcane.adapt.content.adaptation.seaborrne.SeaborneTurtlesMiningSpeed");
    addConfiguration(fields, "adaptations.seaborne-turtles-vision", "adaptation:seaborne-turtles-vision", "art.arcane.adapt.content.adaptation.seaborrne.SeaborneTurtlesVision");
    addConfiguration(fields, "adaptations.stealth-assassinate", "adaptation:stealth-assassinate", "art.arcane.adapt.content.adaptation.stealth.StealthAssassinate");
    addConfiguration(fields, "adaptations.stealth-silent-step", "adaptation:stealth-silent-step", "art.arcane.adapt.content.adaptation.stealth.StealthCore");
    addConfiguration(fields, "adaptations.stealth-cutpurse", "adaptation:stealth-cutpurse", "art.arcane.adapt.content.adaptation.stealth.StealthCutpurse");
    addConfiguration(fields, "adaptations.stealth-decoy-swap", "adaptation:stealth-decoy-swap", "art.arcane.adapt.content.adaptation.stealth.StealthDecoySwap");
    addConfiguration(fields, "adaptations.stealth-enderveil", "adaptation:stealth-enderveil", "art.arcane.adapt.content.adaptation.stealth.StealthEnderVeil");
    addConfiguration(fields, "adaptations.stealth-ghost-armor", "adaptation:stealth-ghost-armor", "art.arcane.adapt.content.adaptation.stealth.StealthGhostArmor");
    addConfiguration(fields, "adaptations.stealth-shadow-decoy", "adaptation:stealth-shadow-decoy", "art.arcane.adapt.content.adaptation.stealth.StealthShadowDecoy");
    addConfiguration(fields, "adaptations.stealth-shadowmeld", "adaptation:stealth-shadowmeld", "art.arcane.adapt.content.adaptation.stealth.StealthShadowmeld");
    addConfiguration(fields, "adaptations.stealth-vision", "adaptation:stealth-vision", "art.arcane.adapt.content.adaptation.stealth.StealthSight");
    addConfiguration(fields, "adaptations.stealth-smoke-pellet", "adaptation:stealth-smoke-pellet", "art.arcane.adapt.content.adaptation.stealth.StealthSmokePellet");
    addConfiguration(fields, "adaptations.stealth-snatch", "adaptation:stealth-snatch", "art.arcane.adapt.content.adaptation.stealth.StealthSnatch");
    addConfiguration(fields, "adaptations.stealth-speed", "adaptation:stealth-speed", "art.arcane.adapt.content.adaptation.stealth.StealthSpeed");
    addConfiguration(fields, "adaptations.stealth-trap-sense", "adaptation:stealth-trap-sense", "art.arcane.adapt.content.adaptation.stealth.StealthTrapSense");
    addConfiguration(fields, "adaptations.stealth-umbral-recovery", "adaptation:stealth-umbral-recovery", "art.arcane.adapt.content.adaptation.stealth.StealthUmbralRecovery");
    addConfiguration(fields, "adaptations.sword-blade-flow", "adaptation:sword-blade-flow", "art.arcane.adapt.content.adaptation.sword.SwordsBladeFlow");
    addConfiguration(fields, "adaptations.sword-bloody-blade", "adaptation:sword-bloody-blade", "art.arcane.adapt.content.adaptation.sword.SwordsBloodyBlade");
    addConfiguration(fields, "adaptations.sword-crescent-guard", "adaptation:sword-crescent-guard", "art.arcane.adapt.content.adaptation.sword.SwordsCrescentGuard");
    addConfiguration(fields, "adaptations.sword-crimson-cyclone", "adaptation:sword-crimson-cyclone", "art.arcane.adapt.content.adaptation.sword.SwordsCrimsonCyclone");
    addConfiguration(fields, "adaptations.sword-dual-wield", "adaptation:sword-dual-wield", "art.arcane.adapt.content.adaptation.sword.SwordsDualWield");
    addConfiguration(fields, "adaptations.sword-duelists-focus", "adaptation:sword-duelists-focus", "art.arcane.adapt.content.adaptation.sword.SwordsDuelistsFocus");
    addConfiguration(fields, "adaptations.sword-executioners-edge", "adaptation:sword-executioners-edge", "art.arcane.adapt.content.adaptation.sword.SwordsExecutionersEdge");
    addConfiguration(fields, "adaptations.sword-hamstring", "adaptation:sword-hamstring", "art.arcane.adapt.content.adaptation.sword.SwordsHamstring");
    addConfiguration(fields, "adaptations.sword-heirloom-edge", "adaptation:sword-heirloom-edge", "art.arcane.adapt.content.adaptation.sword.SwordsHeirloomEdge");
    addConfiguration(fields, "adaptations.sword-lunge-strike", "adaptation:sword-lunge-strike", "art.arcane.adapt.content.adaptation.sword.SwordsLungeStrike");
    addConfiguration(fields, "adaptations.sword-machete", "adaptation:sword-machete", "art.arcane.adapt.content.adaptation.sword.SwordsMachete");
    addConfiguration(fields, "adaptations.sword-poison-blade", "adaptation:sword-poison-blade", "art.arcane.adapt.content.adaptation.sword.SwordsPoisonedBlade");
    addConfiguration(fields, "adaptations.sword-riposte-window", "adaptation:sword-riposte-window", "art.arcane.adapt.content.adaptation.sword.SwordsRiposteWindow");
    addConfiguration(fields, "adaptations.sword-whetstone-ritual", "adaptation:sword-whetstone-ritual", "art.arcane.adapt.content.adaptation.sword.SwordsWhetstoneRitual");
    addConfiguration(fields, "adaptations.tame-alphas-command", "adaptation:tame-alphas-command", "art.arcane.adapt.content.adaptation.taming.TamingAlphasCommand");
    addConfiguration(fields, "adaptations.tame-battle-bond", "adaptation:tame-battle-bond", "art.arcane.adapt.content.adaptation.taming.TamingBattleBond");
    addConfiguration(fields, "adaptations.tame-beast-recall", "adaptation:tame-beast-recall", "art.arcane.adapt.content.adaptation.taming.TamingBeastRecall");
    addConfiguration(fields, "adaptations.tame-damage", "adaptation:tame-damage", "art.arcane.adapt.content.adaptation.taming.TamingDamage");
    addConfiguration(fields, "adaptations.tame-fetch", "adaptation:tame-fetch", "art.arcane.adapt.content.adaptation.taming.TamingFetch");
    addConfiguration(fields, "adaptations.tame-guardian-instinct", "adaptation:tame-guardian-instinct", "art.arcane.adapt.content.adaptation.taming.TamingGuardianInstinct");
    addConfiguration(fields, "adaptations.tame-health", "adaptation:tame-health", "art.arcane.adapt.content.adaptation.taming.TamingHealthBoost");
    addConfiguration(fields, "adaptations.tame-health-regeneration", "adaptation:tame-health-regeneration", "art.arcane.adapt.content.adaptation.taming.TamingHealthRegeneration");
    addConfiguration(fields, "adaptations.tame-last-breath", "adaptation:tame-last-breath", "art.arcane.adapt.content.adaptation.taming.TamingLastBreath");
    addConfiguration(fields, "adaptations.tame-mounted-tactics", "adaptation:tame-mounted-tactics", "art.arcane.adapt.content.adaptation.taming.TamingMountedTactics");
    addConfiguration(fields, "adaptations.tame-pack-leader-aura", "adaptation:tame-pack-leader-aura", "art.arcane.adapt.content.adaptation.taming.TamingPackLeaderAura");
    addConfiguration(fields, "adaptations.tame-shared-pain", "adaptation:tame-shared-pain", "art.arcane.adapt.content.adaptation.taming.TamingSharedPain");
    addConfiguration(fields, "adaptations.tame-stable-hand", "adaptation:tame-stable-hand", "art.arcane.adapt.content.adaptation.taming.TamingStableHand");
    addConfiguration(fields, "adaptations.tame-wild-empathy", "adaptation:tame-wild-empathy", "art.arcane.adapt.content.adaptation.taming.TamingWildEmpathy");
    addConfiguration(fields, "adaptations.tragoul-blood-pact", "adaptation:tragoul-blood-pact", "art.arcane.adapt.content.adaptation.tragoul.TragoulBloodPact");
    addConfiguration(fields, "adaptations.tragoul-bone-harvest", "adaptation:tragoul-bone-harvest", "art.arcane.adapt.content.adaptation.tragoul.TragoulBoneHarvest");
    addConfiguration(fields, "adaptations.tragoul-corpse-explosion", "adaptation:tragoul-corpse-explosion", "art.arcane.adapt.content.adaptation.tragoul.TragoulCorpseExplosion");
    addConfiguration(fields, "adaptations.tragoul-curse-of-frailty", "adaptation:tragoul-curse-of-frailty", "art.arcane.adapt.content.adaptation.tragoul.TragoulCurseOfFrailty");
    addConfiguration(fields, "adaptations.tragoul-death-sense", "adaptation:tragoul-death-sense", "art.arcane.adapt.content.adaptation.tragoul.TragoulDeathSense");
    addConfiguration(fields, "adaptations.tragoul-globe", "adaptation:tragoul-globe", "art.arcane.adapt.content.adaptation.tragoul.TragoulGlobe");
    addConfiguration(fields, "adaptations.tragoul-healing", "adaptation:tragoul-healing", "art.arcane.adapt.content.adaptation.tragoul.TragoulHealing");
    addConfiguration(fields, "adaptations.tragoul-lance", "adaptation:tragoul-lance", "art.arcane.adapt.content.adaptation.tragoul.TragoulLance");
    addConfiguration(fields, "adaptations.tragoul-last-rites", "adaptation:tragoul-last-rites", "art.arcane.adapt.content.adaptation.tragoul.TragoulLastRites");
    addConfiguration(fields, "adaptations.tragoul-marrow-armor", "adaptation:tragoul-marrow-armor", "art.arcane.adapt.content.adaptation.tragoul.TragoulMarrowArmor");
    addConfiguration(fields, "adaptations.tragoul-plague-bearer", "adaptation:tragoul-plague-bearer", "art.arcane.adapt.content.adaptation.tragoul.TragoulPlagueBearer");
    addConfiguration(fields, "adaptations.tragoul-skeletal-servant", "adaptation:tragoul-skeletal-servant", "art.arcane.adapt.content.adaptation.tragoul.TragoulSkeletalServant");
    addConfiguration(fields, "adaptations.tragoul-soul-siphon", "adaptation:tragoul-soul-siphon", "art.arcane.adapt.content.adaptation.tragoul.TragoulSoulSiphon");
    addConfiguration(fields, "adaptations.tragoul-thorns", "adaptation:tragoul-thorns", "art.arcane.adapt.content.adaptation.tragoul.TragoulThorns");
    addConfiguration(fields, "adaptations.unarmed-battering-charge", "adaptation:unarmed-battering-charge", "art.arcane.adapt.content.adaptation.unarmed.UnarmedBatteringCharge");
    addConfiguration(fields, "adaptations.unarmed-combo-chain", "adaptation:unarmed-combo-chain", "art.arcane.adapt.content.adaptation.unarmed.UnarmedComboChain");
    addConfiguration(fields, "adaptations.unarmed-disarm", "adaptation:unarmed-disarm", "art.arcane.adapt.content.adaptation.unarmed.UnarmedDisarm");
    addConfiguration(fields, "adaptations.unarmed-glass-cannon", "adaptation:unarmed-glass-cannon", "art.arcane.adapt.content.adaptation.unarmed.UnarmedGlassCannon");
    addConfiguration(fields, "adaptations.unarmed-grapple", "adaptation:unarmed-grapple", "art.arcane.adapt.content.adaptation.unarmed.UnarmedGrapple");
    addConfiguration(fields, "adaptations.unarmed-iron-fists", "adaptation:unarmed-iron-fists", "art.arcane.adapt.content.adaptation.unarmed.UnarmedIronFists");
    addConfiguration(fields, "adaptations.unarmed-meditation", "adaptation:unarmed-meditation", "art.arcane.adapt.content.adaptation.unarmed.UnarmedMeditation");
    addConfiguration(fields, "adaptations.unarmed-power", "adaptation:unarmed-power", "art.arcane.adapt.content.adaptation.unarmed.UnarmedPower");
    addConfiguration(fields, "adaptations.unarmed-pressure-point", "adaptation:unarmed-pressure-point", "art.arcane.adapt.content.adaptation.unarmed.UnarmedPressurePoint");
    addConfiguration(fields, "adaptations.unarmed-second-wind", "adaptation:unarmed-second-wind", "art.arcane.adapt.content.adaptation.unarmed.UnarmedSecondWind");
    addConfiguration(fields, "adaptations.unarmed-shockwave-clap", "adaptation:unarmed-shockwave-clap", "art.arcane.adapt.content.adaptation.unarmed.UnarmedShockwaveClap");
    addConfiguration(fields, "adaptations.unarmed-sucker-punch", "adaptation:unarmed-sucker-punch", "art.arcane.adapt.content.adaptation.unarmed.UnarmedSuckerPunch");
    return Collections.unmodifiableMap(fields);
  }

  private static void addConfiguration(Map<String, FieldMessages> fields, String path, String source, String ownerClass) {
    Class<?> owner;
    try {
      owner = Class.forName(ownerClass, false, ConfigFieldMessages.class.getClassLoader());
    } catch (ClassNotFoundException absent) {
      return;
    }
    ParameterizedType parent = (ParameterizedType) owner.getGenericSuperclass();
    addFields(fields, path, source, (Class<?>) parent.getActualTypeArguments()[0]);
  }

  private static void addFields(Map<String, FieldMessages> fields, String parentPath, String source, Class<?> type) {
    Class<?> parent = type.getSuperclass();
    if (parent != null && parent != Object.class) {
      addFields(fields, parentPath, source, parent);
    }
    for (Field field : type.getDeclaredFields()) {
      int modifiers = field.getModifiers();
      if (field.isSynthetic() || Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)) {
        continue;
      }
      String path = parentPath + "." + field.getName();
      String id = "config.fields." + path;
      String label = field.getName().replace('_', ' ').replace('-', ' ').replaceAll("([a-z])([A-Z])", "$1 $2");
      label = Character.toUpperCase(label.charAt(0)) + label.substring(1);
      fields.put(path, new FieldMessages(TextKey.of(id + ".label", label),
          LinesKey.of(id + ".description", ConfigDocumentation.buildFieldComments(source, path, field, null))));
      Class<?> fieldType = field.getType();
      if (!fieldType.isEnum() && fieldType.getName().startsWith("art.arcane.adapt.")) {
        addFields(fields, path, source, fieldType);
      }
    }
  }

  public record FieldMessages(TextKey label, LinesKey description) {
  }
}
