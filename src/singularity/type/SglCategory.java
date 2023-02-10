package singularity.type;

import mindustry.type.Category;
import singularity.Sgl;
import universecore.UncCore;

import static singularity.Sgl.modName;

public class SglCategory{
  /**各种生产/传输核能的设备; 消耗核能的工厂应当保持在crafting中*/
  public static Category nuclear;
  /**矩阵网络相关的主要方块*/
  public static Category matrix;
  /**调试方块，在配置game debug mod开启时，在沙盒模式可见*/
  public static Category debugging;
  
  public static void load(){
    nuclear = UncCore.categories.add("nuclear", 5, modName + "-nuclear");
    matrix = UncCore.categories.add("matrix", 6, modName + "-matrix");

    if (Sgl.config.debugMode) debugging = UncCore.categories.add("debugging", modName + "-debugging");
  }
}
