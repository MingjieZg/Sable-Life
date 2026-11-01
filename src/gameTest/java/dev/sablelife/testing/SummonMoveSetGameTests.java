package dev.sablelife.testing;

import dev.sablelife.module.*;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.*;
import java.util.List;

@GameTestHolder("sablelife_summon_tests") @PrefixGameTestTemplate(false)
public final class SummonMoveSetGameTests {
    @GameTest(template="combat",batch="summon_move_set",timeoutTicks=85)
    public static void MoveSetSchedulesAndRetriggersIndependentWeapons(GameTestHelper h) {
        var c=SlashGuardGameTests.core(h);
        var pin=c.modules().add("hover_anchor");
        pin.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.UP)));pin.edit("hoverRotation",1,"");
        var attack=c.modules().add("gun");attack.edit("enabled",0,"");attack.edit("interval",1000,"");
        var summon=c.modules().add("summoned_weapon");summon.summonAttack(attack.id());
        summon.anchors(List.of(ModuleAnchor.create(c,c.getBlockPos(),Direction.EAST)));
        summon.edit("summonPosition",1,"");summon.edit("summonDuration",40,"");
        var table=c.modules().add("move_set");var moves=table.moveSet();moves.edit("add",0,"",c.modules());
        h.assertTrue(moves.edit("action_add",0,"0|"+summon.id(),c.modules()),"Summoned weapon missing from Move Set picker/allowlist");
        h.assertTrue(summon.value("trigger")==4&&VirtualModule.load(summon.save()).value("trigger")==4,"Move Set trigger rejected or lost on load");
        moves.edit("action_start",.2,"0|0",c.modules());moves.edit("action_duration",.15,"0|0",c.modules());
        var move=moves.get(0);move.config().edit("target",1,"");move.config().edit("dispatchCooldown",18,"");
        move.config().edit("dispatchBusy",7,"");move.config().edit("dispatchRest",0,"");move.config().edit("dispatchRepeat",1,"");
        h.assertTrue(ModuleMoveSet.status(move,c).equals("ready"),"Marked summon action is unavailable");
        var cow=EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setNoGravity(true);
        cow.setPos(c.getBlockPos().getCenter().add(0,0,3));h.getLevel().addFreshEntity(cow);
        c.tryActivate(TestActors.creative(h.getLevel()));c.modules().tick(c);
        h.assertTrue(c.modules().summonedWeapons.frames(summon.id()).isEmpty(),"Summoned before action start");
        h.runAfterDelay(9,()->h.assertTrue(c.modules().summonedWeapons.frames(summon.id()).size()==1,"Action window did not spawn exactly one persistent weapon"));
        h.runAfterDelay(25,()->{
            h.assertTrue(c.modules().summonedWeapons.frames(summon.id()).size()==2,"Second move activation failed to append weapon");
            table.edit("enabled",0,"");
        });
        h.runAfterDelay(47,()->h.assertTrue(c.modules().summonedWeapons.frames(summon.id()).size()==1,"Trigger loss removed weapons or lifetimes are not independent"));
        h.runAfterDelay(67,()->{
            h.assertTrue(c.modules().summonedWeapons.frames(summon.id()).isEmpty(),"Weapon exceeded configured lifetime");
            c.forceDeath();cow.discard();h.succeed();
        });
    }
}
