package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.api.world.BlockOwners;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.BrewEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.PotionContents;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Brewing leading Nether, replacing Convergence: a stand over soul fire or lava brews Strength, Fire Resistance, Speed
 * and Regeneration one amplifier higher, up to III, for two thirds of the duration.
 */
public final class SoulfireDistillery extends Signature<SoulfireDistillery.Config> {
  private static final Id BREWING_STAND = Id.minecraft("brewing_stand");
  private static final Id LAVA = Id.minecraft("lava");
  private static final Id SOUL_FIRE = Id.minecraft("soul_fire");
  private static final Id SOUL_CAMPFIRE = Id.minecraft("soul_campfire");
  private static final Set<Id> BOOSTED = Set.of(Id.minecraft("strength"), Id.minecraft("fire_resistance"),
      Id.minecraft("speed"), Id.minecraft("regeneration"));
  private static final int TOP_AMPLIFIER = 2;
  private static final DataKey<Byte> RAISED = DataKey.adapt("soulfire_raised", DataCodecs.BYTE);
  private final Map<UUID, List<ItemRef>> brews = new ConcurrentHashMap<>();

  public SoulfireDistillery(MutationSlot slot) {
    super(slot);
    setLocalizationKey(PairMessages.signatureKey(slot));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("soul_campfire"));
  }

  @Override
  public void addSignatureStats(int level, StatLines v) {
    statLore(v, potionsPerBrew(level), 1);
    statLore(v, Format.pc(getConfig().durationKept, 0), 2);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BrewEvent e) {
    BlockRef stand = e.block();
    if (e.isCancelled() || !BREWING_STAND.equals(stand.type()) || !heated(stand)) {
      return;
    }
    UUID owner = BlockOwners.owner(stand).orElse(null);
    PlayerRef brewer = owner == null ? null : AdaptApi.platform().host().player(owner);
    if (brewer == null || getActiveLevel(brewer) <= 0) {
      return;
    }
    brews.put(owner, e.results());
    try {
      fire(brewer, null, stand.center(), false);
    } finally {
      brews.remove(owner);
    }
  }

  @Override
  public Outcome run(BlockUse use) {
    List<ItemRef> results = brews.remove(use.player().uuid());
    if (results == null) {
      return Outcome.MISSED;
    }
    int left = potionsPerBrew(use.level());
    int boosted = 0;
    for (int slot = 0; slot < results.size() && boosted < left; slot++) {
      ItemRef potion = results.get(slot);
      if (potion != null && !potion.isEmpty() && boost(potion, getConfig().durationKept)) {
        results.set(slot, potion);
        boosted++;
      }
    }
    if (boosted <= 0) {
      return Outcome.MISSED;
    }
    if (use.at() != null) {
      fx(use.at().add(0D, 0.2D, 0D), FxPriority.TRANSITION)
          .ring(Particles.SOUL_FIRE_FLAME, 0.5D, 12, 0D)
          .burst(Particles.SOUL, 4, 0.2D);
    }
    return Outcome.INSTANT;
  }

  /**
   * Raises Strength, Fire Resistance, Speed and Regeneration one amplifier, never past III, once per potion; false when
   * the potion was raised before or none could rise.
   */
  static boolean boost(ItemRef potion, double durationKept) {
    Optional<PotionContents> contents = potion.potionContents();
    if (contents.isEmpty() || potion.data().has(RAISED)) {
      return false;
    }
    PotionContents current = contents.get();
    List<EffectInstance> effects = new ArrayList<>(current.customEffects());
    if (current.potion() != null) {
      effects.addAll(0, AdaptApi.platform().host().registries().potionEffects(current.potion()));
    }
    List<EffectInstance> raised = new ArrayList<>(effects.size());
    boolean any = false;
    for (EffectInstance effect : effects) {
      if (BOOSTED.contains(effect.type()) && effect.amplifier() < TOP_AMPLIFIER && !effect.isInfinite()) {
        raised.add(EffectInstance.of(effect.type(), Math.max(1, (int) Math.round(effect.durationTicks() * durationKept)), effect.amplifier() + 1).withAmbient(effect.ambient()).withParticles(effect.particles()).withIcon(effect.icon()));
        any = true;
      } else {
        raised.add(effect);
      }
    }
    if (!any) {
      return false;
    }
    potion.setPotionContents(PotionContents.EMPTY.withColor(current.color()).withCustomEffects(raised).withName(current.name()));
    potion.data().set(RAISED, (byte) 1);
    return true;
  }

  static boolean heated(BlockRef stand) {
    BlockRef below = stand.relative(0, -1, 0);
    Id type = below.type();
    if (LAVA.equals(type) || SOUL_FIRE.equals(type)) {
      return true;
    }
    if (SOUL_CAMPFIRE.equals(type)) {
      return !"false".equals(below.state().property("lit"));
    }
    return below.isAir() && LAVA.equals(stand.relative(0, -2, 0).type());
  }

  int potionsPerBrew(int level) {
    return Math.max(1, (int) Math.round(SignatureKit.scaled(level, getMaxLevel(), getConfig().potionsAtFirstLevel,
        getConfig().potionsAtTopLevel)));
  }

  @ConfigDescription("Stands over soul fire or lava brew Strength, Fire Resistance, Speed and Regeneration one level higher.")
  public static class Config extends Signature.Config {
    @ConfigDoc(value = "Potions of one brew raised at level 1.", impact = "Higher values raise more of each brew.")
    double potionsAtFirstLevel = 1;
    @ConfigDoc(value = "Potions of one brew raised at the top level.", impact = "Higher values raise more of each brew.")
    double potionsAtTopLevel = 3;
    @ConfigDoc(value = "Share of the duration a raised potion keeps.", impact = "Higher values make raised potions last longer.")
    double durationKept = 0.6667;
  }
}
