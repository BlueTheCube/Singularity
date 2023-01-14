package singularity.graphic;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.graphics.gl.FrameBuffer;
import arc.graphics.gl.Shader;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Rect;
import arc.math.geom.Vec2;
import arc.math.geom.Vec3;
import arc.struct.ObjectMap;
import arc.util.Nullable;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.world.Tile;
import singularity.util.func.Floatc3;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

public class SglDraw{
  private static int blooming = -1;
  private static final Rect rect = new Rect();

  static final Vec2 v1 = new Vec2(), v2 = new Vec2(), v3 = new Vec2(), v4 = new Vec2(), v5 = new Vec2(),
      v6 = new Vec2(), v7 = new Vec2(), v8 = new Vec2(), v9 = new Vec2(), v10 = new Vec2();
  static final Vec3 v31 = new Vec3(), v32 = new Vec3(), v33 = new Vec3(), v34 = new Vec3(), v35 = new Vec3(),
      v36 = new Vec3(), v37 = new Vec3(), v38 = new Vec3(), v39 = new Vec3(), v310 = new Vec3();

  static final Color c1 = new Color(), c2 = new Color(), c3 = new Color(), c4 = new Color(), c5 = new Color(),
      c6 = new Color(), c7 = new Color(), c8 = new Color(), c9 = new Color(), c10 = new Color();

  private static final ObjectMap<String, DrawTask> drawTasks = new ObjectMap<>();
  private static final ObjectMap<String, FrameBuffer> taskBuffer = new ObjectMap<>();

  static {
    Events.run(EventType.Trigger.drawOver, () -> {
      if(Vars.renderer.bloom != null){
        Draw.draw(Layer.block + 0.02f, () -> {
          Vars.renderer.bloom.capture();
          Vars.renderer.bloom.capturePause();
        });
        Draw.draw(Layer.blockOver - 0.02f, () -> Vars.renderer.bloom.render());

        Draw.draw(Layer.flyingUnit + 0.02f, () -> {
          Vars.renderer.bloom.capture();
          Vars.renderer.bloom.capturePause();
        });
        Draw.draw(Layer.overlayUI - 0.02f, () -> Vars.renderer.bloom.render());
      }
    });
  }

  public static void removeTaskCache(String name){
    drawTasks.remove(name);
    taskBuffer.remove(name);
  }

  /**发布缓存的任务并在首次发布时的z轴时进行绘制，传递的一些参数只在初始化时起了效果，之后都被选择性的无视了
   *
   * @param taskName 任务的标识名称，用于区分任务缓存
   * @param target 传递给绘制任务的数据目标，这是为了优化lambda，传递给lambda的数据对象请使用复用对象
   *               <p>避免从描述绘制任务的lambda表达式访问表达式之外的局部变量，这会产生大量的一次性对象，产生不必要的堆占用引起频繁GC影响性能
   * @param drawFirst <strong>选择性的参数，若任务已初始化，这个参数无效</strong>，用于声明这个任务组在执行前要进行的操作
   * @param drawLast <strong>选择性的参数，若任务已初始化，这个参数无效</strong>，用于声明这个任务组在完成主绘制后要执行的操作
   * @param draw 添加到任务缓存的绘制任务，即此次绘制的操作*/
  public static <T> void drawTask(String taskName, T target, DrawAcceptor<T> drawFirst, DrawAcceptor<T> drawLast, DrawAcceptor<T> draw){
    DrawTask task = drawTasks.get(taskName, DrawTask::new);
    if (!task.init){
      task.defaultFirstTask = drawFirst;
      task.defaultLastTask = drawLast;
      task.defaultTarget = target;
      Draw.draw(Draw.z(), task::flush);
      task.init = true;
    }
    task.addTask(target, draw);
  }

