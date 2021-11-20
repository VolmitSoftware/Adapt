package com.volmit.adapt.api.value;

import com.volmit.adapt.util.KList;
import com.volmit.adapt.util.KMap;
import com.volmit.adapt.util.KSet;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.bukkit.Material;

@AllArgsConstructor
@Builder
@Data
public class MaterialRecipe {
    private KList<MaterialCount> input;
    private MaterialCount output;
}
