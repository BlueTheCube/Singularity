package singularity.ui.fragments.override;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.graphics.gl.FrameBuffer;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.math.WindowedMean;
import arc.math.geom.Vec3;
import arc.scene.Group;
import arc.scene.event.ElementGestureListener;
import arc.scene.event.InputEvent;
import arc.scene.event.Touchable;
import arc.scene.ui.Image;
import arc.scene.ui.ImageButton;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.WidgetGroup;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.content.Planets;
import mindustry.core.Version;
import mindustry.game.EventType;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.graphics.g3d.PlanetParams;
import mindustry.graphics.g3d.PlanetRenderer;
import mindustry.ui.Fonts;
import mindustry.ui.fragments.MenuFragment;
import singularity.Sgl;
import singularity.Singularity;
import singularity.graphic.renders.SglPlanetRender;
import universecore.util.handler.FieldHandler;
import universecore.util.handler.MethodHandler;

import static mindustry.Vars.*;
import static mindustry.gen.Tex.discordBanner;
import static mindustry.gen.Tex.infoBanner;

public class SglMenuFrag extends MenuFragment{
  private final PlanetParams params = new PlanetParams();
  private final Vec3 camRight = new Vec3(1, 0, 0);
  
  private final PlanetRenderer renderer = new SglPlanetRender();
  
  WindowedMean dxMean = new WindowedMean(12), dyMean = new WindowedMean(12);
  float lastDx, lastDy, speed;
  boolean controlling, shown = true, paning = false;
  
