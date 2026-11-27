package art.arcane.adapt.content.adaptation.brewing;

import art.arcane.adapt.AdaptCore;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.engine.data.WorldDataPort;
import art.arcane.adapt.api.item.Potions;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.content.skill.SkillBrewing;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.EffectCategory;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EffectModifier;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.event.inventory.BrewEvent;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.event.inventory.InventoryOpenEvent;
import art.arcane.adapt.platform.event.player.PlayerItemConsumeEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.PotionContents;
import art.arcane.adapt.platform.item.TooltipPart;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeBlockState;
import art.arcane.adapt.testkit.FakeInventory;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeWorld;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BrewingRuntimeSkillTest {
  private static final Id SWIFTNESS = Id.minecraft("swiftness");
  private static final Id SPEED = Id.minecraft("speed");

  @TempDir
  Path data;

  private final Map<BlockRef, UUID> owners = new HashMap<>();
  private TestPlatform platform;
  private FakeWorld world;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;
  private BlockRef standBlock;

  @BeforeEach
  void start() {
    AdaptPorts.install(WorldDataPort.class, new OwnerStore());
    platform = TestPlatform.builder(data).build();
    world = platform.world();
    player = platform.player("Brewer", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillBrewing.class));
    profile = adapt.join(player);
    standBlock = world.set(0, 60, 0, "brewing_stand").blockAt(0, 60, 0);
  }

  @AfterEach
  void stop() {
    adapt.close();
    AdaptPorts.uninstall(WorldDataPort.class);
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  private <A extends Adaptation<?>> A learn(Class<A> type, int level) {
    Skill<?> skill = AdaptCore.get().getAdaptServer().getSkillRegistry().getSkill("brewing");
    for (Adaptation<?> adaptation : skill.getAdaptations()) {
      if (type.isInstance(adaptation)) {
        profile.getData().getSkillLine("brewing").setAdaptation(adaptation, level);
        return type.cast(adaptation);
      }
    }
    throw new AssertionError("No adaptation " + type.getSimpleName());
  }

  @Test
  void placingABrewingStandRecordsTheOwnerAndTheStat() {
    adapt.post(new BlockPlaceEvent(player, standBlock, FakeBlockState.of("air"), null, FakeItem.of("brewing_stand"), Hand.MAIN_HAND));

    assertThat(owners.get(standBlock)).isEqualTo(player.uuid());
    assertThat(profile.getData().getStat("brewing.stands.placed")).isEqualTo(1D);
  }

  @Test
  void openingAnUnownedStandClaimsItButNeverStealsAnOwnedOne() {
    adapt.post(new InventoryOpenEvent(player, InventoryKind.BREWING, new FakeInventory(), standBlock, null));
    assertThat(owners.get(standBlock)).isEqualTo(player.uuid());

    UUID other = UUID.randomUUID();
    owners.put(standBlock, other);
    adapt.post(new InventoryOpenEvent(player, InventoryKind.BREWING, new FakeInventory(), standBlock, null));
    assertThat(owners.get(standBlock)).isEqualTo(other);
  }

  @Test
  void breakingTheStandForgetsTheOwner() {
    owners.put(standBlock, player.uuid());

    adapt.post(new BlockBreakEvent(player, standBlock, true, 0));

    assertThat(owners).doesNotContainKey(standBlock);
  }

  @Test
  void lingeringExtendsTheOwnersBrewedPotionsAndWritesVanillaStyleLore() {
    platform.host().potion(SWIFTNESS, EffectInstance.of(SPEED, 3600, 0));
    platform.host().effect(SPEED, EffectCategory.BENEFICIAL, false);
    platform.host().effectModifiers(SPEED,
        new EffectModifier("attribute.name.generic.movement_speed", AttributeOperation.ADD_MULTIPLIED_TOTAL, 0.2D));
    BrewingLingering lingering = learn(BrewingLingering.class, 1);
    owners.put(standBlock, player.uuid());
    ItemRef brewed = Potions.vanilla(Potions.Type.REGULAR, SWIFTNESS);

    BrewEvent event = adapt.post(new BrewEvent(standBlock, FakeItem.of("redstone"), List.of(FakeItem.empty()),
        List.of(brewed, FakeItem.empty(), FakeItem.empty()), 10, 0));

    ItemRef result = event.results().get(0);
    PotionContents contents = result.potionContents().orElseThrow();
    double factor = lingering.getLevelPercent(1);
    int expected = (int) (lingering.getDurationBoost(factor) + (3600 * lingering.getPercentBoost(factor)));
    assertThat(contents.potion()).isEqualTo(SWIFTNESS);
    assertThat(contents.customEffects()).containsExactly(EffectInstance.of(SPEED, expected, 0));
    assertThat(((FakeItem) result).hiddenTooltip()).contains(TooltipPart.POTION_EFFECTS);
    assertThat(result.loreJson()).hasSize(4);
    assertThat(result.loreJson().get(0)).contains("\"translate\":\"potion.withDuration\"", "effect.minecraft.speed",
        "\"color\":\"blue\"", "\"italic\":false");
    assertThat(result.loreJson().get(2)).contains("potion.whenDrank", "dark_purple");
    assertThat(result.loreJson().get(3)).contains("attribute.modifier.plus.2", "\"text\":\"20\"",
        "\"translate\":\"attribute.name.generic.movement_speed\"");
    assertThat(profile.getData().getStat("brewing.lingering.potions-extended")).isEqualTo(1D);
  }

  @Test
  void lingeringLeavesStandsWithoutAnOwnerAlone() {
    platform.host().potion(SWIFTNESS, EffectInstance.of(SPEED, 3600, 0));
    learn(BrewingLingering.class, 1);
    ItemRef brewed = Potions.vanilla(Potions.Type.REGULAR, SWIFTNESS);

    BrewEvent event = adapt.post(new BrewEvent(standBlock, FakeItem.of("redstone"), List.of(FakeItem.empty()),
        List.of(brewed), 10, 0));

    assertThat(event.results().get(0).potionContents().orElseThrow().customEffects()).isEmpty();
  }

  @Test
  void drinkingAStrongPotionCountsBothStats() {
    ItemRef potion = Potions.vanilla(Potions.Type.REGULAR, Id.minecraft("strong_swiftness"));

    adapt.post(new PlayerItemConsumeEvent(player, potion, Hand.MAIN_HAND, null));

    assertThat(profile.getData().getStat("brewing.consumed")).isEqualTo(1D);
    assertThat(profile.getData().getStat("brewing.strong")).isEqualTo(1D);
  }

  @Test
  void drinkingWaterCountsNothing() {
    ItemRef potion = Potions.vanilla(Potions.Type.REGULAR, Id.minecraft("water"));

    adapt.post(new PlayerItemConsumeEvent(player, potion, Hand.MAIN_HAND, null));

    assertThat(profile.getData().getStat("brewing.consumed")).isZero();
  }

  private final class OwnerStore implements WorldDataPort {
    @Override
    public double reportEarnings(BlockRef block) {
      return 1D;
    }

    @Override
    public double getEarningsMultiplier(BlockRef block) {
      return 1D;
    }

    @Override
    public Placement placement(BlockRef block) {
      return null;
    }

    @Override
    public void setPlacement(BlockRef block, Placement placement) {
    }

    @Override
    public void clearPlacement(BlockRef block) {
    }

    @Override
    public UUID blockOwner(BlockRef block) {
      return owners.get(block);
    }

    @Override
    public void setBlockOwner(BlockRef block, UUID owner) {
      if (owner == null) {
        owners.remove(block);
      } else {
        owners.put(block, owner);
      }
    }
  }
}
