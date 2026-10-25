from pathlib import Path
root=Path(__file__).resolve().parents[1]
m=(root/'app/src/main/java/com/nova/lockscapes/MainActivity.java').read_text()
w=(root/'app/src/main/java/com/nova/lockscapes/Worlds.java').read_text()
s=(root/'app/src/main/java/com/nova/lockscapes/LockscapeWallpaperService.java').read_text()
b=(root/'app/build.gradle').read_text()
checks={
  'versionCode10':'versionCode 10' in b,
  'image library':'getFilesDir(),"imports"' in m and 'importImage' in m,
  'video import':'importVideo(Uri uri)' in m and '"video:"+dest.getName()' in m,
  'video remains local':'getFilesDir(),"videos"' in m,
  'original file preserved':'new FileOutputStream(dest)' in m and 'Bitmap.CompressFormat' not in m,
  'no forbidden keep-awake call':'mp.setSurface(getSurfaceHolder().getSurface())' in s and 'p.setDisplay(holder)' not in s,
  'post-prepare playback settings':s.index('mp.setLooping(true)')>s.index('p.setOnPreparedListener(mp->') and 'mp.setVolume(0f,0f)' in s,
  'loop player':'mp.setLooping(true)' in s,
  'no soundtrack':'mp.setVolume(0f,0f)' in s,
  'pause when hidden':'player.pause()' in s,
  'native wallpaper picker':'ACTION_CHANGE_LIVE_WALLPAPER' in m,
  'picture artwork':all((root/f'app/src/main/assets/art/world_{i:02d}.jpg').is_file() for i in (0,1,3,4,7)),
  'native video':(root/'app/src/main/assets/video/infernal_emberfall.mp4').stat().st_size>50000,
  'signature key separate':not list(root.rglob('*.p12')),
  'staged diagnostics':'Video setup at ' in s and 'setupStage="prepareAsync"' in s,
  'no forbidden early scaling':'p.setVideoScalingMode(' not in s,
  'smooth hardware codec':(root/'app/src/main/java/com/nova/lockscapes/CodecLoopPlayer.java').is_file() and 'codec.releaseOutputBuffer(indexOut' in (root/'app/src/main/java/com/nova/lockscapes/CodecLoopPlayer.java').read_text(),
  'fallback retained':'startFrameFallback()' in s and 'startCodecFallback()' in s,
  'codec stopped when hidden':'stopCodec();main.removeCallbacks(timeout)' in s,
  'no internet': 'INTERNET' not in (root/'app/src/main/AndroidManifest.xml').read_text(),
}
for k,ok in checks.items():print(('PASS' if ok else 'FAIL'),k)
assert all(checks.values())