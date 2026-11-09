package dev.sablelife.client;

import dev.sablelife.module.ModuleFields;
import dev.sablelife.module.VirtualModule;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import java.util.List;

/** A field's hover describes that field; full module mechanics remain in the manual. */
final class ModulePropertyHelp {
    static Component label(VirtualModule m,String key){return Component.translatable("dashboard.sablelife."+(m.type().equals("surface_field")&&key.equals("density")?"fieldParticleDensity":m.type().equals("summoned_weapon")&&key.equals("projectile")?"summonItem":m.type().equals("summoned_weapon")&&key.equals("displaySize")?"summonScale":m.type().equals("freeze")&&key.equals("fieldAngle")?"freezeAngle":m.type().equals("grab")&&key.equals("particles")?"grabParticles":m.type().equals("freeze")&&key.equals("particles")?"freezeParticles":m.type().equals("surface_field")&&key.equals("target")?"fieldTarget":m.type().equals("surface_field")&&key.equals("burn")?"fieldBurnDuration":m.type().equals("slash")&&key.equals("burn")?"slashBurnDuration":m.type().equals("slash")&&key.equals("flightSound")?"slashReleaseSound":m.type().equals("slash")&&key.equals("range")?"slash_range":m.type().equals("quake") && key.equals("quakeRange") ? m.value("quakeMode") == 0 ? "quake_length" : "quake_radius" : m.type().equals("ground_strike") && m.value("strikeMode") == 3 && List.of("strikeRange", "waveDelay").contains(key) ? "random_" + key : key.equals("speed") && (m.type().equals("gun") || m.type().equals("mortar")) ? m.type() + "_speed"
                    : key.equals("speed") && m.type().equals("homing") ? "homing_speed" : key.equals("particles") && List.of("arrow", "gun", "mortar", "homing").contains(m.type()) ? "flight_particles"
                    : key.equals("arrows") && m.type().equals("gun") ? "bullets" : key.equals("interval") && m.type().equals("laser") ? "burstInterval" : key));}
    static Component text(VirtualModule module,ModuleFields.Field field){
        String prefix="dashboard.sablelife.property_help.",key=field.key();
        String specific=prefix+module.type()+"."+key,shared=prefix+key;
        Component detail;
        if(I18n.exists(specific))detail=Component.translatable(specific);
        else if(I18n.exists(shared))detail=Component.translatable(shared);
        else if(module.type().equals("signal_turn")&&key.endsWith("Source"))detail=Component.translatable(prefix+"turn_input");
        else if(module.type().equals("signal_turn")&&key.endsWith("Channel"))detail=Component.translatable(prefix+"turn_channel");
        else if(key.endsWith("Sound")||field.choices().equals(java.util.List.of("library")))detail=Component.translatable(prefix+"sound");
        else if(key.endsWith("Particles")||field.choices().equals(java.util.List.of("particles")))detail=Component.translatable(prefix+"particles");
        else if(key.endsWith("Color"))detail=Component.translatable(prefix+"color");
        else if(key.endsWith("Volume"))detail=Component.translatable(prefix+"volume");
        else if(key.endsWith("Radius"))detail=Component.translatable(prefix+"radius");
        else if(field.choices().isEmpty())detail=Component.translatable(prefix+"bounds",number(field.min()/field.scale()),number(field.max()/field.scale()));
        else detail=Component.translatable(prefix+"choice");
        return Component.empty().append(label(module,key).copy().withStyle(net.minecraft.ChatFormatting.BOLD))
                .append("\n").append(detail);
    }
    private static String number(double value){return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();}
    static Tooltip tooltip(VirtualModule module,ModuleFields.Field field){return Tooltip.create(text(module,field));}
    private ModulePropertyHelp(){}
}
