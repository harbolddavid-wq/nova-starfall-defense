from pathlib import Path
import shutil,re,os
root=Path(os.environ.get('NOVA_THEMES_ROOT','/mnt/data/themes_work/mp4base/themes'))
src=root/'android/src/com/novasky/themes/MainActivity.java'
s=src.read_text()
def patch(old,new,label):
 global s
 n=s.count(old)
 if n!=1: raise RuntimeError(f'{label}: expected exactly one match, got {n}')
 s=s.replace(old,new)

patch('final String[] backupKeys={"favorites","dock","nova_pins","nova_hidden","organized_folders_v1","order:home","order:nova","recent_apps","drawer_hidden","theme","previousTheme","clock_x","clock_y"};',
      'final String[] backupKeys={"favorites","dock","nova_pins","nova_hidden","organized_folders_v1","order:home","order:nova","recent_apps","drawer_hidden","theme","previousTheme","clock_x","clock_y","custom_pages_v1"};', 'backup keys')
patch('int homePage=0; // 0: original Home / 1: all Nova-created apps',
      'int homePage=0; // 0: original Home / 1: Nova Apps / 2+: named custom pages', 'comment')

# Insert durable, backwards-compatible page model after folder lookup.
mark=' FolderLayout.Folder folder(String id){for(FolderLayout.Folder f:folders())if(f.id.equals(id))return f;return null;}\n'
new=r''' /** Every existing Home/Nova preference key is preserved; new pages get separate keys. */
 static final int MAX_CUSTOM_PAGES=12;
 static final class NamedPage {final String id,name;NamedPage(String i,String n){id=i;name=n;}}
 ArrayList<NamedPage> customPages(){ArrayList<NamedPage> pages=new ArrayList<>();try{
  JSONArray list=new JSONArray(prefs.getString("custom_pages_v1","[]"));
  for(int i=0;i<list.length()&&pages.size()<MAX_CUSTOM_PAGES;i++){
   JSONObject j=list.optJSONObject(i);if(j==null)continue;
   String id=j.optString("id",""),name=j.optString("name","").trim();
   if(!id.startsWith("page:")||id.length()>60||name.isEmpty()||name.length()>32)continue;
   boolean duplicate=false;for(NamedPage p:pages)if(p.id.equals(id))duplicate=true;
   if(!duplicate)pages.add(new NamedPage(id,name));
  }
 }catch(Exception ignored){}return pages;}
 void saveCustomPages(List<NamedPage> pages){JSONArray array=new JSONArray();try{
  for(NamedPage p:pages){JSONObject j=new JSONObject();j.put("id",p.id);j.put("name",p.name);array.put(j);}
 }catch(Exception ignored){}prefs.edit().putString("custom_pages_v1",array.toString()).apply();}
 ArrayList<String> pageIds(){ArrayList<String> ids=new ArrayList<>();ids.add("home");ids.add("nova");for(NamedPage p:customPages())ids.add(p.id);return ids;}
 String pageId(int index){ArrayList<String> ids=pageIds();return ids.get(Math.max(0,Math.min(index,ids.size()-1)));}
 String pageName(String id){if(id.equals("home"))return "Home";if(id.equals("nova"))return "Nova Apps";
  for(NamedPage p:customPages())if(p.id.equals(id))return p.name;return "My Page";}
 int pageIndex(String id){int i=pageIds().indexOf(id);return i<0?0:i;}
 boolean isCustomPage(String id){for(NamedPage p:customPages())if(p.id.equals(id))return true;return false;}
 void createNamedPage(){createNamedPage(null,null);}
 void createNamedPage(AppEntry selected,String from){ArrayList<NamedPage> saved=customPages();if(saved.size()>=MAX_CUSTOM_PAGES){toast("Limit of 12 custom pages reached");return;}
  EditText entry=new EditText(this);entry.setSingleLine(true);entry.setTextColor(0xff211522);entry.setHint("Google, Work, Games…");entry.setPadding(dp(20),dp(12),dp(20),dp(12));
  new AlertDialog.Builder(this).setTitle("Create a home page").setMessage("Give your new screen a name. Add apps from All apps or move your existing shortcuts.")
   .setView(entry).setPositiveButton("Create",(d,w)->{String label=entry.getText().toString().trim();if(label.isEmpty())label="My Page";
    label=label.substring(0,Math.min(32,label.length()));String id="page:"+UUID.randomUUID();saved.add(new NamedPage(id,label));saveCustomPages(saved);
    if(selected!=null){if(from==null)ensureOnPage(selected,id);else moveAppBetweenPages(selected,from,id);}
    homePage=pageIndex(id);showHome();toast(label+" created");})
   .setNegativeButton("Cancel",null).show();}
 void renameNamedPage(String id){if(!isCustomPage(id))return;String old=pageName(id);
  EditText entry=new EditText(this);entry.setSingleLine(true);entry.setTextColor(0xff211522);entry.setText(old);entry.setSelectAllOnFocus(true);
  new AlertDialog.Builder(this).setTitle("Rename page").setView(entry).setPositiveButton("Save",(d,w)->{
   String label=entry.getText().toString().trim();if(label.isEmpty())label=old;label=label.substring(0,Math.min(32,label.length()));
   ArrayList<NamedPage> list=customPages();for(int i=0;i<list.size();i++)if(list.get(i).id.equals(id))list.set(i,new NamedPage(id,label));
   saveCustomPages(list);showHome();}).setNegativeButton("Cancel",null).show();}
 void deleteNamedPage(String id){if(!isCustomPage(id))return;
  new AlertDialog.Builder(this).setTitle("Delete “"+pageName(id)+"”?")
   .setMessage("This removes the page, its shortcuts and page folders from Nova Themes. It does not uninstall any apps or erase their data.")
   .setPositiveButton("Delete page",(d,w)->{
    ArrayList<NamedPage> list=customPages();list.removeIf(p->p.id.equals(id));
    ArrayList<FolderLayout.Folder> all=folders();all.removeIf(f->f.page.equals(id));
    SharedPreferences.Editor edit=prefs.edit();JSONArray serial=new JSONArray();try{for(NamedPage p:list){JSONObject j=new JSONObject();j.put("id",p.id);j.put("name",p.name);serial.put(j);}}catch(Exception ignored){}
    JSONArray folderData=new JSONArray();try{for(FolderLayout.Folder f:all){JSONObject j=new JSONObject();j.put("id",f.id);j.put("page",f.page);j.put("name",f.name);j.put("members",new JSONArray(f.members));folderData.put(j);}}catch(Exception ignored){}
    edit.putString("custom_pages_v1",serial.toString()).putString("organized_folders_v1",folderData.toString())
      .remove("page_apps:"+id).remove("order:"+id).apply();homePage=0;showHome();
   }).setNegativeButton("Keep page",null).show();}
 void namedPageActions(String id){if(!isCustomPage(id))return;
  new AlertDialog.Builder(this).setTitle(pageName(id)).setItems(new String[]{"Open page","Rename page","Delete page (keep apps)"},(d,i)->{
   if(i==0){homePage=pageIndex(id);showHome();}else if(i==1)renameNamedPage(id);else deleteNamedPage(id);
  }).show();}
 void showPagesMenu(){ArrayList<String> ids=pageIds();String[] choices=new String[ids.size()+1];
  for(int i=0;i<ids.size();i++)choices[i]=(i==homePage?"●  ":"○  ")+pageName(ids.get(i));
  choices[ids.size()]="＋  Create named page";
  new AlertDialog.Builder(this).setTitle("Your home pages").setItems(choices,(d,i)->{
   if(i==ids.size())createNamedPage();else if(i>=2)namedPageActions(ids.get(i));else{homePage=i;showHome();}
  }).setNegativeButton("Close",null).show();}
 void choosePageForApp(AppEntry a,String from){ArrayList<String> ids=pageIds();ArrayList<String> choices=new ArrayList<>();
  for(String id:ids)if(!id.equals(from))choices.add(id);
  String[] names=new String[choices.size()+1];for(int i=0;i<choices.size();i++)names[i]=pageName(choices.get(i));
  names[names.length-1]="＋  Create a new page";
  new AlertDialog.Builder(this).setTitle((from==null?"Add ":"Move ")+a.label+" to…").setItems(names,(d,i)->{
   if(i==choices.size()){
    createNamedPage(a,from);return;
   }
   String target=choices.get(i);if(from!=null)moveAppBetweenPages(a,from,target);else{
    ensureOnPage(a,target);homePage=pageIndex(target);showHome();toast(a.label+" added to "+pageName(target));
   }
  }).setNegativeButton("Cancel",null).show();}
 void moveAppBetweenPages(AppEntry a,String from,String target){if(from.equals(target))return;
  ensureOnPage(a,target);if(!keysFor(target).contains(a.key)){toast("Could not add shortcut; original kept");return;}
  if(from.equals("home")){ArrayList<String> list=stored("favorites");list.remove(a.key);save("favorites",list);}
  else if(from.equals("nova")){ArrayList<String> pins=stored("nova_pins"),hidden=stored("nova_hidden");pins.remove(a.key);
   if(isNovaCreated(a)&&!hidden.contains(a.key))hidden.add(a.key);save("nova_pins",pins);save("nova_hidden",hidden);}
  else{ArrayList<String> list=stored("page_apps:"+from);list.remove(a.key);save("page_apps:"+from,list);}
  ArrayList<FolderLayout.Folder> all=folders();FolderLayout.detach(a.key,all,from);saveFolders(all);
  ArrayList<String> order=stored("order:"+from);order.remove(a.key);saveOrder(from,order);
  homePage=pageIndex(target);showHome();toast(a.label+" moved to "+pageName(target));
 }
'''
if mark not in s:raise RuntimeError('folder marker not found')
s=s.replace(mark,mark+new,1)

