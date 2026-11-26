package art.arcane.adapt.content.adaptation.sword.upgraded;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.ComponentPort;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.mutation.tool.MutationTool;
import art.arcane.adapt.api.mutation.tool.ToolUse;
import art.arcane.adapt.content.adaptation.blocking.BlockingPerfectGuard;
import art.arcane.adapt.content.adaptation.blocking.BlockingShields;
import art.arcane.adapt.content.adaptation.sword.SwordBladeLore;
import art.arcane.adapt.content.adaptation.sword.SwordBleed;
import art.arcane.adapt.content.adaptation.sword.SwordsWhetstoneRitual;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.SwordMessages;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.volmlib.util.localization.TextKey;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tempest Flow's whetstone. Used with a sword in the off hand it grinds that sword Serrated, Weighted or Keen for a
 * stretch of combat time; the grind works only for a holder of Tempest Flow at the tool's level and never while
 * Whetstone Ritual's sharpness runs.
 */
public final class Whetstone extends MutationTool<Whetstone.Data> {
  static final String ID = "tool-swords-whetstone";
  private static final DataKey<String> GRIND_KEY = DataKey.adapt("whetstone_grind", DataCodecs.STRING);
  private static final DataKey<Long> LEFT_KEY = DataKey.adapt("whetstone_left", DataCodecs.LONG);
  private static final DataKey<String> LORE_KEY = DataKey.adapt("whetstone_lore", DataCodecs.STRING);

  private final TempestFlow flow;
  private static final int TARGET_MEMORY = 512;
  private final Map<UUID, Long> lastHit;
  private final Map<UUID, Long> bleedingUntil = new ConcurrentHashMap<>();
  private final Map<UUID, Long> staggerReadyAt = new ConcurrentHashMap<>();

  Whetstone(TempestFlow flow, Map<UUID, Long> lastHit) {
    super(ID, flow, Id.minecraft("flint"), TempestFlow.WHETSTONE_LEVEL, Data.class, SwordMessages.WHETSTONE_NAME);
    this.flow = flow;
    this.lastHit = lastHit;
  }

  public enum Grind {
    SERRATED(SwordMessages.WHETSTONE_SERRATED),
    WEIGHTED(SwordMessages.WHETSTONE_WEIGHTED),
    KEEN(SwordMessages.WHETSTONE_KEEN);

    private final TextKey label;

    Grind(TextKey label) {
      this.label = label;
    }

    public TextKey label() {
      return label;
    }

    static @Nullable Grind of(@Nullable String name) {
      for (Grind grind : values()) {
        if (grind.name().equals(name)) {
          return grind;
        }
      }
      return null;
    }
  }

  public static final class Data {
    String grind = Grind.SERRATED.name();

    Data() {
    }

    Data(Grind grind) {
      this.grind = grind.name();
    }
  }

  @Override
  public List<RecipeSpec> recipes() {
    Map<Character, Ingredient> parts = Map.of(
        'f', Ingredient.of(Id.minecraft("flint")),
        'i', Ingredient.of(Id.minecraft("iron_ingot")),
        's', Ingredient.of(Id.minecraft("stick")));
    return List.of(
        recipe(Grind.SERRATED, List.of("f", "i", "s"), parts),
        recipe(Grind.WEIGHTED, List.of("i", "f", "s"), parts),
        recipe(Grind.KEEN, List.of("fis"), parts));
  }

  private RecipeSpec recipe(Grind grind, List<String> shape, Map<Character, Ingredient> parts) {
    return new RecipeSpec.Shaped(Id.adapt(ID + "-" + grind.name().toLowerCase(Locale.ROOT)), shape, parts,
        create(new Data(grind)), 1);
  }

  @Override
  protected void applyLore(Data data, List<String> lines) {
    Grind grind = Grind.of(data.grind);
    if (grind != null) {
      lines.add(C.GRAY + AdaptLanguage.text(grind.label()));
    }
  }

