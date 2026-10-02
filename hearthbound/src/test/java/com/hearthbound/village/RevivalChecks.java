package com.hearthbound.village;
import com.hearthbound.data.HBData;
import com.hearthbound.rpg.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;
import java.util.function.Consumer;
@GameTestHolder("hearthbound") @PrefixGameTestTemplate(false)
public final class RevivalChecks {
 record Fixture(Village v,Resident r,ServerPlayer p){}
 static Fixture fixture(GameTestHelper h){
  var l=h.getLevel();var pos=h.absolutePos(new BlockPos(2,2,2));
  for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++){
   l.setBlock(pos.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);
   l.setBlock(pos.offset(x,0,z),Blocks.AIR.defaultBlockState(),3);
   l.setBlock(pos.offset(x,1,z),Blocks.AIR.defaultBlockState(),3);
  }
  var v=new Village();v.center=pos;v.dimension=l.dimension();v.culture=HBData.cultureMap().keySet().iterator().next();
  var r=new Resident();r.name="Revival Test";r.role=Role.FARMER;r.persona="test:remembered_story";r.deadSince=l.getGameTime();r.entity=UUID.randomUUID();
  v.residents.add(r);VillageData.get(l.getServer()).add(v);
  var p=net.neoforged.neoforge.common.util.FakePlayerFactory.get(l, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "revival-test"));p.setPos(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5);p.getInventory().clearContent();
  return new Fixture(v,r,p);
 }
 @GameTest(template="empty") public static void paidAndDuplicate(GameTestHelper h){
  var f=fixture(h);UUID id=f.r.id;var bond=new PlayerData.Bond();bond.points=42;bond.stage=3;Rpg.data(f.p).bonds.put(id,bond);
  Wallet.give(f.p,100);
  check(ResidentRevival.cost()==30,"10 copper plus 2 silver");
  check(ResidentRevival.revive(f.p,f.v,id)==ResidentRevival.Result.SUCCESS,"revival succeeds");
  check(Wallet.balance(f.p)==70 && Rpg.data(f.p).coinsSpent==30,"payment and change");
  check(f.r.alive() && h.getLevel().getEntity(f.r.entity)!=null,"spawned alive");
  check(f.r.id.equals(id)&&f.r.role==Role.FARMER&&f.r.name.equals("Revival Test"),"identity and role");
  check(f.r.persona.equals("test:remembered_story")&&Rpg.data(f.p).bonds.get(id).stage==3,"story and relationship");
  var saved=Resident.load(f.r.save());check(saved.alive()&&saved.id.equals(id)&&saved.entity.equals(f.r.entity),"save and load");
  var entity=f.r.entity;
  check(ResidentRevival.revive(f.p,f.v,id)==ResidentRevival.Result.ALREADY_ALIVE,"duplicate rejected");
  check(Wallet.balance(f.p)==70&&entity.equals(f.r.entity),"no duplicate charge or spawn");h.succeed();
 }
 @GameTest(template="empty") public static void insufficientAndInvalid(GameTestHelper h){
  var f=fixture(h);var old=f.r.entity;Wallet.give(f.p,29);
  check(ResidentRevival.revive(f.p,f.v,f.r.id)==ResidentRevival.Result.INSUFFICIENT,"insufficient funds");
  check(!f.r.alive()&&old.equals(f.r.entity)&&Wallet.balance(f.p)==29,"failed purchase unchanged");
  check(ResidentRevival.revive(f.p,f.v,UUID.randomUUID())==ResidentRevival.Result.UNAVAILABLE,"forged resident");
  f.p.setPos(f.v.center.getX()+1000,f.v.center.getY(),f.v.center.getZ());
  check(ResidentRevival.revive(f.p,f.v,f.r.id)==ResidentRevival.Result.UNAVAILABLE,"distance checked");
  check(Wallet.balance(f.p)==29,"no charge on rejected requests");h.succeed();
 }
 @GameTest(template="empty") public static void cancelledSpawn(GameTestHelper h){
  var f=fixture(h);var old=f.r.entity;Wallet.give(f.p,30);
  Consumer<EntityJoinLevelEvent> cancel=e->{if(e.getEntity() instanceof com.hearthbound.entity.SettlerEntity s&&f.v.id.equals(s.villageId()))e.setCanceled(true);};
  NeoForge.EVENT_BUS.addListener(cancel);
  try{
   check(ResidentRevival.revive(f.p,f.v,f.r.id)==ResidentRevival.Result.SPAWN_FAILED,"spawn cancelled");
   check(Wallet.balance(f.p)==30&&!f.r.alive()&&old.equals(f.r.entity),"cancelled spawn no charge");
  }finally{NeoForge.EVENT_BUS.unregister(cancel);}h.succeed();
 }
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
}