  /**发布缓存的任务并在首次发布时的z轴时进行绘制，传递的一些参数只在初始化时起了效果，之后都被选择性的无视了
   *
   * @param taskName 任务的标识名称，用于区分任务缓存
   * @param target 传递给绘制任务的数据目标，这是为了优化lambda，传递给lambda的数据对象请使用复用对象
   *               <p>避免从描述绘制任务的lambda表达式访问表达式之外的局部变量，这会产生大量的一次性对象，产生不必要的堆占用引起频繁GC影响性能
   * @param shader <strong>选择性的参数，若任务已初始化，这个参数无效</strong>，在这组任务绘制时使用的着色器
   * @param draw 添加到任务缓存的绘制任务，即此次绘制的操作*/
  public static <T> void drawTask(String taskName, T target, Shader shader, DrawAcceptor<T> draw){
    drawTask(taskName, target, e -> {
      FrameBuffer buffer = taskBuffer.get(taskName, FrameBuffer::new);
      buffer.resize(Core.graphics.getWidth(), Core.graphics.getHeight());
      buffer.begin(Color.clear);
    }, e -> {
      FrameBuffer buffer = taskBuffer.get(taskName);
      buffer.end();
      buffer.blit(shader);
    }, draw);
  }

  /**发布缓存的任务并在首次发布时的z轴时进行绘制，传递的一些参数只在初始化时起了效果，之后都被选择性的无视了
   * <p><strong>如果这个方法的调用频率非常高，同时描述绘制行为的lambda表达式需要访问局部变量，那么为了优化堆占用，请使用{@link SglDraw#drawTask(String, Object, Shader, DrawAcceptor)}</strong>
   *
   * @param taskName 任务的标识名称，用于区分任务缓存
   * @param shader <strong>选择性的参数，若任务已初始化，这个参数无效</strong>，在这组任务绘制时使用的着色器
   * @param draw 添加到任务缓存的绘制任务，即此次绘制的操作*/
  public static void drawTask(String taskName, Shader shader, Runnable draw){
    drawTask(taskName, null, shader, e -> draw.run());
  }

  /**发布缓存的任务并在首次发布时的z轴时进行绘制
   *
   * @param taskName 任务的标识名称，用于区分任务缓存
   * @param target 传递给绘制任务的数据目标，这是为了优化lambda，传递给lambda的数据对象请使用复用对象
   *               <p>避免从描述绘制任务的lambda表达式访问表达式之外的局部变量，这会产生大量的一次性对象，产生不必要的堆占用引起频繁GC影响性能
   * @param draw 添加到任务缓存的绘制任务，即此次绘制的操作*/
  public static <T> void drawTask(String taskName, T target, DrawAcceptor<T> draw){
    DrawTask task = drawTasks.get(taskName, DrawTask::new);
    if (!task.init){
      Draw.draw(Draw.z(), task::flush);
      task.init = true;
    }
    task.addTask(target, draw);
  }

  /**发布缓存的任务并在首次发布时的z轴时进行绘制
   * <p><strong>如果这个方法的调用频率非常高，同时描述绘制行为的lambda表达式需要访问局部变量，那么为了优化堆占用，请使用{@link SglDraw#drawTask(String, Object, DrawAcceptor)}</strong>
   *
   * @param taskName 任务的标识名称，用于区分任务缓存
   * @param draw 添加到任务缓存的绘制任务，即此次绘制的操作*/
  public static void drawTask(String taskName, Runnable draw){
    DrawTask task = drawTasks.get(taskName, DrawTask::new);
    if (!task.init){
      Draw.draw(Draw.z(), task::flush);
      task.init = true;
    }
    task.addTask(null, e -> draw.run());
  }

  public static void drawDistortion(String taskName, Distortion distortion, DrawAcceptor<Distortion> draw){
    drawTask(taskName, distortion, e -> {
      e.resize();
      e.capture();
    }, Distortion::render, draw);
  }

  public static void drawTransform(float originX, float originY, Vec2 vec, float rotate, Floatc3 draw){
    drawTransform(originX, originY, 0, vec.x, vec.y, rotate, draw);
  }

  public static void drawTransform(float originX, float originY, float dx, float dy, float rotate, Floatc3 draw){
    drawTransform(originX, originY, 0, dx, dy, rotate, draw);
  }

  public static void drawTransform(float originX, float originY, float originAngle, float dx, float dy, float rotate, Floatc3 draw){
    v1.set(dx, dy).rotate(rotate);
    draw.get(originX + v1.x, originY + v1.y, originAngle + rotate);
  }

