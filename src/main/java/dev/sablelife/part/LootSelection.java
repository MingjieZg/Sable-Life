package dev.sablelife.part;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

/** A saved part job draws from its own template snapshot exactly once. */
public final class LootSelection {
    private LootSelection() {}

    public static List<ItemStack> draw(List<ItemStack> templates, int[] chances, int[] weights,
                                       boolean weighted, int rolls, RandomSource random) {
        var result = new ArrayList<ItemStack>();
        if (templates == null) return result;
        if (!weighted) {
            for (int i = 0; i < Math.min(27, templates.size()); i++) {
                var stack = templates.get(i);
                if (!stack.isEmpty() && random.nextInt(100) < value(chances, i)) result.add(stack.copy());
            }
            return result;
        }
        int total = 0;
        for (int i = 0; i < Math.min(27, templates.size()); i++)
            if (!templates.get(i).isEmpty()) total += value(weights, i);
        if (total <= 0) return result;
        for (int roll = 0; roll < Math.clamp(rolls, 1, 27); roll++) {
            int pick = random.nextInt(total);
            for (int i = 0; i < Math.min(27, templates.size()); i++) {
                if (templates.get(i).isEmpty()) continue;
                pick -= value(weights, i);
                if (pick < 0) { result.add(templates.get(i).copy()); break; }
            }
        }
        return result;
    }

    private static int value(int[] values, int index) {
        return index < values.length ? Math.clamp(values[index], 0, 100) : 100;
    }
}
