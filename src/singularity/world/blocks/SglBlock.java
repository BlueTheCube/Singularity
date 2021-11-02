package singularity.world.blocks;

import arc.Core;
import arc.func.Cons2;
import arc.func.Func;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.scene.ui.ImageButton;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Strings;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.core.Renderer;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Tex;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.type.Liquid;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.consumers.ConsumePower;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;
import mindustry.world.meta.StatValues;
import mindustry.world.meta.Stats;
import singularity.type.Gas;
import singularity.type.GasStack;
import singularity.type.SglLiquidStack;
import singularity.world.blockComp.*;
import singularity.world.blocks.nuclear.NuclearPipeNode;
import singularity.world.consumers.SglConsumeEnergy;
import singularity.world.consumers.SglConsumeGases;
import singularity.world.consumers.SglConsumeType;
import singularity.world.consumers.SglConsumers;
import singularity.world.draw.SglBaseDrawer;
import singularity.world.draw.SglDrawBlock;
import singularity.world.meta.SglBlockStatus;
import singularity.world.modules.GasesModule;
import singularity.world.modules.NuclearEnergyModule;
import singularity.world.modules.SglConsumeModule;
import universeCore.entityComps.blockComps.ConsumerBlockComp;
import universeCore.entityComps.blockComps.ConsumerBuildComp;
import universeCore.entityComps.blockComps.Dumpable;
import universeCore.ui.table.RecipeTable;
import universeCore.util.UncLiquidStack;
import universeCore.world.consumers.BaseConsumers;
import universeCore.world.consumers.UncConsumeItems;
import universeCore.world.consumers.UncConsumeLiquids;

import java.util.ArrayList;

import static mindustry.Vars.*;

;

/**此mod的基础方块类型，对block添加了完善的consume系统，并拥有核能的基础模块*/
public class SglBlock extends Block implements ConsumerBlockComp, NuclearEnergyBlockComp, GasBlockComp{
  private final SglBlock self = this;
  
  public RecipeTable recipeTable;
  public RecipeTable optionalRecipeTable;
  
  /**方块处于损坏状态时的贴图*/
  public TextureRegion brokenRegion;
  /**方块可储存的电力，注意当此变量大于0时启用能量容量，此时选择配方的电力控制将被覆盖*/
  public float powerCapacity = 0;
  /**方块的输入配方容器*/
  public final ArrayList<BaseConsumers> consumers = new ArrayList<>();
  /**方块的可选配方容器*/
  public final ArrayList<BaseConsumers> optionalCons = new ArrayList<>();

  /**这是一个指针，用于标记当前编辑的consume*/
  public SglConsumers consume;
  /**控制在多种可选输入都可用时是全部可用还是其中优先级最高的一种使用*/
  public boolean oneOfOptionCons = false;
  
  /**方块是否为核能设备*/
  public boolean hasEnergy;
  
  /**方块是否具有气体*/
  public boolean hasGases;
  /**方块是否输出气体*/
  public boolean outputGases;
  /**是否分类输出气体*/
  public boolean classicDumpGas;
  /**是否显示气体流量*/
  public boolean showGasFlow;
  /**方块是否有气体压缩保护，即气体在其中是否不能被压缩*/
  public boolean compressProtect;
  /**方块允许的最大气体压强*/
  public float maxGasPressure = 8f;
  /**气体容积*/
  public float gasCapacity = 10f;
  
  /**是否显示当前选择的配方*/
  public boolean displaySelectPrescripts = false;
  /**方块的图形绘制方法对象，用于控制方块的draw*/
  public SglDrawBlock<? extends SglBuilding> draw = new SglDrawBlock<>(this);
  
  /**核能阻值，在运送核能时运输速度会除以这个数值（最大情况为瞬间达到平衡）*/
  public float resident = 0.1f;
  /**方块是否输出核能量*/
  public boolean outputEnergy = false;
  /**方块是否需求核能量*/
  public boolean consumeEnergy = false;
  /**是否为核能缓冲器，为真时该方块的核势能将固定为基准势能*/
  public boolean energyBuffered = false;
  /**基准核势能，当能压小于此值时不接受核能传入*/
  public float basicPotentialEnergy = 0f;
  /**核能容量。此变量将决定方块的最大核势能*/
  public float energyCapacity = 256;
  /**此方块接受的最大势能差，可设为-1将根据容量自动设置*/
  public float maxEnergyPressure = -1;