  public static boolean clipDrawable(float x, float y, float clipSize){
    Core.camera.bounds(rect);
    return rect.overlaps(x - clipSize/2, y - clipSize/2, clipSize, clipSize);
  }

  public static void drawLink(Tile origin, Tile other, TextureRegion linkRegion, TextureRegion capRegion, float lerp){
    drawLink(origin, origin.block() != null? origin.block().offset: 0, 0,
        other, other.block() != null? other.block().offset: 0, 0, linkRegion, capRegion, lerp);
  }
  
  public static void drawLink(Tile origin, float blockOffsetOri, float offsetO, Tile other, float blockOffsetOth, float offset,
                              TextureRegion linkRegion, @Nullable TextureRegion capRegion, float lerp){
    v1.set(other.drawx() - origin.drawx(), other.drawy() - origin.drawy()).setLength(offsetO);
    float ox = origin.worldx() + blockOffsetOri + v1.x;
    float oy = origin.worldy() + blockOffsetOri + v1.y;
    v1.scl(-1).setLength(offset);
    float otx = other.worldx() + blockOffsetOth + v1.x;
    float oty = other.worldy() + blockOffsetOth + v1.y;

    v1.set(otx, oty).sub(ox, oy);
    v2.set(v1).scl(lerp);
    v3.set(0, 0);
    
    if(capRegion != null){
      v3.set(v1).setLength(capRegion.width/4f);
      Draw.rect(capRegion, ox + v3.x/2, oy + v3.y/2, v2.angle());
      Draw.rect(capRegion, ox + v2.x - v3.x/2, oy + v2.y - v3.y/2, v2.angle() + 180);
    }

    Lines.stroke(8);
    Lines.line(linkRegion,
        ox + v3.x, oy + v3.y,
        ox + v2.x - v3.x,
        oy + v2.y - v3.y,
        false);
  }

  public static void drawLightEdge(float x, float y, float vertLength, float vertWidth, float horLength, float horWidth){
    Color color = Draw.getColor();
    drawDiamond(x, y, vertLength, vertWidth, 90, color, color);
    drawDiamond(x, y, horLength, horWidth, 0, color, color);
  }

  public static void drawLightEdge(float x, float y, float vertLength, float vertWidth, float horLength, float horWidth, float rotation){
    Color color = Draw.getColor();
    drawDiamond(x, y, vertLength, vertWidth, 90 + rotation, color, color);
    drawDiamond(x, y, horLength, horWidth, 0 + rotation, color, color);
  }

  public static void drawLightEdge(float x, float y, float vertLength, float vertWidth, float horLength, float horWidth, float rotation, float gradientAlpha){
    drawLightEdge(x, y, vertLength, vertWidth, horLength, horWidth, rotation, Tmp.c1.set(Draw.getColor()).a(gradientAlpha));
  }

  public static void drawLightEdge(float x, float y, float vertLength, float vertWidth, float horLength, float horWidth, float rotation, Color gradientTo){
    Color color = Draw.getColor();
    drawDiamond(x, y, vertLength, vertWidth, 90 + rotation, color, gradientTo);
    drawDiamond(x, y, horLength, horWidth, 0 + rotation, color, gradientTo);
  }

  public static void drawLightEdge(float x, float y, Color color, float vertLength, float vertWidth, float rotationV, Color gradientV,
                                   float horLength, float horWidth, float rotationH, Color gradientH){
    drawDiamond(x, y, vertLength, vertWidth, 90 + rotationV, color, gradientV);
    drawDiamond(x, y, horLength, horWidth, rotationH, color, gradientH);
  }

  public static void drawLightEdge(float x, float y, float vertLength, float vertWidth, float rotationV, float gradientV,
                                   float horLength, float horWidth, float rotationH, float gradientH){
    Color color = Draw.getColor(), gradientColorV = color.cpy().a(gradientV), gradientColorH = color.cpy().a(gradientH);
    drawDiamond(x, y, vertLength, vertWidth, 90 + rotationV, color, gradientColorV);
    drawDiamond(x, y, horLength, horWidth, rotationH, color, gradientColorH);
  }

