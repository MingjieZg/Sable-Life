package dev.sablelife.testing;
import dev.sablelife.module.*;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
@GameTestHolder("sablelife_core_ui_tests") @PrefixGameTestTemplate(false)
public final class EditorUiRegressionGameTests {
    @GameTest(template="combat") public static void FreezeAndGrabParticleButtonsOpenEditAndReturn(GameTestHelper h){
        var core=SlashGuardGameTests.core(h);var player=new Author(h.getLevel());player.setPos(ModuleArena.corePosition(core));
        EffectSelectionMenu last=null;VirtualModule module=null;
        for(var type:List.of("freeze","grab")){
            module=core.modules().add(type);var dashboard=new HeartDashboardMenu(1,player.getInventory(),core);
            h.assertTrue(dashboard.action(player,core.modules().revision(),module.id(),"effects","particles",0,""),type+" particle button rejected");
            h.assertTrue(player.containerMenu instanceof EffectSelectionMenu,type+" opened wrong editor");last=(EffectSelectionMenu)player.containerMenu;
            h.assertTrue(last.snapshot().getBoolean("ParticleMode")&&last.action(player,module.id(),"add",0,"sablelife:arc")&&last.action(player,module.id(),"color",0x22AAFF,"sablelife:arc"),type+" particle edit rejected");
            h.assertTrue(module.particleEntry("sablelife:arc").color()==0x22AAFF,"Selection changed wrong module");
            h.assertTrue(last.clickMenuButton(player,0)&&player.containerMenu instanceof HeartDashboardMenu,"Particle editor did not return to dashboard");
            h.assertTrue(!dashboard.action(player,core.modules().revision(),module.id(),"effects","effects",0,"")&&!dashboard.action(player,core.modules().revision(),module.id(),"effects","invalid",0,""),"Visual-only module admitted unsupported effect mode");
        }
        core.tryActivate(player);h.assertTrue(!last.action(player,module.id(),"remove",0,"sablelife:arc"),"Active editor bypassed read-only settings");h.succeed();
    }
    private static final class Author extends net.neoforged.neoforge.common.util.FakePlayer {
        private int next;Author(net.minecraft.server.level.ServerLevel level){super(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"ControlVisualAuthor"));setGameMode(net.minecraft.world.level.GameType.CREATIVE);}
        @Override public java.util.OptionalInt openMenu(net.minecraft.world.MenuProvider provider,java.util.function.Consumer<net.minecraft.network.RegistryFriendlyByteBuf> extra){closeContainer();containerMenu=provider.createMenu(++next,getInventory(),this);return java.util.OptionalInt.of(next);}
    }
}
