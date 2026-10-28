/* Local-only music and event audio. Android focus is mediated by NativeBridge. */
(function(root){
 "use strict";
 let music=null,enabledMusic=true,enabledSfx=true,paused=false,focused=false,started=false;
 const pools={},last={};
 try { enabledMusic=localStorage.getItem('ah_music')!=='off'; enabledSfx=localStorage.getItem('ah_sfx')!=='off'; }catch(e){}
 const file={click:'click-b.ogg',switch:'switch-a.ogg',tap:'tap-a.ogg',sling:'sling.ogg',wood:'wood.ogg',glass:'glass.ogg',charm:'charm.ogg',power:'power.ogg',win:'win.ogg'};
 function makeMusic(){if(!music){music=new Audio('audio/after-hours-waltz.ogg');music.loop=true;music.preload='auto';music.volume=.28;}return music;}
 function acquire(){try{return !root.NativeBridge || root.NativeBridge.acquireAudioFocus();}catch(e){return false}}
 function sync(){
  if(!music && !started)return;
  const m=makeMusic(),wanted=started&&enabledMusic&&!paused&&focused&&!document.hidden;
  if(!wanted){if(!m.paused)m.pause();return}
  if(m.paused){const result=m.play();if(result&&result.catch)result.catch(()=>{});}
 }
 function gesture(){started=true;if(!enabledMusic&&!enabledSfx)return;focused=acquire();sync()}
 function cue(name,volume=1){
  if(!enabledSfx||paused||document.hidden||!file[name])return;
  if(!focused){focused=acquire();if(!focused)return;}
  const now=Date.now();if(now-(last[name]||0)<65)return;last[name]=now;
  try{const pool=pools[name]||(pools[name]=[]);let a=pool.find(x=>x.paused||x.ended);
   if(!a && pool.length<3){a=new Audio('audio/'+file[name]);a.preload='auto';pool.push(a)}
   if(!a)return;a.currentTime=0;a.volume=Math.max(0,Math.min(1,volume*.55));const p=a.play();if(p&&p.catch)p.catch(()=>{});
  }catch(e){}
 }
 function pauseEffects(){for(const pool of Object.values(pools))for(const a of pool){a.pause();try{a.currentTime=0}catch(e){}}}
 function setPaused(v){if(paused===v)return;paused=v;if(v)pauseEffects();sync()}
 function setFocus(v){focused=!!v;if(!focused)pauseEffects();sync()}
 function toggleMusic(){enabledMusic=!enabledMusic;try{localStorage.setItem('ah_music',enabledMusic?'on':'off')}catch(e){}if(enabledMusic)gesture();sync()}
 function toggleSfx(){enabledSfx=!enabledSfx;try{localStorage.setItem('ah_sfx',enabledSfx?'on':'off')}catch(e){}if(!enabledSfx)pauseEffects();else gesture()}
 root.AfterHoursAudio={gesture,cue,setPaused,setFocus,toggleMusic,toggleSfx,isMusicOn:()=>enabledMusic,isSfxOn:()=>enabledSfx};
})(typeof window==='undefined'?globalThis:window);