  @Override
  public void build(Group parent){
    parent.clear();
    Core.scene.root.removeChild(parent);
    
    WidgetGroup group = new WidgetGroup();
    group.setFillParent(true);
    group.touchable = Touchable.childrenOnly;
    group.visible(() -> state.isMenu());
    
    ui.menuGroup = group;
    Core.scene.add(group);

    if(Sgl.config.mainMenuUniverseBackground){
      params.planet = Planets.sun;
      params.uiAlpha = 0;
      float[] pos = Sgl.config.defaultCameraPos;
      params.camPos.set(pos[0], pos[1], pos[2]);
      Tmp.v31.set(0, 1, 0);
      Tmp.v32.set(0, 0, 1).rotate(Tmp.v31, pos[3]);
      params.camDir.set(1, 0, 0)
          .rotate(Tmp.v31, pos[3])
          .rotate(Tmp.v32, pos[4]);
      params.camUp.set(0, 1, 0)
          .rotate(Tmp.v31, pos[3])
          .rotate(Tmp.v32, pos[4]);
      camRight.set(0, 0, 1)
          .rotate(Tmp.v31, pos[3])
          .rotate(Tmp.v32, pos[4]);

      if(Sgl.config.staticMainMenuBackground){
        FrameBuffer buff = new FrameBuffer();
        buff.resize(Core.graphics.getWidth(), Core.graphics.getHeight());
        buff.begin(Color.clear);
        renderer.render(params);
        Draw.flush();
        buff.end();
        TextureRegion region = new TextureRegion(buff.getTexture());

        group.fill((x, y, w, h) -> Draw.rect(region, x - w/2, y - h/2, w, h));
      }
      else{
        group.fill((x, y, w, h) -> renderer.render(params));

        if(Sgl.config.movementCamera){
          group.fill(t -> {
            t.touchable = Touchable.enabled;

            t.update(() -> {
              if (Core.scene.hit(Core.input.mouseX(), Core.input.mouseY(), true) == t){
                Core.scene.setScrollFocus(t);
              }

              float dx = lastDx/9*Time.delta, dy = lastDy/9*Time.delta;
              if(!controlling){
                params.camDir.rotate(camRight, dy);
                params.camUp.rotate(camRight, dy);

                params.camDir.rotate(params.camUp, dx);
                camRight.rotate(params.camUp, dx);
                lastDx = Mathf.lerpDelta(lastDx, 0, 0.035f);
                lastDy = Mathf.lerpDelta(lastDy, 0, 0.035f);
              }

              speed = Mathf.lerpDelta(speed, 0, 0.05f);
              if (Math.abs(speed) > 0.001f) {
                params.camPos.add(
                    params.camDir.cpy().setLength(speed).scl(speed > 0? 1 : -1));
              }

              if (paning){
                paning = false;
              }
              else {
                dxMean.add(0);
                dyMean.add(0);
              }
            });

            t.scrolled(d -> {
              speed = Mathf.clamp(speed - d*0.25f, -1, 1);
            });

            t.addCaptureListener(new ElementGestureListener(){
              @Override
              public void touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                controlling = true;
                Core.graphics.restoreCursor();

                super.touchDown(event, x, y, pointer, button);
              }

              @Override
              public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button){
                controlling = false;
                lastDx = dxMean.rawMean();
                lastDy = dyMean.rawMean();

                super.touchUp(event, x, y, pointer, button);
              }

              @Override
              public void pan(InputEvent event, float x, float y, float deltaX, float deltaY){
                paning = true;
                params.camDir.rotate(camRight, deltaY/9);
                params.camUp.rotate(camRight, deltaY/9);

                params.camDir.rotate(params.camUp, deltaX/9);
                camRight.rotate(params.camUp, deltaX/9);

                dxMean.add(deltaX);
                dyMean.add(deltaY);
                super.pan(event, x, y, deltaX, deltaY);
              }

              @Override
              public void zoom(InputEvent event, float initialDistance, float distance){
                speed = Mathf.clamp((distance - initialDistance)/450, -1, 1);

                super.zoom(event, initialDistance, distance);
              }
            });
          });
        }
      }
    }
  
    //info icon
    if(mobile){
      if(Sgl.config.mainMenuUniverseBackground){
        group.fill(c -> c.top().left().table(Tex.buttonEdge4, t -> {
          t.touchable = Touchable.enabled;
          t.image().update(i -> i.setDrawable(shown? Icon.eye: Icon.eyeOff));
          t.clicked(() -> shown = !shown);
        }).size(84, 45).name("shown"));
      }

      group.fill(c -> c.bottom().left().button("", new TextButton.TextButtonStyle(){{
        font = Fonts.def;
        fontColor = Color.white;
        up = infoBanner;
      }}, ui.about::show).size(84, 45).name("info"));
      group.fill(c -> c.bottom().right().button(Icon.discord, new ImageButton.ImageButtonStyle(){{
        up = discordBanner;
      }}, ui.discord::show).size(84, 45).name("discord"));
    }else if(becontrol.active()){
      group.fill(c -> c.bottom().right().button("@be.check", Icon.refresh, () -> {
        ui.loadfrag.show();
        becontrol.checkUpdate(result -> {
          ui.loadfrag.hide();
          if(!result){
            ui.showInfo("@be.noupdates");
          }
        });
      }).size(200, 60).name("becheck").update(t -> {
        t.getLabel().setColor(becontrol.isUpdateAvailable() ? Tmp.c1.set(Color.white).lerp(Pal.accent, Mathf.absin(5f, 1f)) : Color.white);
      }));
    }
  
    String versionText = ((Version.build == -1) ? "[#fc8140aa]" : "[#ffffffba]") + Version.combined(),
        modVersionText = "UniverseCore:" + Sgl.libVersion + " Singularity:" + Sgl.modVersion;
    group.fill(t -> {
      t.defaults().top();
      t.visibility = () -> shown;
      Image button = new Image(Singularity.getModAtlas("logo"));
      button.clicked(Sgl.ui.mainMenu::show);
      Cell<Image> i = t.top().add(button).size(940, 270);
      t.row();
      t.add(versionText).padTop(4);
      t.row();
      t.add(modVersionText).padTop(2);

      Runnable r = () -> {
        float scl = Math.min(Core.graphics.getHeight()/3.4f/ Scl.scl(270), 1);
        i.size(940*scl, 270*scl);
        t.invalidateHierarchy();
      };
      r.run();
      Events.on(EventType.ResizeEvent.class, e -> {
        r.run();
      });
    });

    group.fill(c -> {
      c.visibility = () -> shown;
      FieldHandler.setValueTemp(ui.menufrag, "container", c);
      c.name = "menu container";
      MethodHandler.invokeTemp(this, mobile? "buildMobile": "buildDesktop");
      arc.Events.on(mindustry.game.EventType.ResizeEvent.class, event -> MethodHandler.invokeTemp(this, mobile? "buildMobile": "buildDesktop"));
    });
  }
}
