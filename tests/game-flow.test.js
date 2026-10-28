'use strict';
const fs=require('fs'),vm=require('vm'),assert=require('assert'),path=require('path');
const base=path.join(__dirname,'../app/src/main/assets');
const events={};let captured=null;
const gradient={addColorStop(){}};
const canvasContext=new Proxy({createLinearGradient:()=>gradient,createRadialGradient:()=>gradient,measureText:()=>({width:10})},{get(o,k){return k in o?o[k]:()=>{}},set(o,k,v){o[k]=v;return true}});
const canvas={width:960,height:540,getContext:()=>canvasContext,getBoundingClientRect:()=>({left:0,top:0,width:960,height:540}),addEventListener:(n,fn)=>events[n]=fn,setPointerCapture:(id)=>{captured=id}};
const local={};
class FakeAudio {constructor(src){this.src=src;this.paused=true;this.ended=false;this.currentTime=0;this.volume=1}play(){this.paused=false;return Promise.resolve()}pause(){this.paused=true}}
const c={console,Math,Date,Audio:FakeAudio,setTimeout:()=>0,clearTimeout:()=>{},requestAnimationFrame:()=>{},innerWidth:960,innerHeight:540,devicePixelRatio:1,navigator:{vibrate:()=>{}},localStorage:{getItem:k=>local[k]||null,setItem:(k,v)=>local[k]=v},Image:class{constructor(){this.complete=true;this.naturalWidth=512;this.naturalHeight=768}},document:{getElementById:()=>canvas,addEventListener:()=>{},hidden:false}};
c.window=c;c.addEventListener=()=>{};vm.createContext(c);
for(const f of ['velvet-art.js','after-hours-physics.js','after-hours-audio.js','velvet-play.js'])vm.runInContext(fs.readFileSync(path.join(base,f),'utf8'),c,{filename:f});
const run=(x)=>vm.runInContext(x,c);
function event(type,x,y,id=1){events[type]({clientX:x,clientY:y,pointerId:id,preventDefault(){}})}
run('start(1);paint()');
event('pointerdown',158,368);event('pointermove',85,418);event('pointerup',85,418);
assert.strictEqual(run('shots'),4,'release consumes one shot');
assert.strictEqual(run('balls.length'),1);
assert.strictEqual(captured,1);
run('start(1);shotMode="split";drag={x:85,y:418};shot();sim(.1)');
assert.strictEqual(run('splitShot()'),true);
assert.strictEqual(run('balls.length'),3,'split creates three separate projectiles');
assert.strictEqual(run('splitShot()'),false,'split cannot repeat');
run('start(1);enemies=[];crates=[];addEnemy(100,100,1);addEnemy(700,300,1);addCrate(105,100,20,20);damageBlock(crates[0],{x:100,y:100,vx:100,vy:0})');
assert.strictEqual(run('enemies.filter(t=>t.dead).length'),0,'direct block hit must not magically charm nearby targets');
run('let rose=newBall(100,100,200,0,"bloom");bloom(rose)');
assert.strictEqual(run('enemies[0].dead'),true,'bloom affects nearby targets');
assert.strictEqual(run('enemies[1].dead'),false,'bloom has finite range');
run('start(1);paint()');event('pointerdown',158,368,3);event('pointermove',88,400,3);event('pointercancel',88,400,3);
assert.strictEqual(run('shots'),5,'cancel must not fire');
assert.strictEqual(run('aiming'),false);
console.log('Game flow: pointer release/cancel, split and bounded bloom passed with mocked rendering.');
const reachable=run(`(()=>{const found=[false,false,false];for(let dx=-108;dx<=-55;dx+=5){for(let dy=-15;dy<=80;dy+=5){start(1);shotMode='direct';drag=Physics.pull(launcher,dx,dy);shot();for(let i=0;i<650&&ball;i++)sim(1/120);for(let j=0;j<3;j++)if(enemies[j].dead)found[j]=true;if(found.every(Boolean))return found}}return found})()`);
assert(reachable.every(Boolean),'each initial target needs at least one reachable trajectory');
console.log('Game flow: all three opening targets are individually reachable by a simulated direct shot.');
const solution=run(`(()=>{
 function play(sequence){start(1);shotMode='direct';for(const [dx,dy] of sequence){if(phase!=='play'||shots<=0)break;drag=Physics.pull(launcher,dx,dy);shot();for(let k=0;k<720&&ball;k++)sim(1/120)}return enemies.map(t=>t.dead)}
 const chosen=[];
 for(let target=0;target<3;target++){
  if(play(chosen)[target])continue;
  let found=null;
  for(let dx=-108;dx<=-55&&!found;dx+=5)for(let dy=-15;dy<=80;dy+=5){const tryShot=[dx,dy];if(play([...chosen,tryShot])[target]){found=tryShot;break}}
  if(!found)return null;chosen.push(found)
 }
 return play(chosen).every(Boolean)&&chosen.length<=5?chosen:null;
})()`);
assert(solution,'opening level must have a simulated path to clear every target within the shot budget');
console.log('Game flow: simulated opening clear found in '+solution.length+' shots; difficulty and feel remain unverified.');
