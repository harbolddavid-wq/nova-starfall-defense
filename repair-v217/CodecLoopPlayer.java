package com.nova.lockscapes;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.SystemClock;
import android.view.Surface;
import java.io.File;
import java.nio.ByteBuffer;

/**
 * Frame-paced decoder for a WallpaperService's Surface. Unlike MediaPlayer,
 * MediaCodec never asks a WallpaperService SurfaceHolder to keep the screen on.
 * This worker only runs while the engine is visible, and releases codecs on exit.
 */
final class CodecLoopPlayer {
  interface Events {
    void firstFrame();
    void failed(String detail);
  }
  private final File file;
  private final Surface target;
  private final Events events;
  private volatile boolean running;
  private Thread worker;
  private boolean announced;

  CodecLoopPlayer(File file, Surface target, Events events) {
    this.file=file;this.target=target;this.events=events;
  }
  void start() {
    if(running)return;
    running=true;
    worker=new Thread(this::run,"Lockscapes-hardware-video");
    worker.setDaemon(true);
    worker.start();
  }
  void stop() {
    running=false;
    if(worker!=null)worker.interrupt();
    // The worker owns its MediaCodec and MediaExtractor; release occurs there.
  }
  private void run() {
    MediaExtractor extractor=null;
    MediaCodec codec=null;
    String error=null;
    try {
      if(!target.isValid())throw new IllegalStateException("Wallpaper surface invalid");
      extractor=new MediaExtractor();
      extractor.setDataSource(file.getAbsolutePath());
      int index=-1;
      MediaFormat format=null;
      String mime=null;
      for(int i=0;i<extractor.getTrackCount();i++) {
        MediaFormat f=extractor.getTrackFormat(i);
        String m=f.getString(MediaFormat.KEY_MIME);
        if(m!=null && m.startsWith("video/")){index=i;format=f;mime=m;break;}
      }
      if(index<0||mime==null)throw new IllegalArgumentException("No video track");
      extractor.selectTrack(index);
      codec=MediaCodec.createDecoderByType(mime);
      codec.configure(format,target,null,0);
      codec.start();
      MediaCodec.BufferInfo output=new MediaCodec.BufferInfo();
      boolean eosInput=false;
      long basePresentationUs=-1;
      long cycleStartNs=System.nanoTime();
      while(running && target.isValid()) {
        if(!eosInput) {
          int input=codec.dequeueInputBuffer(12000);
          if(input>=0) {
            ByteBuffer buffer=codec.getInputBuffer(input);
            if(buffer==null)throw new IllegalStateException("Codec input buffer unavailable");
            buffer.clear();
            int bytes=extractor.readSampleData(buffer,0);
            if(bytes<0) {
              codec.queueInputBuffer(input,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);
              eosInput=true;
            } else {
              long pts=extractor.getSampleTime();
              int flags=(extractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC)!=0 ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
              codec.queueInputBuffer(input,0,bytes,pts,flags);
              extractor.advance();
            }
          }
        }
        int indexOut=codec.dequeueOutputBuffer(output,12000);
        if(indexOut>=0) {
          boolean hasPicture=(output.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0 &&
            (output.size>0 || (output.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM)==0);
          if(hasPicture) {
            if(basePresentationUs<0){basePresentationUs=output.presentationTimeUs;cycleStartNs=System.nanoTime();}
            long desired=cycleStartNs+Math.max(0,output.presentationTimeUs-basePresentationUs)*1000L;
            // Render according to the source video timestamps, without blocking the UI thread.
            while(running && target.isValid()) {
              long remain=desired-System.nanoTime();
              if(remain<=0)break;
              SystemClock.sleep(Math.min(12L,Math.max(1L,remain/1000000L)));
            }
          }
          codec.releaseOutputBuffer(indexOut,running&&target.isValid()&&hasPicture);
          if(hasPicture&&!announced&&running){announced=true;events.firstFrame();}
          if((output.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0 && running) {
            // Rewind without destroying the decoder between short wallpaper loops.
            extractor.seekTo(0,MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            codec.flush();
            eosInput=false;
            basePresentationUs=-1;
            cycleStartNs=System.nanoTime();
          }
        } else if(indexOut==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
          // The Surface handles the new output dimensions automatically.
        }
      }
    } catch(Exception e) {
      if(running)error=e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage());
    } finally {
      if(codec!=null){try{codec.stop();}catch(Exception ignored){}try{codec.release();}catch(Exception ignored){}}
      if(extractor!=null)try{extractor.release();}catch(Exception ignored){}
      boolean signal=running;
      running=false;
      if(signal&&error!=null)events.failed(error);
    }
  }
}