package com.novasky.scout;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.media.AudioManager;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.EGLSurface;
import android.view.Surface;
import android.util.TypedValue;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class ScoutActivity extends Activity {
    static final int BG=0xFF100B19, PANEL=0xFF20172B, PURPLE=0xFFBD78EF, WHITE=0xFFFBF6FF, MUTED=0xFFC6BDD0;
    static final int SAVE_REQUEST=44;
    private TextView reportView, statusView, summaryView;
    private Button scanButton, sendButton, saveButton, copyButton;
    private String currentReport="";
    private JSONObject lastReport;
    private boolean busy=false;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        ScrollView sv=new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(18),dp(18),dp(28));
        root.setFitsSystemWindows(true);
        sv.addView(root);
        setContentView(sv);
        TextView small=label("NOVA // DIAGNOSTICS",12,PURPLE,true);root.addView(small);
        TextView heading=label("Device Scout",32,WHITE,true);root.addView(heading);
        TextView sub=label("A field guide to your Android. Offline. Read-only. You control what leaves this phone.",14,MUTED,false);root.addView(sub);
        space(root,10);
        summaryView=label("Tap SCAN MY PHONE to gather system, boot, graphics, display, media, sensors and hardware capabilities.",14,WHITE,false);
        summaryView.setPadding(dp(14),dp(14),dp(14),dp(14));summaryView.setBackground(round(PANEL));root.addView(summaryView);
        space(root,12);
        scanButton=button("SCAN MY PHONE",true);root.addView(scanButton);
        scanButton.setOnClickListener(v->scan());
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        sendButton=button("SHARE REPORT",true);saveButton=button("SAVE JSON",false);copyButton=button("COPY",false);
        addWeighted(row,sendButton);addWeighted(row,saveButton);addWeighted(row,copyButton);root.addView(row);
        sendButton.setOnClickListener(v->share());saveButton.setOnClickListener(v->save());copyButton.setOnClickListener(v->copy());
        space(root,12);
        statusView=label("Nothing scanned yet.",13,MUTED,false);root.addView(statusView);
        space(root,6);
        TextView privacy=label("PRIVACY: No internet permission, no uploads, no IMEI/serial/Android ID, no contacts, accounts, location, messages, photos or installed-app inventory. The exported report does include system build information and hardware capabilities. Review it before sharing.",12,MUTED,false);
        privacy.setPadding(dp(10),dp(10),dp(10),dp(10));privacy.setBackground(round(PANEL));root.addView(privacy);
        space(root,12);
        TextView reportTitle=label("FULL TECHNICAL REPORT",12,PURPLE,true);root.addView(reportTitle);
        reportView=label("{\n  \"status\": \"scan pending\"\n}",12,WHITE,false);
        reportView.setTextIsSelectable(true);
        reportView.setTypeface(Typeface.MONOSPACE);
        reportView.setPadding(dp(12),dp(14),dp(12),dp(20));reportView.setBackground(round(PANEL));root.addView(reportView);
        updateButtons();
    }
    private android.graphics.drawable.GradientDrawable round(int c){android.graphics.drawable.GradientDrawable gd=new android.graphics.drawable.GradientDrawable();gd.setColor(c);gd.setCornerRadius(dp(14));return gd;}
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private TextView label(String s,int sp,int c,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(TypedValue.COMPLEX_UNIT_SP,sp);t.setTextColor(c);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setLineSpacing(dp(2),1.05f);return t;}
    private Button button(String text,boolean primary){Button b=new Button(this);b.setText(text);b.setTextSize(TypedValue.COMPLEX_UNIT_SP,11);b.setAllCaps(false);b.setTextColor(primary?Color.BLACK:WHITE);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primary?PURPLE:0xFF44324F));b.setPadding(0,0,0,0);b.setMinHeight(dp(52));return b;}
    private void addWeighted(LinearLayout p,Button b){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(52),1);lp.setMargins(dp(1),dp(4),dp(1),dp(4));p.addView(b,lp);}
    private void space(LinearLayout l,int h){TextView z=new TextView(this);l.addView(z,new LinearLayout.LayoutParams(1,dp(h)));}
    private void updateButtons(){boolean has=!currentReport.isEmpty();scanButton.setEnabled(!busy);sendButton.setEnabled(has&&!busy);saveButton.setEnabled(has&&!busy);copyButton.setEnabled(has&&!busy);}
    private void scan(){if(busy)return;busy=true;statusView.setText("Examining device capabilities…");scanButton.setText("SCANNING…");updateButtons();new Thread(()->{
        JSONObject result;try{result=collect();}catch(Exception e){result=new JSONObject();put(result,"scan_error",e.toString());}
        final JSONObject ready=result;
        runOnUiThread(()->{lastReport=ready;try{currentReport=ready.toString(2);}catch(Exception formattingError){currentReport=ready.toString();}reportView.setText(currentReport);busy=false;scanButton.setText("SCAN AGAIN");statusView.setText("Scan finished · report stays on your phone until you choose to export it.");
            JSONObject b=ready.optJSONObject("boot_research"),d=ready.optJSONObject("device");
            summaryView.setText("Device: "+(d==null?"Unknown":d.optString("manufacturer","?")+" "+d.optString("model","?"))+"\nAndroid: "+(d==null?"?":d.optString("android_release","?"))+"\nBootloader/verified boot: "+(b==null?"Unknown":b.optString("status_interpretation","see detailed report"))+"\n\nSections: "+ready.length()+" · Ready to SHARE or SAVE JSON.");updateButtons();});
    }).start();}
    private void share(){if(currentReport.isEmpty())return;Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_SUBJECT,"Nova Device Scout report");i.putExtra(Intent.EXTRA_TEXT,currentReport);startActivity(Intent.createChooser(i,"Share device report"));}
    private void copy(){if(currentReport.isEmpty())return;((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Nova Device Scout JSON",currentReport));Toast.makeText(this,"Report copied",Toast.LENGTH_SHORT).show();}
    private void save(){if(currentReport.isEmpty())return;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"nova-device-scout-"+new SimpleDateFormat("yyyyMMdd-HHmm",Locale.US).format(new Date())+".json");startActivityForResult(i,SAVE_REQUEST);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==SAVE_REQUEST&&result==RESULT_OK&&data!=null&&data.getData()!=null){try(OutputStream o=getContentResolver().openOutputStream(data.getData())){if(o==null)throw new Exception("No output stream");o.write(currentReport.getBytes(java.nio.charset.StandardCharsets.UTF_8));Toast.makeText(this,"JSON saved",Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(this,"Save failed: "+e.getMessage(),Toast.LENGTH_LONG).show();}}}
    private void put(JSONObject j,String k,Object v){try{j.put(k,v==null?JSONObject.NULL:v);}catch(Exception ignored){}}
    private void runSection(JSONObject root,String name,Collector c){try{JSONObject section=new JSONObject();c.run(section);put(root,name,section);}catch(Throwable ex){JSONObject failed=new JSONObject();put(failed,"unavailable_reason",ex.getClass().getSimpleName()+": "+ex.getMessage());put(root,name,failed);}}
    interface Collector{void run(JSONObject dst)throws Exception;}
    private JSONObject collect(){JSONObject r=new JSONObject();put(r,"schema","nova_device_scout_v1");put(r,"generated_at",new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ",Locale.US).format(new Date()));put(r,"data_policy","Read-only local scan. No device identifiers, personal content, network addresses, accounts, app lists or installed package inventory. Manually shared only.");
        runSection(r,"device",j->{put(j,"manufacturer",Build.MANUFACTURER);put(j,"brand",Build.BRAND);put(j,"model",Build.MODEL);put(j,"device",Build.DEVICE);put(j,"product",Build.PRODUCT);put(j,"board",Build.BOARD);put(j,"hardware",Build.HARDWARE);put(j,"bootloader_version",Build.BOOTLOADER);put(j,"android_release",Build.VERSION.RELEASE);put(j,"sdk_int",Build.VERSION.SDK_INT);put(j,"security_patch",Build.VERSION.SECURITY_PATCH);put(j,"build_id",Build.ID);put(j,"build_display",Build.DISPLAY);put(j,"build_type",Build.TYPE);put(j,"build_tags",Build.TAGS);put(j,"build_fingerprint",Build.FINGERPRINT);put(j,"supported_abis",arr(Build.SUPPORTED_ABIS));put(j,"supported_32_bit_abis",arr(Build.SUPPORTED_32_BIT_ABIS));put(j,"supported_64_bit_abis",arr(Build.SUPPORTED_64_BIT_ABIS));put(j,"is_64_bit_runtime",android.os.Process.is64Bit());if(Build.VERSION.SDK_INT>=31){put(j,"soc_manufacturer",Build.SOC_MANUFACTURER);put(j,"soc_model",Build.SOC_MODEL);}put(j,"available_processors",Runtime.getRuntime().availableProcessors());put(j,"java_vm",System.getProperty("java.vm.version"));put(j,"timezone",TimeZone.getDefault().getID());put(j,"locale",Locale.getDefault().toLanguageTag());});
        runSection(r,"boot_research",j->{String[] props={"ro.boot.verifiedbootstate","ro.boot.flash.locked","ro.boot.vbmeta.device_state","ro.boot.veritymode","ro.boot.dynamic_partitions","ro.boot.slot_suffix","ro.build.ab_update","ro.virtual_ab.enabled","ro.virtual_ab.compression.enabled","ro.product.first_api_level","ro.boot.bootreason","ro.boot.mode","ro.boot.hardware","init.svc.bootanim","ro.build.version.sdk"};JSONObject p=new JSONObject();for(String k:props){String v=prop(k);put(p,k,v.isEmpty()?"unavailable_or_not_set":v);}put(j,"read_only_system_properties",p);String locked=prop("ro.boot.flash.locked"),state=prop("ro.boot.vbmeta.device_state");put(j,"status_interpretation",locked.equals("1")||state.equalsIgnoreCase("locked")?"Reported locked (not independently verified)":locked.equals("0")||state.equalsIgnoreCase("unlocked")?"Reported unlocked (not independently verified)":"Not determined by accessible Android properties");String[] paths={"/system/media/bootanimation.zip","/product/media/bootanimation.zip","/system/product/media/bootanimation.zip","/vendor/media/bootanimation.zip","/oem/media/bootanimation.zip","/data/local/bootanimation.zip"};JSONArray files=new JSONArray();for(String path:paths){JSONObject o=new JSONObject();put(o,"path",path);File f=new File(path);put(o,"app_can_see_file",f.exists());put(o,"app_can_read_file",f.canRead());put(o,"warning","False may mean hidden by sandbox, not absent");files.put(o);}put(j,"candidate_bootanimation_paths",files);put(j,"su_binary_at_common_paths",new File("/system/bin/su").exists()||new File("/system/xbin/su").exists());put(j,"notes","Stock apps cannot write protected boot partitions. ADB alone normally cannot bypass read-only verified partitions. No root/unlock/writes attempted.");});
        runSection(r,"screen",j->{DisplayMetrics m=new DisplayMetrics();getWindowManager().getDefaultDisplay().getRealMetrics(m);Display dis=getWindowManager().getDefaultDisplay();put(j,"real_width_px",m.widthPixels);put(j,"real_height_px",m.heightPixels);put(j,"density_dpi",m.densityDpi);put(j,"density_scale",m.density);put(j,"scaled_density",m.scaledDensity);put(j,"current_refresh_hz",dis.getRefreshRate());put(j,"rotation",dis.getRotation());JSONArray modes=new JSONArray();for(Display.Mode mode:dis.getSupportedModes()){JSONObject x=new JSONObject();put(x,"width",mode.getPhysicalWidth());put(x,"height",mode.getPhysicalHeight());put(x,"refresh_hz",mode.getRefreshRate());modes.put(x);}put(j,"supported_modes",modes);Configuration c=getResources().getConfiguration();put(j,"screen_layout",c.screenLayout);put(j,"orientation",c.orientation);put(j,"smallest_width_dp",c.smallestScreenWidthDp);put(j,"ui_mode",c.uiMode);if(Build.VERSION.SDK_INT>=26){Display.HdrCapabilities hdr=dis.getHdrCapabilities();put(j,"hdr_types",arr(hdr.getSupportedHdrTypes()));put(j,"wide_color_gamut",dis.isWideColorGamut());}if(Build.VERSION.SDK_INT>=28){WindowInsets wi=getWindow().getDecorView().getRootWindowInsets();if(wi!=null&&wi.getDisplayCutout()!=null)put(j,"display_cutout_present",true);else put(j,"display_cutout_present","unknown_or_none");}});
        runSection(r,"memory_storage",j->{ActivityManager a=(ActivityManager)getSystemService(ACTIVITY_SERVICE);ActivityManager.MemoryInfo info=new ActivityManager.MemoryInfo();a.getMemoryInfo(info);put(j,"ram_total_bytes",info.totalMem);put(j,"ram_available_bytes",info.availMem);put(j,"ram_low_memory",info.lowMemory);put(j,"memory_class_mb",a.getMemoryClass());put(j,"large_memory_class_mb",a.getLargeMemoryClass());StatFs s=new StatFs(Environment.getDataDirectory().getAbsolutePath());put(j,"internal_storage_total_bytes",s.getTotalBytes());put(j,"internal_storage_available_bytes",s.getAvailableBytes());put(j,"external_storage_state",Environment.getExternalStorageState());put(j,"app_storage_directory_bytes",getFilesDir().getFreeSpace());put(j,"uptime_minutes",SystemClock.elapsedRealtime()/60000);});
        runSection(r,"battery_power",j->{Intent b=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));if(b!=null){int level=b.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=b.getIntExtra(BatteryManager.EXTRA_SCALE,-1);if(scale>0)put(j,"battery_percent",level*100.0/scale);put(j,"battery_status_code",b.getIntExtra(BatteryManager.EXTRA_STATUS,-1));put(j,"battery_health_code",b.getIntExtra(BatteryManager.EXTRA_HEALTH,-1));put(j,"plugged_code",b.getIntExtra(BatteryManager.EXTRA_PLUGGED,-1));put(j,"battery_temp_tenths_celsius",b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,-1));put(j,"voltage_mv",b.getIntExtra(BatteryManager.EXTRA_VOLTAGE,-1));put(j,"technology",b.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY));}PowerManager p=(PowerManager)getSystemService(POWER_SERVICE);put(j,"power_save_mode",p.isPowerSaveMode());put(j,"interactive",p.isInteractive());put(j,"is_device_idle_mode",p.isDeviceIdleMode());BatteryManager manager=(BatteryManager)getSystemService(BATTERY_SERVICE);put(j,"estimated_charge_counter_microamp_hours",manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER));});
        runSection(r,"sensors",j->{SensorManager s=(SensorManager)getSystemService(SENSOR_SERVICE);JSONArray a=new JSONArray();for(Sensor z:s.getSensorList(Sensor.TYPE_ALL)){JSONObject o=new JSONObject();put(o,"name",z.getName());put(o,"vendor",z.getVendor());put(o,"type",z.getType());put(o,"string_type",z.getStringType());put(o,"power_ma",z.getPower());put(o,"min_delay_microseconds",z.getMinDelay());a.put(o);}put(j,"available",a);put(j,"count",a.length());});
        runSection(r,"cameras",j->{CameraManager mgr=(CameraManager)getSystemService(CAMERA_SERVICE);JSONArray a=new JSONArray();for(String id:mgr.getCameraIdList()){JSONObject o=new JSONObject();put(o,"camera_id",id);try{CameraCharacteristics c=mgr.getCameraCharacteristics(id);put(o,"lens_facing_code",c.get(CameraCharacteristics.LENS_FACING));put(o,"hardware_support_level",c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL));put(o,"flash_available",c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE));put(o,"sensor_orientation",c.get(CameraCharacteristics.SENSOR_ORIENTATION));float[] fl=c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);if(fl!=null)put(o,"focal_lengths_mm",arr(fl));}catch(Exception e){put(o,"limited_access",e.getClass().getSimpleName());}a.put(o);}put(j,"camera_count_visible",a.length());put(j,"cameras",a);});
        runSection(r,"multimedia_codecs",j->{MediaCodecList l=new MediaCodecList(MediaCodecList.REGULAR_CODECS);JSONArray a=new JSONArray();int total=0;for(MediaCodecInfo ci:l.getCodecInfos()){if(total++>=130)break;JSONObject o=new JSONObject();put(o,"name",ci.getName());put(o,"encoder",ci.isEncoder());JSONArray types=new JSONArray();for(String type:ci.getSupportedTypes())types.put(type);put(o,"mime_types",types);a.put(o);}put(j,"codecs",a);put(j,"visible_codec_count",l.getCodecInfos().length);put(j,"truncated_to_first",130);});
        runSection(r,"graphics",j->{JSONObject egl=probeGl();for(java.util.Iterator<String>it=egl.keys();it.hasNext();){String k=it.next();put(j,k,egl.opt(k));}});
        runSection(r,"android_features",j->{PackageManager pm=getPackageManager();FeatureInfo[] f=pm.getSystemAvailableFeatures();JSONArray arr=new JSONArray();if(f!=null){Arrays.sort(f,(a,b)->String.valueOf(a.name).compareTo(String.valueOf(b.name)));for(FeatureInfo x:f)if(x.name!=null)arr.put(x.name);}put(j,"declared_system_features",arr);put(j,"camera",pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY));put(j,"nfc",pm.hasSystemFeature(PackageManager.FEATURE_NFC));put(j,"bluetooth_le",pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE));put(j,"wifi_direct",pm.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT));put(j,"live_wallpaper",pm.hasSystemFeature(PackageManager.FEATURE_LIVE_WALLPAPER));put(j,"fingerprint",pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT));put(j,"telephony",pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY));put(j,"touchscreen_multitouch",pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH));});
        runSection(r,"system_services",j->{AudioManager a=(AudioManager)getSystemService(AUDIO_SERVICE);put(j,"audio_output_sample_rate",a.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE));put(j,"audio_frames_per_buffer",a.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER));KeyguardManager km=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);put(j,"device_secure",km.isDeviceSecure());put(j,"keyguard_locked_during_scan",km.isKeyguardLocked());put(j,"default_font_scale",getResources().getConfiguration().fontScale);put(j,"notification_listener_permission_granted","not requested");put(j,"accessibility_permission_granted","not requested");put(j,"overlay_permission_granted",Settings.canDrawOverlays(this));put(j,"write_system_settings_granted",Settings.System.canWrite(this));});
        runSection(r,"app",j->{put(j,"name","Nova Device Scout");put(j,"version","1.0.0");put(j,"package_name",getPackageName());put(j,"app_target_sdk",getApplicationInfo().targetSdkVersion);put(j,"required_runtime_permissions","none");put(j,"internet_permission","not declared");put(j,"data_collected_from_user_apps","none");put(j,"operates_as_root",false);});
        return r;}
    private JSONArray arr(String[] a){JSONArray r=new JSONArray();for(String x:a)r.put(x);return r;}
    private JSONArray arr(int[] a){JSONArray r=new JSONArray();for(int x:a)r.put(x);return r;}
    private JSONArray arr(float[] a){JSONArray r=new JSONArray();for(float x:a)r.put(x);return r;}
    private String prop(String key){try{Process p=new ProcessBuilder("/system/bin/getprop",key).redirectErrorStream(true).start();BufferedReader r=new BufferedReader(new InputStreamReader(p.getInputStream()));String line=r.readLine();r.close();p.waitFor();return line==null?"":line.trim();}catch(Exception e){return "";}}
    private JSONObject probeGl(){JSONObject j=new JSONObject();EGLDisplay display=EGL14.EGL_NO_DISPLAY;EGLSurface surface=EGL14.EGL_NO_SURFACE;EGLContext context=EGL14.EGL_NO_CONTEXT;try{display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);int[] versions=new int[2];if(!EGL14.eglInitialize(display,versions,0,versions,1))throw new Exception("EGL initialize failed");int[] attrs={EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,EGL14.EGL_NONE};EGLConfig[] configs=new EGLConfig[1];int[] n=new int[1];if(!EGL14.eglChooseConfig(display,attrs,0,configs,0,1,n,0)||n[0]==0)throw new Exception("EGL config unavailable");int[] pbuf={EGL14.EGL_WIDTH,1,EGL14.EGL_HEIGHT,1,EGL14.EGL_NONE};surface=EGL14.eglCreatePbufferSurface(display,configs[0],pbuf,0);int[] ctx={EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE};context=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,ctx,0);if(!EGL14.eglMakeCurrent(display,surface,surface,context))throw new Exception("EGL make current failed");put(j,"gpu_vendor",GLES20.glGetString(GLES20.GL_VENDOR));put(j,"gpu_renderer",GLES20.glGetString(GLES20.GL_RENDERER));put(j,"opengl_version",GLES20.glGetString(GLES20.GL_VERSION));put(j,"glsl_version",GLES20.glGetString(GLES20.GL_SHADING_LANGUAGE_VERSION));int[] max=new int[1];GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE,max,0);put(j,"max_texture_size",max[0]);put(j,"egl_version",versions[0]+"."+versions[1]);}catch(Exception e){put(j,"gpu_probe_unavailable",e.toString());}finally{try{if(display!=EGL14.EGL_NO_DISPLAY){EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);if(surface!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,surface);if(context!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,context);EGL14.eglTerminate(display);}}catch(Exception ignored){}}return j;}
}