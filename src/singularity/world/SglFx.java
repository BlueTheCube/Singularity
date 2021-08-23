package singularity.world;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import mindustry.entities.Effect;
import mindustry.graphics.Layer;

import static arc.math.Angles.randLenVectors;

public class SglFx{
  public static Effect gasLeak = new Effect(90, e -> {
    if(!(e.data() instanceof Number)) return;
    float param = ((Number)e.data()).floatValue();
    
    Draw.color(e.color, Color.lightGray, e.fin());
    Draw.alpha(param*e.fout());
    
    Draw.z(Layer.bullet);
    
    randLenVectors(e.id, 2, 2f + e.fin()*(param + 2), (x, y) -> {
      Fill.circle(e.x + x, e.y + y, 0.2f+e.fslope()*1.5f);
    });
  });
}
