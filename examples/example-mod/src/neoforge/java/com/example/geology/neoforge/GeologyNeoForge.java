package com.example.geology.neoforge;

import com.example.geology.GeologyAddon;
import net.neoforged.fml.common.Mod;

@Mod("adaptgeology")
public final class GeologyNeoForge {
  public GeologyNeoForge() {
    GeologyAddon.install();
  }
}