  public static void drawDiamond(float x, float y, float length, float width, float rotation){
    drawDiamond(x, y, length, width, rotation, Draw.getColor());
  }

  public static void drawDiamond(float x, float y, float length, float width, float rotation, float gradientAlpha){
    drawDiamond(x, y, length, width, rotation, Draw.getColor(), gradientAlpha);
  }

  public static void drawDiamond(float x, float y, float length, float width, float rotation, Color color){
    drawDiamond(x, y, length, width, rotation, color, 1);
  }

  public static void drawDiamond(float x, float y, float length, float width, float rotation, Color color, float gradientAlpha){
    drawDiamond(x, y, length, width, rotation, color, Tmp.c1.set(color).a(gradientAlpha));
  }

  public static void drawDiamond(float x, float y, float length, float width, float rotation, Color color, Color gradient){
    v1.set(length/2, 0).rotate(rotation);
    v2.set(0, width/2).rotate(rotation);

    float originColor = color.toFloatBits();
    float gradientColor = gradient.toFloatBits();

    Fill.quad(x, y, originColor, x, y, originColor,
        x + v1.x, y + v1.y, gradientColor,
        x + v2.x, y + v2.y, gradientColor);
    Fill.quad(x, y, originColor, x, y, originColor,
        x + v1.x, y + v1.y, gradientColor,
        x - v2.x, y - v2.y, gradientColor);
    Fill.quad(x, y, originColor, x, y, originColor,
        x - v1.x, y - v1.y, gradientColor,
        x + v2.x, y + v2.y, gradientColor);
    Fill.quad(x, y, originColor, x, y, originColor,
        x - v1.x, y - v1.y, gradientColor,
        x - v2.x, y - v2.y, gradientColor);
  }

  public static void drawCrystal(float x, float y, float length, float width, float height, float centOffX, float centOffY, float edgeStoke,
                                 float edgeLayer, float botLayer, float crystalRotation, float rotation, Color color, Color edgeColor){
    v31.set(length/2, 0, 0);
    v32.set(0, width/2, 0).rotate(Vec3.X, crystalRotation);
    v33.set(centOffX, centOffY, height/2).rotate(Vec3.X, crystalRotation);

    float w1, w2;
    float widthReal = Math.max(w1 = Math.abs(v32.y), w2 = Math.abs(v33.y));

    v31.rotate(Vec3.Z, -rotation);
    v32.rotate(Vec3.Z, -rotation);
    v33.rotate(Vec3.Z, -rotation);

    float z = Draw.z();
    Draw.z(botLayer);
    Draw.color(color);

    float mx = Angles.trnsx(rotation + 90, widthReal), my = Angles.trnsy(rotation + 90, widthReal);
    Fill.quad(
        x + v31.x, y + v31.y,
        x + mx, y + my,
        x - v31.x, y - v31.y,
        x - mx, y - my
    );

    Lines.stroke(edgeStoke, edgeColor);
    crystalEdge(x, y, w1 >= widthReal, v32.z > v33.z, edgeLayer, botLayer, v32);
    crystalEdge(x, y, w2 >= widthReal, v33.z > v32.z, edgeLayer, botLayer, v33);

    Draw.z(z);
  }

  private static void crystalEdge(float x, float y, boolean w, boolean r, float edgeLayer, float botLayer, Vec3 v){
    Draw.z(r || w? edgeLayer: botLayer - 0.01f);
    Lines.line(
        x + v.x, y + v.y,
        x + v31.x, y + v31.y
    );
    Lines.line(
        x + v.x, y + v.y,
        x - v31.x, y - v31.y
    );
    Draw.z(!r || w? edgeLayer: botLayer - 0.01f);
    Lines.line(
        x - v.x, y - v.y,
        x + v31.x, y + v31.y
    );
    Lines.line(
        x - v.x, y - v.y,
        x - v31.x, y - v31.y
    );
  }

  public static void drawHaloPart(float x, float y, float width, float len, float rotate){
    drawHaloPart(x, y, width*0.2f, len*0.8f, width, len*0.2f, rotate);
  }

