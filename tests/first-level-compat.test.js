'use strict';
// Compares gameplay state against the exact 0.1.2 APK David approved.
// This does not prove feel or visual/audio experience on a device.
const assert=require('assert'),fs=require('fs'),path=require('path'),vm=require('vm');
const current=path.join(__dirname,'../app/src/main/assets'),previous=path.join(__dirname,'fixtures/accepted_0_1_2');
assert(fs.existsSync(previous),'accepted baseline fixture is required');
function create(base,modern){
 const gradient={addColorStop(){}};const ctx=new Proxy({createLinearGradient:()=>gradient,createRadialGradient:()=>gradient},{get:(o,k)=>k in o?o[k]:()=>{},set:(o,k,v)=>(o[k]=v,true)});
 const canvas={width:960,height:540,getContext:()=>ctx,getBoundingClientRect:()=>({left:0,top:0,width:960,height:540}),addEventListener(){},setPointerCapture(){}};
 class AudioFake{constructor(){this.paused=true;this.ended=false;this.currentTime=0}play(){this.paused=false;return Promise.resolve()}pause(){this.paused=true}}
 const e={console,Math,Date,Audio:AudioFake,requestAnimationFrame:()=>{},innerWidth:960,innerHeight:540,devicePixelRatio:1,navigator:{vibrate(){}},localStorage:{getItem:()=>null,setItem(){}},Image:class{constructor(){this.complete=true;this.naturalWidth=512;this.naturalHeight=768}},document:{hidden:false,getElementById:()=>canvas,addEventListener(){}}};e.window=e;e.addEventListener=()=>{};vm.createContext(e);
 const files=['velvet-art.js',...(modern?['after-hours-levels.js']:[]),'after-hours-physics.js','after-hours-audio.js','velvet-play.js'];
 for(const f of files)vm.runInContext(fs.readFileSync(path.join(base,f),'utf8'),e,{filename:f});return s=>vm.runInContext(s,e);
}
const a=create(previous,false),b=create(current,true);
const geom=`(()=>{start(1);return JSON.stringify({blocks:crates.map(x=>[x.x,x.y,x.w,x.h,x.type,x.hp]),targets:enemies.map(t=>[t.x,t.y,t.kind,t.r]),shots})})()`;
assert.strictEqual(a(geom),b(geom),'approved opening layout must remain unchanged');
let count=0;
for(const mode of ['direct','bloom','split'])for(const dx of [-108,-90,-72,-54])for(const dy of [12,28,44,60]){
 const script=`(()=>{start(1);shotMode='${mode}';drag=Physics.pull(launcher,${dx},${dy});shot();
  if('${mode}'==='split'){for(let i=0;i<30&&ball;i++)sim(1/120);splitShot()}
  for(let i=0;i<900&&ball;i++)sim(1/120);for(let i=0;i<150&&phase==='play';i++)sim(1/120);
  const n=x=>Math.round(x*10000)/10000;
  return JSON.stringify({phase,score,shots,targets:enemies.map(t=>t.dead),blocks:crates.map(c=>[c.dead,c.hp,n(c.x),n(c.y),n(c.vx),n(c.vy)])});})()`;
 assert.strictEqual(b(script),a(script),`first-level mechanics changed for ${mode}, pull ${dx},${dy}`);count++;
}
console.log(`First-level compatibility: ${count} classic/Bloom/Split shots matched the 0.1.2 APK simulation.`);