  /**方块的合成配方选择器贴图，可自由指定，或者自动初始化*/
  public TextureRegion prescriptSelector;
  
  public String otherLiquidStr = Core.bundle.get("fragment.bars.otherLiquids");
  public int maxShowFlow = 5;
  
  public SglBlock(String name) {
    super(name);
    consumesPower = false;
    appliedConfig();
  }
  
  public void appliedConfig(){
    config(Integer.class, (SglBuilding tile, Integer current) -> tile.recipeCurrent = current);
    configClear((SglBuilding tile) -> tile.recipeCurrent = -1);
  }
  
  @Override
  public BaseConsumers newConsume(){
    consume = new SglConsumers(false);
    this.consumers().add(consume);
    return consume;
  }
  
  @Override
  public BaseConsumers newOptionalConsume(Cons2<ConsumerBuildComp, BaseConsumers> validDef, Cons2<Stats, BaseConsumers> displayDef){
    consume = new SglConsumers(true) {
      {
        this.optionalDef = validDef;
        this.display = displayDef;
      }
    };
    this.optionalCons().add(consume);
    return consume;
  }
  
  @Override
  public void init() {
    if(consumers.size() > 1) configurable = true;
    
    ArrayList<ArrayList<BaseConsumers>> consume = new ArrayList<>();
    if(consumers.size() > 0) consume.add(consumers);
    if(optionalCons.size() > 0) consume.add(optionalCons);
    for(ArrayList<BaseConsumers> list: consume){
      for(BaseConsumers cons: list){
        if(cons.get(SglConsumeType.item) != null){
          hasItems = true;
          if(cons.craftTime == 0) cons.time(90f);
        }
        hasLiquids |= cons.get(SglConsumeType.liquid) != null;
        hasPower |= consumesPower |= cons.get(SglConsumeType.power) != null;
        hasEnergy |= consumeEnergy |= cons.get(SglConsumeType.energy) != null;
        hasGases |= cons.get(SglConsumeType.gas) != null;
      }
    }
  
    if(hasPower){
      initPower(powerCapacity);
    }
    if(hasEnergy && maxEnergyPressure == -1) maxEnergyPressure = energyCapacity*energyCapacity*0.5f;
  
    super.init();
  }
  
  @Override
  public void drawPotentialLinks(int x, int y){
    super.drawPotentialLinks(x, y);
    
    if((consumeEnergy || outputEnergy) && hasEnergy){
      Tile tile = world.tile(x, y);
      if(tile != null){
        NuclearPipeNode.getNodeLinks(tile, this, player.team()).each(e -> {
          if(!(e.getBlock() instanceof NuclearPipeNode)) return;
          NuclearPipeNode node = (NuclearPipeNode)e.getBlock();
          
          Draw.color(node.linkColor, Renderer.laserOpacity * 0.5f);
          node.drawLink(tile.worldx() + offset, tile.worldy() + offset, size, e.getBuilding().tile.drawx(), e.getBuilding().tile.drawy(), e.getBlock().size);
          Drawf.square(e.getBuilding().x, e.getBuilding().y, e.getBlock().size * tilesize / 2f + 2f, Pal.place);
        });
      }
    }
  }
  
  @Override
  public void load(){
    super.load();
    brokenRegion = Core.atlas.has(name + "_broken")? Core.atlas.find(name + "_broken"): region;
    if(displaySelectPrescripts && prescriptSelector == null) prescriptSelector = Core.atlas.has(name + "_prescriptSelector")?
      Core.atlas.find(name + "_prescriptSelector"): Core.atlas.find("ane-prescriptSelector" + size);
    draw.load();
  }
  
