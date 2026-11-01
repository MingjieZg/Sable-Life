package dev.sablelife.testing;
/** A fresh creative actor avoids another test's survival mode leaking into activation. */
public final class TestActors {
    public static net.neoforged.neoforge.common.util.FakePlayer creative(net.minecraft.server.level.ServerLevel level){
        var player=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"CoreTestAuthor"));
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);return player;
    }
}
