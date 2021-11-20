package com.volmit.adapt.api.value;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.bukkit.Material;

@AllArgsConstructor
@Data
public class MaterialCount {
    private Material material;
    private int amount;
}
