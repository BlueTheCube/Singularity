package singularity.world.distribution.request;

import arc.struct.Seq;
import mindustry.Vars;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import singularity.world.components.distnet.DistElementBuildComp;
import singularity.world.distribution.DistBufferType;
import singularity.world.distribution.DistributeNetwork;
import singularity.world.distribution.buffers.ItemsBuffer;
import universecore.util.Empties;

import java.util.Arrays;

/**向网络中写入物品，这一操作将物品写入网络的缓存中，处理结束由网络将缓存分配给网络中的子容器*/
public class PutItemsRequest extends DistRequestBase{
  protected final ItemsBuffer source;
  protected ItemsBuffer destination;

  protected final Seq<ItemStack> reqItems;
  protected boolean all;

  protected int[] lastHandles = new int[Vars.content.items().size];

  public PutItemsRequest(DistElementBuildComp sender, ItemsBuffer source){
    this(sender, source, Empties.nilSeq());
    allItemPut();
  }
  
  public PutItemsRequest(DistElementBuildComp sender, ItemsBuffer source, Seq<ItemStack> items){
    super(sender);
    this.source = source;
    this.reqItems = items;
  }
  
  public void allItemPut(){
    all = true;
  }
  
  @Override
  public int priority(){
    return 64;
  }
  
  @Override
  public void init(DistributeNetwork target){
    super.init(target);
    destination = target.getCore().distCore().getBuffer(DistBufferType.itemBuffer);
  }

  @Override
  protected boolean preHandleTask(){
    return true;
  }

  @Override
  public boolean handleTask(){
    Arrays.fill(lastHandles, 0);
    if(all){
      for(ItemsBuffer.ItemPacket packet : source){
        int move = Math.min(packet.amount(), destination.remainingCapacity());
        if(move <= 0) continue;

        lastHandles[packet.id()] = move;

        packet.remove(move);
        destination.put(packet.get(), move);
      }
      return true;
    }
    else{
      boolean blockTest = false;
      for(ItemStack stack : reqItems){
        int move = Math.min(source.get(stack.item), destination.remainingCapacity());

        if(move <= 0) continue;

        lastHandles[stack.item.id] = move;

        source.remove(stack.item, move);
        destination.put(stack.item, move);
        blockTest = true;
      }
      return blockTest;
    }
  }

  @Override
  protected boolean afterHandleTask(){
    for(int id = 0; id < lastHandles.length; id++){
      Item item = Vars.content.item(id);

      int rem = Math.min(lastHandles[id], destination.get(item));
      rem = Math.min(rem, source.remainingCapacity());
      destination.remove(item, rem);
      destination.deReadFlow(item, rem);
      source.put(item, rem);
      source.dePutFlow(item, rem);
    }

    return true;
  }
}