  public static void drawHaloPart(float x, float y, float interWidth, float interLen, float width, float len, float rotate){
    Drawf.tri(x, y, interWidth, interLen, rotate + 180);
    Drawf.tri(x, y, width, len, rotate);
  }

  public static void gradientTri(float x, float y, float length, float width, float rotation){
    gradientTri(x, y, length, width, rotation, Draw.getColor());
  }

  public static void gradientTri(float x, float y, float length, float width, float rotation, float gradientAlpha){
    gradientTri(x, y, length, width, rotation, Draw.getColor(), gradientAlpha);
  }

  public static void gradientTri(float x, float y, float length, float width, float rotation, Color color){
    gradientTri(x, y, length, width, rotation, color, color);
  }

  public static void gradientTri(float x, float y, float length, float width, float rotation, Color color, float gradientAlpha){
    gradientTri(x, y, length, width, rotation, color, Tmp.c1.set(color).a(gradientAlpha));
  }

  public static void gradientTri(float x, float y, float length, float width, float rotation, Color color, Color gradient){
    v1.set(length/2, 0).rotate(rotation);
    v2.set(0, width/2).rotate(rotation);

    float originColor = color.toFloatBits();
    float gradientColor = gradient.toFloatBits();

    Fill.quad(x, y, originColor, x, y, originColor,
        x + v1.x, y + v1.y, gradientColor,
        x + v2.x, y + v2.y, gradientColor);
    Fill.quad(x, y, originColor, x, y, originColor,
        x + v1.x, y + v1.y, gradientColor,
        x - v2.x, y - v2.y, gradientColor);
  }

  public static void gradientCircle(float x, float y, float radius, Color gradientColor){
    gradientCircle(x, y, radius, x, y, gradientColor);
  }

  public static void gradientCircle(float x, float y, float radius, float gradientAlpha){
    gradientCircle(x, y, radius, x, y, Tmp.c1.set(Draw.getColor()).a(gradientAlpha));
  }

  public static void gradientCircle(float x, float y, float radius, float offset, float gradientAlpha){
    gradientCircle(x, y, radius, x, y, offset, Tmp.c1.set(Draw.getColor()).a(gradientAlpha));
  }

  public static void gradientCircle(float x, float y, float radius, float offset, Color gradientColor){
    gradientCircle(x, y, radius, x, y, offset, gradientColor);
  }

  public static void gradientCircle(float x, float y, float radius, float gradientCenterX, float gradientCenterY, Color gradientColor){
    gradientCircle(x, y, radius, gradientCenterX, gradientCenterY, -radius, gradientColor);
  }

  public static void gradientCircle(float x, float y, float radius, float gradientCenterX, float gradientCenterY, float offset, Color gradientColor){
    gradientPoly(x, y, Lines.circleVertices(radius), radius, Draw.getColor(), gradientCenterX, gradientCenterY, offset, gradientColor, 0);
  }

  public static void gradientSqrt(float x, float y, float radius, float rotation, float offset, Color gradientColor){
    gradientSqrt(x, y, radius, x, y, offset, gradientColor, rotation);
  }

  public static void gradientSqrt(float x, float y, float radius, float gradientCenterX, float gradientCenterY, float offset, Color gradientColor, float rotation){
    gradientPoly(x, y, 4, 1.41421f*(radius/2), Draw.getColor(), gradientCenterX, gradientCenterY, offset, gradientColor, rotation);
  }

