package com.november.mcphone.feature.chat.profile;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
public final class ProfileDataTest {
 private static int checks;
 private static void check(boolean ok,String why) { checks++;if (!ok) throw new AssertionError(why); }
 public static void main(String[] args) {
  var data=new ProfileData();var id=new UUID(0,1);data.book().update(id,id,"公开介绍😀");
  CompoundTag tag=data.write(new CompoundTag());var restored=ProfileData.load(tag);check(restored.book().get(id).equals("公开介绍😀"),"世界 NBT 介绍往返");
  restored.book().update(id,id,"");check(ProfileData.load(restored.write(new CompoundTag())).book().get(id).isEmpty(),"清空介绍重载");
  for (CompoundTag broken:new CompoundTag[]{new CompoundTag(),tag.copy()}) {
   broken.putInt("format",99);broken.putString("unknown_future_data","保留");var locked=ProfileData.load(broken);
   boolean rejected=false;try { locked.book(); } catch (IllegalStateException e) { rejected=true; }
   check(rejected,"未知版本拒绝改写");check(locked.write(new CompoundTag()).equals(broken),"保存锁定资料保留原 NBT");
  }
  System.out.println("全部通过："+checks+" 条断言");
 }
}
