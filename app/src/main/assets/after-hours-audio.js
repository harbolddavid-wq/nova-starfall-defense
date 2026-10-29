/* Offline music and effects. WebView owns media focus; no competing native
   audio-focus request. Scores change with the scene after the first gesture. */
(function(root){
 "use strict";
 const MUSIC_GAIN=.19,FADE_SECONDS=.7;
 let music=null,scene=0,fadeFrom=null,fadeReady=false,fadeAge=0,musicEpoch=0;
 let enabledMusic=true,enabledSfx=true,paused=false,started=false;
 const pools={},last={};
 try { enabledMusic=localStorage.getItem('ah_music')!=='off'; enabledSfx=localStorage.getItem('ah_sfx')!=='off'; }catch(e){}
 const file={click:'click-b.ogg',switch:'switch-a.ogg',tap:'tap-a.ogg',sling:'sling.ogg',wood:'wood.ogg',glass:'glass.ogg',iron:'iron.ogg',charm:'charm.ogg',power:'power.ogg',win:'win.ogg',voiceHitA:'voice-hit-a.ogg',voiceHitB:'voice-hit-b.ogg',voiceIdleA:'voice-idle-a.ogg',voiceIdleB:'voice-idle-b.ogg',rivalHitA:'rival-hit-a.ogg',rivalHitB:'rival-hit-b.ogg',rivalIdleA:'rival-idle-a.ogg',rivalIdleB:'rival-idle-b.ogg'};
 const preload=['sling','wood','glass','iron','voiceHitA','voiceHitB','voiceIdleA','voiceIdleB','rivalHitA','rivalHitB','rivalIdleA','rivalIdleB'];
 const wanted=()=>started&&enabledMusic&&!paused&&!document.hidden;
 function filename(n){return 'audio/evening-'+(n===0?'menu':String(n).padStart(2,'0'))+'.ogg'}
 function newMusic(n){const m=new Audio(filename(n));m.loop=true;m.preload='auto';m.volume=MUSIC_GAIN;return m}
 function makeMusic(){if(!music)music=newMusic(scene);return music}
 function stopFade(){
  musicEpoch++;if(fadeFrom){fadeFrom.pause();try{fadeFrom.currentTime=0}catch(e){}fadeFrom=null}
  fadeReady=false;fadeAge=0;if(music)music.volume=MUSIC_GAIN;
 }
 function sync(){
  if(!music&&!started)return;
  const m=makeMusic();
  if(!wanted()){stopFade();if(!m.paused)m.pause();return}
  if(m.paused){try{const result=m.play();if(result&&result.catch)result.catch(()=>{})}catch(e){}}
 }
 function gesture(){
  if(!started && enabledSfx)for(const name of preload){try{const a=new Audio('audio/'+file[name]);a.preload='auto';pools[name]=[a]}catch(e){}}
  started=true;sync();
 }
 function setScene(n){
  n=Number.isFinite(Number(n))?Math.max(0,Math.min(999,Math.floor(Number(n)))):0;
  if(n===scene)return;scene=n;
  if(!music)return;
  // If another transition is already in progress, retain its audible side.
  if(fadeFrom){
   if(!fadeFrom.paused&&fadeFrom.volume>music.volume){music.pause();music=fadeFrom}
   else fadeFrom.pause();
   fadeFrom=null;fadeReady=false;
  }
  const old=music,next=newMusic(scene),epoch=++musicEpoch;music=next;fadeAge=0;
  if(!wanted()){old.pause();return}
  if(!old.paused){fadeFrom=old;next.volume=0}
  const ready=()=>{if(epoch===musicEpoch&&music===next&&fadeFrom===old&&wanted())fadeReady=true};
  const failed=()=>{if(epoch===musicEpoch&&music===next){if(fadeFrom){fadeFrom.pause();fadeFrom=null}next.volume=MUSIC_GAIN;fadeReady=false}};
  try{
   const p=next.play();
   if(fadeFrom){if(p&&p.then)p.then(ready,failed);else ready()}
   else if(p&&p.catch)p.catch(()=>{});
  }catch(e){failed()}
 }
 function advance(dt){
  if(!fadeFrom||!fadeReady||!wanted())return;
  fadeAge+=Math.max(0,Math.min(.1,dt));const u=Math.min(1,fadeAge/FADE_SECONDS);
  fadeFrom.volume=MUSIC_GAIN*(1-u);music.volume=MUSIC_GAIN*u;
  if(u>=1)stopFade();
 }
 function cue(name,volume=1){
  if(!started||!enabledSfx||paused||document.hidden||!file[name])return;
  const now=Date.now();if(now-(last[name]||0)<65)return;last[name]=now;
  try{const pool=pools[name]||(pools[name]=[]);let a=pool.find(x=>x.paused||x.ended);
   if(!a&&pool.length<3){a=new Audio('audio/'+file[name]);a.preload='auto';pool.push(a)}
   if(!a)return;a.currentTime=0;a.volume=Math.max(0,Math.min(1,volume*.55));const p=a.play();if(p&&p.catch)p.catch(()=>{});
  }catch(e){}
 }
 function stopCue(name){for(const a of pools[name]||[]){a.pause();try{a.currentTime=0}catch(e){}}}
 function pauseEffects(){for(const pool of Object.values(pools))for(const a of pool){a.pause();try{a.currentTime=0}catch(e){}}}
 function setPaused(v){if(paused===v)return;paused=v;if(v)pauseEffects();sync()}
 function toggleMusic(){enabledMusic=!enabledMusic;try{localStorage.setItem('ah_music',enabledMusic?'on':'off')}catch(e){}if(enabledMusic)gesture();sync()}
 function toggleSfx(){enabledSfx=!enabledSfx;try{localStorage.setItem('ah_sfx',enabledSfx?'on':'off')}catch(e){}if(!enabledSfx)pauseEffects();else gesture()}
 root.AfterHoursAudio={gesture,cue,stopCue,setPaused,setScene,advance,toggleMusic,toggleSfx,isMusicOn:()=>enabledMusic,isSfxOn:()=>enabledSfx};
})(typeof window==='undefined'?globalThis:window);
