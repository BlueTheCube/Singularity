package singularity;

import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.graphics.g2d.TextureRegion;
import arc.scene.style.Drawable;
import arc.struct.ObjectMap;
import arc.util.Log;
import mindustry.Vars;
import mindustry.ctype.Content;
import mindustry.ctype.ContentType;
import mindustry.ctype.UnlockableContent;
import mindustry.mod.Mod;
import singularity.contents.*;
import singularity.contents.override.OverrideBlocks;
import singularity.contents.override.OverridePlanets;
import singularity.contents.override.OverrideTechThree;
import singularity.core.Init;
import singularity.type.SglCategory;
import singularity.type.SglContentType;
import singularity.world.meta.SglAttribute;
import singularity.contents.SglUnits;
import universecore.UncCore;
import universecore.annotations.Annotations;
import universecore.util.OverrideContentList;

import java.util.Locale;

import static mindustry.game.EventType.*;

@Annotations.ImportUNC(requireVersion = "1.8.9")
public class Singularity extends Mod{
  private static final ContentList[] modContents = new ContentList[]{
      new OtherContents(),//其他内容

      new SglItems(),//物品
      new SglLiquids(),//液体
      new Environments(),//环境块
      new PowerBlocks(),//电力方块
      new NuclearBlocks(),//核能方块
      new CrafterBlocks(),//工厂方块
      new LiquidBlocks(),//物流方块
      new ProductBlocks(),//采集方块
      new DistributeBlocks(),//物流运输方块
      new SglTurrets(),//炮台
      new SglUnits(),//单位相关内容（单位、工厂）
      new DefenceBlocks(),//防御方块

      new SglTechThree(),//科技树
  };
  
  private static final OverrideContentList[] overrideContents = new OverrideContentList[]{
      new OverrideBlocks(),
      new OverridePlanets(),
      new OverrideTechThree(),
  };
  
  public boolean initialized = false;
  
  public Singularity(){
    //加载模组配置数据
    Sgl.config.load();
    Sgl.classes = UncCore.classes.newInstance(Singularity.class);

    Log.info(
       """
       [Singularity] Singularity mod is loading!
       Thanks for your play.
       
       Visit the GitHub project about this mod:
       >\040""" + Sgl.githubProject + " <"
    );

    if(Sgl.config.showModMenuWenLaunch){
      Events.on(ClientLoadEvent.class, e -> {
        Sgl.ui.mainMenu.show();
      });
    }

    if(Sgl.config.modReciprocalContent){
      Events.on(ContentInitEvent.class, e -> {
        Init.reloadContent();
      });
    }

    if(Sgl.config.debugMode) Events.on(WorldLoadEvent.class, e -> Vars.state.rules.infiniteResources = true);
  }
  
  @Override
  public void init(){
    //游戏本体更改初始化
    Init.init();

    //加载全局变量
    Sgl.init();

    initialized = true;

    Sgl.classes.finishGenerate();
    if(Sgl.config.loadInfo) Log.info("[Singularity] mod initialize finished");
  }
  
  @Override
  public void loadContent(){
    //加载属性类型
    SglAttribute.load();
    //加载方块类型
    SglCategory.load();
    //载入所有新内容类型
    SglContentType.load();

    for(ContentList list: Singularity.modContents){
      list.load();
    }

    if(Sgl.config.modReciprocalContent){
      for(OverrideContentList override: Singularity.overrideContents){
        override.load();
      }
    }

    if(Sgl.config.debugMode){
      new DebugBlocks().load();

      for (ContentType type : ContentType.all) {
        for (Content content : Vars.content.getBy(type)) {
          if (content instanceof UnlockableContent uc){
            uc.alwaysUnlocked = true;
          }
        }
      }
    }

    if(Sgl.config.loadInfo) Log.info("[Singularity] mod content load finished");
  }
  
  public static TextureRegion getModAtlas(String name){
    return Core.atlas.find(Sgl.modName + "-" + name);
  }

  public static <T extends Drawable> T getModDrawable(String name){
    return Core.atlas.getDrawable(Sgl.modName + "-" + name);
  }

  public static Fi getInternalFile(String path){
    return Sgl.modFile.child(path);
  }

  public static Fi getDocumentFile(String name){
    return getInternalFile("documents").child(Core.bundle.getLocale().toString()).child(name);
  }

  public static Fi getDocumentFile(Locale locale, String name){
    return getInternalFile("documents").child(locale.toString()).child(name);
  }

  private static final ObjectMap<Fi, String> docCache = new ObjectMap<>();
  public static String getDocument(String name){
    return getDocument(name, true);
  }
  public static String getDocument(String name, boolean cache){
    Fi fi = getDocumentFile(name);
    return cache? docCache.get(fi, fi::readString): fi.readString();
  }
  public static String getDocument(Locale locale, String name){
    return getDocumentFile(locale, name).readString();
  }
}