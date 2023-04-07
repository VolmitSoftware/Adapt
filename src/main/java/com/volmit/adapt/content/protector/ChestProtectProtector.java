package com.volmit.adapt.content.protector;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.adaptation.Adaptation;
import com.volmit.adapt.api.protection.Protector;
import me.angeschossen.chestprotect.api.addons.ChestProtectAddon;
import me.angeschossen.chestprotect.api.objects.BlockProtection;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class ChestProtectProtector implements Protector {
    private final ChestProtectAddon chestProtect;

    public ChestProtectProtector() {
        this.chestProtect = new ChestProtectAddon(Adapt.instance);
    }

    @Override
    public boolean canAccessChest(Player player, Location chestlocation, Adaptation<?> adaptation) {
        if (!chestProtect.isProtectable(chestlocation.getBlock())) return true;
        BlockProtection blockProtection = chestProtect.getProtection(chestlocation);
        if (blockProtection == null) return true;
        return blockProtection.isTrusted(player.getUniqueId());
    }

    @Override
    public String getName() {
        return "ChestProtect";
    }

    @Override
    public boolean isEnabledByDefault() {
        return AdaptConfig.get().getProtectorSupport().isChestProtect();
    }
}
