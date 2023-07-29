package com.volmit.adapt.content.protector;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.adaptation.Adaptation;
import com.volmit.adapt.api.protection.Protector;
import me.angeschossen.chestprotect.api.addons.ChestProtectAddon;
import me.angeschossen.chestprotect.api.protection.block.BlockProtection;
import me.crafter.mc.lockettepro.LocketteProAPI;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class LocketteProProtector implements Protector {
    @Override
    public boolean canAccessChest(Player player, Location chestlocation, Adaptation<?> adaptation) {
        return LocketteProAPI.isOwner(chestlocation.getBlock(), player);
    }

    @Override
    public String getName() {
        return "LockettePro";
    }

    @Override
    public boolean isEnabledByDefault() {
        return AdaptConfig.get().getProtectorSupport().isLockettePro();
    }
}
