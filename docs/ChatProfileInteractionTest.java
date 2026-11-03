package com.november.mcphone.feature.chat.client;
import com.november.mcphone.feature.chat.client.profile.ProfileClientCache;
import com.november.mcphone.feature.chat.profile.net.ProfileWire;
import java.util.*;
public final class ChatProfileInteractionTest {
 private static int checks;
 private static void check(boolean ok,String why) { checks++;if (!ok) throw new AssertionError(why); }
 private static void field(Object o,String n,Object value) throws Exception { var f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,value); }
 private static Object field(Object o,String n) throws Exception { var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o); }
 private static void reply(ProfileWire.Query q,ProfileWire.Status status,String text) { ProfileClientCache.accept(new ProfileWire.Reply(q.request(),q.target(),status,"Player",text)); }
 public static void main(String[] args) throws Exception {
  var menu=ChatProfileUi.Icon.values();
  check(Arrays.stream(menu).map(i->i.label).toList().equals(List.of("favorites","github","sponsor","settings")),"用户要求的菜单顺序");
  check(Arrays.stream(menu).map(i->i.element).distinct().count()==4,"四个入口使用各自对应的贴图");
  var profile=new ChatProfile();var profileRows=ChatProfileLayout.home(new ChatLayout.Rect(10,20,110,140)).rows();
  field(profile,"rows",profileRows);var last=profileRows.get(3);profile.mouseClicked(last.x()+5,last.y()+9,0);
  check(field(profile,"page").toString().equals("SETTINGS"),"重排后第四行实际进入设置，不触发赞助");
  check(profile.back()&&field(profile,"page").toString().equals("HOME"),"设置仍可返回我页");
  field(profile,"rows",profileRows);var first=profileRows.get(0);profile.mouseClicked(first.x()+5,first.y()+9,0);
  check(field(profile,"page").toString().equals("FAVORITES"),"第一行继续打开收藏");
  check(profile.back()&&field(profile,"page").toString().equals("HOME"),"收藏列表返回我页");profile.close();
  check(!profile.back(),"关闭后没有残留子页返回");
  UUID id=new UUID(0,1);var nav=new ChatNavigationBar();field(nav,"bounds",new ChatLayout.Rect(10,100,119,18));
  check(nav.click(100,105,0)==ChatNavigationBar.Page.PROFILE,"第四列我可点击");check(nav.click(80,105,0)==null,"第三列发现继续占位");
  check(ChatNavigationBar.Page.PROFILE.column==3,"我页高亮第四列，不使用序号误高亮发现");
  var home=new ChatList();field(field(home,"navigation"),"bounds",new ChatLayout.Rect(10,100,119,18));home.mouseClicked(110,105,0);
  check(!home.consumeContactsRequest()&&home.consumeProfileRequest(),"首页消费联系人意图不能清掉我页意图");check(!home.consumeProfileRequest(),"我页导航仅消费一次");
  var contacts=new ChatContacts();field(field(contacts,"navigation"),"bounds",new ChatLayout.Rect(10,100,119,18));contacts.mouseClicked(110,105,0);
  check(contacts.consumeAction()==ChatContacts.Action.PROFILE,"联系人可直接进入我页");
  var sent=new ArrayList<ProfileWire.Query>();var details=new ChatProfileDetails(sent::add);details.open(id,"fallback",true);
  check(details.name().equals("fallback")&&!details.loaded(),"服务端资料到达前先显示调用方已知名字");
  check(sent.size()==1&&!sent.get(0).update(),"打开先读服务端公开资料");reply(sent.get(0),ProfileWire.Status.OK,"原介绍");details.poll(System.currentTimeMillis());
  check(details.loaded()&&details.introduction().equals("原介绍"),"只有服务端成功结果更新资料");
  field(details,"action",new ChatLayout.Rect(10,10,100,14));details.click(20,15,0);check((boolean)field(details,"editing"),"编辑使用独立草稿");
  var editor=(ChatContactSearch)field(details,"editor");editor.value("新介绍");field(details,"action",new ChatLayout.Rect(10,10,100,14));details.click(20,15,0);
  check(sent.get(1).update()&&sent.get(1).introduction().equals("新介绍"),"保存发送完整介绍");check(details.introduction().equals("原介绍"),"未确认前展示原资料");
  check(details.back()&&(boolean)field(details,"editing"),"保存进行中不误当取消服务端写入");
  reply(sent.get(1),ProfileWire.Status.UNAVAILABLE,"");details.poll(System.currentTimeMillis());
  check(editor.query().equals("新介绍")&&details.introduction().equals("原介绍"),"失败保留草稿和旧资料");
  field(details,"action",new ChatLayout.Rect(10,10,100,14));details.click(20,15,0);reply(sent.get(2),ProfileWire.Status.OK,"新介绍");details.poll(System.currentTimeMillis());
  check(details.introduction().equals("新介绍")&&!(boolean)field(details,"editing"),"成功确认后退出编辑");
  field(details,"action",new ChatLayout.Rect(10,10,100,14));details.click(20,15,0);editor.value("");field(details,"action",new ChatLayout.Rect(10,10,100,14));details.click(20,15,0);
  check(sent.get(3).update()&&sent.get(3).introduction().isEmpty(),"可保存空介绍");
  details.close();reply(sent.get(3),ProfileWire.Status.OK,"");details.poll(System.currentTimeMillis());check(details.introduction().equals("新介绍"),"关页后回声不改显示值");
  details.open(id,"fallback",false);var read=sent.get(4);reply(read,ProfileWire.Status.OK,"别人介绍");details.poll(System.currentTimeMillis());
  field(details,"action",new ChatLayout.Rect(10,10,100,14));details.click(20,15,0);check(!(boolean)field(details,"editing")&&sent.size()==5,"别人资料只能读取");
  details.close();details.open(id,"fallback",true);details.poll(Long.MAX_VALUE);check(details.status().equals("timeout"),"超时明确反馈");details.close();ProfileClientCache.clear();
  for(int i=0;i<ProfileClientCache.MAX_PENDING;i++) ProfileClientCache.begin(id,System.currentTimeMillis());
  details.open(id,"fallback",true);
  check(details.status().equals("unavailable")&&field(details,"request")==null,"票据耗尽只显示失败，不使资料页崩溃或留下假等待");
  details.close();ProfileClientCache.clear();
  var rejected=new ChatProfileDetails(q->{throw new IllegalStateException("模拟发送失败");});rejected.open(id,"fallback",true);
  check(rejected.status().equals("unavailable")&&field(rejected,"request")==null,"发送失败清除本页票据");rejected.close();
  field(profile,"page",Enum.valueOf((Class)field(profile,"page").getClass(),"SETTINGS"));profile.close();
  check(!profile.back()&&field(profile,"page").toString().equals("HOME"),"从子页关闭后重置页面和返回关系");
  System.out.println("全部通过："+checks+" 条断言");
 }
}
