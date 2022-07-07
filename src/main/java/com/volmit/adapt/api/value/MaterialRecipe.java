package com.volmit.adapt.api.value;

import com.volmit.adapt.util.KList;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@AllArgsConstructor
@Builder
@Data
public class MaterialRecipe {
    private KList<MaterialCount> input;
    private MaterialCount output;
}