  public static void gradientPoly(float x, float y, int edges, float radius, Color color, float gradientCenterX, float gradientCenterY, float offset, Color gradientColor, float rotation){
    v1.set(gradientCenterX - x, gradientCenterY - y).rotate(rotation);
    gradientCenterX = x + v1.x;
    gradientCenterY = y + v1.y;

    v1.set(1, 0).setLength(radius).rotate(rotation);
    float step = 360f/edges;

    float lastX = -1, lastY = -1;
    float lastGX = -1, lastGY = -1;

    for(int i = 0; i <= edges; i++){
      if(i == edges) v1.setAngle(rotation);
      v2.set(v1).sub(gradientCenterX - x, gradientCenterY - y);

      if(lastX != -1){
        v3.set(v2).setLength(offset).scl(offset < 0? -1: 1);
        v4.set(lastGX, lastGY).setLength(offset).scl(offset < 0? -1: 1);
        Fill.quad(
            lastX, lastY, color.toFloatBits(),
            x + v1.x, y + v1.y, color.toFloatBits(),
            gradientCenterX + v2.x + v3.x, gradientCenterY + v2.y + v3.y, gradientColor.toFloatBits(),
            gradientCenterX + lastGX + v4.x, gradientCenterY + lastGY + v4.y, gradientColor.toFloatBits()
        );
      }

      lastX = x + v1.x;
      lastY = y + v1.y;
      lastGX = v2.x;
      lastGY = v2.y;
      v1.rotate(step);
    }
  }

  public static void startBloom(float z){
    if(!(z > Layer.block + 0.02f && z < Layer.blockOver - 0.02f) && !(z > Layer.flyingUnit + 0.02f && z < Layer.overlayUI - 0.02f))
      throw new IllegalArgumentException("bloom z should be 30 < z < 35 or 115 < z < 120, given " + "z: " + z);

    if(blooming >= 0) throw new IllegalStateException("current is blooming, please endBloom");
    blooming = z < Layer.blockOver - 0.02f? 0: 1;
    Draw.z(z);
    if(Vars.renderer.bloom != null) Draw.draw(z, () -> Vars.renderer.bloom.captureContinue());
  }

  public static void endBloom(){
    if(blooming == -1) throw new IllegalStateException("current is not blooming, please statBloom");
    if(Vars.renderer.bloom != null) Draw.draw(Draw.z(), () -> Vars.renderer.bloom.capturePause());
    Draw.z(blooming == 0? Layer.blockOver: Layer.overlayUI);
    blooming = -1;
  }

  public static void dashCircle(float x, float y, float radius){
    dashCircle(x, y, radius, 0);
  }

  public static void dashCircle(float x, float y, float radius, float rotate){
    dashCircle(x, y, radius, 1.25f, 6, 180, rotate);
  }

  public static void dashCircle(float x, float y, float radius, int dashes, float totalDashDeg, float rotate){
    dashCircle(x, y, radius, 1.25f, dashes, totalDashDeg, rotate);
  }

  public static void dashCircle(float x, float y, float radius, float scaleFactor, int dashes, float totalDashDeg, float rotate){
    int sides = 10 + (int)(radius * scaleFactor);
    if(sides % 2 == 1) sides++;

    v1.set(0, 0);
    float per = 360f / sides;

    float rem = 360 - totalDashDeg;
    float dashDeg = totalDashDeg/dashes;
    float empDeg = rem/dashes;

    for(int i = 0; i < sides; i++){
      if(i*per%(dashDeg+empDeg) > dashDeg) continue;

      v1.set(radius, 0).setAngle(rotate + per * i + 90);
      float x1 = v1.x;
      float y1 = v1.y;

      v1.set(radius, 0).setAngle(rotate + per * (i + 1) + 90);

      Lines.line(x1 + x, y1 + y, v1.x + x, v1.y + y);
    }
  }

  public static void drawLaser(float originX, float originY, float otherX, float otherY, TextureRegion linkRegion,
                               TextureRegion capRegion, float stoke){
    float rot = Mathf.angle(otherX - originX, otherY - originY);

    if(capRegion != null){
      Draw.rect(capRegion, otherX, otherY, rot);
    }

    Lines.stroke(stoke);
    Lines.line(linkRegion, originX, originY, otherX, otherY, capRegion != null);
  }

