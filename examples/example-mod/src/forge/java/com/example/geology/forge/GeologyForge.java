package com.example.geology.forge;

import com.example.geology.GeologyAddon;
import net.minecraftforge.fml.common.Mod;

@Mod("adaptgeology")
public final class GeologyForge {
  public GeologyForge() {
    GeologyAddon.install();
  }
}
