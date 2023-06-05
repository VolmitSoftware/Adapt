package com.volmit.adapt.content.protector;

import com.bekvon.bukkit.residence.Residence;
import com.bekvon.bukkit.residence.containers.Flags;
import com.bekvon.bukkit.residence.protection.ClaimedResidence;
import com.bekvon.bukkit.residence.protection.FlagPermissions;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.adaptation.Adaptation;
import com.volmit.adapt.api.protection.Protector;
import com.volmit.adapt.util.J;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.concurrent.atomic.AtomicBoolean;

public class ResidenceProtector implements Protector {

    public ResidenceProtector() {
        FlagPermissions.addFlag("use-adaptations");
    }

    @Override
    public boolean checkRegion(Player player, Location location, Adaptation<?> adaptation) {
        return checkPerm(player, location, "use-adaptations");
    }

    @Override
    public boolean canBlockBreak(Player player, Location blockLocation, Adaptation<?> adaptation) {
        return checkRegion(player, blockLocation, adaptation) && checkPerm(player, blockLocation, Flags.destroy);
    }

    @Override
    public boolean canBlockPlace(Player player, Location blockLocation, Adaptation<?> adaptation) {
        return checkRegion(player, blockLocation, adaptation) && checkPerm(player, blockLocation, Flags.place);
    }

    @Override
    public boolean canPVP(Player player, Location entityLocation, Adaptation<?> adaptation) {
        return checkRegion(player, entityLocation, adaptation) && checkPerm(player, entityLocation, Flags.pvp);
    }

    @Override
    public boolean canPVE(Player player, Location entityLocation, Adaptation<?> adaptation) {
        return checkRegion(player, entityLocation, adaptation) && checkPerm(player, entityLocation, Flags.damage);
    }

    @Override
    public boolean canInteract(Player player, Location targetLocation, Adaptation<?> adaptation) {
        return checkRegion(player, targetLocation, adaptation) && checkPerm(player, targetLocation, Flags.use);
    }

    @Override
    public boolean canAccessChest(Player player, Location chestLocation, Adaptation<?> adaptation) {
        return checkRegion(player, chestLocation, adaptation) && checkPerm(player, chestLocation, Flags.container);
    }

    private boolean checkPerm(Player player, Location location, Flags flag) {
        AtomicBoolean perm = new AtomicBoolean(false);
        J.s(() -> {
            if (!Residence.getInstance().isDisabledWorld(location.getWorld())) {
                ClaimedResidence res = Residence.getInstance().getResidenceManager().getByLoc(location);
                if (res != null) {
                    perm.set(res.getPermissions().playerHas(player.getName(), flag, true));
                }
            }
        });
        return perm.get();
    }

    private boolean checkPerm(Player player, Location location, String flag) {
        AtomicBoolean perm = new AtomicBoolean(false);
        J.s(() -> {
            if (!Residence.getInstance().isDisabledWorld(location.getWorld())) {
                ClaimedResidence res = Residence.getInstance().getResidenceManager().getByLoc(location);
                if (res != null) {
                    perm.set(res.getPermissions().playerHas(player.getName(), flag, true));
                }
            }
        });
        return true;
    }

    @Override
    public String getName() {
        return "Residence";
    }

    @Override
    public boolean isEnabledByDefault() {
        return AdaptConfig.get().getProtectorSupport().isResidence();
    }

}
