package singularity.contents;

import arc.Core;
import arc.audio.Sound;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.util.Strings;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Sounds;
import mindustry.gen.Tex;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.ItemStack;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.draw.DrawBlock;
import singularity.Sgl;
import singularity.graphic.Blur;
import singularity.graphic.Distortion;
import singularity.graphic.MathRenderer;
import singularity.graphic.SglDraw;
import singularity.type.SglCategory;
import singularity.ui.SglStyles;
import singularity.util.MathTransform;
import singularity.world.blocks.TestBlock;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class DebugBlocks implements ContentList{
  public static Block drawTest, voidDrawTest, volTest, empDamageTester;

  @Override
  public void load(){
    drawTest = new TestBlock("draw_test"){{
      requirements(SglCategory.debugging, ItemStack.with());

      configurable = true;
      hasShadow = false;

      buildType = () -> new TestBlockBuild(){
        float alpha = 1f;
        float alp = 20;
        float a = 20;
        int n = 2;

        static final Blur blur = new Blur(Blur.DEf_B);

        static {
          blur.blurSpace = 1f;
        }

        @Override
        public void draw() {
          Draw.draw(Draw.z(), () -> {
            MathRenderer.setDispersion(alpha);
            MathRenderer.setThreshold(0.4f, 0.7f);
            MathRenderer.drawCurveCircle(x, y, alp, n, a, Time.time);
          });
        }

        @Override
        public void buildConfiguration(Table table) {
          table.table(t -> {
            t.slider(0.1f, 10, 0.01f, alpha, f -> alpha = f).size(200, 50).padLeft(8).padRight(8).get().setStyle(SglStyles.sliderLine);
            t.add("0").size(50).update(lable -> lable.setText("" + alpha));
          });
          table.row();
          table.table(t -> {
            t.slider(20, 120, 0.1f, alp, f -> alp = f).size(200, 50).padLeft(8).padRight(8).get().setStyle(SglStyles.sliderLine);
            t.add("0").size(50).update(lable -> lable.setText("" + alp));
          });
          table.row();
          table.table(t -> {
            t.slider(20, 120, 0.1f, a, f -> a = f).size(200, 50).padLeft(8).padRight(8).get().setStyle(SglStyles.sliderLine);
            t.add("0").size(50).update(lable -> lable.setText("" + a));
          });
          table.row();
          table.table(t -> {
            t.slider(2, 20, 1f, n, f -> n = (int) f).size(200, 50).padLeft(8).padRight(8).get().setStyle(SglStyles.sliderLine);
            t.add("0").size(50).update(lable -> lable.setText("" + n));
          });
        }
      };
    }};

    voidDrawTest = new TestBlock("void_draw_test"){{
      requirements(SglCategory.debugging, ItemStack.with());
      hasShadow = false;

      draw = new DrawBlock(){
        static final Distortion dist = new Distortion();
        static final int drawIDD = SglDraw.nextTaskID();

        static final float[] param = new float[9];

        static {
          for (int d = 0; d < 3; d++) {
            param[d * 3] = Mathf.random(0.5f, 3f) / (d + 1) * Mathf.randomSign();
            param[d * 3 + 1] = Mathf.random(0f, 360f);
            param[d * 3 + 2] = Mathf.random(24f, 64f) / ((d + 1) * (d + 1));
          }
        }

        @Override
        public void draw(Building e) {
          dist.setStrength(-64*Vars.renderer.getScale());

          Tmp.v1.set(MathTransform.fourierSeries(Time.time, param));
          float dx = Tmp.v1.x;
          float dy = Tmp.v1.y;

          Draw.z(Layer.flyingUnit);
          SglDraw.drawDistortion(drawIDD, e, dist, d -> {
            Distortion.drawVoidDistortion(d.x + dx, d.y + dy, 16, 64);
          });

          Draw.z(Layer.flyingUnit + 0.5f);
          Draw.color(Color.black);
          Fill.circle( e.x + dx, e.y + dy, 14);

          SglDraw.drawBloomUponFlyUnit(e, b -> {
            Lines.stroke(4, Color.orange);
            Lines.circle(b.x + dx, b.y + dy, 16);

            SglDraw.drawDiamond(b.x + dx, b.y + dy, 180, 5, 0);
            Draw.reset();
          });
        }
      };
    }};

    volTest = new TestBlock("vol_test"){{
      requirements(SglCategory.debugging, ItemStack.with());
      configurable = true;

      buildType = () -> new TestBlockBuild(){
        Sound curr;
        float volume;
        float pitch;
        boolean continuation;

        @Override
        public void update() {
          super.update();
          if (curr != null && continuation) Vars.control.sound.loop(curr, this, volume);
        }

        @Override
        public void buildConfiguration(Table table){
          table.table(Styles.black6, t -> {
            t.defaults().pad(0).margin(0);
            t.table(Tex.buttonTrans, i -> i.image().size(35)).size(40);
            t.button(b -> {
              b.table(text -> {
                text.defaults().grow().left();
                text.add(Core.bundle.get("misc.currentMode")).color(Pal.accent);
                text.row();
                text.add("").update(l -> {
                  l.setText(continuation? "continuation": "none");
                });
              }).grow().right().padLeft(8);
            }, Styles.cleart, () -> continuation = !continuation).size(194, 40).padLeft(8);
          }).size(250, 40);
          table.row();

          table.pane(sound -> {
            for (Field field : Sounds.class.getDeclaredFields()) {
              if (Modifier.isStatic(field.getModifiers()) && field.getType() == Sound.class) {
                try {
                  Sound s = (Sound) field.get(null);
                  sound.button(t -> t.add(field.getName()).left().grow(), Styles.underlineb, () -> {
                    continuation = false;
                    curr = s;
                    curr.at(x, y, pitch, volume);
                  }).update(b -> b.setChecked(curr == s)).size(250, 35).pad(0);
                  sound.row();
                } catch (IllegalAccessException e) {
                  throw new RuntimeException(e);
                }
              }
            }
          }).fillX().height(460);

          table.row();
          table.table(t -> {
            t.slider(0, 10, 0.1f, volume, f -> volume = f).size(200, 50).padLeft(8).padRight(8).get().setStyle(SglStyles.sliderLine);
            t.add("0").size(50).update(lable -> lable.setText(Strings.autoFixed(volume, 1)));
          });
          table.table(t -> {
            t.slider(0f, 2, 0.05f, pitch, f -> pitch = f).size(200, 50).padLeft(8).padRight(8).get().setStyle(SglStyles.sliderLine);
            t.add("0").size(50).update(lable -> lable.setText(Strings.autoFixed(pitch, 2)));
          });
        }
      };
    }};

    empDamageTester = new TestBlock("empDamageTester"){{
      requirements(SglCategory.debugging, ItemStack.with());
      configurable = true;

      buildType = () -> new TestBlockBuild(){
        float damage, damageRange;

        @Override
        public void buildConfiguration(Table table){
          table.table(t -> {
            t.slider(0, 10, 0.1f, damage, f -> damage = f).size(200, 50).padLeft(8).padRight(8).get().setStyle(SglStyles.sliderLine);
            t.add("0").size(50).update(lable -> lable.setText(Strings.autoFixed(damage, 1)));
          });
          table.row();
          table.table(t -> {
            t.slider(0, 400, 1f, damageRange, f -> damageRange = f).size(200, 50).padLeft(8).padRight(8).get().setStyle(SglStyles.sliderLine);
            t.add("0").size(50).update(lable -> lable.setText(Strings.autoFixed(damageRange, 1)));
          });
        }

        @Override
        public void update() {
          super.update();
          Groups.unit.intersect(x - damageRange, y - damageRange, damageRange*2, damageRange*2, u -> {
            if (u.dst(x, y) < damageRange){
              Sgl.empHealth.empDamage(u, damage*Time.delta, false);
            }
          });
        }

        @Override
        public void draw() {
          super.draw();
          Lines.stroke(2, Pal.lightishGray);
          Lines.dashCircle(x, y, damageRange);
        }
      };
    }};
  }
}
