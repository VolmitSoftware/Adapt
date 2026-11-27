package art.arcane.adapt.gameplay;

import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.engine.item.ItemDurability;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.player.PlayerItemDamageEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.WorldRef;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class MiningAuditFixtures {
  private static final String DENY_TAG = "adapt-qa-deny-bonus";
  private static final String PET_TAG = "adapt-qa-mining-pet";
  private static final Map<UUID, JsonArray> BREAKS = new HashMap<>();
  private static final Map<UUID, JsonArray> WEAR = new HashMap<>();
  private static final Map<UUID, JsonObject> HITS = new HashMap<>();

  private MiningAuditFixtures() {
  }

  public static void install() {
    Fixtures.register(new MiningAuditFixtures());
  }

  public static JsonObject execute(PlayerRef player, String[] args) {
    if (!Fixtures.isFixtureWorld(player.world())) {
      throw new IllegalStateException("Mining fixture requires its isolated arena");
    }
    if (args[2].equals("prepare")) {
      prepare(player, args[3]);
    } else if (args[2].equals("deny")) {
      Fixtures.tag(player, DENY_TAG);
    }
    return snapshot(player);
  }

  private static void prepare(PlayerRef player, String mode) {
    WorldRef world = player.world();
    BREAKS.put(player.uuid(), new JsonArray());
    WEAR.put(player.uuid(), new JsonArray());
    HITS.remove(player.uuid());
    if (Fixtures.tags(player).contains(DENY_TAG)) {
      Fixtures.command("tag " + player.name() + " remove " + DENY_TAG);
    }
    Fixtures.clearInventory(player);
    player.inventory().setHeldSlot(0);
    player.setHealth(20);
    player.setFoodLevel(10);
    player.setSaturation(0);
    player.setAbsorption(0);
    player.setNoDamageTicks(0);
    Fixtures.teleport(player, Fixtures.loc(world, 0.5, 100, 0.5, -90, 0));
    for (EntityRef entity : world.entities()) {
      if (!(entity instanceof PlayerRef)) {
        entity.remove();
      }
    }
    AdaptPlayer runtime = Objects.requireNonNull(Fixtures.adaptPlayer(player));
    for (Skill<?> skill : Fixtures.core().getAdaptServer().getSkillRegistry().getAllSkills()) {
      for (Adaptation<?> adaptation : skill.getAdaptations()) {
        runtime.getData().getSkillLine(SkillHost.lineKey(skill)).setAdaptation(adaptation, 0);
      }
    }
    if (mode.equals("shared") || mode.equals("guardian")) {
      learn(player, "adapt:tame/shared_pain");
      if (mode.equals("guardian")) {
        Adaptation<?> guardian = learn(player, "adapt:tame/guardian_instinct");
        configure(guardian, "interceptChanceBase", 1D);
        configure(guardian, "interceptChanceFactor", 0D);
        configure(guardian, "maxInterceptChance", 1D);
      }
      player.inventory().setOffHand(Fixtures.item("shield"));
      LivingRef wolf = Fixtures.spawnLiving(world, "wolf", 0.5, 100, 2.5);
      Fixtures.platform().setOwner(wolf, player);
      wolf.setAi(false);
      Fixtures.tag(wolf, PET_TAG);
      return;
    }
    boolean shovel = mode.equals("tunneler") || mode.equals("grave");
    Adaptation<?> adaptation = learn(player, shovel ? "adapt:excavation/tunneler" : "adapt:pickaxe/tunnel_bore");
    int cost = mode.equals("free") ? 0 : mode.equals("cost") ? 3 : 1;
    configure(adaptation, shovel ? "durabilityCostPerBonusBlock" : "durabilityPerBonusBlock", cost);
    if (mode.equals("pact")) {
      learn(player, "adapt:pickaxe/unbreakable_pact");
    }
    if (mode.equals("inventory") || mode.equals("tunneler")) {
      learn(player, shovel ? "adapt:excavation/drop_to_inventory" : "adapt:pickaxe/drop_to_inventory");
    }
    if (mode.equals("grave")) {
      Adaptation<?> grave = learn(player, "adapt:excavation/grave_digger");
      configure(grave, "lootChanceBase", 1D);
      configure(grave, "lootChanceFactor", 0D);
      configure(grave, "maxLootChance", 1D);
      configure(grave, "graveChanceBase", 0D);
      configure(grave, "graveChanceFactor", 0D);
    }
    ItemRef tool = Fixtures.item(shovel ? "diamond_shovel" : "diamond_pickaxe");
    tool.setDisplayName("Audit Tool");
    int damage = mode.equals("pact") || mode.equals("masterwork") ? 1560 : 0;
    if (mode.equals("masterwork")) {
      ItemDurability.setMaxDamage(tool, 2000);
      if (!ItemDurability.perStack()) {
        int typeMax = tool.maxDamage();
        damage = typeMax - (int) Math.ceil((2000D - damage) * typeMax / 2000D);
      }
    }
    tool.setDamage(damage);
    player.inventory().setMainHand(tool);
    for (int y = 99; y <= 101; y++) {
      for (int z = -1; z <= 1; z++) {
        Fixtures.set(world, 3, y, z, shovel ? "dirt" : "stone");
      }
    }
  }

  private static Adaptation<?> learn(PlayerRef player, String id) {
    AdaptPlayer runtime = Objects.requireNonNull(Fixtures.adaptPlayer(player));
    Adaptation<?> adaptation = Fixtures.adaptation(id);
    runtime.getData().getSkillLine(SkillHost.lineKey(adaptation.getSkill())).setAdaptation(adaptation, adaptation.getMaxLevel());
    return adaptation;
  }

  private static void configure(Adaptation<?> adaptation, String name, Object value) {
    try {
      Object config = adaptation.getConfig();
      Field field = config.getClass().getDeclaredField(name);
      field.setAccessible(true);
      field.set(config, value);
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException("Could not configure " + adaptation.getId().toString() + ": " + name, failure);
    }
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void denyBonus(BlockBreakEvent event) {
    if ((event.block().y() != 100 || event.block().z() != 0) && Fixtures.tags(event.player()).contains(DENY_TAG)) {
      event.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBreak(BlockBreakEvent event) {
    JsonArray breaks = BREAKS.get(event.player().uuid());
    if (breaks != null) {
      JsonObject broken = new JsonObject();
      broken.addProperty("x", event.block().x());
      broken.addProperty("y", event.block().y());
      broken.addProperty("z", event.block().z());
      breaks.add(broken);
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void onWear(PlayerItemDamageEvent event) {
    JsonArray wear = WEAR.get(event.player().uuid());
    if (wear != null) {
      JsonObject entry = new JsonObject();
      entry.addProperty("damage", event.damage());
      entry.addProperty("cancelled", event.isCancelled());
      wear.add(entry);
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void onHit(EntityDamageEvent event) {
    if (event.entity() instanceof PlayerRef player && BREAKS.containsKey(player.uuid())) {
      JsonObject hit = new JsonObject();
      hit.addProperty("base", event.damage());
      hit.addProperty("final", event.finalDamage());
      hit.addProperty("cancelled", event.isCancelled());
      HITS.put(player.uuid(), hit);
    }
  }

  private static JsonObject snapshot(PlayerRef player) {
    JsonObject state = new JsonObject();
    state.add("breaks", BREAKS.getOrDefault(player.uuid(), new JsonArray()));
    state.add("wear", WEAR.getOrDefault(player.uuid(), new JsonArray()));
    state.addProperty("health", player.health());
    state.addProperty("blocking", player.isBlocking());
    state.addProperty("sneaking", player.isSneaking());
    AdaptPlayer runtime = Objects.requireNonNull(Fixtures.adaptPlayer(player));
    state.addProperty("graveLoot", runtime.getData().getStat("excavation.grave-digger.bones-unearthed"));
    state.addProperty("sharedDamage", runtime.getData().getStat("taming.shared-pain.damage-taken"));
    state.addProperty("intercepts", runtime.getData().getStat("taming.guardian-instinct.intercepts"));
    if (HITS.containsKey(player.uuid())) {
      state.add("hit", HITS.get(player.uuid()));
    }
    JsonArray items = new JsonArray();
    for (ItemRef item : player.inventory().contents()) {
      add(items, item, "inventory");
    }
    Id wolf = Id.minecraft("wolf");
    for (EntityRef entity : player.world().entities()) {
      if (entity instanceof ItemEntityRef dropped) {
        add(items, dropped.item(), "drop");
      }
    }
    state.add("items", items);
    for (EntityRef entity : player.world().entities()) {
      if (entity.type().equals(wolf) && entity instanceof LivingRef pet && Fixtures.tags(entity).contains(PET_TAG)) {
        state.addProperty("petHealth", pet.health());
      }
    }
    JsonArray blocks = new JsonArray();
    for (int y = 99; y <= 101; y++) {
      for (int z = -1; z <= 1; z++) {
        blocks.add(FixtureItems.material(player.world().blockAt(3, y, z).type()));
      }
    }
    state.add("blocks", blocks);
    return state;
  }

  private static void add(JsonArray items, ItemRef item, String source) {
    if (item == null || item.isEmpty() || item.amount() <= 0) {
      return;
    }
    JsonObject entry = new JsonObject();
    entry.addProperty("type", FixtureItems.material(item.type()));
    entry.addProperty("amount", item.amount());
    entry.addProperty("source", source);
    entry.addProperty("damage", item.damage());
    entry.addProperty("maximum", ItemDurability.maxDamage(item));
    items.add(entry);
  }
}
