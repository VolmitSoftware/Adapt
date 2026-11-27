package com.example.geology.bukkit;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.addon.AddonHandle;
import art.arcane.adapt.api.player.PlayerProfile;
import com.example.geology.GeologyAddon;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

public final class GeologyPlugin extends JavaPlugin {
  private AddonHandle handle;

  @Override
  public void onEnable() {
    handle = GeologyAddon.install();
    getLogger().info("Geology addon " + handle.state());
  }

  @Override
  public void onDisable() {
    if (handle != null) {
      handle.uninstall();
      handle = null;
    }
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    String action = args.length == 0 ? "state" : args[0];
    switch (action) {
      case "uninstall" -> {
        if (handle != null) {
          handle.uninstall();
        }
      }
      case "install" -> {
        if (handle == null || AdaptApi.addons().find(GeologyAddon.NAMESPACE).isEmpty()) {
          handle = GeologyAddon.install();
        }
      }
      case "state" -> {
      }
      default -> {
        return false;
      }
    }
    sender.sendMessage("GEOLOGY-PLUGIN " + action + " " + describe(sender));
    return true;
  }

  private String describe(CommandSender sender) {
    String state = handle == null ? "none" : handle.state().toString();
    boolean listed = AdaptApi.content().skill(GeologyAddon.GEOLOGY).isPresent();
    String orphans = "n/a";
    if (sender instanceof Player player) {
      Optional<PlayerProfile> profile = AdaptApi.players().find(player.getUniqueId());
      orphans = profile.map((PlayerProfile found) -> found.orphans().toString()).orElse("no-profile");
    }
    return "state=" + state + " listed=" + listed + " orphans=" + orphans;
  }
}
