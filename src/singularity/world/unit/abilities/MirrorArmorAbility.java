package singularity.world.unit.abilities;

import arc.Core;
import arc.func.Cons;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.Pixmap;
import arc.graphics.g2d.Draw;
import arc.graphics.gl.FrameBuffer;
import arc.math.Mathf;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.graphics.OverlayRenderer;
import mindustry.graphics.Pal;
import mindustry.graphics.Shaders;
import mindustry.input.Binding;
import singularity.graphic.SglDraw;
import singularity.graphic.SglDrawConst;
import singularity.graphic.SglShaders;

import static arc.graphics.g2d.Draw.mixcol;

public class MirrorArmorAbility extends MirrorShieldBase{
  private static final int drawID = SglDraw.nextTaskID();
  private static final int maskID = SglDraw.nextTaskID();

  private static final FrameBuffer drawBuffer = new FrameBuffer();
  private static final FrameBuffer pingpongBuffer = new FrameBuffer();

  @Override
  public String localized() {
    return Core.bundle.get("ability.mirror_armor");
  }

  @Override
  public boolean shouldReflect(Unit unit, Bullet bullet) {
    return bullet.dst(unit) < unit.hitSize + bullet.vel.len()*2*Time.delta;
  }

  @Override
  public void eachNearBullets(Unit unit, Cons<Bullet> cons) {
    float nearRadius = unit.hitSize;
    Groups.bullet.intersect(unit.x - nearRadius, unit.y - nearRadius, nearRadius*2, nearRadius*2, b -> {
      if (unit.team != b.team) cons.get(b);
    });
  }

  @Override
  public void draw(Unit unit) {
    if (unit.shield <= 0) return;

    float z = Draw.z();
    Draw.z(Layer.shields - 2f);
    SglDraw.drawToBuffer(drawID, drawBuffer, unit, b -> {
      SglShaders.mirrorField.waveMix = Tmp.c1.set(SglDrawConst.matrixNet);
      SglShaders.mirrorField.stroke = 1.2f;
      SglShaders.mirrorField.sideLen = 5;
      SglShaders.mirrorField.waveScl = 0.03f;
      SglShaders.mirrorField.gridStroke = 0.6f;
      SglShaders.mirrorField.maxThreshold = 1f;
      SglShaders.mirrorField.minThreshold = 0.7f;
      SglShaders.mirrorField.offset.set(Time.time*0.1f, Time.time*0.1f);

      pingpongBuffer.resize(Core.graphics.getWidth(), Core.graphics.getHeight());
      pingpongBuffer.begin(Color.clear);
      Blending.disabled.apply();
      b.blit(SglShaders.mirrorField);
      Blending.normal.apply();
      pingpongBuffer.end();
    }, e -> {
      Draw.mixcol(Tmp.c1.set(e.team.color).lerp(Color.white, alpha), 1f);
      Draw.scl(1.1f);
      Draw.rect(e.type.shadowRegion, e.x, e.y, e.rotation -90);
      Draw.reset();
    });

    SglDraw.drawTask(maskID, unit, () -> new FrameBuffer(Pixmap.Format.alpha, 2, 2), SglShaders.alphaMask, s -> SglShaders.alphaMask.texture = pingpongBuffer.getTexture(), e -> {
      Draw.color(Color.white, Math.max(alpha, Mathf.absin(6, 0.6f)));
      Draw.scl(1.15f);
      Draw.rect(e.type.shadowRegion, e.x, e.y, e.rotation -90);
      Draw.reset();
    });

    //Yes, this code doesn't do anything, but it won't work properly without this code
    //fu*k off arc GL
    Draw.draw(Draw.z(), () -> {
      pingpongBuffer.resize(1, 1);
      pingpongBuffer.begin(Color.clear);
      pingpongBuffer.end();
      pingpongBuffer.blit(Draw.getShader());
    });

    Draw.z(z);
    Draw.reset();
  }
}
