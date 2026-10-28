"use strict";
// After Hours work-in-progress: original branch remains untouched.
const Physics=AfterHoursPhysics;
let frame=0,phase="menu",stage=1,unlocked=1,score=0,best=0,shots=5;
let ball=null,balls=[],drag=null,aiming=false,activePointer=null,dragOrigin=null;
let crates=[],enemies=[],particles=[],popups=[],buttons=[],finishClock=0,shake=0;
let hapticsOn=true,shotMode="direct",simTime=0,actorReact=0;
try{hapticsOn=localStorage.getItem('ah_haptics')!=='off'}catch(e){}
function toggleHaptics(){hapticsOn=!hapticsOn;try{localStorage.setItem('ah_haptics',hapticsOn?'on':'off')}catch(e){}}
try { unlocked=clamp(parseInt(localStorage.getItem("velvet_ah_unlocked")||"1",10)||1,1,5); best=parseInt(localStorage.getItem("velvet_ah_best")||"0",10)||0; } catch(e) {}
function haptic(){
 if(!hapticsOn)return;
 try{if(window.NativeBridge)window.NativeBridge.haptic();else if(navigator.vibrate)navigator.vibrate(8)}catch(e){}
}
function sfx(name,volume=1){AfterHoursAudio.cue(name,volume)}
function burst(x,y,color="#ff70b6",n=16){for(let i=0;i<n;i++){let a=rnd(0,TAU),v=rnd(40,175);particles.push({x,y,vx:Math.cos(a)*v,vy:Math.sin(a)*v-rnd(0,70),life:rnd(.4,.85),max:.85,size:rnd(2,6),color,heart:Math.random()<.35})}}
function popup(x,y,text,color="#ffe7b7"){popups.push({x,y,text,color,life:1.1})}
function addCrate(x,y,w,h,type="wood"){crates.push({id:crates.length,x,y,w,h,type,hp:type==="crystal"?1:2,vx:0,vy:0,rot:0,spin:0,moving:false,dead:false,transferAfter:0})}
function addEnemy(x,y,kind){enemies.push({x,y,kind,seed:rnd(0,TAU),dead:false,r:25})}
function start(l){
 stage=clamp(l,1,5);phase="play";score=0;shots=5;balls=[];ball=null;drag=null;aiming=false;activePointer=null;crates=[];enemies=[];particles=[];popups=[];finishClock=0;shake=0;simTime=0;
 const rows=[[[666,354],[803,358]],[[649,362],[755,320],[859,362]],[[648,326],[752,375],[863,313]],[[632,360],[725,308],[819,360],[890,320]],[[620,342],[705,295],[789,363],[873,300],[924,363]]];
 rows[stage-1].forEach(([x,y],i)=>{addCrate(x,y+43,75,17,i%2?"crystal":"wood");addCrate(x-32,y+62,18,46,"wood");addCrate(x+32,y+62,18,46,"wood");if(stage>2)addCrate(x,y-48,55,14,"crystal");addEnemy(x,y,stage+i)});
 if(stage>=4){addCrate(558,418,29,48,"wood");addCrate(578,381,45,16,"crystal")}
 AfterHoursAudio.setPaused(false);
}
function charm(t){if(t.dead)return;t.dead=true;t.vanish=.55;score+=750;shake=Math.max(shake,3.5);burst(t.x,t.y,"#fa80c1",18);burst(t.x,t.y,"#ffdc92",7);popup(t.x,t.y-45,"+750 CHARM!");sfx("charm");haptic(8)}
function damageBlock(b,p,damage=1){
 if(b.dead)return;
 b.hp-=damage;b.moving=true;b.vx+=p.vx*.18;b.vy+=Math.min(0,p.vy*.10)-105;b.spin+=clamp(p.vx/500,-1.5,1.5);
 score+=95;shake=Math.max(shake,2.5);popup(b.x,b.y-20,"+95");burst(p.x,p.y,b.type==="crystal"?"#adf1fb":"#ffbaac",b.type==="crystal"?12:8);sfx(b.type==="crystal"?"glass":"wood");
 if(b.hp<=0){b.dead=true;burst(b.x,b.y,b.type==="crystal"?"#d2eaff":"#e8b496",10)}
}
function bloom(p){
 if(p.bloomed || p.mode!=="bloom")return;p.bloomed=true;
 burst(p.x,p.y,"#f75f9e",24);burst(p.x,p.y,"#ffdc92",12);sfx("power");haptic(12);
 // Area damage and movement use distance to the actual object, not the old
 // blanket rule that charmed every enemy merely near a hit block.
 for(const t of enemies)if(!t.dead && Math.hypot(t.x-p.x,t.y-p.y) < 86+t.r)charm(t);
 for(const b of crates){if(b.dead)continue;const qx=clamp(p.x,b.x-b.w/2,b.x+b.w/2),qy=clamp(p.y,b.y-b.h/2,b.y+b.h/2);if(Math.hypot(qx-p.x,qy-p.y)<86){const dx=b.x-p.x,dy=b.y-p.y,d=Math.max(1,Math.hypot(dx,dy));damageBlock(b,{x:p.x,y:p.y,vx:dx/d*320,vy:dy/d*220},.8)}}
 p.life=99;
}
function newBall(x,y,vx,vy,mode=shotMode,r=17){return{x,y,px:x,py:y,vx,vy,r,life:0,bounces:0,trail:[],mode,bloomed:false,splitUsed:false,cooldowns:{}}}
function shot(){
 if(!drag)return;const d=Math.hypot(launcher.x-drag.x,launcher.y-drag.y);
 if(d<16){drag=null;return}
 const v=Physics.velocity(launcher,drag);balls=[newBall(drag.x,drag.y,v.x,v.y)];ball=balls[0];shots--;drag=null;actorReact=1;shake=1.4;burst(ball.x,ball.y,"#ff8ec1",8);sfx("sling");haptic(12)
}
function splitShot(){
 if(balls.length!==1)return false;const p=balls[0];if(p.mode!=="split" || p.splitUsed || p.life<.08)return false;
 const speed=Math.hypot(p.vx,p.vy),angle=Math.atan2(p.vy,p.vx);balls=[-.24,0,.24].map(a=>{let q=newBall(p.x,p.y,Math.cos(angle+a)*speed*.88,Math.sin(angle+a)*speed*.88,"fragment",10);q.splitUsed=true;return q});ball=balls[0];burst(p.x,p.y,"#ffd2ed",12);sfx("power");haptic(8);return true;
}
function finished(win){
 if(phase!=="play")return;phase=win?"win":"lose";aiming=false;drag=null;
 if(win){unlocked=Math.max(unlocked,Math.min(5,stage+1));best=Math.max(best,score);try{localStorage.setItem("velvet_ah_unlocked",unlocked);localStorage.setItem("velvet_ah_best",best)}catch(e){}burst(474,241,"#ffd599",45);haptic(18);sfx("win")}else sfx("switch",.65)
}
function moveCrates(dt){
 for(const b of crates){if(b.dead||!b.moving)continue;b.vy+=780*dt;b.x+=b.vx*dt;b.y+=b.vy*dt;b.rot+=b.spin*dt;b.vx*=Math.pow(.45,dt);
  if(b.y+b.h/2>=G){b.y=G-b.h/2;if(b.vy>35)b.vy*=-.18;else b.vy=0;b.vx*=Math.pow(.06,dt);b.spin*=Math.pow(.05,dt)}
  if(Math.hypot(b.vx,b.vy)>28){
   for(const t of enemies){if(t.dead)continue;const qx=clamp(t.x,b.x-b.w/2,b.x+b.w/2),qy=clamp(t.y,b.y-b.h/2,b.y+b.h/2);if(Math.hypot(qx-t.x,qy-t.y)<t.r)charm(t)}
   if(simTime>=b.transferAfter){for(const other of crates){if(other===b||other.dead||other.moving)continue;if(Math.abs(b.x-other.x)<(b.w+other.w)/2 && Math.abs(b.y-other.y)<(b.h+other.h)/2){other.moving=true;other.vx+=b.vx*.5;other.vy-=45;other.spin+=b.spin*.35;b.vx*=.7;b.transferAfter=simTime+.18;break}}}
  }
 }
}
function moveBall(p,dt){
 p.px=p.x;p.py=p.y;p.life+=dt;
 const x=p.x,y=p.y;Physics.move(p,dt);const dx=p.x-x,dy=p.y-y;let hit=null;
 for(const t of enemies){if(t.dead)continue;const at=Physics.sweepCircle(x,y,dx,dy,t.x,t.y,p.r+t.r);if(at!==null && (!hit || at<hit.t))hit={t:at,target:t}}
 for(const b of crates){if(b.dead || (p.cooldowns[b.id]||0)>simTime)continue;const h=Physics.sweepBox(x,y,dx,dy,p.r,b);if(h && (!hit || h.t<hit.t))hit={...h,block:b}}
 if(hit){p.x=x+dx*hit.t;p.y=y+dy*hit.t;
  if(hit.target){charm(hit.target);p.vx*=.74;p.vy*=.82;bloom(p)}
  else{p.cooldowns[hit.block.id]=simTime+.12;damageBlock(hit.block,p,p.mode==="fragment"?.65:1);let v=Physics.reflect(p.vx,p.vy,hit.nx,hit.ny,.42);p.vx=v.x*.82;p.vy=v.y*.82;p.x+=hit.nx*.3;p.y+=hit.ny*.3;bloom(p)}
 }
 if(p.y+p.r>G){p.y=G-p.r;if(p.vy>0){p.vy*=-.4;p.vx*=.72;p.bounces++;sfx("wood",.42);bloom(p)}}
 if(p.trail.length===0 || Math.hypot(p.x-p.trail[p.trail.length-1].x,p.y-p.trail[p.trail.length-1].y)>9)p.trail.push({x:p.x,y:p.y});if(p.trail.length>14)p.trail.shift();
}
function sim(dt){
 frame+=dt*60;simTime+=dt;shake*=Math.pow(.004,dt);actorReact=Math.max(0,actorReact-dt*2.4);
 for(const t of enemies)if(t.vanish>0)t.vanish=Math.max(0,t.vanish-dt);
 for(let i=particles.length-1;i>=0;i--){let a=particles[i];a.x+=a.vx*dt;a.y+=a.vy*dt;a.vy+=180*dt;a.life-=dt;if(a.life<=0)particles.splice(i,1)}
 for(let i=popups.length-1;i>=0;i--){let p=popups[i];p.y-=22*dt;p.life-=dt;if(p.life<=0)popups.splice(i,1)}
 if(phase!=="play")return;moveCrates(dt);
 for(const p of balls)moveBall(p,dt);
 balls=balls.filter(p=>p.x<=1030 && p.x>=-50 && p.y<=580 && p.life<=6 && !(p.bounces>=3 && Math.abs(p.vx)<70));ball=balls[0]||null;
 if(enemies.every(t=>t.dead)){finishClock+=dt;if(finishClock>.85)finished(true)}
 else if(!ball&&shots<=0){finishClock+=dt;if(finishClock>1.0)finished(false)}else finishClock=0;
}
function screenPoint(e){const r=cv.getBoundingClientRect(),x=(e.clientX-r.left)*cv.width/r.width,y=(e.clientY-r.top)*cv.height/r.height;return{x:(x-ox)/sc,y:(y-oy)/sc}}
function findButton(p){for(let i=buttons.length-1;i>=0;i--){const b=buttons[i];if(p.x>=b.x&&p.x<=b.x+b.w&&p.y>=b.y&&p.y<=b.y+b.h)return b}return null}
function aimMove(p){if(!aiming)return;drag=Physics.pull(launcher,p.x-dragOrigin.x,p.y-dragOrigin.y)}
cv.addEventListener("pointerdown",e=>{
 if(activePointer!==null)return;e.preventDefault();AfterHoursAudio.gesture();let p=screenPoint(e),b=findButton(p);if(b){sfx("click",.65);b.run();return}
 if(phase!=="play")return;
 if(ball){splitShot();return}
 if(shots>0 && Math.hypot(p.x-launcher.x,p.y-launcher.y)<78){activePointer=e.pointerId;try{cv.setPointerCapture(e.pointerId)}catch(x){}aiming=true;dragOrigin=p;drag={...launcher};sfx("tap",.5)}
});
cv.addEventListener("pointermove",e=>{if(aiming&&e.pointerId===activePointer){e.preventDefault();aimMove(screenPoint(e))}});
cv.addEventListener("pointerup",e=>{if(aiming&&e.pointerId===activePointer){e.preventDefault();aimMove(screenPoint(e));aiming=false;activePointer=null;shot()}});
function cancelAim(e){if(e && activePointer!==null && e.pointerId!==activePointer)return;aiming=false;activePointer=null;drag=null}
cv.addEventListener("pointercancel",cancelAim);cv.addEventListener("lostpointercapture",cancelAim);
document.addEventListener("visibilitychange",()=>{if(document.hidden){cancelAim();if(phase==="play")phase="pause";AfterHoursAudio.setPaused(true)}});
document.addEventListener("keydown",e=>{if(e.key==="Escape"||e.key==="Backspace"){cancelAim();if(phase==="play")phase="pause";else if(phase==="pause")phase="play";else phase="menu"}});
let last=0,lag=0,renderAlpha=0;
function tick(now){let dt=last?Math.min(.05,Math.max(0,(now-last)/1000)):0;last=now;lag+=dt;let k=0;const step=1/120;while(lag>=step&&k<6){sim(step);lag-=step;k++}if(k===6 && lag>=step)lag=0;renderAlpha=lag/step;AfterHoursAudio.setPaused(phase==="pause"||document.hidden);paint();requestAnimationFrame(tick)}
requestAnimationFrame(tick);

// Called only by the local Android wrapper on audio focus or activity lifecycle.
window.onNativeAudioFocus=function(ok){AfterHoursAudio.setFocus(ok);if(!ok&&phase==="play")phase="pause"};
window.onNativePause=function(){cancelAim();if(phase==="play")phase="pause";AfterHoursAudio.setPaused(true);AfterHoursAudio.setFocus(false)};