patch('if(!id.startsWith("folder:")||!(pg.equals("nova")||pg.equals("home")))continue;',
      'if(!id.startsWith("folder:")||!(pg.equals("nova")||pg.equals("home")||isCustomPage(pg)))continue;', 'folder validation')
patch('String currentPageId(){return homePage==1?"nova":"home";}',
      'String currentPageId(){return pageId(homePage);}', 'page id')
patch(' }else for(AppEntry a:novaEntries())keys.add(a.key);return keys;}',
      ' }else if(pg.equals("nova")){for(AppEntry a:novaEntries())keys.add(a.key);}else if(isCustomPage(pg)){for(AppEntry a:entries("page_apps:"+pg))keys.add(a.key);}return keys;}', 'keysFor')
patch(' }else if(!onNovaPage(a)){\n  ArrayList<String> pins=stored("nova_pins"),hidden=stored("nova_hidden");hidden.remove(a.key);if(!pins.contains(a.key))pins.add(a.key);save("nova_pins",pins);save("nova_hidden",hidden);\n }}',
      ' }else if(pg.equals("nova")&&!onNovaPage(a)){\n  ArrayList<String> pins=stored("nova_pins"),hidden=stored("nova_hidden");hidden.remove(a.key);if(!pins.contains(a.key))pins.add(a.key);save("nova_pins",pins);save("nova_hidden",hidden);\n }else if(isCustomPage(pg)){ArrayList<String> pins=stored("page_apps:"+pg);if(!pins.contains(a.key)){pins.add(a.key);save("page_apps:"+pg,pins);}}\n }', 'ensureOnPage')
