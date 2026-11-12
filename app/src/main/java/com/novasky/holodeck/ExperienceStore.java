package com.novasky.holodeck;

import android.content.*;
import android.content.pm.*;
import android.os.Build;
import android.util.AtomicFile;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.util.*;
import java.util.zip.*;

/** Native package registry. Web content never receives a filesystem or network API. */
final class ExperienceStore {
 static final String REPO="https://github.com/harbolddavid-wq/nova-starfall-defense/releases/download/";
 static final String CATALOG=REPO+"holodeck-worlds/catalog.json";
 static final byte[] DOMAIN="NOVA-HOLODECK-CATALOG-V1\n".getBytes(StandardCharsets.UTF_8);
 static final int MAX_ZIP=16*1024*1024,MAX_UNPACKED=32*1024*1024,MAX_FILE=8*1024*1024,MAX_FILES=128;
 final Context context;final File root;volatile JSONObject catalog;volatile long revision=0;
 interface Progress{void update(int percent);}
 static final class World {
  final JSONObject metadata;final String id,title,subtitle,description,entry,version;final int versionCode;final File directory;
  World(JSONObject m,File d)throws JSONException{metadata=m;id=m.getString("id");title=m.getString("title");subtitle=m.optString("subtitle");description=m.optString("description");entry=m.optString("entry","index.html");versionCode=m.getInt("versionCode");version=m.getString("version");directory=d;}
  String origin(){return "https://"+id+".holodeck.invalid";}
 }
 ExperienceStore(Context c){context=c.getApplicationContext();root=new File(c.getFilesDir(),"worlds");root.mkdirs();try{JSONObject envelope=readAtomicJson(new File(root,"catalog.json"));catalog=verify(envelope);revision=catalog.getLong("revision");}catch(Exception ignored){}}
 static boolean id(String s){return s!=null&&s.matches("[a-z][a-z0-9-]{1,47}");}
 static boolean path(String s){if(s==null||s.length()>160||!s.matches("[A-Za-z0-9_./-]+")||s.startsWith("/")||s.endsWith("/"))return false;for(String p:s.split("/",-1))if(p.isEmpty()||p.equals(".")||p.equals(".."))return false;return true;}
 static String hash(byte[] b)throws Exception{byte[] d=MessageDigest.getInstance("SHA-256").digest(b);StringBuilder s=new StringBuilder();for(byte x:d)s.append(String.format(Locale.ROOT,"%02x",x&255));return s.toString();}
 static byte[] bytes(InputStream in,int limit)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();if(out.size()+n>limit)throw new IOException("Size limit exceeded");out.write(b,0,n);}return out.toByteArray();}
 static JSONObject readJson(File f)throws Exception{try(InputStream in=new FileInputStream(f)){return new JSONObject(new String(bytes(in,256*1024),StandardCharsets.UTF_8));}}
 static synchronized JSONObject readAtomicJson(File f)throws Exception{try(InputStream in=new AtomicFile(f).openRead()){return new JSONObject(new String(bytes(in,256*1024),StandardCharsets.UTF_8));}}
 static synchronized void atomicJson(File f,JSONObject json)throws IOException{f.getParentFile().mkdirs();AtomicFile a=new AtomicFile(f);FileOutputStream out=null;try{out=a.startWrite();out.write(json.toString().getBytes(StandardCharsets.UTF_8));a.finishWrite(out);}catch(IOException e){if(out!=null)a.failWrite(out);throw e;}}
 static File child(File base,String p)throws IOException{if(!path(p))throw new IOException("Invalid package path");File f=new File(base,p);if(!f.getCanonicalPath().startsWith(base.getCanonicalPath()+File.separator))throw new IOException("Path escaped package");return f;}
 static void delete(File f){if(f.isDirectory()){File[] all=f.listFiles();if(all!=null)for(File x:all)delete(x);}f.delete();}
 World builtin(){try{return new World(new JSONObject("{\"id\":\"starwell\",\"title\":\"Starwell\",\"subtitle\":\"The Listening Sky\",\"description\":\"Wake three constellations and leave a little light behind.\",\"entry\":\"index.html\",\"versionCode\":1,\"version\":\"1.0.0\"}"),null);}catch(JSONException impossible){throw new IllegalStateException(impossible);}}
 File pointer(String id){return new File(new File(root,id),"installed.json");}
 World fromPointer(String key,String id)throws Exception{JSONObject state=readAtomicJson(pointer(id)),m=state.getJSONObject(key);String dir=m.getString("directory");if(!dir.matches("v[0-9]+-[a-f0-9-]+"))throw new IOException("Invalid installed version");File d=child(new File(new File(root,id),"versions"),dir);if(!d.isDirectory()||!child(d,m.optString("entry","index.html")).isFile())throw new IOException("Missing installed files");return new World(m,d);}
 World installed(String id){if(!id(id))return null;try{return fromPointer("active",id);}catch(Exception ignored){return id.equals("starwell")?builtin():null;}}
 List<World> installed(){ArrayList<World> result=new ArrayList<>();result.add(installed("starwell"));File[] ids=root.listFiles(File::isDirectory);if(ids!=null)for(File d:ids){if(!d.getName().equals("starwell")&&id(d.getName())){World w=installed(d.getName());if(w!=null)result.add(w);}}return result;}
 boolean previous(String id){try{return fromPointer("previous",id)!=null;}catch(Exception e){return false;}}
 void rollback(String id)throws Exception{World prior=fromPointer("previous",id);JSONObject s=readAtomicJson(pointer(id)),old=s.getJSONObject("active");s.put("active",prior.metadata);s.put("previous",old);atomicJson(pointer(id),s);}
 void remove(String id){if(!id(id))return;delete(new File(root,id));}
 static boolean sourceUrl(String s){try{URI u=new URI(s);return "https".equals(u.getScheme())&&"github.com".equals(u.getHost())&&u.getUserInfo()==null&&(u.getPort()==-1||u.getPort()==443)&&u.getPath().startsWith("/harbolddavid-wq/nova-starfall-defense/releases/download/")&&u.getFragment()==null;}catch(Exception e){return false;}}
 static boolean redirectUrl(URL u){return u.getProtocol().equals("https")&&(u.getPort()==-1||u.getPort()==443)&&u.getUserInfo()==null&&Arrays.asList("github.com","release-assets.githubusercontent.com","objects.githubusercontent.com").contains(u.getHost());}
 static byte[] fetch(String address,int max,int expected,Progress progress)throws Exception{
  if(!sourceUrl(address))throw new IOException("Untrusted download address");URL url=new URL(address);
  for(int redirects=0;redirects<5;redirects++){
   if(!redirectUrl(url))throw new IOException("Untrusted download redirect");
   HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(25000);c.setRequestProperty("User-Agent","Nova-Pocket-Holodeck/0.2");c.setRequestProperty("Cache-Control","no-cache");
   try{int status=c.getResponseCode();if(status>=300&&status<400){String next=c.getHeaderField("Location");if(next==null)throw new IOException("Missing redirect");url=new URL(url,next);continue;}if(status!=200)throw new IOException("Download unavailable ("+status+")");
    long len=c.getContentLengthLong();if(len>max)throw new IOException("Download too large");ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] b=new byte[16384];int n,last=-1;while((n=in.read(b))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();if(out.size()+n>max)throw new IOException("Download too large");out.write(b,0,n);if(progress!=null&&expected>0){int p=Math.min(95,out.size()*95/expected);if(p!=last){last=p;progress.update(p);}}}}return out.toByteArray();
   }finally{c.disconnect();}
  }throw new IOException("Too many redirects");
 }
 JSONObject verify(JSONObject envelope)throws Exception{
  if(!"SHA256withRSA".equals(envelope.getString("algorithm")))throw new SecurityException("Unknown signature scheme");String b64=envelope.getString("payload");if(b64.length()>180000)throw new SecurityException("Catalog too large");byte[] data=Base64.decode(b64,Base64.DEFAULT),sig=Base64.decode(envelope.getString("signature"),Base64.DEFAULT);if(data.length>128*1024||sig.length>1024)throw new SecurityException("Invalid catalog size");
  PackageManager pm=context.getPackageManager();PackageInfo p=pm.getPackageInfo(context.getPackageName(),Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES);
  android.content.pm.Signature[] signers=Build.VERSION.SDK_INT>=28?p.signingInfo.getApkContentsSigners():p.signatures;boolean valid=false;
  for(android.content.pm.Signature signer:signers){PublicKey key=CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(signer.toByteArray())).getPublicKey();java.security.Signature v=java.security.Signature.getInstance("SHA256withRSA");v.initVerify(key);v.update(DOMAIN);v.update(data);if(v.verify(sig)){valid=true;break;}}
  if(!valid)throw new SecurityException("Catalog signature mismatch");JSONObject body=new JSONObject(new String(data,StandardCharsets.UTF_8));if(body.getInt("schema")!=1||!body.getString("publisher").equals("nova-pocket-holodeck")||body.getInt("runtime")!=1||body.getInt("minHostVersion")>BuildConfig.VERSION_CODE||body.getLong("revision")<1)throw new SecurityException("Incompatible catalog");
  JSONArray worlds=body.getJSONArray("experiences");if(worlds.length()>64)throw new SecurityException("Catalog too large");Set<String> ids=new HashSet<>();for(int i=0;i<worlds.length();i++){JSONObject m=worlds.getJSONObject(i);validateMetadata(m);if(!ids.add(m.getString("id")))throw new SecurityException("Duplicate experience");if(!sourceUrl(m.getString("url"))||!m.getString("sha256").matches("[a-f0-9]{64}")||m.getInt("bytes")<1||m.getInt("bytes")>MAX_ZIP)throw new SecurityException("Invalid download metadata");}return body;
 }
 static void validateMetadata(JSONObject m)throws Exception{if(!id(m.getString("id"))||m.getInt("versionCode")<1||m.getInt("runtime")!=1||m.getString("version").length()>32||m.getString("title").length()>64||m.optString("description").length()>1000||m.optString("subtitle").length()>100||!path(m.getString("entry"))||!m.getString("entry").endsWith(".html"))throw new SecurityException("Invalid world metadata");}
 JSONObject refresh()throws Exception{byte[] data=fetch(CATALOG+"?refresh="+System.currentTimeMillis(),256*1024,0,null);JSONObject envelope=new JSONObject(new String(data,StandardCharsets.UTF_8)),body=verify(envelope);long next=body.getLong("revision");if(next<revision)throw new SecurityException("Stale catalog");if(next==revision&&catalog!=null&&!catalog.toString().equals(body.toString()))throw new SecurityException("Changed catalog revision");atomicJson(new File(root,"catalog.json"),envelope);catalog=body;revision=next;return body;}
 List<JSONObject> available(){ArrayList<JSONObject> items=new ArrayList<>();if(catalog!=null){JSONArray a=catalog.optJSONArray("experiences");if(a!=null)for(int i=0;i<a.length();i++)items.add(a.optJSONObject(i));}return items;}
 World install(JSONObject entry,Progress progress)throws Exception{validateMetadata(entry);World current=installed(entry.getString("id"));if(current!=null&&current.versionCode>=entry.getInt("versionCode"))throw new IOException("Already installed");byte[] zip=fetch(entry.getString("url"),MAX_ZIP,entry.getInt("bytes"),progress);World w=installZip(entry,zip);if(progress!=null)progress.update(100);return w;}
 World installZip(JSONObject entry,byte[] zip)throws Exception{
  validateMetadata(entry);String id=entry.getString("id");World old=installed(id);if(old!=null&&old.versionCode>=entry.getInt("versionCode"))throw new SecurityException("Version downgrade refused");if(zip.length!=entry.getInt("bytes")||!hash(zip).equals(entry.getString("sha256")))throw new SecurityException("Package checksum mismatch");
  File versions=new File(new File(root,id),"versions");versions.mkdirs();String versionDir="v"+entry.getInt("versionCode")+"-"+UUID.randomUUID();File stage=new File(versions,"staging-"+UUID.randomUUID()),target=new File(versions,versionDir);stage.mkdirs();boolean committed=false;
  try{
   Set<String> seen=new HashSet<>();int total=0;try(ZipInputStream in=new ZipInputStream(new ByteArrayInputStream(zip))){ZipEntry z;while((z=in.getNextEntry())!=null){String name=z.getName();if(z.isDirectory()||!path(name)||!seen.add(name)||seen.size()>MAX_FILES+1)throw new SecurityException("Invalid archive path");byte[] b=bytes(in,MAX_FILE);total+=b.length;if(total>MAX_UNPACKED)throw new SecurityException("Package expands too much");File f=child(stage,name);f.getParentFile().mkdirs();try(FileOutputStream out=new FileOutputStream(f)){out.write(b);}}}
   JSONObject manifest=readJson(child(stage,"experience.json"));validateMetadata(manifest);if(!id.equals(manifest.getString("id"))||manifest.getInt("versionCode")!=entry.getInt("versionCode")||!manifest.getString("entry").equals(entry.getString("entry")))throw new SecurityException("Package identity mismatch");JSONObject files=manifest.getJSONObject("files");if(files.length()<1||files.length()>MAX_FILES||seen.size()!=files.length()+1||!files.has(manifest.getString("entry")))throw new SecurityException("Invalid package inventory");Iterator<String> keys=files.keys();while(keys.hasNext()){String name=keys.next();File f=child(stage,name);JSONObject info=files.getJSONObject(name);if(!seen.contains(name)||!f.isFile()||f.length()!=info.getInt("bytes"))throw new SecurityException("Missing package file");try(InputStream in=new FileInputStream(f)){if(!hash(bytes(in,MAX_FILE)).equals(info.getString("sha256")))throw new SecurityException("Package file mismatch");}}
   if(!stage.renameTo(target))throw new IOException("Could not commit package");JSONObject active=new JSONObject(entry.toString());active.put("directory",versionDir);JSONObject pointer=new JSONObject();pointer.put("active",active);if(old!=null&&old.directory!=null)pointer.put("previous",old.metadata);atomicJson(pointer(id),pointer);committed=true;
   File[] all=versions.listFiles();if(all!=null)for(File f:all)if(!f.equals(target)&&(old==null||old.directory==null||!f.equals(old.directory)))delete(f);
   return new World(active,target);
  }finally{delete(stage);if(!committed)delete(target);}
 }
 InputStream open(World w,String asset)throws Exception{if(!path(asset))throw new IOException("Invalid asset");if(w.directory==null){if(!Arrays.asList("index.html","starwell.js","style.css").contains(asset))throw new IOException("Unknown bundled asset");return context.getAssets().open("starwell/"+asset);}JSONObject manifest=readJson(child(w.directory,"experience.json"));if(!manifest.getJSONObject("files").has(asset))throw new IOException("Unknown world asset");return new FileInputStream(child(w.directory,asset));}
}
