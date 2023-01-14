package singularity.world.gen;

import arc.graphics.Color;
import arc.math.geom.Vec3;
import mindustry.maps.generators.PlanetGenerator;
import mindustry.world.Block;

public class ForyustGenerator extends PlanetGenerator{
  public float pole, tropic;



  @Override
  public float getHeight(Vec3 position){
    return 0;
  }

  @Override
  public Color getColor(Vec3 position){
    return null;
  }

  protected static abstract class environmentArea{
    public abstract Block[] floor();

  }
}