patch('All shortcuts return to "+(f.page.equals("nova")?"Nova Apps":"Home")+". No installed apps or data will be deleted.',
      'All shortcuts return to "+pageName(f.page)+". No installed apps or data will be deleted.', 'folder delete wording')
patch('homePage=f.page.equals("nova")?1:0;showHome();',
      'homePage=pageIndex(f.page);showHome();', 'folder delete navigation')

# Keep drag triggered within held gesture; add menu when holding without dragging.
patch('void startMove(View tile,String pg,String key){', 'boolean startMove(View tile,String pg,String key){','drag return signature')
patch('  if(!started)toast("Unable to start dragging. Try again.");\n }',
      '  if(!started)toast("Unable to start dragging. Try again.");\n  return started;\n }', 'drag return')
mark=' int foldersFor(String pg){'
new=r''' void homeIconInteractions(View tile,AppEntry a,String pg){
  tile.setOnLongClickListener(v->{showHomeIconMenu(a,pg);return true;});
  final float[] start={0,0};final boolean[] armed={false},dragging={false},slipped={false};
  final Runnable arm=()->{armed[0]=true;tile.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);};
  tile.setOnTouchListener((v,event)->{
   int action=event.getActionMasked();
   if(action==MotionEvent.ACTION_DOWN){start[0]=event.getX();start[1]=event.getY();armed[0]=false;dragging[0]=false;slipped[0]=false;
    handler.removeCallbacks(arm);handler.postDelayed(arm,450);return true;}
   if(action==MotionEvent.ACTION_MOVE){
    float dx=event.getX()-start[0],dy=event.getY()-start[1];
    if(!armed[0]&&(dx*dx+dy*dy)>dp(18)*dp(18)){slipped[0]=true;handler.removeCallbacks(arm);}
    if(armed[0]&&!dragging[0]&&(dx*dx+dy*dy)>dp(13)*dp(13)){
     handler.removeCallbacks(arm);dragging[0]=startMove(tile,pg,a.key);
    }
    return true;
   }
   if(action==MotionEvent.ACTION_UP){handler.removeCallbacks(arm);
    if(dragging[0])return true;
    if(armed[0]){showHomeIconMenu(a,pg);return true;}
    if(!slipped[0])v.performClick();return true;
   }
   if(action==MotionEvent.ACTION_CANCEL){handler.removeCallbacks(arm);return true;}
   return true;
  });
 }
 void showHomeIconMenu(AppEntry a,String pg){
  new AlertDialog.Builder(this).setTitle(a.label)
   .setItems(new String[]{"Move to another page","Put in a folder","Move earlier","Move later","Customize icon","Uninstall app","App info","Remove shortcut from this page"},(d,n)->{
    switch(n){
     case 0:choosePageForApp(a,pg);break;
     case 1:chooseFolderForApp(a,pg);break;
     case 2:shiftKey(pg,a.key,-1);break;
     case 3:shiftKey(pg,a.key,1);break;
     case 4:customize(a);break;
     case 5:uninstallApp(a);break;
     case 6:openAppInfo(a);break;
     case 7:removePageShortcut(a,pg);break;
    }
   }).setNegativeButton("Cancel",null).show();
 }
 void removePageShortcut(AppEntry a,String pg){
  if(pg.equals("home")){ArrayList<String> list=stored("favorites");list.remove(a.key);save("favorites",list);}
  else if(pg.equals("nova")){ArrayList<String> pins=stored("nova_pins"),hidden=stored("nova_hidden");pins.remove(a.key);
   if(isNovaCreated(a)&&!hidden.contains(a.key))hidden.add(a.key);save("nova_pins",pins);save("nova_hidden",hidden);}
  else if(isCustomPage(pg)){ArrayList<String> list=stored("page_apps:"+pg);list.remove(a.key);save("page_apps:"+pg,list);}
  ArrayList<FolderLayout.Folder> all=folders();FolderLayout.detach(a.key,all,pg);saveFolders(all);
  ArrayList<String> order=stored("order:"+pg);order.remove(a.key);saveOrder(pg,order);showHome();
 }
 void openAppInfo(AppEntry a){try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
   Uri.parse("package:"+a.component.getPackageName())));}catch(Exception e){toast("Android couldn't open app info");}}
 void uninstallApp(AppEntry a){String pkg=a.component.getPackageName();try{
  ApplicationInfo info=getPackageManager().getApplicationInfo(pkg,0);
  if((info.flags&ApplicationInfo.FLAG_SYSTEM)!=0){
   new AlertDialog.Builder(this).setTitle("System app: "+a.label)
    .setMessage("Android handles system apps in App info. You may be able to disable this app there.")
    .setPositiveButton("Open App info",(d,w)->openAppInfo(a)).setNegativeButton("Cancel",null).show();return;}
  Intent uninstall=new Intent(Intent.ACTION_DELETE,Uri.parse("package:"+pkg));
  startActivity(uninstall); // Android displays its own uninstall confirmation. Never silent-delete.
 }catch(Exception e){toast("Android couldn't open uninstall. Try App info.");}}
'''
if mark not in s:raise RuntimeError('insert interactions marker missing')
s=s.replace(mark,new+mark,1)

