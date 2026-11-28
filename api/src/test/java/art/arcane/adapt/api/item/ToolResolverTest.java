package art.arcane.adapt.api.item;

import art.arcane.adapt.api.TestStubs;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.RegistryLookup;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.ToolKind;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockStateRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class ToolResolverTest {
  private static final Id STONE = Id.minecraft("stone");
  private static final Id OAK_LOG = Id.minecraft("oak_log");
  private static final Id DIRT = Id.minecraft("dirt");
  private static final Id GLASS = Id.minecraft("glass");
  private static final Id MARBLE = Id.parse("quarry:marble");
  private static final Id MINEABLE_PICKAXE = Id.minecraft("mineable/pickaxe");
  private static final Id MINEABLE_AXE = Id.minecraft("mineable/axe");
  private static final Id MINEABLE_SHOVEL = Id.minecraft("mineable/shovel");

  private final Map<Id, Set<Id>> itemTags = new HashMap<>();
  private final Map<Id, Set<Id>> blockTags = new HashMap<>();
  private final Set<Id> registered = new HashSet<>();
  private final Map<Id, Set<ToolKind>> actions = new HashMap<>();
  private final Map<Id, Set<Id>> correct = new HashMap<>();
  private final List<String> warnings = new ArrayList<>();
  private RegistryLookup registries;
  private ToolResolver resolver;

  @BeforeEach
  void setUp() {
    registries = TestStubs.stub(RegistryLookup.class, Map.of(
        "isTagged", (Object[] args) -> tags((RegistryKind) args[0]).getOrDefault((Id) args[1], Set.of()).contains((Id) args[2]),
        "tagValues", (Object[] args) -> tags((RegistryKind) args[0]).getOrDefault((Id) args[1], Set.of()),
        "exists", (Object[] args) -> registered.contains((Id) args[1])));
    resolver = new ToolResolver(new ToolResolver.Port() {
      @Override
      public RegistryLookup registries() {
        return registries;
      }

      @Override
      public Set<ToolKind> toolActions(ItemRef item) {
        return actions.getOrDefault(item.type(), Set.of());
      }

      @Override
      public BlockStateRef state(Id block) {
        return ToolResolverTest.this.state(block);
      }
    });
    blockTags.put(MINEABLE_PICKAXE, Set.of(STONE, MARBLE));
    blockTags.put(MINEABLE_AXE, Set.of(OAK_LOG));
    blockTags.put(MINEABLE_SHOVEL, Set.of(DIRT));
  }

  @Test
  void aVanillaPickaxeIsAPickaxeByItsClassAndNothingElse() {
    ItemRef pickaxe = item("minecraft:iron_pickaxe");

    assertThat(resolver.match(pickaxe, ToolKind.PICKAXE)).isEqualTo(ToolMatch.VANILLA);
    assertThat(resolver.kinds(pickaxe)).containsExactly(ToolKind.PICKAXE);
  }

  @Test
  void everyAutomaticTagSpellingCounts() {
    for (Id tag : Tools.automaticTags(ToolKind.PICKAXE)) {
      itemTags.clear();
      itemTags.put(tag, Set.of(Id.parse("mekanismtools:osmium_pickaxe")));
      resolver.invalidate();
      assertThat(resolver.match(item("mekanismtools:osmium_pickaxe"), ToolKind.PICKAXE)).as(tag.toString())
          .isEqualTo(ToolMatch.TAG);
    }
    assertThat(Tools.automaticTags(ToolKind.PICKAXE)).extracting(Id::toString)
        .containsExactly("minecraft:pickaxes", "forge:tools/pickaxes", "c:pickaxes", "fabric:pickaxes");
  }

  @Test
  void aLoaderDiggingActionCountsWhenNoTagSaysSo() {
    actions.put(Id.parse("mekanismtools:osmium_paxel"), EnumSet.of(ToolKind.PICKAXE, ToolKind.AXE, ToolKind.SHOVEL));
    ItemRef paxel = item("mekanismtools:osmium_paxel");

    assertThat(resolver.match(paxel, ToolKind.PICKAXE)).isEqualTo(ToolMatch.CAPABILITY);
    assertThat(resolver.kinds(paxel)).containsExactlyInAnyOrder(ToolKind.PICKAXE, ToolKind.AXE, ToolKind.SHOVEL);
  }

  @Test
  void beingTheCorrectToolForTheReferenceBlockCounts() {
    correct.put(Id.parse("drills:drill"), Set.of(STONE));
    correct.put(Id.parse("drills:chainsaw"), Set.of(OAK_LOG));
    correct.put(Id.parse("drills:spade"), Set.of(DIRT));

    assertThat(resolver.match(item("drills:drill"), ToolKind.PICKAXE)).isEqualTo(ToolMatch.BEHAVIOUR);
    assertThat(resolver.match(item("drills:chainsaw"), ToolKind.AXE)).isEqualTo(ToolMatch.BEHAVIOUR);
    assertThat(resolver.match(item("drills:spade"), ToolKind.SHOVEL)).isEqualTo(ToolMatch.BEHAVIOUR);
    assertThat(resolver.match(item("drills:drill"), ToolKind.AXE)).isEqualTo(ToolMatch.NONE);
  }

  @Test
  void rulesAnswerInOrderOperatorTagCapabilityBehaviourVanilla() {
    Id odd = Id.minecraft("iron_pickaxe");
    itemTags.put(Id.minecraft("pickaxes"), Set.of(odd));
    actions.put(odd, EnumSet.of(ToolKind.PICKAXE));
    correct.put(odd, Set.of(STONE));
    ItemRef pickaxe = item("minecraft:iron_pickaxe");

    assertThat(resolver.match(pickaxe, ToolKind.PICKAXE)).isEqualTo(ToolMatch.TAG);
    resolver.rules(ToolKind.PICKAXE, rules(List.of("minecraft:iron_pickaxe"), List.of(), Map.of(), List.of(), true, true, true));
    assertThat(resolver.match(pickaxe, ToolKind.PICKAXE)).isEqualTo(ToolMatch.OPERATOR);
    resolver.rules(ToolKind.PICKAXE, rules(List.of(), List.of(), Map.of(), List.of(), false, true, true));
    assertThat(resolver.match(pickaxe, ToolKind.PICKAXE)).isEqualTo(ToolMatch.CAPABILITY);
    resolver.rules(ToolKind.PICKAXE, rules(List.of(), List.of(), Map.of(), List.of(), false, false, true));
    assertThat(resolver.match(pickaxe, ToolKind.PICKAXE)).isEqualTo(ToolMatch.BEHAVIOUR);
    resolver.rules(ToolKind.PICKAXE, rules(List.of(), List.of(), Map.of(), List.of(), false, false, false));
    assertThat(resolver.match(pickaxe, ToolKind.PICKAXE)).isEqualTo(ToolMatch.VANILLA);
  }

  @Test
  void operatorListsTakeIdsAndTagsAndExclusionWinsOverEverything() {
    registered.add(Id.minecraft("bone"));
    itemTags.put(Id.parse("forge:tools/hammers"), Set.of(Id.parse("ie:hammer")));
    itemTags.put(Id.minecraft("pickaxes"), Set.of(Id.minecraft("golden_pickaxe")));
    resolver.rules(ToolKind.PICKAXE, rules(List.of("bone", "#forge:tools/hammers"),
        List.of("minecraft:golden_pickaxe", "minecraft:diamond_pickaxe"), Map.of(), List.of(), true, true, true));

    assertThat(resolver.match(item("minecraft:bone"), ToolKind.PICKAXE)).isEqualTo(ToolMatch.OPERATOR);
    assertThat(resolver.match(item("ie:hammer"), ToolKind.PICKAXE)).isEqualTo(ToolMatch.OPERATOR);
    assertThat(resolver.match(item("minecraft:golden_pickaxe"), ToolKind.PICKAXE)).isEqualTo(ToolMatch.EXCLUDED);
    assertThat(resolver.match(item("minecraft:diamond_pickaxe"), ToolKind.PICKAXE)).isEqualTo(ToolMatch.EXCLUDED);
    assertThat(resolver.serves(item("minecraft:diamond_pickaxe"), state(STONE), ToolKind.PICKAXE)).isFalse();
  }

  @Test
  void aMultiToolServesTheKindWhoseWorkTheBlockIs() {
    actions.put(Id.parse("mekanismtools:osmium_paxel"), EnumSet.of(ToolKind.PICKAXE, ToolKind.AXE, ToolKind.SHOVEL));
    ItemRef paxel = item("mekanismtools:osmium_paxel");

    assertThat(serving(paxel, STONE)).containsExactly(ToolKind.PICKAXE);
    assertThat(serving(paxel, OAK_LOG)).containsExactly(ToolKind.AXE);
    assertThat(serving(paxel, DIRT)).containsExactly(ToolKind.SHOVEL);
    assertThat(serving(paxel, MARBLE)).containsExactly(ToolKind.PICKAXE);
    assertThat(serving(paxel, GLASS)).isEmpty();
  }

  @Test
  void aSingleKindToolKeepsServingEveryBlockAsVanillaToolsAlwaysHave() {
    assertThat(serving(item("minecraft:iron_pickaxe"), DIRT)).containsExactly(ToolKind.PICKAXE);
    assertThat(serving(item("minecraft:iron_pickaxe"), GLASS)).containsExactly(ToolKind.PICKAXE);
    assertThat(serving(item("minecraft:iron_shovel"), STONE)).containsExactly(ToolKind.SHOVEL);
  }

  @Test
  void aToolOfNoKindServesOnlyClaimedBlocksItIsCorrectFor() {
    correct.put(Id.parse("ie:hammer"), Set.of(MARBLE));
    ItemRef hammer = item("ie:hammer");

    assertThat(resolver.match(hammer, ToolKind.PICKAXE)).isEqualTo(ToolMatch.NONE);
    assertThat(serving(hammer, MARBLE)).containsExactly(ToolKind.PICKAXE);
    assertThat(serving(hammer, STONE)).isEmpty();
    resolver.rules(ToolKind.PICKAXE, rules(List.of(), List.of(), Map.of(), List.of(), true, true, false));
    assertThat(serving(hammer, MARBLE)).isEmpty();
  }

  @Test
  void aToolUsedUpToItsMaximumDamageCountsAsNothingEvenWhenTaggedOrListed() {
    itemTags.put(Id.minecraft("pickaxes"), Set.of(Id.parse("tconstruct:pickaxe")));
    correct.put(Id.parse("tconstruct:pickaxe"), Set.of(STONE));
    ItemRef broken = item("tconstruct:pickaxe", 250, 250);
    ItemRef worn = item("tconstruct:pickaxe", 249, 250);

    assertThat(resolver.match(broken, ToolKind.PICKAXE)).isEqualTo(ToolMatch.BROKEN);
    assertThat(resolver.match(worn, ToolKind.PICKAXE)).isEqualTo(ToolMatch.TAG);
    assertThat(serving(broken, STONE)).isEmpty();
    assertThat(resolver.isEffective(broken, state(STONE))).isFalse();
    resolver.rules(ToolKind.PICKAXE, rules(List.of("tconstruct:pickaxe"), List.of(), Map.of(), List.of(), true, true, true));
    assertThat(resolver.match(broken, ToolKind.PICKAXE)).isEqualTo(ToolMatch.BROKEN);
    assertThat(resolver.match(item("minecraft:iron_pickaxe", 250, 250), ToolKind.PICKAXE)).isEqualTo(ToolMatch.BROKEN);
  }

  @Test
  void aTaggedModdedToolThatStopsMiningItsReferenceBlockIsBroken() {
    itemTags.put(Id.minecraft("pickaxes"), Set.of(Id.parse("tconstruct:pickaxe")));
    itemTags.put(Id.minecraft("axes"), Set.of(Id.parse("tconstruct:hand_axe")));
    correct.put(Id.parse("tconstruct:hand_axe"), Set.of(OAK_LOG));
    ItemRef brokenPickaxe = item("tconstruct:pickaxe", 1, 2);

    assertThat(resolver.match(brokenPickaxe, ToolKind.PICKAXE)).isEqualTo(ToolMatch.BROKEN);
    assertThat(serving(brokenPickaxe, STONE)).isEmpty();
    assertThat(resolver.match(item("tconstruct:hand_axe", 1, 2), ToolKind.AXE)).isEqualTo(ToolMatch.TAG);
    assertThat(resolver.match(item("tconstruct:pickaxe"), ToolKind.PICKAXE)).isEqualTo(ToolMatch.TAG);
    assertThat(resolver.match(item("minecraft:golden_pickaxe", 1, 32), ToolKind.PICKAXE)).isEqualTo(ToolMatch.VANILLA);
  }

  @Test
  void aTinkersStyleMultiToolFeedsTheSkillOfTheBlockByItsTags() {
    itemTags.put(Id.minecraft("pickaxes"), Set.of(Id.parse("tconstruct:pickadze")));
    itemTags.put(Id.minecraft("shovels"), Set.of(Id.parse("tconstruct:pickadze"), Id.parse("tconstruct:mattock")));
    itemTags.put(Id.minecraft("axes"), Set.of(Id.parse("tconstruct:mattock")));
    correct.put(Id.parse("tconstruct:pickadze"), Set.of(STONE, DIRT));
    correct.put(Id.parse("tconstruct:mattock"), Set.of(OAK_LOG, DIRT));

    assertThat(serving(item("tconstruct:pickadze", 0, 500), STONE)).containsExactly(ToolKind.PICKAXE);
    assertThat(serving(item("tconstruct:pickadze", 0, 500), DIRT)).containsExactly(ToolKind.SHOVEL);
    assertThat(serving(item("tconstruct:mattock", 0, 500), OAK_LOG)).containsExactly(ToolKind.AXE);
    assertThat(serving(item("tconstruct:mattock", 0, 500), DIRT)).containsExactly(ToolKind.SHOVEL);
    assertThat(serving(item("tconstruct:mattock", 0, 500), STONE)).isEmpty();
  }

  @Test
  void bareHandsServeNothing() {
    ItemRef empty = TestStubs.stub(ItemRef.class, Map.of("isEmpty", (Object[] args) -> true,
        "type", (Object[] args) -> Id.minecraft("air")));

    assertThat(serving(empty, STONE)).isEmpty();
    assertThat(resolver.kinds(empty)).isEmpty();
  }

  @Test
  void operatorBlocksAreClaimedWithTheirXpAndExcludedBlocksAreNot() {
    blockTags.put(Id.parse("forge:ores/tin"), Set.of(Id.parse("thermal:tin_ore")));
    registered.add(Id.parse("quarry:basalt_bricks"));
    resolver.rules(ToolKind.PICKAXE, rules(List.of(), List.of(),
        Map.of("quarry:basalt_bricks", 7D, "#forge:ores/tin", 12D), List.of("quarry:marble"), true, true, true));
    ToolRules active = resolver.rules(ToolKind.PICKAXE);

    assertThat(resolver.claims(ToolKind.PICKAXE, Id.parse("quarry:basalt_bricks"))).isTrue();
    assertThat(active.blockXp(Id.parse("quarry:basalt_bricks"))).hasValue(7D);
    assertThat(active.blockXp(Id.parse("thermal:tin_ore"))).hasValue(12D);
    assertThat(resolver.claims(ToolKind.PICKAXE, MARBLE)).isFalse();
    assertThat(resolver.serves(item("minecraft:iron_pickaxe"), state(MARBLE), ToolKind.PICKAXE)).isFalse();
  }

  @Test
  void loaderOreAndStoneTagsMakeABlockPickaxeWork() {
    blockTags.put(Id.parse("forge:ores"), Set.of(Id.parse("thermal:tin_ore")));
    blockTags.put(Id.parse("c:stones"), Set.of(Id.parse("quarry:slate")));

    assertThat(resolver.claims(ToolKind.PICKAXE, Id.parse("thermal:tin_ore"))).isTrue();
    assertThat(resolver.claims(ToolKind.PICKAXE, Id.parse("quarry:slate"))).isTrue();
    assertThat(resolver.claims(ToolKind.SHOVEL, Id.parse("quarry:slate"))).isFalse();
  }

  @Test
  void invalidateReadsTagsAgain() {
    ItemRef pickaxe = item("quarry:flint_pickaxe");
    assertThat(resolver.match(pickaxe, ToolKind.PICKAXE)).isEqualTo(ToolMatch.NONE);

    itemTags.put(Id.parse("c:pickaxes"), Set.of(Id.parse("quarry:flint_pickaxe")));
    assertThat(resolver.match(pickaxe, ToolKind.PICKAXE)).isEqualTo(ToolMatch.NONE);
    resolver.invalidate();
    assertThat(resolver.match(pickaxe, ToolKind.PICKAXE)).isEqualTo(ToolMatch.TAG);
  }

  @Test
  void isEffectiveAcceptsTheCorrectToolOrAKindWhoseWorkTheBlockIs() {
    correct.put(Id.parse("ie:hammer"), Set.of(GLASS));

    assertThat(resolver.isEffective(item("ie:hammer"), state(GLASS))).isTrue();
    assertThat(resolver.isEffective(item("minecraft:iron_pickaxe"), state(STONE))).isTrue();
    assertThat(resolver.isEffective(item("minecraft:iron_pickaxe"), state(OAK_LOG))).isFalse();
  }

  @Test
  void badEntriesAreReportedWithFileAndKeyAndNeverThrown() {
    registered.add(Id.minecraft("bone"));
    ToolRules parsed = ToolRules.parse("skills/adapt/pickaxe.toml", List.of("bone", "not an id", "ghost:tool", "#empty:tag"),
        List.of(), Map.of("minecraft:stone", -1D, "Bad Key", 3D), List.of(), true, true, true, registries,
        warnings::add);

    assertThat(parsed.operatorMatch(Id.minecraft("bone"))).isEqualTo(ToolMatch.OPERATOR);
    assertThat(parsed.operatorMatch(Id.parse("ghost:tool"))).isEqualTo(ToolMatch.OPERATOR);
    assertThat(warnings).containsExactlyInAnyOrder(
        "skills/adapt/pickaxe.toml extraTools: 'not an id' is not an item id or #tag; skipped",
        "skills/adapt/pickaxe.toml extraTools: ghost:tool is not a registered item on this server",
        "skills/adapt/pickaxe.toml extraTools: tag #empty:tag has no items on this server",
        "skills/adapt/pickaxe.toml extraBlocks: 'minecraft:stone' needs an XP value of 0 or more; skipped",
        "skills/adapt/pickaxe.toml extraBlocks: 'Bad Key' is not a block id or #tag; skipped");
  }

  private Set<ToolKind> serving(ItemRef item, Id block) {
    Set<ToolKind> out = EnumSet.noneOf(ToolKind.class);
    for (ToolKind kind : ToolResolver.MINING) {
      if (resolver.serves(item, state(block), kind)) {
        out.add(kind);
      }
    }
    return out;
  }

  private ToolRules rules(List<String> extraTools, List<String> excludedTools, Map<String, Double> extraBlocks,
      List<String> excludedBlocks, boolean tags, boolean capability, boolean behaviour) {
    return ToolRules.parse("skills/adapt/test.toml", extraTools, excludedTools, extraBlocks, excludedBlocks, tags,
        capability, behaviour, registries, warnings::add);
  }

  private Map<Id, Set<Id>> tags(RegistryKind kind) {
    return kind == RegistryKind.ITEM ? itemTags : kind == RegistryKind.BLOCK ? blockTags : Map.of();
  }

  private static ItemRef item(String id) {
    return item(id, 0, 0);
  }

  private static ItemRef item(String id, int damage, int maxDamage) {
    Id type = Id.parse(id);
    return TestStubs.stub(ItemRef.class, Map.of("type", (Object[] args) -> type, "isEmpty", (Object[] args) -> false,
        "damage", (Object[] args) -> damage, "maxDamage", (Object[] args) -> maxDamage,
        "isUnbreakable", (Object[] args) -> false));
  }

  private BlockStateRef state(Id block) {
    Map<String, Function<Object[], Object>> answers = Map.of("type", (Object[] args) -> block,
        "isCorrectTool", (Object[] args) -> correct.getOrDefault(((ItemRef) args[0]).type(), Set.of()).contains(block));
    return TestStubs.stub(BlockStateRef.class, answers);
  }
}
