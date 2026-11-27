package art.arcane.adapt.content.protector;

import art.arcane.adapt.AdaptConfig;
import com.bekvon.bukkit.residence.Residence;
import com.bekvon.bukkit.residence.containers.Flags;
import com.bekvon.bukkit.residence.protection.ClaimedResidence;
import com.bekvon.bukkit.residence.protection.FlagPermissions;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class ResidenceProtector extends BukkitProtector {

  public ResidenceProtector() {
    FlagPermissions.addFlag("use-adaptations");
  }

  @Override
  protected boolean checkRegion(Player player, Location location) {
    return checkPerm(player, location, "use-adaptations");
  }

  @Override
  protected boolean canBlockBreak(Player player, Location blockLocation) {
    return checkRegion(player, blockLocation) && checkPerm(player, blockLocation, Flags.destroy);
  }

  @Override
  protected boolean canBlockPlace(Player player, Location blockLocation) {
    return checkRegion(player, blockLocation) && checkPerm(player, blockLocation, Flags.place);
  }

  @Override
  protected boolean canPVP(Player player, Location entityLocation) {
    return checkRegion(player, entityLocation) && checkPerm(player, entityLocation, Flags.pvp);
  }

  @Override
  protected boolean canPVE(Player player, Location entityLocation) {
    return checkRegion(player, entityLocation) && checkPerm(player, entityLocation, Flags.damage);
  }

  @Override
  protected boolean canInteract(Player player, Location targetLocation) {
    return checkRegion(player, targetLocation) && checkPerm(player, targetLocation, Flags.use);
  }

  @Override
  protected boolean canAccessChest(Player player, Location chestLocation) {
    return checkRegion(player, chestLocation) && checkPerm(player, chestLocation, Flags.container);
  }

  private boolean checkPerm(Player player, Location location, Flags flag) {
    Residence residence = Residence.getInstance();
    if (residence.isDisabledWorld(location.getWorld())) {
      return true;
    }
    ClaimedResidence claimedResidence = residence.getResidenceManager().getByLoc(location);
    return claimedResidence == null
        || claimedResidence.getPermissions().playerHas(player.getName(), flag, true);
  }

  private boolean checkPerm(Player player, Location location, String flag) {
    Residence residence = Residence.getInstance();
    if (residence.isDisabledWorld(location.getWorld())) {
      return true;
    }
    ClaimedResidence claimedResidence = residence.getResidenceManager().getByLoc(location);
    return claimedResidence == null
        || claimedResidence.getPermissions().playerHas(player.getName(), flag, true);
  }

  @Override
  public String name() {
    return "Residence";
  }

  @Override
  public boolean enabledByDefault() {
    return AdaptConfig.get().getProtectorSupport().isResidence();
  }

}