# Replace in-grid long-click override only (preserve dock and drawer menus).
patch('if(!key.startsWith("folder:")&&a!=null){ tile.setOnLongClickListener(v->{startMove(v,pg,key);return true;});enableTileDrop(tile,pg,key);}',
      'if(!key.startsWith("folder:")&&a!=null){homeIconInteractions(tile,a,pg);enableTileDrop(tile,pg,key);}', 'home icon interactions')

# Show Home: page manager, title and dynamic grid, instructions, page indicator.
patch(' void showHome(){if(theme==null)return;base("home",theme,false);',
      ' void showHome(){if(theme==null)return;homePage=Math.max(0,Math.min(homePage,pageIds().size()-1));base("home",theme,false);', 'page clamp')
patch('  top.addView(button("Themes",()->showLibrary()));top.addView(button("⚙",()->showSettings()));spaced(page,top,0);',
      '  top.addView(button("Pages",()->showPagesMenu()));top.addView(button("Themes",()->showLibrary()));top.addView(button("⚙",()->showSettings()));spaced(page,top,0);', 'page manager')
patch('   TextView title=text("NOVA\'S APPS",32);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);',
      '   TextView title=text(homePage==1?"NOVA\'S APPS":pageName(currentPageId()).toUpperCase(Locale.ROOT),30);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);', 'custom page title')