  @Override
  public void setStats() {
    stats.add(Stat.size, "@x@", size, size);
    stats.add(Stat.health, health, StatUnit.none);
    if(canBeBuilt()){
      stats.add(Stat.buildTime, buildCost / 60, StatUnit.seconds);
      stats.add(Stat.buildCost, StatValues.items(false, requirements));
    }

    if(instantTransfer){
      stats.add(Stat.maxConsecutive, 2, StatUnit.none);
    }
    
    if(hasGases) setGasStats(stats);
    
    setConsumeStats(stats);
  }
  
  @Override
  public void setBars(){
    bars.add("health", entity -> new Bar("stat.health", Pal.health, entity::healthf).blink(Color.white));
    if(hasGases) bars.add("gasPressure", (SglBuilding entity) -> new Bar(
      () -> Core.bundle.get("fragment.bars.gasPressure") + ":" + Strings.autoFixed(entity.smoothPressure*100, 2) + "kPa",
      () -> Pal.accent,
      () -> Math.min(entity.smoothPressure / maxGasPressure, 1)));
  }
  
  @Override
  public TextureRegion[] icons(){
    return draw.icons();
  }
  
  public class SglBuilding extends Building implements ConsumerBuildComp, GasBuildComp, NuclearEnergyBuildComp, DrawableComp, Dumpable{
    public SglConsumeModule consumer;
    public NuclearEnergyModule energy;
    public GasesModule gases;
    
    public float smoothPressure = 0;
    
    public SglBaseDrawer<? extends SglBuilding> drawer;
    
    public SglBlockStatus status = SglBlockStatus.proper;
  
    protected final Seq<SglLiquidStack> displayLiquids = new Seq<>();
    
    public Seq<NuclearEnergyBuildComp> energyLinked = new Seq<>();
    
    public final ObjectMap<Class<?>, Object> consData = new ObjectMap<>();
    public int recipeCurrent = -1;
    
    @Override
    public Building create(Block block, Team team) {
      super.create(block, team);
      
      if(consumers.size() == 1) recipeCurrent = 0;
      
      consumer = new SglConsumeModule(this, consumers, optionalCons);
      cons(consumer);
      
      if(hasEnergy){
        energy = new NuclearEnergyModule(this, basicPotentialEnergy, energyBuffered);
        
        energy.setNet();
      }
      
      if(hasGases) gases = new GasesModule(this);
  
      drawer = draw.get(this);
      return this;
    }
  
    @Override
    public void placed(){
      super.placed();
      if(net.client()) return;
  
      if((consumeEnergy || outputEnergy) && hasEnergy){
        NuclearPipeNode.getNodeLinks(tile, block(), player.team()).each(e -> {
          if(!(e.getBlock() instanceof NuclearPipeNode)) return;
          
          if(!e.energy().linked.contains(pos())) e.getBuilding().configureAny(pos());
        });
      }
    }
  
    @Override
    public void onProximityUpdate(){
      super.onProximityUpdate();
      if(energy != null) updateLinked();
    }
  
    @Override
    public void onProximityAdded(){
      super.onProximityAdded();
      if(energy != null){
        onEnergyNetworkUpdated();
      }
    }
    
    @Override
    public void onProximityRemoved(){
      super.onProximityAdded();
      if(energy != null){
        onEnergyNetworkRemoved();
      }
    }
  
    @Override
    public int consumeCurrent(){
      return recipeCurrent;
    }
  
    /**使效率返回值乘以核能的效率*/
    @Override
    public float efficiency(){
      //无配方要求时返回1
      if(!consumer.hasConsume()) return super.efficiency();
      //未选择配方时返回0
      if(recipeCurrent == -1) return 0f;
      SglConsumeEnergy<?> ce = consumer.current.get(SglConsumeType.energy);
      float powerE = super.efficiency();
      if(!hasEnergy) return powerE;
      float energyE = ce == null? 1f: ce.buffer? 1f: Math.min(1f, energy.getEnergy() / (ce.usage * 60f * delta()));
      return powerE * energyE;
    }
    
    public void dumpLiquid(){
      liquids.each((l, n) -> dumpLiquid(l));
    }
  
    @Override
    public void displayConsumption(Table table){
      if(consumer != null) table.update(() -> {
        table.clear();
        table.left();
        consumer.build(table);
      });
    }
    
