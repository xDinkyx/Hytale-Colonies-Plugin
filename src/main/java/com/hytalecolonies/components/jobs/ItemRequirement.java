package com.hytalecolonies.components.jobs;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;

/**
 * One entry in a task's item list: an item ID (or glob pattern like
 * {@code Tool_Hatchet_*})
 * and the required quantity.
 */
public class ItemRequirement {

    public static final BuilderCodec<ItemRequirement> CODEC = BuilderCodec
            .builder(ItemRequirement.class, ItemRequirement::new)
            .append(new KeyedCodec<>("Item", Codec.STRING),
                    (o, v) -> o.item = v,
                    o -> o.item)
            .add()
            .append(new KeyedCodec<>("Quantity", Codec.INTEGER),
                    (o, v) -> o.quantity = v,
                    o -> o.quantity)
            .add()
            .build();

    public static final ArrayCodec<ItemRequirement> ARRAY_CODEC = new ArrayCodec<>(CODEC, ItemRequirement[]::new);

    /**
     * Exact item ID or glob pattern (e.g. {@code Block_Stone} or {@code Block_*}).
     */
    public String item;
    public int quantity = 1;

    public ItemRequirement() {
    }

    public ItemRequirement(String item, int quantity) {
        this.item = item;
        this.quantity = quantity;
    }

    @Override
    public String toString() {
        return item + "*" + quantity;
    }
}