  @Override
  protected void use(ToolUse use) {
    PlayerRef p = use.player();
    Data data = read(use.item());
    Grind grind = data == null ? null : Grind.of(data.grind);
    ItemRef sword = p.inventory().offHand();
    int cost = flow.getConfig().whetstoneDurabilityCost;
    if (grind == null || !flow.isSword(sword) || sharpened(p)
        || (!sword.isUnbreakable() && sword.maxDamage() - sword.damage() <= cost)) {
      Fx.now(flow, p.location().add(0, 1, 0), FxPriority.GAMEPLAY).sound(Sounds.BLOCK_GRINDSTONE_USE, 0.4F, 0.6F);
      return;
    }

    boolean paid = flow.payDurabilityCost(p, "whetstone", cost, () -> {
      AdaptPorts.get(ComponentPort.class, ComponentPort.NONE).damageItem(p, EquipmentSlot.OFF_HAND, cost);
      return true;
    });
    if (!paid) {
      return;
    }

    ItemRef ground = p.inventory().offHand();
    ground.data().set(GRIND_KEY, grind.name());
    ground.data().set(LEFT_KEY, (long) flow.getConfig().whetstoneCombatMillis);
    SwordBladeLore.replace(ground, LORE_KEY, null, C.AQUA + AdaptLanguage.text(grind.label()));
    p.inventory().setOffHand(ground);

    ItemRef stone = use.item();
    stone.setAmount(stone.amount() - 1);
    p.inventory().setMainHand(stone.amount() > 0 ? stone : AdaptCore.platform().host().items().empty());
    Fx.now(flow, p.location().add(0, 1, 0), FxPriority.GAMEPLAY)
        .particle(Particles.CRIT, 8, 0, 0, 0, 0.2D, 0.05D)
        .chord(Sounds.BLOCK_GRINDSTONE_USE, 0.8F, 1.3F, Sounds.ITEM_AXE_STRIP, 0.5F, 1.5F);
  }

  /** Applies the grind of the attacking sword to a melee hit, spending its combat time. */
  void strike(EntityDamageByEntityEvent e, Adaptation.MeleeContext combat) {
    PlayerRef p = combat.attacker();
    ItemRef sword = combat.mainHand();
    Grind grind = Grind.of(sword.data().get(GRIND_KEY));
    if (grind == null || level(p) <= 0 || sharpened(p)) {
      return;
    }

    long now = System.currentTimeMillis();
    Long previous = lastHit.put(p.uuid(), now);
    long gap = (long) flow.getConfig().whetstoneMillisPerHit;
    long spent = previous == null ? gap : Math.min(gap, Math.max(0L, now - previous));
    long left = sword.data().getOrDefault(LEFT_KEY, 0L) - spent;
    if (left <= 0L) {
      sword.data().remove(GRIND_KEY);
      sword.data().remove(LEFT_KEY);
      SwordBladeLore.replace(sword, LORE_KEY, null, null);
    } else {
      sword.data().set(LEFT_KEY, left);
    }
    p.inventory().setMainHand(sword);

    LivingRef target = combat.target();
    switch (grind) {
      case SERRATED -> serrate(p, target, now);
      case WEIGHTED -> weigh(p, target, now);
      case KEEN -> {
        if (e.isCritical()) {
          e.setDamage(e.damage() * (1D + flow.keenBonus(scalingLevel(p, DuelistsVerdict.class))));
        }
      }
    }
  }

  private void serrate(PlayerRef p, LivingRef target, long now) {
    long duration = (long) flow.getConfig().serratedBleedMillis;
    if (!claim(bleedingUntil, target.uuid(), now, duration)) {
      return;
    }
    new SwordBleed(flow, (PlayerRef source, double dealt) -> {
    }).start(target, p, flow.serratedBleed(scalingLevel(p, Witherblade.class)), SwordBleed.procCount(duration));
  }

  private void weigh(PlayerRef p, LivingRef target, long now) {
    if (!claim(staggerReadyAt, target.uuid(), now, (long) flow.getConfig().weightedCooldownMillis)) {
      return;
    }
    int ticks = flow.weightedTicks(scalingLevel(p, TempestFlow.class));
    BlockingPerfectGuard.stagger(target, p.location(), ticks, flow.getConfig().weightedSlownessAmplifier, 0.3D);
    if (target instanceof PlayerRef blocker) {
      blocker.setCooldown(BlockingShields.SHIELD, Math.max(blocker.cooldownTicks(BlockingShields.SHIELD), ticks));
    }
  }

  /** Takes the per-target gate when it is free, keeping it for {@code millis}; forgets expired targets as it grows. */
  private static boolean claim(Map<UUID, Long> gates, UUID target, long now, long millis) {
    if (gates.getOrDefault(target, 0L) > now) {
      return false;
    }
    if (gates.size() >= TARGET_MEMORY) {
      gates.values().removeIf((Long until) -> until <= now);
    }
    gates.put(target, now + millis);
    return true;
  }

  private int scalingLevel(PlayerRef p, Class<? extends Adaptation<?>> upgrade) {
    if (flow.getSkill() == null) {
      return 1;
    }
    for (Adaptation<?> candidate : flow.getSkill().getUpgrades()) {
      if (upgrade.isInstance(candidate)) {
        return Math.max(1, candidate.getActiveLevel(p));
      }
    }
    return 1;
  }

  private boolean sharpened(PlayerRef p) {
    if (flow.getSkill() == null) {
      return false;
    }
    for (Adaptation<?> original : flow.getSkill().getAdaptations()) {
      if (original instanceof SwordsWhetstoneRitual ritual) {
        return ritual.sharpened(p);
      }
    }
    return false;
  }
}
