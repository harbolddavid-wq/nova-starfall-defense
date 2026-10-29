'use strict';
const assert=require('assert'),fs=require('fs'),path=require('path'),vm=require('vm');
const base=path.join(__dirname,'../app/src/main/assets');let env,focusRequests=0,focusLosses=0;
const documentEvents={},canvasEvents={},instances=[],store={};
class FakeAudio {
 constructor(src){this.src=src;this.paused=true;this.ended=false;this.currentTime=0;instances.push(this)}
 play(){this.paused=false;
  // An HTML media player taking focus away from an independently requested
  // native focus session is the suspected failure mode on the BLU.
  if(focusRequests && focusLosses++ < 20 && typeof env.onNativeAudioFocus==='function')env.onNativeAudioFocus(false);
  return Promise.resolve();
 }
 pause(){this.paused=true}
}
const gradient={addColorStop(){}};
const ctx=new Proxy({createLinearGradient:()=>gradient,createRadialGradient:()=>gradient},{get:(o,k)=>k in o?o[k]:()=>{},set:(o,k,v)=>(o[k]=v,true)});
const canvas={width:960,height:540,getContext:()=>ctx,getBoundingClientRect:()=>({left:0,top:0,width:960,height:540}),addEventListener:(n,f)=>canvasEvents[n]=f,setPointerCapture(){}};
env={console,Math,Date,Audio:FakeAudio,requestAnimationFrame:()=>{},innerWidth:960,innerHeight:540,devicePixelRatio:1,navigator:{vibrate(){}},localStorage:{getItem:k=>store[k],setItem:(k,v)=>store[k]=v},Image:class{constructor(){this.complete=true;this.naturalWidth=512;this.naturalHeight=768}},document:{hidden:false,getElementById:()=>canvas,addEventListener:(n,f)=>documentEvents[n]=f},NativeBridge:{acquireAudioFocus(){focusRequests++;return true},haptic(){}}};env.window=env;env.addEventListener=()=>{};vm.createContext(env);
for(const f of ['velvet-art.js','after-hours-levels.js','after-hours-physics.js','after-hours-audio.js','velvet-play.js'])vm.runInContext(fs.readFileSync(path.join(base,f),'utf8'),env,{filename:f});
const run=s=>vm.runInContext(s,env);
run('start(1); AfterHoursAudio.gesture()');
assert.strictEqual(run('phase'),'play','Starting music must not force the pause screen');
assert.strictEqual(focusRequests,0,'A second native focus owner must not compete with WebView playback');
const music=instances.find(a=>a.src.includes('evening-01'));
assert(music&&!music.paused,'music starts during foreground play');
for(let i=0;i<20;i++)if(typeof env.onNativeAudioFocus==='function')env.onNativeAudioFocus(false);
assert.strictEqual(run('phase'),'play','Audio focus changes alone must not interrupt gameplay');
env.document.hidden=true;documentEvents.visibilitychange();
assert.strictEqual(run('phase'),'pause','Backgrounding must still pause the game');
assert(music.paused,'Backgrounding must still stop music');
env.document.hidden=false;run('phase="play";AfterHoursAudio.setPaused(false);AfterHoursAudio.gesture()');
assert.strictEqual(run('phase'),'play','Continue must remain playable');
assert(!music.paused,'Continue restores foreground music');
env.onNativePause();assert.strictEqual(run('phase'),'pause','Activity pause still pauses the game');assert(music.paused,'Activity pause still stops the music');
console.log('Pause regression: no competing native focus; foreground remains playable, background still pauses.');
