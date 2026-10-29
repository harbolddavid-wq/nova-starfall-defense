'use strict';
const assert=require('assert'),fs=require('fs'),vm=require('vm'),path=require('path');
const base=path.join(__dirname,'../app/src/main/assets'),events={},arcs=[];
const gradient={addColorStop(){}};
const ctx=new Proxy({createLinearGradient:()=>gradient,createRadialGradient:()=>gradient,arc:(x,y,r)=>arcs.push({x,y,r})},{get:(o,k)=>k in o?o[k]:()=>{},set:(o,k,v)=>(o[k]=v,true)});
const canvas={width:960,height:540,getContext:()=>ctx,getBoundingClientRect:()=>({left:0,top:0,width:960,height:540}),addEventListener:(n,f)=>events[n]=f,setPointerCapture(){}};
class AudioFake{constructor(){this.paused=true;this.ended=false;this.currentTime=0}play(){this.paused=false;return Promise.resolve()}pause(){this.paused=true}}
const env={console,Math,Date,Audio:AudioFake,requestAnimationFrame:()=>{},innerWidth:960,innerHeight:540,devicePixelRatio:1,navigator:{vibrate(){}},localStorage:{getItem:()=>null,setItem(){}},Image:class{constructor(){this.complete=true;this.naturalWidth=512;this.naturalHeight=768}},document:{hidden:false,getElementById:()=>canvas,addEventListener(){}}};env.window=env;env.addEventListener=()=>{};vm.createContext(env);
for(const f of ['velvet-art.js','after-hours-physics.js','after-hours-audio.js','velvet-play.js'])vm.runInContext(fs.readFileSync(path.join(base,f),'utf8'),env,{filename:f});
const run=s=>vm.runInContext(s,env);
function event(type,x,y,id=1){events[type]({clientX:x,clientY:y,pointerId:id,preventDefault(){}})}
// Exercise the actual on-screen choice and launcher input, then let the shot fly
// through the real opening level until it strikes something.
run('start(1);paint()');event('pointerdown',500,505);assert.strictEqual(run('shotMode'),'bloom','Bloom button must select the ability');
event('pointerdown',158,368);event('pointermove',90,400);event('pointerup',90,400);
assert.strictEqual(run('ball.mode'),'bloom','selected ability must be carried by the launched shot');
run('globalThis.observedShot=ball;for(let i=0;i<1200 && ball && !observedShot.bloomed;i++)sim(1/120)');
assert.strictEqual(run('observedShot.bloomed'),true,'Bloom must activate on its first actual level impact');
// Look at canvas output, not only the internal area-damage flag. The effect must
// be an expanding, visible burst rather than a few nearly indistinguishable dots.
run('for(let i=0;i<12;i++)sim(1/120)');arcs.length=0;run('particlesDraw()');const first=Math.max(0,...arcs.map(a=>a.r));
run('for(let i=0;i<18;i++)sim(1/120)');arcs.length=0;run('particlesDraw()');const second=Math.max(0,...arcs.map(a=>a.r));
assert(first>=30,'Bloom needs a prominent visible impact burst');assert(second>first+5,'the burst must visibly expand after impact');
// Verify each collision type, exactly one activation per projectile, and a
// bounded area effect on nearby versus distant targets.
for(const surface of ['target','block','ground']){
 const result=run(`(()=>{start(1);enemies=[];crates=[];particles=[];
   ${surface==='target'?'addEnemy(205,110,1);':surface==='block'?'addCrate(205,110,20,60);':'addEnemy(250,410,1);'}
   addEnemy(245,110,2);addEnemy(800,50,3);
   const p=${surface==='ground'?'newBall(200,430,0,160,"bloom")':'newBall(140,110,400,0,"bloom")'};
   moveBall(p,.005);const early=p.bloomed;moveBall(p,.15);const activated=p.bloomed;const count=particles.length;bloom(p);
   return {early,activated,count,again:particles.length,near:enemies.find(t=>t.x===245).dead,groundNear:enemies.find(t=>t.x===250)?.dead,far:enemies.find(t=>t.x===800).dead};})()`);
 assert(!result.early,`${surface}: no burst while flying`);assert(result.activated,`${surface}: burst on impact`);assert.strictEqual(result.again,result.count,`${surface}: burst only once`);assert(!result.far,`${surface}: distant target unaffected`);
 if(surface!=='ground')assert(result.near,`${surface}: nearby target affected`);
 if(surface==='ground')assert(result.groundNear,`ground: nearby target affected`);
}
console.log('Bloom: real UI selection/launch, visible expanding burst, impact surfaces and bounded area effects passed.');
