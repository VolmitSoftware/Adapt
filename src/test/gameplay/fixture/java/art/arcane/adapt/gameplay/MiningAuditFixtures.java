package art.arcane.adapt.gameplay;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class MiningAuditFixtures implements Listener {
  private static final Map<UUID, JsonArray> BREAKS = new HashMap<>();
  private static final Map<UUID, JsonArray> WEAR = new HashMap<>();
  private static final Map<UUID, JsonObject> HITS = new HashMap<>();

  public static JsonObject execute(Player player, String[] args) {
    if (!player.getWorld().getName().startsWith("adapt_gameplay_")) {
      throw new IllegalStateException("Mining fixture requires its isolated arena");
    }
    if (args[2].equals("prepare")) {
      prepare(player, args[3]);
    } else if (args[2].equals("deny")) {
      player.addScoreboardTag("adapt-qa-deny-bonus");
    }
    return snapshot(player);
  }

  private static void prepare(Player player, String mode) {
    World world = player.getWorld();
    BREAKS.put(player.getUniqueId(), new JsonArray());
    WEAR.put(player.getUniqueId(), new JsonArray());
    HITS.remove(player.getUniqueId());
    player.removeScoreboardTag("adapt-qa-deny-bonus");
    player.getInventory().clear();
    player.getInventory().setHeldItemSlot(0);
    player.setHealth(20);
    player.setFoodLevel(10);
    player.setSaturation(0);
    player.setAbsorptionAmount(0);
    player.setNoDamageTicks(0);
    player.teleport(new Location(world, 0.5, 100, 0.5, -90, 0));
    for (Entity entity : world.getEntities()) {
      if (!(entity instanceof Player)) {
        entity.remove();
      }
    }
    AdaptPlayer runtime = Objects.requireNonNull(Adapt.instance.getAdaptServer().getPlayer(player));
    for (Skill<?> skill : Adapt.instance.getAdaptServer().getSkillRegistry().getAllSkills()) {
      for (Adaptation<?> adaptation : skill.getAdaptations()) {
        runtime.getData().getSkillLine(skill.getName()).setAdaptation(adaptation, 0);
      }
    }
    if (mode.equals("shared") || mode.equals("guardian")) {
      learn(player, "tame-shared-pain");
      if (mode.equals("guardian")) {
        Adaptation<?> guardian = learn(player, "tame-guardian-instinct");
        configure(guardian, "interceptChanceBase", 1D);
        configure(guardian, "interceptChanceFactor", 0D);
        configure(guardian, "maxInterceptChance", 1D);
      }
      player.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
      Wolf wolf = world.spawn(new Location(world, 0.5, 100, 2.5), Wolf.class);
      wolf.setOwner(player);
      wolf.setAI(false);
      wolf.addScoreboardTag("adapt-qa-mining-pet");
      return;
    }
    boolean shovel = mode.equals("tunneler") || mode.equals("grave");
    Adaptation<?> adaptation = learn(player, shovel ? "excavation-tunneler" : "pickaxe-tunnel-bore");
    int cost = mode.equals("free") ? 0 : mode.equals("cost") ? 3 : 1;
    configure(adaptation, shovel ? "durabilityCostPerBonusBlock" : "durabilityPerBonusBlock", cost);
    if (mode.equals("pact")) {
      learn(player, "pickaxe-unbreakable-pact");
    }
    if (mode.equals("inventory") || mode.equals("tunneler")) {
      learn(player, shovel ? "excavation-drop-to-inventory" : "pickaxe-drop-to-inventory");
    }
    if (mode.equals("grave")) {
      Adaptation<?> grave = learn(player, "excavation-grave-digger");
      configure(grave, "lootChanceBase", 1D);
      configure(grave, "lootChanceFactor", 0D);
      configure(grave, "maxLootChance", 1D);
      configure(grave, "graveChanceBase", 0D);
      configure(grave, "graveChanceFactor", 0D);
    }
    ItemStack tool = new ItemStack(shovel ? Material.DIAMOND_SHOVEL : Material.DIAMOND_PICKAXE);
    Damageable metadata = (Damageable) tool.getItemMeta();
    metadata.setDisplayName("Audit Tool");
    metadata.setDamage(mode.equals("pact") || mode.equals("masterwork") ? 1560 : 0);
    if (mode.equals("masterwork")) {
      metadata.setMaxDamage(2000);
    }
    tool.setItemMeta(metadata);
    player.getInventory().setItemInMainHand(tool);
    for (int y = 99; y <= 101; y++) {
      for (int z = -1; z <= 1; z++) {
        world.getBlockAt(3, y, z).setType(shovel ? Material.DIRT : Material.STONE, false);
      }
    }
  }

  private static Adaptation<?> learn(Player player, String id) {
    AdaptPlayer runtime = Objects.requireNonNull(Adapt.instance.getAdaptServer().getPlayer(player));
    for (Skill<?> skill : Adapt.instance.getAdaptServer().getSkillRegistry().getAllSkills()) {
      for (Adaptation<?> adaptation : skill.getAdaptations()) {
        if (adaptation.getName().equals(id)) {
          runtime.getData().getSkillLine(skill.getName()).setAdaptation(adaptation, adaptation.getMaxLevel());
          return adaptation;
        }
      }
    }
    throw new IllegalArgumentException("Unknown adaptation " + id);
  }

  private static void configure(Adaptation<?> adaptation, String name, Object value) {
    try {
      Object config = adaptation.getConfig();
      Field field = config.getClass().getDeclaredField(name);
      field.setAccessible(true);
      field.set(config, value);
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException("Could not configure " + adaptation.getName() + ": " + name, failure);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void denyBonus(BlockBreakEvent event) {
    if (event.getPlayer().getScoreboardTags().contains("adapt-qa-deny-bonus")
        && (event.getBlock().getY() != 100 || event.getBlock().getZ() != 0)) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBreak(BlockBreakEvent event) {
    JsonArray breaks = BREAKS.get(event.getPlayer().getUniqueId());
    if (breaks != null) {
      JsonObject broken = new JsonObject();
      broken.addProperty("x", event.getBlock().getX());
      broken.addProperty("y", event.getBlock().getY());
      broken.addProperty("z", event.getBlock().getZ());
      breaks.add(broken);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onWear(PlayerItemDamageEvent event) {
    JsonArray wear = WEAR.get(event.getPlayer().getUniqueId());
    if (wear != null) {
      JsonObject entry = new JsonObject();
      entry.addProperty("damage", event.getDamage());
      entry.addProperty("cancelled", event.isCancelled());
      wear.add(entry);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onHit(EntityDamageEvent event) {
    if (event.getEntity() instanceof Player player && BREAKS.containsKey(player.getUniqueId())) {
      JsonObject hit = new JsonObject();
      hit.addProperty("base", event.getDamage());
      hit.addProperty("final", event.getFinalDamage());
      hit.addProperty("cancelled", event.isCancelled());
      HITS.put(player.getUniqueId(), hit);
    }
  }

  private static JsonObject snapshot(Player player) {
    JsonObject state = new JsonObject();
    state.add("breaks", BREAKS.getOrDefault(player.getUniqueId(), new JsonArray()));
    state.add("wear", WEAR.getOrDefault(player.getUniqueId(), new JsonArray()));
    state.addProperty("health", player.getHealth());
    state.addProperty("blocking", player.isBlocking());
    AdaptPlayer runtime = Objects.requireNonNull(Adapt.instance.getAdaptServer().getPlayer(player));
    state.addProperty("graveLoot", runtime.getData().getStat("excavation.grave-digger.bones-unearthed"));
    state.addProperty("sharedDamage", runtime.getData().getStat("taming.shared-pain.damage-taken"));
    state.addProperty("intercepts", runtime.getData().getStat("taming.guardian-instinct.intercepts"));
    if (HITS.containsKey(player.getUniqueId())) {
      state.add("hit", HITS.get(player.getUniqueId()));
    }
    JsonArray items = new JsonArray();
    for (ItemStack item : player.getInventory().getContents()) {
      add(items, item, "inventory");
    }
    for (Item item : player.getWorld().getEntitiesByClass(Item.class)) {
      add(items, item.getItemStack(), "drop");
    }
    state.add("items", items);
    for (Wolf wolf : player.getWorld().getEntitiesByClass(Wolf.class)) {
      if (wolf.getScoreboardTags().contains("adapt-qa-mining-pet")) {
        state.addProperty("petHealth", wolf.getHealth());
      }
    }
    JsonArray blocks = new JsonArray();
    for (int y = 99; y <= 101; y++) {
      for (int z = -1; z <= 1; z++) {
        blocks.add(player.getWorld().getBlockAt(3, y, z).getType().name());
      }
    }
    state.add("blocks", blocks);
    return state;
  }

  private static void add(JsonArray items, ItemStack item, String source) {
    if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
      return;
    }
    JsonObject entry = new JsonObject();
    entry.addProperty("type", item.getType().name());
    entry.addProperty("amount", item.getAmount());
    entry.addProperty("source", source);
    if (item.getItemMeta() instanceof Damageable damage) {
      entry.addProperty("damage", damage.getDamage());
      entry.addProperty("maximum", damage.hasMaxDamage() ? damage.getMaxDamage() : item.getType().getMaxDurability());
    }
    items.add(entry);
  }
}
