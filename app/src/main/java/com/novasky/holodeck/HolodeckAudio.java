package com.novasky.holodeck;

import android.content.Context;
import android.media.*;
import android.os.*;
import android.util.Log;
import java.util.*;

/** Host-owned, bounded musical cues. No JavaScript interfaces or arbitrary Android calls. */
final class HolodeckAudio {
 static final int RATE=44100;
 static final double[] NOTES={523.25,659.25,783.99,987.77,1046.5,1318.51,1567.98};
 final HandlerThread thread=new HandlerThread("Holodeck audio");
 final Handler worker;final AudioManager manager;final AudioFocusRequest focus;
 final ArrayList<AudioTrack> voices=new ArrayList<>();
 AudioTrack bed;boolean enabled=true,foreground=true,scene=false,hasFocus=false,closed=false;
 volatile String status="idle",error="";
 volatile long renderedFrames=0;volatile int acceptedCues=0;
 volatile double toneRms=0,ambientRms=0;long lastCue=0;int generation=0;
 HolodeckAudio(Context c){
  manager=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE);thread.start();worker=new Handler(thread.getLooper());
  focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes()).setOnAudioFocusChangeListener(change->worker.post(()->{
   if(change==AudioManager.AUDIOFOCUS_GAIN){hasFocus=true;if(scene&&enabled&&foreground)startBed();}
   else{hasFocus=false;silence("interrupted");}
  }),worker).build();
 }
 static AudioAttributes attributes(){return new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();}
 static short[] samples(double frequency,boolean ambient){
  int count=ambient?RATE*2:(int)(RATE*.85);short[] pcm=new short[count];
  for(int n=0;n<count;n++){
   double t=n/(double)RATE,v;
   if(ambient){v=.035*(Math.sin(2*Math.PI*330*t)+Math.sin(2*Math.PI*440*t)+Math.sin(2*Math.PI*550*t)+Math.sin(2*Math.PI*660*t));}
   else{double envelope=Math.min(1,t/.012)*Math.exp(-5*t/.85)*Math.min(1,(.85-t)/.04);v=.44*envelope*(Math.sin(2*Math.PI*frequency*t)+.32*Math.sin(4*Math.PI*frequency*t)+.12*Math.sin(6*Math.PI*frequency*t));}
   pcm[n]=(short)Math.round(Math.max(-.95,Math.min(.95,v))*32767);
  }return pcm;
 }
 static double rms(short[] pcm){double v=0;for(short s:pcm)v+=(s/32768.0)*(s/32768.0);return Math.sqrt(v/pcm.length);}
 AudioTrack track(short[] pcm){
  AudioTrack t=new AudioTrack.Builder().setAudioAttributes(attributes()).setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()).setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.length*2).build();
  if(t.getState()!=AudioTrack.STATE_INITIALIZED)throw new IllegalStateException("AudioTrack initialization failed");
  if(t.write(pcm,0,pcm.length)!=pcm.length){t.release();throw new IllegalStateException("Incomplete audio write");}return t;
 }
 void cue(String name){if(!closed)worker.post(()->{
  try{
   if(!foreground||!enabled||closed)return;
   long now=SystemClock.uptimeMillis();if(now-lastCue<45)return;lastCue=now;acceptedCues++;
   if(!ensureFocus())return;
   scene=true;if(bed==null||bed.getPlayState()!=AudioTrack.PLAYSTATE_PLAYING)startBed();
   if(name.equals("start")){startBed();chime();return;}
   if(name.equals("success"))chime();else if(name.matches("note[0-6]"))note(name.charAt(4)-'0');
  }catch(RuntimeException e){failed(e);}
 });}
 boolean ensureFocus(){if(hasFocus)return true;hasFocus=manager.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;if(!hasFocus)status="focus-denied";return hasFocus;}
 void startBed(){
  if(!foreground||!enabled||!scene||!hasFocus||closed)return;
  if(bed==null){short[] pcm=samples(0,true);ambientRms=rms(pcm);bed=track(pcm);bed.setLoopPoints(0,pcm.length,-1);bed.setVolume(.8f);}
  bed.play();status="playing";poll();
 }
 void poll(){worker.removeCallbacks(tick);worker.postDelayed(tick,100);}
 final Runnable tick=new Runnable(){public void run(){if(closed||!foreground||!enabled||!scene)return;try{if(bed!=null&&bed.getPlayState()==AudioTrack.PLAYSTATE_PLAYING){renderedFrames=Math.max(renderedFrames,Integer.toUnsignedLong(bed.getPlaybackHeadPosition()));}if(bed!=null)worker.postDelayed(this,100);}catch(RuntimeException e){failed(e);}}};
 void note(int n){
  if(!foreground||!enabled||!scene||!hasFocus||closed)return;
  while(voices.size()>=2){AudioTrack old=voices.remove(0);old.stop();old.release();}
  short[] pcm=samples(NOTES[n%7],false);toneRms=rms(pcm);AudioTrack voice=track(pcm);voice.setVolume(.7f);voices.add(voice);voice.play();
  worker.postDelayed(()->{if(voices.remove(voice)){voice.stop();voice.release();}},1000);
 }
 void chime(){final int g=generation;for(int n=0;n<3;n++){final int note=n*2;worker.postDelayed(()->{if(g==generation){try{note(note);}catch(RuntimeException e){failed(e);}}},n*140);}}
 void silence(String state){generation++;worker.removeCallbacks(tick);if(bed!=null)bed.pause();for(AudioTrack t:voices){t.stop();t.release();}voices.clear();status=state;}
 void enabled(boolean value){worker.post(()->{enabled=value;try{if(!enabled){silence("muted");abandon();}else if(foreground&&scene&&ensureFocus()){startBed();}}catch(RuntimeException e){failed(e);}});}
 void foreground(boolean value){worker.post(()->{foreground=value;try{if(!value){silence("paused");abandon();}else if(enabled&&scene&&ensureFocus())startBed();}catch(RuntimeException e){failed(e);}});}
 void stopScene(){worker.post(()->{scene=false;silence("idle");abandon();});}
 void abandon(){if(hasFocus)manager.abandonAudioFocusRequest(focus);hasFocus=false;}
 void failed(RuntimeException e){error=e.toString();status="error";Log.e("HolodeckAudio","Playback failed",e);}
 void close(){worker.post(()->{closed=true;scene=false;silence("closed");abandon();if(bed!=null){bed.release();bed=null;}thread.quitSafely();});}
}
