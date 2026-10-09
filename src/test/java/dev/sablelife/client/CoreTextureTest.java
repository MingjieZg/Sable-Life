package dev.sablelife.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import javax.imageio.ImageIO;
class CoreTextureTest {

 @Test void BothAuthoredDiamondsRemain32PixelTransparentOverlays()throws Exception{for(String file:new String[]{"heart_crystal","redstone_crystal"})try(var stream=getClass().getResourceAsStream("/assets/sablelife/textures/block/"+file+".png")){assertNotNull(stream);var image=ImageIO.read(stream);assertEquals(32,image.getWidth());assertEquals(32,image.getHeight());assertEquals(0,image.getRGB(0,0)>>>24);assertEquals(255,image.getRGB(16,16)>>>24);}}
}