    @Override
    public void drawStatus(){
      if(this.block.enableDrawStatus && this.block().consumers.size() > 0) {
        float multiplier = block.size > 1 ? 1.0F : 0.64F;
        float brcx = this.tile.drawx() + (float)(this.block.size * 8)/2.0F - 8*multiplier/2;
        float brcy = this.tile.drawy() - (float)(this.block.size * 8)/2.0F + 8*multiplier/2;
        Draw.z(71.0F);
        Draw.color(Pal.gray);
        Fill.square(brcx, brcy, 2.5F*multiplier, 45.0F);
        Draw.color(status == SglBlockStatus.proper? status().color: Color.valueOf("#000000"));
        Fill.square(brcx, brcy, 1.5F*multiplier, 45.0F);
        Draw.color();
      }
    }

    @Override
    public boolean consValid() {
      if(status == SglBlockStatus.broken) return false;
      if(consumer != null && consumer.hasConsume()) return consumer.valid() && enabled;
      return enabled;
    }
    
    @Override
    public void consume(){
      consumer.trigger();
    }
    
    /**
     * 获取临近周围特定位置的方块
     * @param rotation 获取目标的方向，表示该方块的某一面
     * @param distance 在指定侧的平移距离，不可大于方块的size
     * @return <entity> 获取到的目标方块
     * */
    public Building getNearby(int rotation, int distance) {
      int size = block().size;
      int d = ((size % 2 == 0)? size: size + 1)/2 - 1;
      int rd = size % 2;
  
      switch(rotation){
        case 0: return nearby(size/2 + 1, distance - d);
        case 1: return nearby(distance - d, size/2 + 1);
        case 2: return nearby(-size/2 - rd, distance - d);
        case 3: return nearby(distance - d, -size/2 - rd);
        default: return null;
      }
    }
  
    public boolean updateValid(){
      return true;
    }
    
    @Override
    public boolean shouldConsume(){
      return this.consumer() != null && this.consumer().hasOptional() || this.consumeCurrent() != -1;
    }
    
    @Override
    public void update(){
      if(hasEnergy && energy != null) energy.update(updateFlow);
      
      if(hasGases && gases != null){
        gases.update(updateFlow);
        smoothPressure = Mathf.lerpDelta(smoothPressure, pressure(), 0.2f);
      }
  
      super.update();
    }
    
    public void updateDisplayLiquid(){
      displayLiquids.clear();
      Seq<Liquid> temp = new Seq<>();
      temp.clear();
      if(recipeCurrent >= 0 && consumer.current != null){
        if(consumer.current.get(SglConsumeType.liquid) != null) for(UncLiquidStack stack : consumer.current.get(SglConsumeType.liquid).liquids) {
          temp.add(stack.liquid);
        }
      }
      liquids.each((key, val) -> {
        if(!temp.contains(key) && val > 0.1f) displayLiquids.add(new SglLiquidStack(key, val));
      });
    }
    
