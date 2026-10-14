package dev.sablelife.testing;
import dev.sablelife.heart.HeartBlockEntity;import dev.sablelife.module.VirtualModule;
final class ArmorTestSupport {
 static void set(HeartBlockEntity heart,VirtualModule module,double modifier){var rules=module.armorRules();boolean added=rules.rows().isEmpty();var row=added?rules.add():rules.rows().getFirst();if(added){int channel=heart.wireless().add();var signal=heart.wireless().channel(channel);signal.set(0,1);signal.set(6,0);signal.set(2,72000);signal.set(3,0);row.edit("channel",channel+1);}row.edit("modifier",modifier);}
}
