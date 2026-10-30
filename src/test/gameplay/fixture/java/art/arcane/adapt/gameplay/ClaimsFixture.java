package art.arcane.adapt.gameplay;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.claims.ClaimBlock;
import art.arcane.adapt.api.claims.ClaimCity;
import art.arcane.adapt.api.claims.ClaimChunk;
import art.arcane.adapt.api.claims.ClaimManager;
import art.arcane.adapt.api.claims.ClaimSnapshot;
import art.arcane.adapt.api.claims.ClaimWorldAccess;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.claims.ClaimsService;
import art.arcane.adapt.claims.ClaimArrivalProfile;
import art.arcane.adapt.claims.ClaimTravelService;
import art.arcane.adapt.content.item.BoundEnderPearl;
import art.arcane.adapt.util.common.scheduling.J;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Objective;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;

public final class ClaimsFixture extends JavaPlugin implements Listener {
  private static final Gson JSON = new Gson();
  private static final int HEIGHT = 100;
  private static final int TRAVEL_HEIGHT = -59;
  private static final Map<String, Site> TRAVEL_SITES = Map.of(
      "town", new Site(4108, 4108),
      "anchor", new Site(4104, 4104),
      "homestead", new Site(4154, 4104),
      "personalAnchor", new Site(4152, 4104));
  private static final Map<String, Site> SITES = Map.of(
      "anchor", new Site(4104, 4104),
      "stone", new Site(4106, 4104),
      "placement", new Site(4107, 4104),
      "chest", new Site(4108, 4104),
      "overlapEdge", new Site(4136, 4104),
      "overlapCorner", new Site(4136, 4136),
      "personalAnchor", new Site(4152, 4104),
      "personalStone", new Site(4154, 4104));

  private final Map<UUID, Location> origins = new ConcurrentHashMap<>();
  private final Set<UUID> denied = ConcurrentHashMap.newKeySet();
  private final Map<UUID, Integer> lateDenials = new ConcurrentHashMap<>();
  private final Map<UUID, List<JsonObject>> travelEvents = new ConcurrentHashMap<>();
  private CompletableFuture<Void> travelArena;
  private ClaimsPortalFixture portals;
  private ClaimsArrivalFixture arrivals;
  private CompletableFuture<Void> arena;
  private World world;

  @Override
  public void onEnable() {
    try {
      String source = Files.readString(Path.of(".server-source"));
      if (Bukkit.getOnlineMode() || !"127.0.0.1".equals(Bukkit.getIp())
          || !source.lines().anyMatch("isolated=true"::equals)) {
        throw new IllegalStateException("Claims fixture requires an isolated offline loopback Multiplexor instance");
      }
      Bukkit.getPluginManager().registerEvents(this, this);
      portals = new ClaimsPortalFixture(this);
      Bukkit.getPluginManager().registerEvents(portals, this);
      arrivals = new ClaimsArrivalFixture(this, portals);
      Bukkit.getPluginManager().registerEvents(arrivals, this);
    } catch (IOException | IllegalStateException failure) {
      getLogger().log(Level.SEVERE, "Claims fixture refused this server", failure);
      Bukkit.getPluginManager().disablePlugin(this);
    }
  }

