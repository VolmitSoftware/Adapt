package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismGrowables;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismHarvest;
import art.arcane.adapt.content.adaptation.herbalism.upgraded.Wildgrowth;
import art.arcane.adapt.content.adaptation.taming.TamePets;
import art.arcane.adapt.content.adaptation.taming.TameRegeneration;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.Mutations;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.AnimalOps;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.entity.EntityChangeBlockEvent;
import art.arcane.adapt.platform.event.player.PlayerHarvestBlockEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEntityEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Herbalism and Taming, either leading, replacing Convergence: harvesting a crop that grew on its own feeds your pets
 * and the animals you fed inside Wildgrowth's reach from your own produce, healing them and keeping them grazing in
 * place without trampling the field.
 */
public final class ShepherdsField extends Signature<ShepherdsField.Config> {
  private static final DataKey<String> FED_BY = DataKey.adapt("shepherd-fed-by", DataCodecs.STRING);
  private static final Id FARMLAND = Id.minecraft("farmland");
  private final Map<UUID, Graze> grazing = new ConcurrentHashMap<>();

  public ShepherdsField(MutationSlot slot) {
    super(slot);
    setLocalizationKey(PairMessages.signatureKey(slot));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("hay_block"));
    setInterval(1000);
  }

  @Override
  public void addSignatureStats(int level, StatLines v) {
    statLore(v, animals(level), 1);
    statLore(v, Format.f(healPerFeed(level), 1), 2);
    statLore(v, Format.duration(grazeMillis(level), 0), 3);
  }

  @Override
  public boolean paysOut() {
    return true;
  }

  @Override
  public boolean hasTickDemand() {
    return !grazing.isEmpty();
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    if (!e.protectionProbe()) {
      harvested(e.player(), e.block());
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerHarvestBlockEvent e) {
    harvested(e.player(), e.harvestedBlock());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerInteractEntityEvent e) {
    PlayerRef player = e.player();
    EntityRef clicked = e.clicked();
    ItemRef held = player.inventory().hand(e.hand());
    if (!clicked.has(EntityTrait.ANIMAL) || held == null || held.isEmpty() || getActiveLevel(player) <= 0
        || !animals().isBreedItem(clicked, held.type())) {
      return;
    }
    clicked.data().set(FED_BY, player.uuid().toString());
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityChangeBlockEvent e) {
    if (FARMLAND.equals(e.block().type()) && grazing.containsKey(e.entity().uuid())) {
      e.setCancelled(true);
    }
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    for (Map.Entry<UUID, Graze> entry : grazing.entrySet()) {
      Graze graze = entry.getValue();
      MobRef animal = graze.animal();
      if (now >= graze.until() || !animal.isValid() || animal.isDead()) {
        grazing.remove(entry.getKey(), graze);
        continue;
      }
      AdaptApi.scheduler().runEntity(animal, () -> {
        Loc at = animal.location();
        if (!at.sameWorld(graze.anchor()) || at.distanceSquared(graze.anchor()) > getConfig().leash * getConfig().leash) {
          animal.pathTo(graze.anchor(), 1.0D);
        }
      });
    }
  }

  private void harvested(PlayerRef player, @Nullable BlockRef crop) {
    if (crop == null || getActiveLevel(player) <= 0 || !HerbalismGrowables.isCrop(crop.type())
        || !HerbalismHarvest.isMature(crop) || !AdaptApi.settings().bonemealTracking()) {
      return;
    }
    fire(player, null, crop.center(), Mutations.farmedHarvest(crop));
  }

  @Override
  public Outcome run(BlockUse use) {
    PlayerRef player = use.player();
    int level = use.level();
    Wildgrowth wildgrowth = PairParts.owned(Id.adapt("herbalism/upgrade/wildgrowth"), Wildgrowth.class);
    int wildLevel = wildgrowth == null ? 0 : wildgrowth.getActiveLevel(player);
    if (wildLevel <= 0) {
      return Outcome.MISSED;
    }
    double radius = wildgrowth.getRadius(wildLevel);
    int left = animals(level);
    long until = System.currentTimeMillis() + grazeMillis(level);
    String owner = player.uuid().toString();
    int fed = 0;
    Map<Id, ItemRef> foods = new HashMap<>();
    for (EntityRef entity : player.nearbyEntities(radius, radius, radius)) {
      if (left <= 0) {
        break;
      }
      if (!(entity instanceof MobRef animal) || animal.isDead() || !animal.isValid()
          || !(TamePets.isOwnedPet(animal, player.uuid()) || owner.equals(animal.data().get(FED_BY)))
          || !feed(use, player, animal, foods)) {
        continue;
      }
      left--;
      fed++;
      double heal = healPerFeed(level);
      Loc anchor = animal.location();
      grazing.put(animal.uuid(), new Graze(animal, anchor, until));
      AdaptApi.scheduler().runEntity(animal, () -> {
        animal.stopPathing();
        TameRegeneration.heal(this, animal, heal);
        fx(animal.location().add(0, animal.height(), 0), FxPriority.GAMEPLAY).burst(Particles.HAPPY_VILLAGER, 4, 0.3D);
      });
    }
    return fed > 0 ? Outcome.INSTANT : Outcome.MISSED;
  }

  /**
   * Pays one item of the animal's own food from the player's inventory. The food for each kind is looked up once per
   * harvest; a kind whose food ran out is fed no more.
   */
  private boolean feed(BlockUse use, PlayerRef player, LivingRef animal, Map<Id, ItemRef> foods) {
    ItemRef unit = foods.computeIfAbsent(animal.type(), (Id kind) -> food(player, animal));
    if (unit.isEmpty()) {
      return false;
    }
    boolean paid = use.adaptation().payItemCost(player, "feed", unit, 1,
        () -> player.inventory().removeSimilar(unit, 1) >= 1);
    if (!paid) {
      foods.put(animal.type(), AdaptApi.platform().host().items().empty());
    }
    return paid;
  }

  /** One item of the first stack in the player's inventory that {@code animal} eats, or an empty item. */
  private static ItemRef food(PlayerRef player, LivingRef animal) {
    for (ItemRef stack : player.inventory().contents()) {
      if (stack != null && !stack.isEmpty() && animals().isBreedItem(animal, stack.type())) {
        return AdaptApi.platform().host().items().create(stack.type(), 1);
      }
    }
    return AdaptApi.platform().host().items().empty();
  }

  int animals(int level) {
    return (int) Math.round(SignatureKit.scaled(level, getMaxLevel(), getConfig().animalsAtFirstLevel, getConfig().animalsAtTopLevel));
  }

  double healPerFeed(int level) {
    return SignatureKit.scaled(level, getMaxLevel(), getConfig().healAtFirstLevel, getConfig().healAtTopLevel);
  }

  long grazeMillis(int level) {
    return (long) SignatureKit.scaled(level, getMaxLevel(), getConfig().grazeMillisAtFirstLevel, getConfig().grazeMillisAtTopLevel);
  }

  private static AnimalOps animals() {
    return AdaptApi.platform().host().animals();
  }

  private record Graze(MobRef animal, Loc anchor, long until) {
  }

  @ConfigDescription("Harvesting a crop that grew on its own feeds your animals inside Wildgrowth's reach from your produce; they heal and graze in place.")
  public static class Config extends Signature.Config {
    @ConfigDoc(value = "Most animals fed per harvest at level 1.", impact = "Higher values feed bigger flocks, each for one item.")
    double animalsAtFirstLevel = 3;
    @ConfigDoc(value = "Most animals fed per harvest at the top level.", impact = "Higher values feed bigger flocks, each for one item.")
    double animalsAtTopLevel = 8;
    @ConfigDoc(value = "Health each fed animal regains at level 1.", impact = "Higher values heal more per item.")
    double healAtFirstLevel = 4;
    @ConfigDoc(value = "Health each fed animal regains at the top level.", impact = "Higher values heal more per item.")
    double healAtTopLevel = 8;
    @ConfigDoc(value = "How long fed animals graze in place at level 1, in milliseconds.", impact = "Higher values keep the flock put longer.")
    double grazeMillisAtFirstLevel = 20000;
    @ConfigDoc(value = "How long fed animals graze in place at the top level, in milliseconds.", impact = "Higher values keep the flock put longer.")
    double grazeMillisAtTopLevel = 45000;
    @ConfigDoc(value = "How far a grazing animal may wander before it walks back, in blocks.", impact = "Lower values keep animals tighter to their spot.")
    double leash = 3;
  }
}