patch('  ArrayList<AppEntry> show=homePage==0?entries("favorites"):novaEntries();',
      '  ArrayList<AppEntry> show=homePage==0?entries("favorites"):homePage==1?novaEntries():entries("page_apps:"+currentPageId());', 'custom page entries')
patch('  }else addOrganizedGrid(grid,"home");',
      '  }else if(homePage==0)addOrganizedGrid(grid,"home");else addOrganizedGrid(grid,currentPageId());', 'custom grid')
patch('  TextView pageHint=text(homePage==0?"●  ○     Swipe left: Nova Apps   ·   Swipe up: All apps":\n                    "○  ●     Swipe right: Home   ·   Swipe up: All apps",11);\n  pageHint.setTextColor(theme.accent);pageHint.setGravity(Gravity.CENTER);\n  pageHint.setContentDescription(homePage==0?"Home page. Swipe left for Nova Apps or up for All apps.":\n                                        "Nova Apps page. Swipe right for Home or up for All apps.");\n  spaced(page,pageHint,3);',
      '''  StringBuilder dots=new StringBuilder();for(int i=0;i<pageIds().size();i++){dots.append(i==homePage?"●":"○").append("  ");}
  TextView pageHint=text(dots+"  "+pageName(currentPageId())+"  ·  Tap for pages  ·  ↑ All apps",11);
  pageHint.setTextColor(theme.accent);pageHint.setGravity(Gravity.CENTER);pageHint.setMinHeight(dp(37));
  pageHint.setContentDescription(pageName(currentPageId())+". Page "+(homePage+1)+" of "+pageIds().size()+". Swipe left or right to switch pages. Tap to manage pages. Swipe up for All apps.");
  pageHint.setOnClickListener(v->showPagesMenu());
  spaced(page,pageHint,3);''', 'page indicator')

# Folder menu now includes app uninstall action.
patch('new String[]{"Customize icon","Move out of "+f.name,"Move to a different folder","App info"}',
      'new String[]{"Customize icon","Move out of "+f.name,"Move to a different folder","Move to another page","Uninstall app","App info"}', 'folder menu')
patch('   if(n==0)customize(a);else if(n==1)folderRemove(id,a.key);else if(n==2)chooseFolderForApp(a,f.page);\n   else try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+a.component.getPackageName())));}catch(Exception e){toast("App settings unavailable");}',
      '   if(n==0)customize(a);else if(n==1)folderRemove(id,a.key);else if(n==2)chooseFolderForApp(a,f.page);\n   else if(n==3)choosePageForApp(a,f.page);else if(n==4)uninstallApp(a);else openAppInfo(a);', 'folder menu actions')
# Drawer options: insert Add to custom page and Uninstall.
patch('"Move to first Home position","Move to a themed folder",stored("drawer_hidden").contains(a.key)?"Show in All apps":"Hide from All apps","App info"};',
      '"Move to first Home position","Move to a themed folder","Add to named page",stored("drawer_hidden").contains(a.key)?"Show in All apps":"Hide from All apps","Uninstall app","App info"};', 'drawer menu')
