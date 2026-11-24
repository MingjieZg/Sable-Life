package dev.sablelife.heart;

import com.mojang.logging.LogUtils;
import dev.sablelife.SableLifeServerConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Resolves configured prices in Energy per 1000 mB. */
public final class SurvivalFluidPrices {
    private static final int BUCKET = 1000;
    private static final int MAX_PRICE = 1_000_000;
    private static final Quote EMPTY = new Quote(0, 0);
    private static volatile Pricing cached;

    public record Quote(int amount, long energy) {}

    private record TagPrice(TagKey<Fluid> tag, int energy) {}

    private record Pricing(List<String> entries, Map<ResourceLocation, Integer> exact, List<TagPrice> tags) {}

    public static int price(Fluid fluid) {
        if (fluid == Fluids.EMPTY) return 0;
        var id = BuiltInRegistries.FLUID.getKey(fluid);
        if (id == null) return 0;
        var prices = configured();
        var exact = prices.exact().get(id);
        if (exact != null) return exact;
        var holder = BuiltInRegistries.FLUID.wrapAsHolder(fluid);
        for (var match : prices.tags().reversed()) {
            if (holder.is(match.tag())) return match.energy();
        }
        return 0;
    }

    public static Map<Fluid, Integer> catalog() {
        var prices = configured();
        var byFluid = new LinkedHashMap<Fluid, Integer>();
        var fluids = BuiltInRegistries.FLUID;
        for (var tag : prices.tags()) {
            for (var holder : fluids.getTagOrEmpty(tag.tag())) {
                if (holder.value() != Fluids.EMPTY) byFluid.put(holder.value(), tag.energy());
            }
        }
        prices.exact().forEach((id, energy) -> fluids.getOptional(id).ifPresent(fluid -> {
            if (fluid != Fluids.EMPTY) byFluid.put(fluid, energy);
        }));
        var sorted = new LinkedHashMap<Fluid, Integer>();
        byFluid.entrySet().stream()
                .sorted(Comparator.comparing(entry -> fluids.getKey(entry.getKey()).toString()))
                .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return Collections.unmodifiableMap(sorted);
    }

    public static Quote quote(FluidStack fluid, long room) {
        if (fluid.isEmpty() || room <= 0) return EMPTY;
        return quote(fluid.getAmount(), price(fluid.getFluid()), room);
    }

    static Quote quote(int available, int price, long room) {
        if (available <= 0 || price <= 0 || price > MAX_PRICE || room <= 0) return EMPTY;
        long energy = (long) available * price / BUCKET;
        if (energy > room) {
            // Only cap after checking the available value so the boundary stays in long range.
            long amountBelowNextEnergy = ((room + 1) * BUCKET - 1) / price;
            energy = amountBelowNextEnergy * price / BUCKET;
        }
        if (energy <= 0) return EMPTY;
        // Consume only the first mB count that earns this Energy, leaving unused fluid in the tank.
        int amount = (int) ((energy * BUCKET + price - 1) / price);
        return new Quote(amount, energy);
    }

    private static Pricing configured() {
        var entries = SableLifeServerConfig.fluidTokenValues();
        var current = cached;
        if (current != null && current.entries().equals(entries)) return current;
        current = compile(List.copyOf(entries));
        cached = current;
        return current;
    }

    private static Pricing compile(List<String> entries) {
        var exact = new LinkedHashMap<ResourceLocation, Integer>();
        var tags = new ArrayList<TagPrice>();
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            int split = entry.indexOf('=');
            if (split <= 0 || !addPrice(entry.substring(0, split).strip(), entry.substring(split + 1).strip(), exact, tags)) {
                LogUtils.getLogger().warn("Ignoring invalid Survival Heart Core fluid price at survivalTokens.fluidDepositValues entry {}", i + 1);
            }
        }
        return new Pricing(entries, Map.copyOf(exact), List.copyOf(tags));
    }

    private static boolean addPrice(String selector, String value, Map<ResourceLocation, Integer> exact, List<TagPrice> tags) {
        boolean tag = selector.startsWith("#");
        String location = tag ? selector.substring(1) : selector;
        int separator = location.indexOf(':');
        if (separator <= 0 || separator == location.length() - 1) return false;
        var id = ResourceLocation.tryParse(location);
        if (id == null || value.isEmpty() || value.length() > 7) return false;
        int energy;
        try {
            energy = Integer.parseInt(value);
        } catch (NumberFormatException invalid) {
            return false;
        }
        if (energy <= 0 || energy > MAX_PRICE) return false;
        if (tag) tags.add(new TagPrice(TagKey.create(Registries.FLUID, id), energy));
        else exact.put(id, energy);
        return true;
    }

    private SurvivalFluidPrices() {}
}