  public static void gradientLine(float originX, float originY, float targetX, float targetY, Color origin, Color target, int gradientDir){
    float halfWidth = Lines.getStroke()/2;
    v1.set(halfWidth, 0).rotate(Mathf.angle(targetX - originX, targetY - originY) + 90);

    float c1, c2, c3, c4;
    switch(gradientDir){
      case 0 -> {
        c1 = origin.toFloatBits();
        c2 = origin.toFloatBits();
        c3 = target.toFloatBits();
        c4 = target.toFloatBits();
      }
      case 1 -> {
        c1 = target.toFloatBits();
        c2 = origin.toFloatBits();
        c3 = origin.toFloatBits();
        c4 = target.toFloatBits();
      }
      case 2 -> {
        c1 = target.toFloatBits();
        c2 = target.toFloatBits();
        c3 = origin.toFloatBits();
        c4 = origin.toFloatBits();
      }
      case 3 -> {
        c1 = origin.toFloatBits();
        c2 = target.toFloatBits();
        c3 = target.toFloatBits();
        c4 = origin.toFloatBits();
      }
      default -> {throw new IllegalArgumentException("gradient rotate must be 0 to 3, currently: " + gradientDir);}
    }

    Fill.quad(
      originX + v1.x, originY + v1.y, c1,
      originX - v1.x, originY - v1.y, c2,
      targetX - v1.x, targetY - v1.y, c3,
      targetX + v1.x, targetY + v1.y, c4
    );
  }

  public static void oval(float x, float y, float horLen, float vertLen, float rotation, float offset, Color gradientColor){
    int sides = Lines.circleVertices(Math.max(horLen, vertLen));
    float step = 360f/sides;

    float c1 = Draw.getColor().toFloatBits();
    float c2 = gradientColor.toFloatBits();

    for (int i = 0; i < sides; i++) {
      float dx = horLen*Mathf.cosDeg(i*step);
      float dy = vertLen*Mathf.sinDeg(i*step);
      float dx1 = horLen*Mathf.cosDeg((i + 1)*step);
      float dy1 = vertLen*Mathf.sinDeg((i + 1)*step);

      v1.set(dx, dy).setAngle(rotation);
      v2.set(dx1, dy1).setAngle(rotation);
      v3.set(v1).setLength(v1.len() + offset);
      v4.set(v2).setLength(v2.len() + offset);

      Fill.quad(
          x + v1.x, y + v1.y, c1,
          x + v2.x, y + v2.y, c1,
          x + v4.x, y + v4.y, c2,
          x + v3.x, y + v3.y, c2
      );
    }
  }

  public static void drawRectAsCylindrical(float x, float y, float rowWidth, float rowHeight,
                                           float cycRadius, float cycRotation, float rotation){
    drawRectAsCylindrical(x, y, rowWidth, rowHeight, cycRadius, cycRotation, rotation, Draw.getColor());
  }

  public static void drawRectAsCylindrical(float x, float y, float rowWidth, float rowHeight,
                                           float cycRadius, float cycRotation, float rotation, Color color){
    drawRectAsCylindrical(x, y, rowWidth, rowHeight, cycRadius, cycRotation, rotation, color, color, Draw.z(), Draw.z() - 0.01f);
  }

  public static void drawRectAsCylindrical(float x, float y, float rowWidth, float rowHeight, float cycRadius, float cycRotation,
                                           float rotation, Color color, Color dark, float lightLayer, float darkLayer){
    if(rowWidth >= 2*Mathf.pi*cycRadius){
      v1.set(cycRadius, rowHeight).rotate(rotation);
      Draw.color(color);
      float z = Draw.z();
      Draw.z(lightLayer);
      Fill.quad(
          x + v1.x, y - v1.y,
          x + v1.x, y + v1.y,
          x - v1.x, y + v1.y,
          x - v1.x, y - v1.y
      );
      Draw.z(z);
      return;
    }

    cycRotation = Mathf.mod(cycRotation, 360);

    float phaseDiff = 180*rowWidth/(Mathf.pi*cycRadius);
    float rot = cycRotation + phaseDiff;

    v31.set(cycRadius, rowHeight/2, 0).rotate(Vec3.Y, cycRotation);
    v33.set(v31);
    v32.set(cycRadius, rowHeight/2, 0).rotate(Vec3.Y, rot);
    v34.set(v32);

    if(cycRotation < 180){
      if(rot > 180) v33.set(-cycRadius, rowHeight/2, 0);
      if(rot > 360) v34.set(cycRadius, rowHeight/2, 0);
    }
    else{
      if(rot > 360) v33.set(cycRadius, rowHeight/2, 0);
      if(rot > 540) v34.set(-cycRadius, rowHeight/2, 0);
    }

    float z = Draw.z();
    // A to C
    drawArcPart(v31.z > 0, color, dark, lightLayer, darkLayer, x, y, v31, v33, rotation);

    // B to D
    drawArcPart(v34.z > 0, color, dark, lightLayer, darkLayer, x, y, v32, v34, rotation);

    // C to D
    drawArcPart(
        (v33.z > 0 && v34.z > 0) || (Mathf.zero(v33.z) && Mathf.zero(v34.z) && v31.z < 0 && v32.z < 0)
            || (Mathf.zero(v33.z) && v34.z > 0) || (Mathf.zero(v34.z) && v33.z > 0),
        color, dark, lightLayer, darkLayer, x, y, v33, v34, rotation);

    Draw.z(z);
    Draw.reset();
  }