patch('    case 6:{ArrayList<String> hidden=stored("drawer_hidden");if(hidden.contains(a.key))hidden.remove(a.key);else hidden.add(a.key);save("drawer_hidden",hidden);if(screen.equals("drawer"))refreshDrawer();else toast("App drawer updated; restore from Settings anytime.");break;}\n    case 7:\n     try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,\n       Uri.parse("package:"+a.component.getPackageName())));}\n     catch(Exception e){toast("App settings unavailable.");}break;',
      '    case 6:choosePageForApp(a,null);break;\n    case 7:{ArrayList<String> hidden=stored("drawer_hidden");if(hidden.contains(a.key))hidden.remove(a.key);else hidden.add(a.key);save("drawer_hidden",hidden);if(screen.equals("drawer"))refreshDrawer();else toast("App drawer updated; restore from Settings anytime.");break;}\n    case 8:uninstallApp(a);break;\n    case 9:openAppInfo(a);break;', 'drawer actions')

patch('  spaced(content,button("Organize Nova Apps and folders",()->{homePage=1;showHome();}),10);',
      '  spaced(content,button("Organize Nova Apps and folders",()->{homePage=1;showHome();}),10);\n  spaced(content,button("Create or manage named Home pages",()->showPagesMenu()),10);', 'settings pages')
patch('NOVA THEMES 2  /  2.0.10 TEST','NOVA THEMES 2  /  2.0.11','settings version')

patch('boolean backupKey(String name){for(String k:backupKeys)if(k.equals(name))return true;return name.startsWith("icon:")&&name.length()<200;}',
      'boolean backupKey(String name){for(String k:backupKeys)if(k.equals(name))return true;return (name.startsWith("icon:")||name.startsWith("page_apps:page:")||name.startsWith("order:page:"))&&name.length()<200;}', 'backup filters')

# Dynamic swipe navigation. HOME/NOVA kept constant to protect existing GestureGate tests,
# but now mean previous/next page in a sequence rather than hard-coded 0/1.
patch('    if(gesture==NavigationGestures.HOME){homePage=0;showHome();}\n    else if(gesture==NavigationGestures.NOVA){homePage=1;showHome();}',
      '    if(gesture==NavigationGestures.HOME){homePage=Math.max(0,homePage-1);showHome();}\n    else if(gesture==NavigationGestures.NOVA){homePage=Math.min(pageIds().size()-1,homePage+1);showHome();}', 'navigation dispatch')
src.write_text(s)

nav=root/'android/src/com/novasky/themes/NavigationGestures.java'
t=nav.read_text();old='    if(dx<0&&page==0)return NOVA;\n    if(dx>0&&page==1)return HOME;';new='    if(dx<0&&page>=0)return NOVA; // next page (bounded by Activity)\n    if(dx>0&&page>0)return HOME; // previous page'
assert t.count(old)==1;t=t.replace(old,new);nav.write_text(t)
manifest=root/'android/AndroidManifest.xml';m=manifest.read_text();assert 'android:versionCode="210" android:versionName="2.0.10-mp4-test"' in m;m=m.replace('android:versionCode="210" android:versionName="2.0.10-mp4-test"','android:versionCode="211" android:versionName="2.0.11"');manifest.write_text(m)

# Add host-side regression assertions to tested gesture policy.
test=root/'tests/NavigationGesturesTest.java';s=test.read_text()
s=s.replace('assertEq(swipe("home",1,650,600,320,610,false,false,false),NavigationGestures.NONE,"ignore already nova");',
'''assertEq(swipe("home",1,650,600,320,610,false,false,false),NavigationGestures.NOVA,"Nova Apps can continue to named pages");
  assertEq(swipe("home",2,650,600,320,610,false,false,false),NavigationGestures.NOVA,"third page can advance");
  assertEq(swipe("home",3,140,600,490,610,false,false,false),NavigationGestures.HOME,"custom page swipes back");''')
test.write_text(s)

# Sanity. Java block char balance is crude but catches accidental structural errors.
s=src.read_text();assert '2.0.11' in manifest.read_text();assert s.count('{')==s.count('}'),(s.count('{'),s.count('}'))
print('patched',src,'bytes',len(s),'new navigation',nav.read_text().count('next page'))