package com.volmit.adapt.content.protector;

import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.adaptation.Adaptation;
import com.volmit.adapt.api.protection.Protector;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Location;

import java.util.UUID;

public class GriefPreventionProtector implements Protector {

    private final GriefPrevention griefPrevention;

    public GriefPreventionProtector() {
        this.griefPrevention = GriefPrevention.instance;
    }

    @Override
    public boolean canEditClaim(UUID player, Location location, Adaptation<?> adaptation) {
        return canEditClaim(player, location);
    }

    @Override
    public String getName() {
        return "GriefPrevention";
    }

    @Override
    public boolean isEnabledByDefault() {
        return AdaptConfig.get().getProtectorSupport().isGriefprevention();
    }

    @Override
    public void unregister() {
        Protector.super.unregister();
    }



    private boolean canEditClaim(UUID player, Location location) {
        Claim claim = griefPrevention.dataStore.getClaimAt(location, true, null);

        if (claim == null) {
            return true;
        }

        return claim.getOwnerID().equals(player) || claim.getPermission(player.toString()) == ClaimPermission.Build;
    }









}

