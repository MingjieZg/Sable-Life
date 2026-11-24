package dev.sablelife.heart;

import dev.sablelife.SableLifeServerConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SurvivalFluidPricesTest {
    @Test
    void pricesTheDefaultLavaAtTheFortyBucketTankCapacity() {
        assertEquals(List.of("minecraft:lava=250"), SableLifeServerConfig.DEFAULT_FLUID_VALUES);
        assertEquals(new SurvivalFluidPrices.Quote(40_000, 10_000),
                SurvivalFluidPrices.quote(40_000, 250, Long.MAX_VALUE));
        assertEquals(new SurvivalFluidPrices.Quote(39_996, 9_999),
                SurvivalFluidPrices.quote(40_000, 250, 9_999));
        assertEquals(new SurvivalFluidPrices.Quote(4, 1),
                SurvivalFluidPrices.quote(7, 250, Long.MAX_VALUE));
    }

    @Test
    void leavesFluidThatCannotEarnAnotherWholeEnergy() {
        assertEquals(new SurvivalFluidPrices.Quote(1000, 25), SurvivalFluidPrices.quote(1000, 25, Long.MAX_VALUE));
        assertEquals(new SurvivalFluidPrices.Quote(40, 1), SurvivalFluidPrices.quote(79, 25, Long.MAX_VALUE));
        assertEquals(new SurvivalFluidPrices.Quote(0, 0), SurvivalFluidPrices.quote(39, 25, Long.MAX_VALUE));
        assertEquals(new SurvivalFluidPrices.Quote(960, 24), SurvivalFluidPrices.quote(1000, 25, 24));
    }

    @Test
    void usesOnlyPricesThatFitInTheRemainingBalance() {
        assertEquals(new SurvivalFluidPrices.Quote(1, 1), SurvivalFluidPrices.quote(20, 1500, 2));
        assertEquals(new SurvivalFluidPrices.Quote(2, 3), SurvivalFluidPrices.quote(20, 1500, 3));
        assertEquals(new SurvivalFluidPrices.Quote(0, 0), SurvivalFluidPrices.quote(20, 2500, 1));
        assertEquals(new SurvivalFluidPrices.Quote(0, 0), SurvivalFluidPrices.quote(20, 25, 0));
        assertEquals(new SurvivalFluidPrices.Quote(0, 0), SurvivalFluidPrices.quote(1000, 0, Long.MAX_VALUE));
    }

    @Test
    void handlesTheLargestSupportedPriceWithoutOverflow() {
        long total = (long) Integer.MAX_VALUE * 1000;
        assertEquals(new SurvivalFluidPrices.Quote(Integer.MAX_VALUE, total),
                SurvivalFluidPrices.quote(Integer.MAX_VALUE, 1_000_000, Long.MAX_VALUE));
        assertEquals(new SurvivalFluidPrices.Quote(Integer.MAX_VALUE - 1, total - 1000),
                SurvivalFluidPrices.quote(Integer.MAX_VALUE, 1_000_000, total - 1));
    }

    @Test
    void agreesWithEveryRepresentableAmountForWholeAndFractionalRates() {
        for (int price : new int[]{1, 25, 250, 333, 999, 1000, 1001, 1499, 2500, 1_000_000}) {
            for (int available = 0; available <= 33; available++) {
                for (long room : new long[]{0, 1, 2, 3, 11, 25, 50, 999, 1000, 1001, 9999, Long.MAX_VALUE}) {
                    int bestAmount = 0;
                    long bestEnergy = 0;
                    for (int amount = 1; amount <= available; amount++) {
                        long energy = (long) amount * price / 1000;
                        if (energy > bestEnergy && energy <= room) {
                            bestAmount = amount;
                            bestEnergy = energy;
                        }
                    }
                    assertEquals(new SurvivalFluidPrices.Quote(bestAmount, bestEnergy),
                            SurvivalFluidPrices.quote(available, price, room));
                }
            }
        }
    }
}
