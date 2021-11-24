package com.volmit.adapt.api.recipe;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.bukkit.Material;

@AllArgsConstructor
@Data
public class MaterialChar {
    private char character;
    private Material material;
}
