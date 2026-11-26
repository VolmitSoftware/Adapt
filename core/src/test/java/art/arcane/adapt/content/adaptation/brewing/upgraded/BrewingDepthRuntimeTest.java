package art.arcane.adapt.content.adaptation.brewing.upgraded;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.mutation.blocks.BarterGesture;
import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.potion.BrewingRecipe;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.brewing.BrewingDoses;
import art.arcane.adapt.content.adaptation.brewing.BrewingHaste;
import art.arcane.adapt.content.adaptation.brewing.BrewingMixer;
import art.arcane.adapt.content.skill.SkillBrewing;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.platform.entity.EffectCategory;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.inventory.BrewEvent;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.event.inventory.PrepareItemCraftEvent;
import art.arcane.adapt.platform.event.inventory.InventoryAction;
import art.arcane.adapt.platform.event.inventory.InventoryClickEvent;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.event.inventory.SlotKind;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerItemHeldEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.item.PotionContents;
import art.arcane.adapt.platform.item.TooltipPart;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeBrewingStand;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.MutationKit;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Mixing on Witch's Shelf and Apothecary, the Alchemist's Bandolier on Master Distiller, and Haste on Apothecary. */
class BrewingDepthRuntimeTest {
  private static final Id SPEED = Id.minecraft("speed");
  private static final Id NIGHT_VISION = Id.minecraft("night_vision");
  private static final Id POISON = Id.minecraft("poison");
  private static final Id WEAKNESS = Id.minecraft("weakness");
  private static final Id INSTANT_HEALTH = Id.minecraft("instant_health");
  private static final Id GLASS_BOTTLE = Id.minecraft("glass_bottle");
  private static final EffectInstance HASTE_EFFECT = new EffectInstance(Id.minecraft("haste"), 1_200, 0, true, true, true);

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;
  private MutationKit kit;
  private BlockRef standBlock;
  private FakeBrewingStand stand;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    platform.host().potion(Id.minecraft("swiftness"), EffectInstance.of(SPEED, 3_600, 0));
    platform.host().potion(Id.minecraft("long_swiftness"), EffectInstance.of(SPEED, 9_600, 0));
    platform.host().potion(Id.minecraft("night_vision"), EffectInstance.of(NIGHT_VISION, 3_600, 0));
    platform.host().potion(Id.minecraft("poison"), EffectInstance.of(POISON, 900, 0));
    platform.host().potion(Id.minecraft("weakness"), EffectInstance.of(WEAKNESS, 1_800, 0));
    platform.host().potion(Id.minecraft("healing"), EffectInstance.of(INSTANT_HEALTH, 1, 0));
    platform.host().effect(INSTANT_HEALTH, EffectCategory.BENEFICIAL, true);
    platform.host().effect(POISON, EffectCategory.HARMFUL, false);
    platform.host().effect(WEAKNESS, EffectCategory.HARMFUL, false);
    platform.host().effect(SPEED, EffectCategory.BENEFICIAL, false);
    player = platform.player("Alchemist", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillBrewing.class, SkillSwords.class));
    profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
    standBlock = platform.world().set(0, 60, 0, "brewing_stand").blockAt(0, 60, 0);
    stand = (FakeBrewingStand) standBlock.brewingStand().orElseThrow();
    stand.setFuelLevel(5);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void apothecaryMixesTwoDrinkablePotionsIntoOneAndUsesBothAndOneGlowstone() {
    kit.mutate("brewing", "swords");
    Apothecary apothecary = learn(Apothecary.class, 1);
    load(potion("potion", "swiftness"), potion("potion", "night_vision"), FakeItem.empty(), 2);

    clickStand();
    platform.scheduler().advance(2 + apothecary.getConfig().mixBrewTicks);

    PotionContents mixed = stand.bottle(0).potionContents().orElseThrow();
    int ticks = (int) Math.round(3_600 * apothecary.getMixDuration(1));
    assertThat(stand.bottle(0).type()).isEqualTo(Id.minecraft("potion"));
    assertThat(mixed.customEffects()).containsExactly(EffectInstance.of(SPEED, ticks, 0), EffectInstance.of(NIGHT_VISION, ticks, 0));
    assertThat(stand.bottle(1).isEmpty()).isTrue();
    assertThat(stand.ingredient().amount()).isEqualTo(1);
    assertThat(stand.fuelLevel()).isEqualTo(4);
    assertThat(profile.getData().getStat("brewing.apothecary.potions-mixed")).isEqualTo(1D);
  }

  @Test
  void aMixRefusesInstantEffectsAnEffectTwiceOpenedBottlesAndMoreEffectsThanTheLevelAllows() {
    Apothecary apothecary = upgrade(Apothecary.class);
    BrewingMixer.Rules first = apothecary.mixRules(1);
    ItemRef pair = BrewingMixer.mix(List.of(potion("potion", "swiftness"), potion("potion", "night_vision")), first);

    assertThat(pair).isNotNull();
    assertThat(BrewingMixer.mix(List.of(potion("potion", "swiftness"), potion("potion", "healing")), first)).isNull();
    assertThat(BrewingMixer.mix(List.of(potion("potion", "swiftness"), potion("potion", "swiftness")), first)).isNull();
    assertThat(BrewingMixer.mix(List.of(pair, potion("potion", "poison")), first)).isNull();
    assertThat(BrewingMixer.mix(List.of(pair, potion("potion", "poison")), apothecary.mixRules(2))).isNull();
    assertThat(BrewingMixer.mix(List.of(pair, potion("potion", "poison")), apothecary.mixRules(3))).isNotNull();
    assertThat(BrewingMixer.mix(List.of(potion("potion", "swiftness"), potion("splash_potion", "poison")), first)).isNull();

    player.inventory().setOffHand(potion("potion", "poison"));
    BrewingDoses.take(player, BrewingDoses.find(player));
    assertThat(BrewingMixer.mix(List.of(player.inventory().offHand(), potion("potion", "swiftness")), first)).isNull();
  }

  @Test
  void remixingNeverGainsDurationOrEffects() {
    Apothecary apothecary = upgrade(Apothecary.class);
    BrewingMixer.Rules top = apothecary.mixRules(3);
    ItemRef pair = BrewingMixer.mix(List.of(potion("potion", "swiftness"), potion("potion", "night_vision")), top);
    ItemRef triple = BrewingMixer.mix(List.of(pair, potion("potion", "poison")), top);

    assertThat(triple.potionContents().orElseThrow().customEffects()).hasSize(3)
        .allSatisfy((EffectInstance effect) -> assertThat(effect.durationTicks()).isLessThan(3_600));
    assertThat(BrewingMixer.mix(List.of(triple, potion("potion", "weakness")), top)).isNull();
  }

  @Test
  void takingABottleOutMidBrewStopsTheMixAndMakesNothing() {
    kit.mutate("brewing", "swords");
    Apothecary apothecary = learn(Apothecary.class, 1);
    load(potion("potion", "swiftness"), potion("potion", "night_vision"), FakeItem.empty(), 2);

    clickStand();
    platform.scheduler().advance(100);
    ItemRef taken = stand.bottle(1);
    stand.setBottle(1, FakeItem.empty());
    platform.scheduler().advance(apothecary.getConfig().mixBrewTicks);

    assertThat(taken.potionContents().orElseThrow().potion()).isEqualTo(NIGHT_VISION);
    assertThat(stand.bottle(0).potionContents().orElseThrow().potion()).isEqualTo(Id.minecraft("swiftness"));
    assertThat(stand.bottle(0).potionContents().orElseThrow().customEffects()).isEmpty();
    assertThat(stand.ingredient().amount()).isEqualTo(2);
    assertThat(stand.brewingTime()).isZero();
  }

  @Test
  void aVanillaBrewFinishingOnAMixingStandIsCancelledAndPoursTheMix() {
    kit.mutate("brewing", "swords");
    learn(Apothecary.class, 1);
    load(potion("potion", "swiftness"), potion("potion", "night_vision"), FakeItem.empty(), 1);
    clickStand();
    platform.scheduler().advance(2);

    BrewEvent vanilla = adapt.post(new BrewEvent(standBlock, stand.ingredient(), bottles(), bottles(), 4, 0));

    assertThat(vanilla.isCancelled()).isTrue();
    assertThat(stand.bottle(0).potionContents().orElseThrow().customEffects()).hasSize(2);
    assertThat(stand.bottle(1).isEmpty()).isTrue();
    assertThat(stand.ingredient().isEmpty()).isTrue();
  }

  @Test
  void witchsShelfMixesSplashesOnlyAndNobodyMixesWithoutTheUpgrade() {
    load(potion("splash_potion", "poison"), potion("splash_potion", "weakness"), FakeItem.empty(), 2);
    clickStand();
    platform.scheduler().advance(500);
    assertThat(stand.bottle(1).isEmpty()).isFalse();

    kit.mutate("brewing", "swords");
    WitchsShelf shelf = learn(WitchsShelf.class, 2);
    load(potion("potion", "poison"), potion("potion", "weakness"), FakeItem.empty(), 2);
    clickStand();
    platform.scheduler().advance(500);
    assertThat(stand.bottle(1).isEmpty()).isFalse();

    load(potion("splash_potion", "poison"), potion("splash_potion", "weakness"), FakeItem.empty(), 2);
    clickStand();
    platform.scheduler().advance(2 + shelf.getConfig().mixBrewTicks);
    assertThat(stand.bottle(0).type()).isEqualTo(Id.minecraft("splash_potion"));
    assertThat(stand.bottle(0).potionContents().orElseThrow().customEffects()).extracting(EffectInstance::type)
        .containsExactly(POISON, WEAKNESS);
    assertThat(stand.bottle(1).isEmpty()).isTrue();
  }

  @Test
  void witchsShelfRefusesASplashWithAHelpfulEffect() {
    kit.mutate("brewing", "swords");
    WitchsShelf shelf = learn(WitchsShelf.class, 2);
    load(potion("splash_potion", "poison"), potion("splash_potion", "swiftness"), FakeItem.empty(), 1);
    clickStand();
    platform.scheduler().advance(2 + shelf.getConfig().mixBrewTicks);

    assertThat(stand.bottle(1).isEmpty()).isFalse();
    assertThat(stand.ingredient().amount()).isEqualTo(1);
  }

  @Test
  void apothecaryAlsoUnlocksTheHasteBrews() {
    Apothecary apothecary = upgrade(Apothecary.class);
    List<BrewingRecipe> haste = original(BrewingHaste.class).getBrewingRecipes();

    assertThat(apothecary.getBrewingRecipes()).containsAll(haste);
    assertThat(apothecary.isBrewingRecipe("brewing-haste-2")).isTrue();
  }

  @Test
  void theBandolierLoadsFromTheOffHandAndFeedsDosesBehindAShield() {
    kit.mutate("brewing", "swords");
    kit.learn("brewing", "swords", Recipe.ECHO, 1);
    MasterDistiller distiller = learn(MasterDistiller.class, 1);
    AlchemistsBandolier bandolier = (AlchemistsBandolier) distiller.getTools().getFirst();
    player.holding(bandolier.create(new AlchemistsBandolier.Data()));
    player.inventory().setOffHand(potion("potion", "poison"));

    use();
    player.inventory().setItem(8, player.inventory().mainHand());
    player.holding(FakeItem.tool("iron_sword", 250));
    player.inventory().setOffHand(FakeItem.of("shield"));
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);
    kit.hit(player, zombie, 4D, true);

    assertThat(zombie.effect(POISON)).isNotNull();
    assertThat(player.inventory().offHand().type()).isEqualTo(Id.minecraft("shield"));
    for (int quarter = 2; quarter <= 4; quarter++) {
      BrewingDoses.take(player, BrewingDoses.find(player));
    }
    assertThat(AlchemistsBandolier.find(player)).isNull();
    assertThat(count(GLASS_BOTTLE)).isEqualTo(1);
    assertThat(BrewingDoses.find(player)).isNull();
  }

  @Test
  void loadingAndUnloadingMovesTheSamePotionAndNeverCopiesIt() {
    kit.mutate("brewing", "swords");
    MasterDistiller distiller = learn(MasterDistiller.class, 1);
    AlchemistsBandolier bandolier = (AlchemistsBandolier) distiller.getTools().getFirst();
    player.holding(bandolier.create(new AlchemistsBandolier.Data()));
    player.inventory().setItem(5, potion("potion", "swiftness"));

    use();
    assertThat(count(Id.minecraft("potion"))).isZero();
    player.setSneaking(true);
    use();
    use();

    assertThat(count(Id.minecraft("potion"))).isEqualTo(1);
    assertThat(AlchemistsBandolier.find(player)).isNull();
  }

  @Test
  void theBandolierHoldsThreePotionsAtTheFirstLevel() {
    kit.mutate("brewing", "swords");
    MasterDistiller distiller = learn(MasterDistiller.class, 1);
    AlchemistsBandolier bandolier = (AlchemistsBandolier) distiller.getTools().getFirst();
    player.holding(bandolier.create(new AlchemistsBandolier.Data()));
    for (int slot = 1; slot <= 4; slot++) {
      player.inventory().setItem(slot, potion("potion", "swiftness"));
    }

    for (int load = 0; load < 4; load++) {
      use();
    }

    assertThat(count(Id.minecraft("potion"))).isEqualTo(1);
    assertThat(distiller.getBandolierSlots(1)).isEqualTo(3);
    assertThat(distiller.getBandolierSlots(2)).isEqualTo(4);
    assertThat(distiller.getBandolierSlots(3)).isEqualTo(6);
  }

  @Test
  void theBandolierTakesOneBottleOfAStack() {
    kit.mutate("brewing", "swords");
    MasterDistiller distiller = learn(MasterDistiller.class, 1);
    AlchemistsBandolier bandolier = (AlchemistsBandolier) distiller.getTools().getFirst();
    player.holding(bandolier.create(new AlchemistsBandolier.Data()));
    FakeItem stack = potion("potion", "swiftness");
    stack.setAmount(5);
    player.inventory().setItem(5, stack);

    for (int load = 0; load < 4; load++) {
      use();
    }

    assertThat(count(Id.minecraft("potion"))).isEqualTo(2);
  }

  @Test
  void aLoadedBandolierCannotBeCraftedIntoAnything() {
    kit.mutate("brewing", "swords");
    MasterDistiller distiller = learn(MasterDistiller.class, 1);
    AlchemistsBandolier bandolier = (AlchemistsBandolier) distiller.getTools().getFirst();
    player.holding(bandolier.create(new AlchemistsBandolier.Data()));
    player.inventory().setItem(5, potion("potion", "swiftness"));
    use();
    ItemRef loaded = player.inventory().mainHand();
    ItemRef empty = bandolier.create(new AlchemistsBandolier.Data());

    PrepareItemCraftEvent withPotions = adapt.post(new PrepareItemCraftEvent(player, List.of(loaded, FakeItem.of("string")),
        Id.adapt("tool-brewing-bandolier"), CraftItemEvent.RecipeKind.SHAPELESS, false, empty.copy()));
    PrepareItemCraftEvent withoutPotions = adapt.post(new PrepareItemCraftEvent(player, List.of(empty, FakeItem.of("string")),
        Id.adapt("tool-brewing-bandolier"), CraftItemEvent.RecipeKind.SHAPELESS, false, empty.copy()));

    assertThat(withPotions.result().isEmpty()).isTrue();
    assertThat(withoutPotions.result().isEmpty()).isFalse();
  }

  @Test
  void sneakScrollPicksTheActivePotionAndPlainScrollDoesNot() {
    kit.mutate("brewing", "swords");
    MasterDistiller distiller = learn(MasterDistiller.class, 1);
    AlchemistsBandolier bandolier = (AlchemistsBandolier) distiller.getTools().getFirst();
    player.holding(bandolier.create(new AlchemistsBandolier.Data()));
    player.inventory().setItem(1, potion("potion", "swiftness"));
    player.inventory().setItem(2, potion("potion", "night_vision"));
    use();
    use();
    int held = player.inventory().heldSlot();

    PlayerItemHeldEvent plain = adapt.post(new PlayerItemHeldEvent(player, held, held + 1));
    player.setSneaking(true);
    PlayerItemHeldEvent sneaking = adapt.post(new PlayerItemHeldEvent(player, held, held + 1));

    assertThat(plain.isCancelled()).isFalse();
    assertThat(sneaking.isCancelled()).isTrue();
    assertThat(AlchemistsBandolier.find(player).potion().potionContents().orElseThrow().potion())
        .isEqualTo(Id.minecraft("swiftness"));
    assertThat(BarterGesture.reserved("brewing")).contains(BarterGesture.SNEAK_SCROLL);
  }

  @Test
  void aBandolierIsIgnoredByDosesForAPlayerWithoutTheUpgrade() {
    kit.mutate("brewing", "swords");
    MasterDistiller distiller = learn(MasterDistiller.class, 1);
    AlchemistsBandolier bandolier = (AlchemistsBandolier) distiller.getTools().getFirst();
    player.holding(bandolier.create(new AlchemistsBandolier.Data()));
    player.inventory().setItem(3, potion("potion", "swiftness"));
    use();
    profile.getData().getSkillLine("brewing").setAdaptation(distiller, 0);
    player.inventory().setOffHand(potion("potion", "poison"));

    PlayerInteractEvent refused = use();

    assertThat(refused.useItem()).isEqualTo(UseResult.DENY);
    assertThat(AlchemistsBandolier.find(player)).isNull();
    assertThat(BrewingDoses.find(player).effects()).containsExactly(EffectInstance.of(POISON, 900, 0));
  }

  @Test
  void aPotionDrunkTwiceFromTheBandolierComesOutNamedWithItsEffectsAndTwoDosesLeft() {
    AlchemistsBandolier bandolier = bandolier();
    player.inventory().setItem(5, haste("Bottled Haste"));
    use();

    BrewingDoses.take(player, BrewingDoses.find(player));
    BrewingDoses.take(player, BrewingDoses.find(player));
    player.setSneaking(true);
    use();

    ItemRef opened = takePotion();
    assertThat(opened.type()).isEqualTo(Id.minecraft("potion"));
    assertThat(opened.potionContents().orElseThrow().name()).isEqualTo("Bottled Haste");
    assertThat(((FakeItem) opened).hiddenTooltip()).contains(TooltipPart.POTION_EFFECTS);
    assertThat(opened.lore()).anySatisfy((String line) -> assertThat(line).contains("effect.minecraft.haste"));
    assertThat(opened.lore()).anySatisfy((String line) -> assertThat(line).contains("2/4"));
    assertThat(AlchemistsBandolier.find(player)).isNull();
    player.inventory().setOffHand(opened);
    assertThat(BrewingDoses.find(player).effects()).containsExactly(HASTE_EFFECT);
    BrewingDoses.take(player, BrewingDoses.find(player));
    BrewingDoses.take(player, BrewingDoses.find(player));
    assertThat(player.inventory().offHand().type()).isEqualTo(GLASS_BOTTLE);
    assertThat(count(Id.minecraft("potion"))).isZero();
    assertThat(bandolier.read(player.inventory().mainHand()).potions).isEmpty();
  }

  @Test
  void anOpenedBottleWithoutACustomNameIsNamedAfterItsPotion() {
    assertThat(openedName(haste(null))).isEqualTo("<lang:effect.minecraft.haste>");
    assertThat(openedName(potion("potion", "long_swiftness"))).isEqualTo("<lang:item.minecraft.potion.effect.swiftness>");
    assertThat(openedName(potion("splash_potion", "poison"))).isEqualTo("<lang:item.minecraft.splash_potion.effect.poison>");
  }

  @Test
  void theFirstDoseListsTheEffectsOnTheOpenedBottle() {
    player.inventory().setOffHand(haste(null));

    BrewingDoses.take(player, BrewingDoses.find(player));

    List<String> lore = player.inventory().offHand().lore();
    assertThat(lore).anySatisfy((String line) -> assertThat(line).contains("effect.minecraft.haste"));
    assertThat(lore).anySatisfy((String line) -> assertThat(line).contains("3/4"));
  }

  @Test
  void anUntouchedPotionLeavesTheBandolierExactlyAsItWentIn() {
    bandolier();
    FakeItem named = haste("Bottled Haste").enchant("unbreaking", 1);
    named.setLoreJson(List.of("{\"text\":\"kept\"}"));
    named.data().set(DataKey.adapt("marker", DataCodecs.STRING), "kept");
    player.inventory().setItem(5, named.copy());
    use();

    player.setSneaking(true);
    use();

    ItemRef back = takePotion();
    assertThat(back.isSimilar(named)).isTrue();
    assertThat(back.data().get(DataKey.adapt("marker", DataCodecs.STRING))).isEqualTo("kept");
  }

  @Test
  void theLastQuarterHandsTheRestOfAStackBackAsItWasBrewed() {
    FakeItem stack = haste("Bottled Haste");
    stack.setLoreJson(List.of("{\"text\":\"brewed\"}"));
    FakeItem original = (FakeItem) stack.copy();
    stack.setAmount(2);
    player.inventory().setOffHand(stack);

    for (int quarter = 1; quarter <= 4; quarter++) {
      BrewingDoses.take(player, BrewingDoses.find(player));
    }

    ItemRef rest = player.inventory().offHand();
    assertThat(rest.amount()).isEqualTo(1);
    assertThat(rest.isSimilar(original)).isTrue();
    assertThat(((FakeItem) rest).hiddenTooltip()).isEmpty();
  }

  @Test
  void aBandolierWhosePotionsCannotBeReadKeepsThemAndRefusesEverything() {
    AlchemistsBandolier bandolier = bandolier();
    AlchemistsBandolier.Data unreadable = new AlchemistsBandolier.Data();
    unreadable.potions = Base64.getEncoder().encodeToString("lost".getBytes(StandardCharsets.UTF_8));
    player.holding(bandolier.create(unreadable));
    player.inventory().setItem(5, potion("potion", "swiftness"));

    use();
    player.setSneaking(true);
    use();

    assertThat(bandolier.read(player.inventory().mainHand()).potions).isEqualTo(unreadable.potions);
    assertThat(player.inventory().item(5).potionContents().orElseThrow().potion()).isEqualTo(Id.minecraft("swiftness"));
    assertThat(AlchemistsBandolier.find(player)).isNull();
    assertThat(AlchemistsBandolier.loaded(player.inventory().mainHand())).isTrue();
  }

  @Test
  void theBandolierNeverHandsOutAPotionWithNoContents() {
    AlchemistsBandolier bandolier = bandolier();
    AlchemistsBandolier.Data stored = new AlchemistsBandolier.Data();
    stored.potions = Base64.getEncoder().encodeToString(
        AdaptCore.platform().host().items().serializeItems(List.of(new FakeItem(Id.minecraft("potion"), 1))));
    player.holding(bandolier.create(stored));

    player.setSneaking(true);
    use();

    assertThat(count(Id.minecraft("potion"))).isZero();
    assertThat(bandolier.read(player.inventory().mainHand()).potions).isEqualTo(stored.potions);
  }

  private AlchemistsBandolier bandolier() {
    kit.mutate("brewing", "swords");
    MasterDistiller distiller = learn(MasterDistiller.class, 1);
    AlchemistsBandolier bandolier = (AlchemistsBandolier) distiller.getTools().getFirst();
    player.holding(bandolier.create(new AlchemistsBandolier.Data()));
    return bandolier;
  }

  private ItemRef takePotion() {
    for (int slot = 0; slot < 36; slot++) {
      ItemRef item = player.inventory().item(slot);
      if (!item.isEmpty() && item.type().equals(Id.minecraft("potion"))) {
        player.inventory().setItem(slot, FakeItem.empty());
        return item;
      }
    }
    throw new AssertionError("no potion in the inventory");
  }

  private String openedName(ItemRef bottle) {
    player.inventory().setOffHand(bottle);
    BrewingDoses.take(player, BrewingDoses.find(player));
    return player.inventory().offHand().potionContents().orElseThrow().name();
  }

  private static FakeItem haste(String name) {
    FakeItem item = new FakeItem(Id.minecraft("potion"), 1);
    item.setPotionContents(new PotionContents(null, Rgb.of(0xFFFF00), List.of(HASTE_EFFECT), name));
    return item;
  }

  private PlayerInteractEvent use() {
    return adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_AIR, Hand.MAIN_HAND,
        player.inventory().mainHand(), null, BlockFace.UP, null, UseResult.DEFAULT, UseResult.DEFAULT));
  }

  private int count(Id type) {
    int total = 0;
    for (int slot = 0; slot < 36; slot++) {
      ItemRef item = player.inventory().item(slot);
      if (!item.isEmpty() && item.type().equals(type)) {
        total += item.amount();
      }
    }
    ItemRef offHand = player.inventory().offHand();
    return total + (!offHand.isEmpty() && offHand.type().equals(type) ? offHand.amount() : 0);
  }

  private void load(ItemRef first, ItemRef second, ItemRef third, int glowstone) {
    stand.setBottle(0, first);
    stand.setBottle(1, second);
    stand.setBottle(2, third);
    stand.setIngredient(new FakeItem(BrewingMixer.GLOWSTONE, glowstone));
  }

  private List<ItemRef> bottles() {
    List<ItemRef> bottles = new ArrayList<>();
    for (int slot = 0; slot < 3; slot++) {
      bottles.add(stand.bottle(slot));
    }
    return bottles;
  }

  private void clickStand() {
    player.openContainer(standBlock);
    adapt.post(new InventoryClickEvent(player, InventoryKind.BREWING, InventoryKind.BREWING, 3, 3, SlotKind.CONTAINER,
        ClickKind.LEFT, InventoryAction.PLACE_ALL, -1, FakeItem.empty(), FakeItem.empty()));
  }

  private <A extends Adaptation<?>> A learn(Class<A> type, int level) {
    A upgrade = upgrade(type);
    profile.getData().getSkillLine("brewing").setAdaptation(upgrade, level);
    return upgrade;
  }

  private <A extends Adaptation<?>> A upgrade(Class<A> type) {
    return kit.registry().getSkill("brewing").getUpgrades().stream().filter(type::isInstance).map(type::cast)
        .findFirst().orElseThrow();
  }

  private <A extends Adaptation<?>> A original(Class<A> type) {
    return kit.registry().getSkill("brewing").getAdaptations().stream().filter(type::isInstance).map(type::cast)
        .findFirst().orElseThrow();
  }

  private static FakeItem potion(String type, String base) {
    FakeItem item = new FakeItem(Id.minecraft(type), 1);
    item.setPotionContents(new PotionContents(Id.minecraft(base), null, List.of(), null));
    return item;
  }
}