  @Override
  public void onDisable() {
    if (arrivals != null) {
      arrivals.close();
    }
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!(sender instanceof Player admin) || !admin.isOp() || args.length < 2) {
      sender.sendMessage("ADAPT_QA ERROR operator and actor/action required");
      return true;
    }
    Player player = Bukkit.getPlayerExact(args[0]);
    if (player == null || player.isOp() || !player.getName().startsWith("AQAc")) {
      sender.sendMessage("ADAPT_QA ERROR ordinary AQAc actor required");
      return true;
    }
    CompletableFuture<JsonObject> response;
    try {
      if (!service().enabled()) {
        throw new IllegalStateException("Enable Claims before running its gameplay scenario");
      }
      response = switch (args[1]) {
        case "prepare" -> prepare(player, args.length > 2 ? args[2] : "attacker");
        case "resume" -> entity(player, () -> {
          world = player.getWorld();
          origins.putIfAbsent(player.getUniqueId(), world.getSpawnLocation());
          return null;
        }).thenCompose(ignored -> snapshot(player));
        case "snapshot" -> snapshot(player);
        case "travel-prepare" -> prepareTravel(player, args[2], args.length > 3 ? Bukkit.getPlayerExact(args[3]) : null);
        case "travel-source" -> travelSource(player, args[2]);
        case "travel-snapshot" -> travelSnapshot(player);
        case "travel-watch" -> entity(player, () -> {
          travelEvents.get(player.getUniqueId()).clear();
          return null;
        }).thenCompose(ignored -> travelSnapshot(player));
        case "travel-hold" -> entity(player, () -> service().travel().prepare(new ClaimTravelService.Request(player,
            travelLocation(TRAVEL_SITES.get("town")), PlayerTeleportEvent.TeleportCause.PLUGIN,
            ClaimArrivalProfile.UNDERDARK_TO_OVERWORLD))).thenCompose(approval -> approval)
            .thenCompose(approval -> {
              if (approval.isEmpty()) {
                return CompletableFuture.failedFuture(new IllegalStateException("Travel hold was not prepared"));
              }
              return travelSnapshot(player);
            });
        case "deny" -> {
          if (Boolean.parseBoolean(args[2])) {
            denied.add(player.getUniqueId());
          } else {
            denied.remove(player.getUniqueId());
          }
          yield snapshot(player);
        }
        case "flush" -> CompletableFuture.runAsync(this::flush).thenCompose(ignored -> snapshot(player));
        case "reload" -> reload().thenCompose(ignored -> snapshot(player));
        case "access" -> prepareAccess(player, Boolean.parseBoolean(args[2]));
        case "orphan" -> {
          Site anchor = SITES.get("anchor");
          yield at(new Location(world, anchor.x(), HEIGHT, anchor.z()), () -> {
            world.getBlockAt(anchor.x(), HEIGHT, anchor.z()).setType(Material.AIR, false);
            return null;
          }).thenCompose(ignored -> snapshot(player));
        }
        case "late-deny" -> {
          lateDenials.put(player.getUniqueId(), 2);
          yield snapshot(player);
        }
        case "cleanup" -> cleanup(player);
        default -> args[1].startsWith("portal-") ? portals.command(player, args)
            : args[1].startsWith("arrival-") ? arrivals.command(player, args)
            : CompletableFuture.failedFuture(new IllegalArgumentException("Unknown Claims fixture action: " + args[1]));
      };
    } catch (RuntimeException failure) {
      fail(admin, failure);
      return true;
    }
    response.whenComplete((result, failure) -> {
      if (failure != null) {
        fail(admin, failure);
      } else {
        J.runEntity(admin, () -> send(admin, args[1], result));
      }
    });
    return true;
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void denyBreak(BlockBreakEvent event) {
    if (denied.contains(event.getPlayer().getUniqueId()) || consumeLateDenial(event.getPlayer().getUniqueId())) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void denyPlace(BlockPlaceEvent event) {
    if (denied.contains(event.getPlayer().getUniqueId()) || consumeLateDenial(event.getPlayer().getUniqueId())) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void observeTravel(PlayerTeleportEvent event) {
    List<JsonObject> observations = travelEvents.get(event.getPlayer().getUniqueId());
    if (observations == null) {
      return;
    }
    JsonObject observation = new JsonObject();
    observation.addProperty("kind", "teleport");
    observation.addProperty("cancelled", event.isCancelled());
    observation.addProperty("allowed", service().canTeleport(event.getPlayer(), event.getTo()));
    observation.add("from", position(event.getFrom()));
    observation.add("to", position(event.getTo()));
    observations.add(observation);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void observeArrival(PlayerChangedWorldEvent event) {
    List<JsonObject> observations = travelEvents.get(event.getPlayer().getUniqueId());
    if (observations == null) {
      return;
    }
    JsonObject observation = new JsonObject();
    observation.addProperty("kind", "arrival");
    observation.addProperty("allowed", service().canTeleport(event.getPlayer(), event.getPlayer().getLocation()));
    observation.add("to", position(event.getPlayer().getLocation()));
    observations.add(observation);
  }

  private CompletableFuture<JsonObject> prepareTravel(Player player, String role, Player founder) {
    if (J.isFoliaThreading() || !Set.of("founder", "member", "attacker").contains(role) || role.equals("member") && founder == null) {
      return CompletableFuture.failedFuture(new IllegalArgumentException("Travel role or founder missing"));
    }
    return entity(player, () -> {
      runtime(player);
      world = Objects.requireNonNull(Bukkit.getWorld(NamespacedKey.minecraft("overworld")));
      if (origins.putIfAbsent(player.getUniqueId(), player.getLocation()) != null) {
        throw new IllegalStateException("Use a fresh Claims actor");
      }
      travelEvents.put(player.getUniqueId(), new ArrayList<>());
      player.setGameMode(GameMode.SURVIVAL);
      player.setGravity(false);
      player.setHealth(20);
      player.setFoodLevel(20);
      player.setSaturation(20);
      if (!role.equals("attacker")) {
        Skill<?> skill = Adapt.instance.getAdaptServer().getSkillRegistry().getAnySkill("claims");
        PlayerSkillLine line = runtime(player).getData().getSkillLine("claims");
        for (Adaptation<?> adaptation : skill.getAdaptations()) {
          line.setAdaptation(adaptation, 1);
        }
        service().refreshRanks(player);
      }
      return null;
    }).thenCompose(ignored -> prepareTravelArena()).thenCompose(ignored -> entity(player, () -> {
      ClaimWorldAccess access = new ClaimWorldAccess(world.getName(), true);
      if (role.equals("founder")) {
        manager().found(player.getUniqueId(), "Travel Trial", ClaimsService.block(travelLocation(TRAVEL_SITES.get("anchor"))),
            service().ranks(player), access);
        for (int x = 258; x <= 260; x++) {
          for (int z = 255; z <= 257; z++) {
            manager().expand(player.getUniqueId(), new ClaimChunk(world.getUID(), x, z), access);
          }
        }
      } else if (role.equals("member")) {
        long now = System.currentTimeMillis();
        manager().invite(founder.getUniqueId(), player.getUniqueId(), now);
        manager().accept(player.getUniqueId(), founder.getUniqueId(), now);
        manager().createPlot(player.getUniqueId(), ClaimsService.block(travelLocation(TRAVEL_SITES.get("personalAnchor"))),
            service().ranks(player));
      }
      return null;
    })).thenCompose(ignored -> travelSnapshot(player));
  }

  private synchronized CompletableFuture<Void> prepareTravelArena() {
    if (travelArena != null) {
      return travelArena;
    }
    List<CompletableFuture<Void>> chunks = new ArrayList<>();
    for (int chunkX = 254; chunkX <= 261; chunkX++) {
      for (int chunkZ = 254; chunkZ <= 258; chunkZ++) {
        int x = chunkX;
        int z = chunkZ;
        chunks.add(world.getChunkAtAsync(x, z).thenCompose(chunk -> at(new Location(world, x * 16, TRAVEL_HEIGHT, z * 16), () -> {
          world.addPluginChunkTicket(x, z, this);
          for (int blockX = x * 16; blockX < (x + 1) * 16; blockX++) {
            for (int blockZ = z * 16; blockZ < (z + 1) * 16; blockZ++) {
              for (int y = TRAVEL_HEIGHT - 1; y <= TRAVEL_HEIGHT + 1; y++) {
                world.getBlockAt(blockX, y, blockZ).setType(Material.DEEPSLATE, false);
              }
            }
          }
          for (String key : List.of("anchor", "personalAnchor")) {
            Site site = TRAVEL_SITES.get(key);
            if (site.x() >> 4 == x && site.z() >> 4 == z) {
              world.getBlockAt(site.x(), TRAVEL_HEIGHT, site.z()).setType(Material.LODESTONE, false);
            }
          }
          return null;
        })));
      }
    }
    travelArena = CompletableFuture.allOf(chunks.toArray(CompletableFuture[]::new));
    return travelArena;
  }

  private CompletableFuture<JsonObject> travelSource(Player player, String key) {
    Site site = Objects.requireNonNull(TRAVEL_SITES.get(key));
    World underdark = Objects.requireNonNull(Bukkit.getWorld(NamespacedKey.fromString("bracken:the_underdark")));
    Location source = new Location(underdark, site.x() + 0.5, 260, site.z() + 0.5);
    return entity(player, () -> {
      service().travel().cancel(player);
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "execute as " + player.getUniqueId()
          + " at @s run function bracken:travel/reset");
      player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
      return null;
    }).thenCompose(ignored -> underdark.getChunkAtAsync(site.x() >> 4, site.z() >> 4))
        .thenCompose(chunk -> at(source, () -> {
          underdark.getBlockAt(site.x(), 259, site.z()).setType(Material.STONE, false);
          underdark.getBlockAt(site.x(), 260, site.z()).setType(Material.AIR, false);
          underdark.getBlockAt(site.x(), 261, site.z()).setType(Material.AIR, false);
          return null;
        })).thenCompose(ignored -> player.teleportAsync(source)).thenCompose(moved -> {
          if (!moved) {
            return CompletableFuture.failedFuture(new IllegalStateException("Underdark source teleport refused"));
          }
          return travelSnapshot(player);
        });
  }

  private CompletableFuture<JsonObject> travelSnapshot(Player player) {
    return entity(player, () -> {
      JsonObject result = new JsonObject();
      result.addProperty("player", player.getUniqueId().toString());
      result.addProperty("operator", player.isOp());
      result.addProperty("bypass", service().bypass(player));
      result.addProperty("pending", service().travel().pending(player));
      result.addProperty("fireResistance", player.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE));
      result.add("location", position(player.getLocation()));
      result.add("events", JSON.toJsonTree(travelEvents.get(player.getUniqueId())));
      result.add("city", JSON.toJsonTree(manager().snapshot().cityAt(ClaimsService.chunk(travelLocation(TRAVEL_SITES.get("anchor")))).orElse(null)));
      result.add("plots", JSON.toJsonTree(manager().snapshot().plots()));
      JsonObject scores = new JsonObject();
      for (String name : List.of("state", "seq", "result", "age")) {
        Objective objective = Bukkit.getScoreboardManager().getMainScoreboard().getObjective("bp.travel_" + name);
        scores.addProperty(name, objective == null ? 0 : objective.getScore(player.getName()).getScore());
      }
      result.add("travel", scores);
      JsonObject blocks = new JsonObject();
      for (Map.Entry<String, Site> entry : TRAVEL_SITES.entrySet()) {
        Site site = entry.getValue();
        JsonObject column = new JsonObject();
        column.addProperty("floor", world.getBlockAt(site.x(), TRAVEL_HEIGHT - 1, site.z()).getType().name());
        column.addProperty("feet", world.getBlockAt(site.x(), TRAVEL_HEIGHT, site.z()).getType().name());
        column.addProperty("head", world.getBlockAt(site.x(), TRAVEL_HEIGHT + 1, site.z()).getType().name());
        blocks.add(entry.getKey(), column);
      }
      Location current = player.getLocation();
      JsonObject landing = new JsonObject();
      landing.addProperty("floor", current.clone().add(0, -1, 0).getBlock().getType().name());
      landing.addProperty("feet", current.getBlock().getType().name());
      landing.addProperty("head", current.clone().add(0, 1, 0).getBlock().getType().name());
      result.add("landing", landing);
      result.add("blocks", blocks);
      return result;
    });
  }

  private Location travelLocation(Site site) {
    return new Location(world, site.x() + 0.5, TRAVEL_HEIGHT, site.z() + 0.5);
  }

  private static JsonObject position(Location location) {
    JsonObject result = new JsonObject();
    result.addProperty("world", location.getWorld().getKey().toString());
    result.addProperty("x", location.getX());
    result.addProperty("y", location.getY());
    result.addProperty("z", location.getZ());
    return result;
  }

  private boolean consumeLateDenial(UUID player) {
    Integer remaining = lateDenials.get(player);
    if (remaining == null) {
      return false;
    }
    if (remaining == 1) {
      lateDenials.remove(player);
      return true;
    }
    lateDenials.put(player, remaining - 1);
    return false;
  }

  private CompletableFuture<JsonObject> prepare(Player player, String role) {
    if (!Set.of("founder", "member", "attacker").contains(role)) {
      return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown Claims role"));
    }
    return entity(player, () -> {
      runtime(player);
      if (origins.putIfAbsent(player.getUniqueId(), player.getLocation()) != null) {
        throw new IllegalStateException("Use a fresh Claims actor");
      }
      return player.getWorld();
    }).thenCompose(this::prepareArena).thenCompose(ignored -> {
      double x = switch (role) {
        case "founder" -> 4103.5;
        case "member" -> 4105.5;
        default -> 4107.5;
      };
      return player.teleportAsync(new Location(world, x, HEIGHT, 4107.5));
    }).thenCompose(teleported -> {
      if (!teleported) {
        return CompletableFuture.failedFuture(new IllegalStateException("Claims fixture preparation teleport refused"));
      }
      return entity(player, () -> {
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setHealth(20);
        player.setFoodLevel(20);
        player.setSaturation(20);
        player.getInventory().clear();
        ItemStack pickaxe = new ItemStack(Material.NETHERITE_PICKAXE);
        pickaxe.addUnsafeEnchantment(Enchantment.EFFICIENCY, 5);
        pickaxe.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        player.getInventory().setItem(0, pickaxe);
        player.getInventory().setItem(1, new ItemStack(Material.IRON_SHOVEL));
        player.getInventory().setItem(2, new ItemStack(Material.LODESTONE, 4));
        player.getInventory().setItem(3, new ItemStack(Material.DIRT, 64));
        if (!role.equals("attacker")) {
          Skill<?> skill = Adapt.instance.getAdaptServer().getSkillRegistry().getAnySkill("claims");
          PlayerSkillLine line = runtime(player).getData().getSkillLine("claims");
          for (Adaptation<?> adaptation : skill.getAdaptations()) {
            line.setAdaptation(adaptation, 1);
          }
          service().refreshRanks(player);
        }
        return null;
      }).thenCompose(ignored -> snapshot(player));
    });
  }

  private synchronized CompletableFuture<Void> prepareArena(World target) {
    if (arena != null) {
      if (!world.getUID().equals(target.getUID())) {
        return CompletableFuture.failedFuture(new IllegalStateException("Claims actors must use one world"));
      }
      return arena;
    }
    world = target;
    if (manager().snapshot().cityAt(block(SITES.get("anchor")).chunk()).isPresent()) {
      return CompletableFuture.failedFuture(new IllegalStateException("Existing Claims trial must be resumed or removed first"));
    }
    List<CompletableFuture<Void>> chunks = new ArrayList<>();
    for (int chunkX = 254; chunkX <= 260; chunkX++) {
      for (int chunkZ = 254; chunkZ <= 259; chunkZ++) {
        int x = chunkX;
        int z = chunkZ;
        chunks.add(world.getChunkAtAsync(x, z).thenCompose(chunk -> at(new Location(world, x * 16, HEIGHT, z * 16), () -> {
          buildChunk(x, z);
          return null;
        })));
      }
    }
    arena = CompletableFuture.allOf(chunks.toArray(CompletableFuture[]::new));
    return arena;
  }

  private void buildChunk(int chunkX, int chunkZ) {
    for (int x = chunkX * 16; x < (chunkX + 1) * 16; x++) {
      for (int z = chunkZ * 16; z < (chunkZ + 1) * 16; z++) {
        world.getBlockAt(x, HEIGHT - 1, z).setType(Material.STONE, false);
        for (int y = HEIGHT; y <= HEIGHT + 4; y++) {
          world.getBlockAt(x, y, z).setType(Material.AIR, false);
        }
      }
    }
    for (Map.Entry<String, Site> entry : SITES.entrySet()) {
      Site site = entry.getValue();
      if (site.x() >> 4 != chunkX || site.z() >> 4 != chunkZ) {
        continue;
      }
      if (entry.getKey().equals("stone") || entry.getKey().equals("personalStone")) {
        world.getBlockAt(site.x(), HEIGHT, site.z()).setType(Material.STONE, false);
      } else if (entry.getKey().equals("chest")) {
        world.getBlockAt(site.x(), HEIGHT, site.z()).setType(Material.CHEST, false);
        Chest chest = (Chest) world.getBlockAt(site.x(), HEIGHT, site.z()).getState();
        chest.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 3));
      }
    }
  }

  private CompletableFuture<JsonObject> snapshot(Player player) {
    return entity(player, () -> {
      JsonObject result = new JsonObject();
      result.addProperty("processId", ProcessHandle.current().pid());
      result.addProperty("now", System.currentTimeMillis());
      result.addProperty("player", player.getUniqueId().toString());
      result.addProperty("username", player.getName());
      result.addProperty("operator", player.isOp());
      result.addProperty("bypass", service().bypass(player));
      result.addProperty("survival", player.getGameMode() == GameMode.SURVIVAL);
      result.addProperty("experience", player.getTotalExperience());
      PlayerSkillLine claims = runtime(player).getData().getSkillLine("claims");
      result.addProperty("claimsXp", claims.getXp() + claims.getPooledXp());
      result.add("ranks", JSON.toJsonTree(service().ranks(player)));
      JsonObject location = new JsonObject();
      location.addProperty("x", player.getX());
      location.addProperty("y", player.getY());
      location.addProperty("z", player.getZ());
      result.add("location", location);
      JsonObject inventory = new JsonObject();
      for (ItemStack item : player.getInventory().getContents()) {
        if (item != null && !item.getType().isAir()) {
          String key = item.getType().name().toLowerCase();
          inventory.addProperty(key, (inventory.has(key) ? inventory.get(key).getAsInt() : 0) + item.getAmount());
        }
      }
      result.add("inventory", inventory);
      ClaimSnapshot snapshot = manager().snapshot();
      result.add("city", JSON.toJsonTree(snapshot.cityOf(player.getUniqueId()).orElse(null)));
      result.add("siteCity", JSON.toJsonTree(snapshot.cityAt(block(SITES.get("anchor")).chunk()).orElse(null)));
      result.add("plots", JSON.toJsonTree(snapshot.plots()));
      JsonObject progress = new JsonObject();
      for (Map.Entry<String, Site> entry : SITES.entrySet()) {
        progress.add(entry.getKey(), JSON.toJsonTree(manager().progress(block(entry.getValue())).orElse(null)));
      }
      result.add("progress", progress);
      result.add("sites", JSON.toJsonTree(SITES));
      return result;
    }).thenCompose(result -> {
      List<CompletableFuture<Void>> observations = new ArrayList<>();
      JsonObject blocks = new JsonObject();
      for (Map.Entry<String, Site> entry : SITES.entrySet()) {
        Site site = entry.getValue();
        observations.add(at(new Location(world, site.x(), HEIGHT, site.z()), () -> {
          String material = world.getBlockAt(site.x(), HEIGHT, site.z()).getType().name().toLowerCase();
          synchronized (blocks) {
            blocks.addProperty(entry.getKey(), material);
          }
          return null;
        }));
      }
      return CompletableFuture.allOf(observations.toArray(CompletableFuture[]::new)).thenApply(ignored -> {
        result.add("blocks", blocks);
        return result;
      });
    });
  }

  CompletableFuture<Void> reload() {
    CompletableFuture<Void> result = new CompletableFuture<>();
    J.s(() -> complete(result, () -> {
      if (!AdaptConfig.reload() || !Adapt.instance.getAdaptServer().getSkillRegistry().hotReloadSkillConfig("claims")) {
        throw new IllegalStateException("Claims configuration reload failed");
      }
      return null;
    }));
    return result;
  }

  private CompletableFuture<JsonObject> prepareAccess(Player player, boolean wall) {
    Site chest = SITES.get("chest");
    Location target = new Location(world, chest.x(), HEIGHT, chest.z());
    return at(target, () -> {
      for (int x = chest.x() - 1; x <= chest.x() + 1; x++) {
        for (int y = HEIGHT; y <= HEIGHT + 2; y++) {
          world.getBlockAt(x, y, chest.z() + 1).setType(wall ? Material.STONE : Material.AIR, false);
        }
      }
      return BoundEnderPearl.withData(target.getBlock());
    }).thenCompose(pearl -> entity(player, () -> {
      Skill<?> rift = Adapt.instance.getAdaptServer().getSkillRegistry().getAnySkill("rift");
      for (Adaptation<?> adaptation : rift.getAdaptations()) {
        if (adaptation.getName().equals("rift-access")) {
          runtime(player).getData().getSkillLine("rift").setAdaptation(adaptation, 1);
        }
      }
      player.getInventory().setItem(5, pearl);
      return null;
    })).thenCompose(ignored -> snapshot(player));
  }

  private CompletableFuture<JsonObject> cleanup(Player player) {
    if (travelEvents.remove(player.getUniqueId()) != null) {
      J.runEntity(player, () -> player.setGravity(true));
    }
    service().travel().cancel(player);
    denied.remove(player.getUniqueId());
    lateDenials.remove(player.getUniqueId());
    ClaimCity city = manager().snapshot().cityOf(player.getUniqueId()).orElse(null);
    if (city != null && city.founder().equals(player.getUniqueId())) {
      manager().removeCity(city.id());
    }
    if (manager().snapshot().plots().containsKey(player.getUniqueId())) {
      manager().removePlot(player.getUniqueId());
    }
    Location origin = origins.remove(player.getUniqueId());
    synchronized (this) {
      if (origins.isEmpty()) {
        arena = null;
        travelArena = null;
        if (!J.isFoliaThreading()) {
          world.removePluginChunkTickets(this);
        }
      }
    }
    CompletableFuture<Boolean> moved = origin == null ? CompletableFuture.completedFuture(true) : player.teleportAsync(origin);
    return moved.thenCompose(ignored -> snapshot(player));
  }

  private ClaimBlock block(Site site) {
    return new ClaimBlock(world.getUID(), site.x(), HEIGHT, site.z());
  }

  private void flush() {
    try {
      manager().flush();
    } catch (IOException exception) {
      throw new CompletionException(exception);
    }
  }

  static ClaimsService service() {
    return Objects.requireNonNull(Adapt.instance.getClaimsService());
  }

  private static ClaimManager manager() {
    return service().manager();
  }

  static AdaptPlayer runtime(Player player) {
    AdaptPlayer runtime = Adapt.instance.getAdaptServer().getOnlineAdaptPlayer(player.getUniqueId());
    if (runtime == null || !runtime.isRuntimeReady() || runtime.getPlayer() != player) {
      throw new IllegalStateException("Adapt player is not ready");
    }
    return runtime;
  }

  static <T> CompletableFuture<T> entity(Entity entity, Supplier<T> action) {
    CompletableFuture<T> result = new CompletableFuture<>();
    if (!J.runEntity(entity, () -> complete(result, action))) {
      result.completeExceptionally(new IllegalStateException("Claims fixture entity scheduler refused"));
    }
    return result;
  }

  static <T> CompletableFuture<T> at(Location location, Supplier<T> action) {
    CompletableFuture<T> result = new CompletableFuture<>();
    if (!J.runAt(location, () -> complete(result, action))) {
      result.completeExceptionally(new IllegalStateException("Claims fixture region scheduler refused"));
    }
    return result;
  }

  private static <T> void complete(CompletableFuture<T> result, Supplier<T> action) {
    try {
      result.complete(action.get());
    } catch (RuntimeException failure) {
      result.completeExceptionally(failure);
    }
  }

  private void fail(Player admin, Throwable failure) {
    getLogger().log(Level.SEVERE, "Claims fixture command failed", failure);
    J.runEntity(admin, () -> admin.sendMessage("ADAPT_QA ERROR Claims fixture: " + failure.getMessage()));
  }

  private static void send(Player admin, String action, JsonObject result) {
    String json = result.toString();
    int total = (json.length() + 2499) / 2500;
    for (int index = 0; index < total; index++) {
      admin.sendMessage("ADAPT_QA CLAIMS " + action + " " + index + " " + total + " "
          + json.substring(index * 2500, Math.min(json.length(), (index + 1) * 2500)));
    }
  }

  private record Site(int x, int z) {
  }
}