    public void setBars(Table table){
      //显示流体存储量
      if(hasLiquids) updateDisplayLiquid();
      if (!displayLiquids.isEmpty()){
        table.table(Tex.buttonTrans, t -> {
          t.defaults().growX().height(18f).pad(4);
          t.top().add(otherLiquidStr).padTop(0);
          t.row();
          for (SglLiquidStack stack : displayLiquids) {
            Func<Building, Bar> bar = entity -> new Bar(
              () -> stack.liquid.localizedName,
              () -> stack.liquid.barColor != null ? stack.liquid.barColor : stack.liquid.color,
              () -> Math.min(entity.liquids.get(stack.liquid) / entity.block().liquidCapacity, 1f)
            );
            t.add(bar.get(this)).growX();
            t.row();
          }
        }).height(26 * displayLiquids.size + 40);
        table.row();
      }
      
      if(recipeCurrent == -1 || consumer.current == null) return;
  
      if(hasPower && consumes.hasPower()){
        ConsumePower cons = block().consumes.getPower();
        boolean buffered = cons.buffered;
        float capacity = cons.capacity;
        Func<Building, Bar> bar = (entity -> new Bar(() -> buffered ? Core.bundle.format("bar.poweramount", Float.isNaN(entity.power.status * capacity) ? "<ERROR>" : (int)(entity.power.status * capacity)) :
          Core.bundle.get("bar.power"), () -> Pal.powerBar, () -> Mathf.zero(cons.requestedPower(entity)) && entity.power.graph.getPowerProduced() + entity.power.graph.getBatteryStored() > 0f ? 1f : entity.power.status));
        table.add(bar.get(this)).growX();
        table.row();
      }
      
      UncConsumeLiquids<?> cl = consumer.current.get(SglConsumeType.liquid);
      SglConsumeGases<?> cg = consumer.current.get(SglConsumeType.gas);
      if(cl != null || cg != null){
        table.table(Tex.buttonEdge1, t -> t.left().add(Core.bundle.get("fragment.bars.consume")).pad(4)).pad(0).height(38).padTop(4);
        table.row();
        table.table(t -> {
          t.defaults().grow().margin(0);
          if(cl != null){
            t.table(Tex.pane2, liquid -> {
              liquid.defaults().growX().margin(0).pad(4).height(18);
              liquid.left().add(Core.bundle.get("misc.liquid")).color(Pal.gray);
              liquid.row();
              for(UncLiquidStack stack: cl.liquids){
                Func<Building, Bar> bar = (entity -> new Bar(
                  () -> stack.liquid.localizedName,
                  () -> stack.liquid.barColor != null? stack.liquid.barColor: stack.liquid.color,
                  () -> Math.min(entity.liquids.get(stack.liquid) / entity.block().liquidCapacity, 1f)
                ));
                liquid.add(bar.get(this));
                liquid.row();
              }
            });
          }
    
          if(cg != null){
            t.table(Tex.pane2, gases -> {
              gases.defaults().growX().margin(0).pad(4).height(18);
              gases.left().add(Core.bundle.get("misc.gas")).color(Pal.gray);
              gases.row();
              for(GasStack stack: cg.gases){
                Func<Building, Bar> bar = (ent -> {
                  GasBuildComp entity = (GasBuildComp) ent;
                  return new Bar(
                    () -> stack.gas.localizedName,
                    () -> stack.gas.color,
                    () -> Math.min((entity.gases().get(stack.gas) / gasCapacity) * (entity.gases().get(stack.gas) / entity.gases().total() > 0? entity.gases().total(): 1f), 1f)
                  );
                });
                gases.add(bar.get(this));
                gases.row();
              }
            });
          }
        }).height(46 + Math.max((cl != null? cl.liquids.length: 0), (cg != null? cg.gases.length: 0))*26).padBottom(0).padTop(2);
      }
      
      table.row();
    }
    
    public void reset(){
      if(items != null)items.clear();
      if(liquids != null)liquids.clear();
      if(gases != null)gases.clear();
    }
    
    @Override
    public Object config(){
      return recipeCurrent;
    }
    
    @Override
    public void display(Table table){
      super.display(table);
      displayEnergy(table);
  
      if(gases != null && showGasFlow){
        table.row();
        table.table(l -> {
          Runnable rebuild = () -> {
            l.clearChildren();
            l.left();
            Seq<GasStack> gasesFlow = new Seq<>();
            float[] flowing = {0};
            gases.eachFlow((gas, flow) -> {
              if(flow < 0.01f) return;
              gasesFlow.add(new GasStack(gas, flow));
              flowing[0] += flow;
            });
            
            if(gasesFlow.size < maxShowFlow){
              for(GasStack stack: gasesFlow){
                l.image(() -> stack.gas.uiIcon).padRight(3f);
                l.label(() -> stack.amount < 0 ? "..." : Strings.fixed(stack.amount, 2) + Core.bundle.get("misc.preSecond")).color(Color.lightGray);
                l.row();
              }
            }
            else{
              l.add(Core.bundle.get("misc.gasFlowRate") + ": " + flowing[0] + Core.bundle.get("misc.preSecond"));
            }
          };
      
          l.update(rebuild);
        }).left();
      }
    }
    
    @Override
    public void displayBars(Table table){
      if(consumer != null){
        table.update(() -> {
          table.clearChildren();
          super.displayBars(table);
          setBars(table);
        });
      }
      else{
        super.displayBars(table);
      }
    }
  