  private static void drawArcPart(boolean light, Color colorLight, Color darkColor, float layer, float darkLayer,
                                  float x, float y, Vec3 vec1, Vec3 vec2, float rotation){
    if(light){
      Draw.color(colorLight);
      Draw.z(layer);
    }
    else{
      Draw.color(darkColor);
      Draw.z(darkLayer);
    }

    v1.set(vec1.x, vec1.y).rotate(rotation);
    v2.set(vec2.x, vec2.y).rotate(rotation);
    v3.set(vec1.x, -vec1.y).rotate(rotation);
    v4.set(vec2.x, -vec2.y).rotate(rotation);

    Fill.quad(
        x + v3.x, y + v3.y,
        x + v1.x, y + v1.y,
        x + v2.x, y + v2.y,
        x + v4.x, y + v4.y
    );
  }

  public static void gapTri(float x, float y, float width, float length, float insideLength, float rotation) {
    v1.set(0, width/2).rotate(rotation);
    v2.set(length, 0).rotate(rotation);
    v3.set(insideLength, 0).rotate(rotation);

    Fill.quad(
        x + v1.x, y + v1.y,
        x + v2.x, y + v2.y,
        x + v3.x, y + v3.y,
        x + v1.x, y + v1.y
    );
    Fill.quad(
        x - v1.x, y - v1.y,
        x + v2.x, y + v2.y,
        x + v3.x, y + v3.y,
        x - v1.x, y - v1.y
    );
  }

  @SuppressWarnings("unchecked")
  private static class DrawTask {
    DrawAcceptor<?> defaultFirstTask, defaultLastTask;
    protected Object defaultTarget;
    protected AtomicReference<DrawAcceptor<?>>[] tasks = new AtomicReference[16];
    protected AtomicReference<Object>[] dataTarget = new AtomicReference[16];
    int taskCounter;
    boolean init;

    {
      for (int i = 0; i < tasks.length; i++) {
        tasks[i] = new AtomicReference<>();
        dataTarget[i] = new AtomicReference<>();
      }
    }

    <T> void addTask(T dataAcceptor, DrawAcceptor<T> task){
      if (tasks.length <= taskCounter){
        tasks = Arrays.copyOf(tasks, tasks.length + 1);
        tasks[taskCounter] = new AtomicReference<>(task);
        dataTarget = Arrays.copyOf(dataTarget, tasks.length);
        dataTarget[taskCounter++] = new AtomicReference<>(dataAcceptor);
      }
      else{
        tasks[taskCounter].set(task);
        dataTarget[taskCounter++].set(dataAcceptor);
      }
    }

    @SuppressWarnings("rawtypes")
    void flush(){
      if (defaultFirstTask != null) ((DrawAcceptor)defaultFirstTask).draw(defaultTarget);
      for (int i = 0; i < taskCounter; i++) {
        ((DrawAcceptor)tasks[i].get()).draw(dataTarget[i].get());
      }
      taskCounter = 0;
      init = false;
      if (defaultLastTask != null) ((DrawAcceptor)defaultLastTask).draw(defaultTarget);
    }
  }

  public interface DrawAcceptor<T>{
    void draw(T accept);
  }
}
