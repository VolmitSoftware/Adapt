package art.arcane.adapt.content.adaptation.pickaxe;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.PickaxeMessages;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.RNG;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

public class PickaxeSilkSpawner extends SimpleAdaptation<PickaxeSilkSpawner.Config> {
  private final RNG rng = new RNG();

  public PickaxeSilkSpawner() {
    super("pickaxe-silk-spawner");
    registerConfiguration(PickaxeSilkSpawner.Config.class);
    setIcon(Material.SPAWNER);
    setInterval(8444);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SPAWNER)
        .key("challenge_pickaxe_spawner_10")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.SPAWNER)
            .key("challenge_pickaxe_spawner_50")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_pickaxe_spawner_10", "pickaxe.silk-spawner.spawners-collected", 10, 500);
    registerMilestone("challenge_pickaxe_spawner_50", "pickaxe.silk-spawner.spawners-collected", 50, 2000);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onBlockBreak(BlockBreakEvent event) {
    org.bukkit.entity.Player player = event.getPlayer();
    org.bukkit.block.Block block = event.getBlock();
    art.arcane.adapt.api.adaptation.Adaptation.BlockActionContext context = resolveBlockBreakContext(player, block.getLocation());
    if (!event.isDropItems() || block.getType() != Material.SPAWNER || context == null)
      return;
    ItemStack tool = player.getInventory().getItemInMainHand();
    if (!isPickaxe(tool)) {
      return;
    }
    int level = context.level();
    boolean silkTouch = tool.getEnchantments().containsKey(Enchantment.SILK_TOUCH);
    if (!silkTouch && (level <= 1 || !player.isSneaking())) {
      return;
    }

    event.setDropItems(false);
    Location spawnerCenter = block.getLocation().add(0.5, 0.5, 0.5);
    timeline(spawnerCenter)
        .duration(18)
        .priority(FxPriority.TRANSITION)
        .cullRadius(32)
        .frame((fxE, tick, progress) -> {
          fxE.ring(Particle.SOUL, 1.4D - (1.2D * progress), 16, 0.4D);
          fxE.particle(Particle.PORTAL, 4, 0, 0.5, 0, 0.8, 0.1);
          if (tick == 0) {
            fxE.sound(Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8f, 0.8f);
          } else if ((tick & 3) == 0) {
            fxE.sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, (float) (1.0D + (progress * 0.8D)));
          }
          if (tick == 17) {
            fxE.particle(Particle.FLASH, 1, 0, 0.5, 0, 0, 0)
                .ring(Particle.SOUL, 1.6D, 20, 0.4D)
                .chord(Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 0.9f, 1.2f, Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.0f);
          }
        })
        .start();
    org.bukkit.inventory.ItemStack spawner = new ItemStack(Material.SPAWNER);
    org.bukkit.block.BlockState state = block.getState();
    if (spawner.getItemMeta() instanceof BlockStateMeta meta) {
      meta.setBlockState(state);
      spawner.setItemMeta(meta);
    }

    org.bukkit.Location loc = block.getLocation().add(
        rng.d(-0.25D, 0.25D),
        rng.d(-0.25D, 0.25D) - 0.125D,
        rng.d(-0.25D, 0.25D)
    );
    org.bukkit.entity.Item item = block.getWorld().createEntity(loc, Item.class);
    item.setItemStack(spawner);
    item.setOwner(player.getUniqueId());

    org.bukkit.event.block.BlockDropItemEvent dropEvent = new BlockDropItemEvent(block, state, player, new KList<Item>().qadd(item));
    Bukkit.getPluginManager().callEvent(dropEvent);
    if (dropEvent.isCancelled()) {
      for (Item i : dropEvent.getItems()) {
        if (i.isValid()) i.remove();
      }
      fx(spawnerCenter, FxPriority.TRANSITION)
          .particle(Particles.SMOKE, 6, 0, 0.3, 0, 0.2, 0.02)
          .chord(Sound.BLOCK_FIRE_EXTINGUISH, 0.6f, 0.6f, Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
    } else {
      for (Item i : dropEvent.getItems()) {
        if (!i.isValid()) block.getWorld().addEntity(i);
      }
      addStat(player, "pickaxe.silk-spawner.spawners-collected", 1);
      fx(loc, FxPriority.TRANSITION)
          .column(Particles.END_ROD, 8, 1.0D)
          .sound(Sound.ENTITY_ITEM_PICKUP, 0.6f, 0.7f);
    }
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + AdaptLanguage.text(
        level < 2 ? PickaxeMessages.SILK_SPAWNER_LORE1 : PickaxeMessages.SILK_SPAWNER_LORE2
    ));
  }


  @ConfigDescription("Spawners drop when broken with silk touch or while sneaking.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      baseCost = 6;
      costFactor = 0.95;
      maxLevel = 2;
      initialCost = 4;
    }
  }
}