    @Override
    public float getMoveResident(NuclearEnergyBuildComp destination){
      return 0;
    }
  
    @Override
    public void onOverpressure(float potentialEnergy){
      //TODO:把默认爆炸写了
    }
    
    /**具有输出物品的方块返回可能输出的物品，没有则返回null*/
    public Seq<Item> outputItems(){
      return null;
    }
  
    /**具有输出液体的方块返回可能输出的液体，没有则返回null*/
    public Seq<Liquid> outputLiquids(){
      return null;
    }
    
    @Override
    public void buildConfiguration(Table table){
      if(status == SglBlockStatus.broken) return;
      if(consumers.size() > 1){
        Table prescripts = new Table(Tex.buttonTrans);
        prescripts.defaults().grow().marginTop(0).marginBottom(0).marginRight(5).marginRight(5);
        prescripts.add(Core.bundle.get("fragment.buttons.selectPrescripts")).padLeft(5).padTop(5).padBottom(5);
        prescripts.row();
        
        TextureRegion icon;
        Table buttons = new Table();
        for(int i=0; i<consumers.size(); i++){
          int s = i;
          BaseConsumers c = consumers.get(i);
          UncConsumeItems<?> consumeItems = c.get(SglConsumeType.item);
          UncConsumeLiquids<?> consumeLiquids = c.get(SglConsumeType.liquid);
          
          icon = c.icon != null? c.icon: consumeItems != null && consumeItems.items != null?
          consumeItems.items[0].item.uiIcon: consumeLiquids != null && consumeLiquids.liquids != null?
          consumeLiquids.liquids[0].liquid.uiIcon: null;
          ImageButton button = new ImageButton(icon, Styles.selecti);
          button.clicked(() -> {
            reset();
            if(recipeCurrent == s){
              recipeCurrent = -1;
              return;
            }
            recipeCurrent = s;
          });
          button.update(() -> button.setChecked(recipeCurrent == s));
          buttons.add(button).size(50, 50);
          if((i+1) % 4 == 0) buttons.row();
        }
        
        prescripts.add(buttons);
        table.add(prescripts);
        table.row();
      }
    }
    
    @Override
    public boolean acceptItem(Building source, Item item){
      return source.team == this.team && hasItems && consumer.filter(SglConsumeType.item, item) && items.get(item) < block().itemCapacity && status == SglBlockStatus.proper;
    }

    @Override
    public boolean acceptLiquid(Building source, Liquid liquid){
      return source.team == this.team && hasLiquids && consumer.filter(SglConsumeType.liquid, liquid) && liquids.get(liquid) <= block().liquidCapacity - 0.0001f && status == SglBlockStatus.proper;
    }
  
    @Override
    public boolean acceptEnergy(NuclearEnergyBuildComp source){
      return source.getBuilding().team == team && energy.getEnergy() < getNuclearBlock().energyCapacity() &&
        source.getEnergyPressure(this) > Math.max(basicPotentialEnergy, getEnergy());
    }
  
    @Override
    public boolean acceptGas(GasBuildComp source, Gas gas){
      return GasBuildComp.super.acceptGas(source, gas) && consumer.filter(SglConsumeType.gas, gas);
    }
  
    @Override
    public void draw(){
      if(status == SglBlockStatus.proper){
        drawer.doDraw();
      }
      else{
        Draw.rect(brokenRegion, x, y);
      }
      drawStatus();
    }
  
    @Override
    public void drawLight(){
      super.drawLight();
      drawer.drawLight();
    }
    
    @Override
    public SglBlock block(){
      return self;
    }
    
    @Override
    public void write(Writes write) {
      super.write(write);
      write.i(recipeCurrent);
      if(consumer != null) consumer.write(write);
      if(energy != null) energy.write(write);
      if(gases != null) gases.write(write);
    }
  
    @Override
    public void read(Reads read, byte revision){
      super.read(read, revision);
      recipeCurrent = read.i();
      if(consumer != null) consumer.read(read);
      if(energy != null) energy.read(read);
      if(gases != null) gases.read(read);
    }
  }
}