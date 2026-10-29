"use strict";
// After Hours work-in-progress: original branch remains untouched.
const Physics=AfterHoursPhysics,LEVELS=AFTER_HOURS_LEVELS;
let frame=0,phase="menu",stage=1,unlocked=1,score=0,best=0,shots=5,shotBudget=5;
let stageStars={},stageScores={};
let ball=null,balls=[],drag=null,aiming=false,activePointer=null,dragOrigin=null;
let crates=[],enemies=[],particles=[],bloomWaves=[],roseCharges=[],popups=[],buttons=[],finishClock=0,shake=0;
let hapticsOn=true,shotMode="direct",powerCharges={bloom:0,split:0},simTime=0,actorReact=0;
let soundClock=0,idleVoiceAt=0,lastHitVoice=-10,lastShotAt=-10,idleVoiceIndex=0,hitVoiceIndex=0;
let rivalIdleIndex=0,rivalHitIndex=0,phantomIdleIndex=0,phantomHitIndex=0,shotSerial=0,levelPage=0;
try{hapticsOn=localStorage.getItem('ah_haptics')!=='off'}catch(e){}
function toggleHaptics(){hapticsOn=!hapticsOn;try{localStorage.setItem('ah_haptics',hapticsOn?'on':'off')}catch(e){}}
try {
 unlocked=clamp(parseInt(localStorage.getItem("velvet_ah_unlocked")||"1",10)||1,1,LEVELS.length);
 best=Math.max(0,parseInt(localStorage.getItem("velvet_ah_best")||"0",10)||0);
 const readRecord=(key,max)=>{try{const v=JSON.parse(localStorage.getItem(key)||"{}");if(!v||typeof v!=="object"||Array.isArray(v))return {};const out={};for(let n=1;n<=LEVELS.length;n++){const x=Number(v[n]);if(Number.isFinite(x)&&x>0)out[n]=Math.min(max,Math.floor(x))}return out}catch(e){return {}}};
 stageStars=readRecord("velvet_ah_stars",3);stageScores=readRecord("velvet_ah_scores",999999999);
 // Earlier versions only stored a best score on completing the single level.
 // Preserve that win and unlock the next stage without inventing a star rating.
 if(best>0 && localStorage.getItem("velvet_ah_campaign_v1")!=="1"){
  unlocked=Math.max(unlocked,Math.min(2,LEVELS.length));stageStars[1]=Math.max(stageStars[1]||0,1);stageScores[1]=Math.max(stageScores[1]||0,best);
  localStorage.setItem("velvet_ah_unlocked",String(unlocked));localStorage.setItem("velvet_ah_stars",JSON.stringify(stageStars));localStorage.setItem("velvet_ah_scores",JSON.stringify(stageScores));localStorage.setItem("velvet_ah_campaign_v1","1");
 }
 // A completed old finale unlocks the newly added next night on upgrade.
 // Scores, stars and the user's existing settings are never reset.
 if(LEVELS.length>6 && stageStars[6]>0 && unlocked<7){
  unlocked=7;localStorage.setItem("velvet_ah_unlocked",String(unlocked));
 }
 if(LEVELS.length>9 && stageStars[9]>0 && unlocked<10){
  unlocked=10;localStorage.setItem("velvet_ah_unlocked",String(unlocked));
 }
 if(LEVELS.length>14 && stageStars[14]>0 && unlocked<15){
  unlocked=15;localStorage.setItem("velvet_ah_unlocked",String(unlocked));
 }
 if(LEVELS.length>19 && stageStars[19]>0 && unlocked<20){
  unlocked=20;localStorage.setItem("velvet_ah_unlocked",String(unlocked));
 }
} catch(e) {}
function haptic(){
 if(!hapticsOn)return;
 try{if(window.NativeBridge)window.NativeBridge.haptic();else if(navigator.vibrate)navigator.vibrate(8)}catch(e){}
}
function sfx(name,volume=1){AfterHoursAudio.cue(name,volume)}
function quietIdleVoice(){for(const name of ['voiceIdleA','voiceIdleB','rivalIdleA','rivalIdleB','phantomIdleA','phantomIdleB'])AfterHoursAudio.stopCue(name)}
function gargoyleHitVoice(t){
 if(phase!=="play"||soundClock-lastHitVoice<.38)return;
 quietIdleVoice();
 if(t && t.type==='phantom')sfx(phantomHitIndex++%2?'phantomHitB':'phantomHitA',.78);
 else if(t && t.type==='rival')sfx(rivalHitIndex++%2?'rivalHitB':'rivalHitA',.88);
 else sfx(hitVoiceIndex++%2?'voiceHitB':'voiceHitA',.92);
 lastHitVoice=soundClock;
 idleVoiceAt=Math.max(idleVoiceAt,soundClock+5);
}
function updateGargoyleIdle(){
 if(soundClock<idleVoiceAt||ball||aiming||roseCharges.length||soundClock-lastShotAt<1.4||soundClock-lastHitVoice<3)return;
 const present=enemies.filter(t=>!t.dead);if(!present.length)return;
 const t=present[Math.floor(Math.random()*present.length)];t.voicePulse=.7;
 if(t.type==='phantom')sfx(phantomIdleIndex++%2?'phantomIdleB':'phantomIdleA',.42);
 else if(t.type==='rival')sfx(rivalIdleIndex++%2?'rivalIdleB':'rivalIdleA',.48);
 else sfx(idleVoiceIndex++%2?'voiceIdleB':'voiceIdleA',.48);
 idleVoiceAt=soundClock+rnd(7.5,12);
}
function burst(x,y,color="#ff70b6",n=16){for(let i=0;i<n;i++){let a=rnd(0,TAU),v=rnd(40,175);particles.push({x,y,vx:Math.cos(a)*v,vy:Math.sin(a)*v-rnd(0,70),life:rnd(.4,.85),max:.85,size:rnd(2,6),color,heart:Math.random()<.35})}}
function popup(x,y,text,color="#ffe7b7"){popups.push({x,y,text,color,life:1.1})}
function addCrate(x,y,w,h,type="wood",tag=null){crates.push({id:crates.length,x,y,w,h,type,tag,hp:type==="iron"?Infinity:(type==="crystal"||type==="rose"||type==="ward")?1:2,vx:0,vy:0,rot:0,spin:0,moving:false,dead:false,transferAfter:0,sourceShot:null})}
function addEnemy(x,y,kind,ward=null,type='gargoyle',options={}){
 enemies.push({id:enemies.length,x,y,kind,ward,type,hp:type==='rival'?3:1,damageKeys:{},react:0,wardFlash:0,voicePulse:0,noticeAt:0,seed:rnd(0,TAU),dead:false,r:25,
  shielded:type==='rival'&&options.shield===true,shieldFlash:0,shieldRemovedByShot:null,originX:x,motionTime:0,
  motionRange:type==='phantom'?clamp(Number(options.range)||24,0,38):0,
  motionRate:type==='phantom'?clamp(Number(options.rate)||.85,.3,1.5):0});
}
function isWarded(t){return !!t.ward && crates.some(b=>!b.dead&&b.type==="ward"&&b.tag===t.ward)}
function start(l){
 stage=clamp(Math.floor(Number(l)||1),1,Math.min(unlocked,LEVELS.length));
 const level=LEVELS[stage-1];phase="play";score=0;shotBudget=level.shots;shots=shotBudget;balls=[];ball=null;drag=null;aiming=false;activePointer=null;
 powerCharges={bloom:level.powers.bloom,split:level.powers.split};shotMode="direct";
 crates=[];enemies=[];particles=[];bloomWaves=[];roseCharges=[];popups=[];finishClock=0;shake=0;simTime=0;actorReact=0;
 soundClock=0;idleVoiceAt=rnd(4.5,7);lastHitVoice=-10;lastShotAt=-10;idleVoiceIndex=0;hitVoiceIndex=0;rivalIdleIndex=0;rivalHitIndex=0;phantomIdleIndex=0;phantomHitIndex=0;shotSerial=0;quietIdleVoice();
 for(const [x,y,w,h,type,tag] of level.blocks)addCrate(x,y,w,h,type,tag||null);
 level.targets.forEach(([x,y,ward,type,options],i)=>addEnemy(x,y,i+1,ward||null,type||'gargoyle',options||{}));
 AfterHoursAudio.setScene(stage);AfterHoursAudio.setPaused(false);
}
function charm(t,hit={}){
 if(t.dead)return false;
 const sourceShot=Number.isInteger(hit.shotId)?hit.shotId:(typeof hit.key==='string'&&hit.key.startsWith('shot:')?Number(hit.key.slice(5)):null);
 if(isWarded(t)){
  t.wardFlash=.25;
  if(simTime>=t.noticeAt){t.noticeAt=simTime+.5;popup(t.x,t.y-48,"BREAK THE SEAL","#ffe0a6");sfx("glass",.55);gargoyleHitVoice(t)}
  return false;
 }
 // A shield consumes this source's impact. In particular the follow-up
 // Bloom area and split fragments share a shot key and cannot damage health
 // on the very same shot that removed the shield.
 if(t.shielded){
  t.shielded=false;t.shieldFlash=.35;t.react=.38;t.shieldRemovedByShot=sourceShot;
  if(hit.key)t.damageKeys[hit.key]=true;
  shake=Math.max(shake,2.4);burst(t.x,t.y,'#c8e8ff',14);popup(t.x,t.y-58,'SHIELD BROKEN','#ddf5ff');sfx('glass',.72);gargoyleHitVoice(t);haptic(8);return false;
 }
 if(t.type==='rival'){
  if(sourceShot!==null&&sourceShot===t.shieldRemovedByShot)return false;
  if(hit.key && t.damageKeys[hit.key])return false;
  if(hit.key)t.damageKeys[hit.key]=true;
  t.hp=hit.bloom?0:Math.max(0,t.hp-1);t.react=.38;
  if(t.hp>0){score+=125;shake=Math.max(shake,2);burst(t.x,t.y,'#eeb8ff',9);popup(t.x,t.y-58,t.hp+' ♥ LEFT','#ffd6eb');sfx('charm',.28);gargoyleHitVoice(t);haptic(8);return false}
 }
 t.dead=true;t.vanish=.55;score+=750;shake=Math.max(shake,3.5);burst(t.x,t.y,"#fa80c1",18);burst(t.x,t.y,"#ffdc92",7);popup(t.x,t.y-45,"+750 CHARM!");sfx("charm",.5);gargoyleHitVoice(t);haptic(8);return true;
}
function damageBlock(b,p,damage=1){
 if(b.dead)return;
 if(b.type==="iron"){shake=Math.max(shake,1.2);burst(p.x,p.y,"#d7c5ed",5);sfx("iron",.8);return}
 if(Number.isInteger(p.shotId))b.sourceShot=p.shotId;
 b.hp-=damage;b.moving=true;b.vx+=p.vx*.18;b.vy+=Math.min(0,p.vy*.10)-105;b.spin+=clamp(p.vx/500,-1.5,1.5);
 score+=95;shake=Math.max(shake,2.5);popup(b.x,b.y-20,"+95");burst(p.x,p.y,(b.type==="crystal"||b.type==="ward")?"#adf1fb":"#ffbaac",b.type==="crystal"?12:8);sfx((b.type==="crystal"||b.type==="ward")?"glass":"wood",(b.type==="crystal"||b.type==="ward")?1:1.25);
 if(b.hp<=0){b.dead=true;burst(b.x,b.y,b.type==="crystal"?"#d2eaff":"#e8b496",10);if(b.type==="rose")roseCharges.push({x:b.x,y:b.y,remaining:.18,shotId:b.sourceShot})}
}
function bloom(p){
 if(p.bloomed || p.mode!=="bloom")return;p.bloomed=true;
 bloomWaves.push({x:p.x,y:p.y,age:0,duration:.55,radius:86});
 burst(p.x,p.y,"#f75f9e",24);burst(p.x,p.y,"#ffdc92",12);sfx("power");haptic(12);
 // Area damage and movement use distance to the actual object, not the old
 // blanket rule that charmed every enemy merely near a hit block.
 for(const b of crates){if(b.dead||b.type==="iron")continue;const qx=clamp(p.x,b.x-b.w/2,b.x+b.w/2),qy=clamp(p.y,b.y-b.h/2,b.y+b.h/2);if(Math.hypot(qx-p.x,qy-p.y)<86){const dx=b.x-p.x,dy=b.y-p.y,d=Math.max(1,Math.hypot(dx,dy));damageBlock(b,{x:p.x,y:p.y,vx:dx/d*320,vy:dy/d*220,shotId:p.shotId},b.type==="ward"?1:.8)}}
 for(const t of enemies)if(!t.dead && Math.hypot(t.x-p.x,t.y-p.y) < 86+t.r)charm(t,{bloom:true,key:'shot:'+p.shotId,shotId:p.shotId});
 p.life=99;
}
function explodeRose(q){
 const radius=76;bloomWaves.push({x:q.x,y:q.y,age:0,duration:.55,radius});
 burst(q.x,q.y,"#f77ba9",20);burst(q.x,q.y,"#ffe1a4",8);sfx("power");haptic(10);
 for(const b of crates){
  if(b.dead||b.type==="iron")continue;const px=clamp(q.x,b.x-b.w/2,b.x+b.w/2),py=clamp(q.y,b.y-b.h/2,b.y+b.h/2);
  if(Math.hypot(px-q.x,py-q.y)>=radius)continue;
  const dx=b.x-q.x,dy=b.y-q.y,d=Math.max(1,Math.hypot(dx,dy));
  damageBlock(b,{x:q.x,y:q.y,vx:dx/d*260,vy:dy/d*180,shotId:q.shotId},1);
 }
 for(const t of enemies)if(!t.dead&&Math.hypot(t.x-q.x,t.y-q.y)<radius+t.r)charm(t,{shotId:q.shotId});
}
function updateRoseCharges(dt){
 // Remove a charge before exploding it: an adjacent rose may safely enqueue
 // a new charge, but no already-broken crate can trigger twice.
 for(let i=roseCharges.length-1;i>=0;i--){const q=roseCharges[i];q.remaining-=dt;if(q.remaining<=0){roseCharges.splice(i,1);explodeRose(q)}}
}

function newBall(x,y,vx,vy,mode=shotMode,r=17,shotId=++shotSerial){return{x,y,px:x,py:y,vx,vy,r,shotId,life:0,bounces:0,trail:[],mode,bloomed:false,splitUsed:false,cooldowns:{},targetCooldowns:{}}}
function selectShot(mode){
 if(phase!=="play"||ball||aiming)return false;
 if(mode!=="direct" && (mode!=="bloom"&&mode!=="split" || powerCharges[mode]<=0))return false;
 shotMode=mode;return true;
}
function shot(){
 if(!drag||phase!=="play"||ball||shots<=0){drag=null;return}const d=Math.hypot(launcher.x-drag.x,launcher.y-drag.y);
 if(d<16){drag=null;return}
 const mode=shotMode==="direct"||powerCharges[shotMode]>0?shotMode:"direct";
 const v=Physics.velocity(launcher,drag);balls=[newBall(drag.x,drag.y,v.x,v.y,mode)];ball=balls[0];shots--;
 if(mode!=="direct"){powerCharges[mode]--;if(powerCharges[mode]===0)shotMode="direct"}
 drag=null;actorReact=1;shake=1.4;quietIdleVoice();lastShotAt=soundClock;burst(ball.x,ball.y,"#ff8ec1",8);sfx("sling");haptic(12)
}
function splitShot(){
 if(balls.length!==1)return false;const p=balls[0];if(p.mode!=="split" || p.splitUsed || p.life<.08)return false;
 const speed=Math.hypot(p.vx,p.vy),angle=Math.atan2(p.vy,p.vx);balls=[-.24,0,.24].map(a=>{let q=newBall(p.x,p.y,Math.cos(angle+a)*speed*.88,Math.sin(angle+a)*speed*.88,"fragment",10,p.shotId);q.splitUsed=true;return q});ball=balls[0];burst(p.x,p.y,"#ffd2ed",12);sfx("power");haptic(8);return true;
}
function finished(win){
 if(phase!=="play")return;phase=win?"win":"lose";aiming=false;drag=null;
 if(win){
  const earned=shots>=3?3:shots>=1?2:1;
  stageStars[stage]=Math.max(stageStars[stage]||0,earned);stageScores[stage]=Math.max(stageScores[stage]||0,score);
  unlocked=Math.max(unlocked,Math.min(LEVELS.length,stage+1));best=Math.max(best,score);
  try{localStorage.setItem("velvet_ah_unlocked",String(unlocked));localStorage.setItem("velvet_ah_best",String(best));localStorage.setItem("velvet_ah_stars",JSON.stringify(stageStars));localStorage.setItem("velvet_ah_scores",JSON.stringify(stageScores));localStorage.setItem("velvet_ah_campaign_v1","1")}catch(e){}
  burst(474,241,"#ffd599",45);haptic(18);sfx("win");
 }else sfx("switch",.65)
}
function moveCrates(dt){
 for(const b of crates){if(b.dead||!b.moving)continue;b.vy+=780*dt;b.x+=b.vx*dt;b.y+=b.vy*dt;b.rot+=b.spin*dt;b.vx*=Math.pow(.45,dt);
  if(b.y+b.h/2>=G){b.y=G-b.h/2;if(b.vy>35)b.vy*=-.18;else b.vy=0;b.vx*=Math.pow(.06,dt);b.spin*=Math.pow(.05,dt)}
  if(Math.hypot(b.vx,b.vy)>28){
   for(const t of enemies){if(t.dead)continue;const qx=clamp(t.x,b.x-b.w/2,b.x+b.w/2),qy=clamp(t.y,b.y-b.h/2,b.y+b.h/2);if(Math.hypot(qx-t.x,qy-t.y)<t.r)charm(t,{key:'block:'+b.id,shotId:b.sourceShot})}
   if(simTime>=b.transferAfter){for(const other of crates){if(other===b||other.dead||other.moving)continue;if(Math.abs(b.x-other.x)<(b.w+other.w)/2 && Math.abs(b.y-other.y)<(b.h+other.h)/2){
    if(other.type==="iron"){
     const dx=b.x-other.x,dy=b.y-other.y,overX=(b.w+other.w)/2-Math.abs(dx),overY=(b.h+other.h)/2-Math.abs(dy);
     if(overX<overY){b.x+=(Math.sign(dx)||-Math.sign(b.vx)||1)*(overX+.1);b.vx*=-.3}
     else{b.y+=(Math.sign(dy)||-Math.sign(b.vy)||-1)*(overY+.1);b.vy*=-.2}
    }else{other.moving=true;other.sourceShot=b.sourceShot;other.vx+=b.vx*.5;other.vy-=45;other.spin+=b.spin*.35;b.vx*=.7}
    b.transferAfter=simTime+.18;break
   }}}
  }
 }
}
function moveBall(p,dt){
 p.px=p.x;p.py=p.y;p.life+=dt;
 const x=p.x,y=p.y;Physics.move(p,dt);const dx=p.x-x,dy=p.y-y;let hit=null;
 for(const t of enemies){if(t.dead||(p.targetCooldowns[t.id]||0)>simTime)continue;const at=Physics.sweepCircle(x,y,dx,dy,t.x,t.y,p.r+t.r);if(at!==null && (!hit || at<hit.t))hit={t:at,target:t}}
 for(const b of crates){if(b.dead || (p.cooldowns[b.id]||0)>simTime)continue;const h=Physics.sweepBox(x,y,dx,dy,p.r,b);if(h && (!hit || h.t<hit.t))hit={...h,block:b}}
 if(hit){p.x=x+dx*hit.t;p.y=y+dy*hit.t;
  if(hit.target){
   if(charm(hit.target,{bloom:p.mode==='bloom',key:'shot:'+p.shotId,shotId:p.shotId})){p.vx*=.74;p.vy*=.82;}
   else{let nx=p.x-hit.target.x,ny=p.y-hit.target.y,d=Math.hypot(nx,ny);if(d<.001){nx=-p.vx;ny=-p.vy;d=Math.max(.001,Math.hypot(nx,ny))}nx/=d;ny/=d;let v=Physics.reflect(p.vx,p.vy,nx,ny,.45);p.vx=v.x*.85;p.vy=v.y*.85;p.x+=nx*.3;p.y+=ny*.3;p.targetCooldowns[hit.target.id]=simTime+.12;}
   bloom(p);
  }
  else{p.cooldowns[hit.block.id]=simTime+.12;damageBlock(hit.block,p,p.mode==="fragment"?.65:1);let v=Physics.reflect(p.vx,p.vy,hit.nx,hit.ny,hit.block.type==="iron"?.72:.42);p.vx=v.x*.82;p.vy=v.y*.82;p.x+=hit.nx*.3;p.y+=hit.ny*.3;bloom(p)}
 }
 if(p.y+p.r>G){p.y=G-p.r;if(p.vy>0){p.vy*=-.4;p.vx*=.72;p.bounces++;sfx("wood",.42);bloom(p)}}
 if(p.trail.length===0 || Math.hypot(p.x-p.trail[p.trail.length-1].x,p.y-p.trail[p.trail.length-1].y)>9)p.trail.push({x:p.x,y:p.y});if(p.trail.length>14)p.trail.shift();
}
function sim(dt){
 frame+=dt*60;simTime+=dt;shake*=Math.pow(.004,dt);actorReact=Math.max(0,actorReact-dt*2.4);
 for(const t of enemies){if(t.vanish>0)t.vanish=Math.max(0,t.vanish-dt);if(t.wardFlash>0)t.wardFlash=Math.max(0,t.wardFlash-dt);if(phase==="play"&&t.voicePulse>0)t.voicePulse=Math.max(0,t.voicePulse-dt);if(phase==="play"&&t.react>0)t.react=Math.max(0,t.react-dt);if(phase==="play"&&t.shieldFlash>0)t.shieldFlash=Math.max(0,t.shieldFlash-dt);if(phase==="play"&&!t.dead&&t.type==='phantom'){t.motionTime+=dt;t.x=t.originX+Math.sin(t.motionTime*t.motionRate)*t.motionRange}}
 if(phase!=="pause")for(let i=bloomWaves.length-1;i>=0;i--){bloomWaves[i].age+=dt;if(bloomWaves[i].age>=bloomWaves[i].duration)bloomWaves.splice(i,1)}
 for(let i=particles.length-1;i>=0;i--){let a=particles[i];a.x+=a.vx*dt;a.y+=a.vy*dt;a.vy+=180*dt;a.life-=dt;if(a.life<=0)particles.splice(i,1)}
 for(let i=popups.length-1;i>=0;i--){let p=popups[i];p.y-=22*dt;p.life-=dt;if(p.life<=0)popups.splice(i,1)}
 if(phase!=="play")return;soundClock+=dt;updateGargoyleIdle();updateRoseCharges(dt);moveCrates(dt);
 for(const p of balls)moveBall(p,dt);
 balls=balls.filter(p=>p.x<=1030 && p.x>=-50 && p.y<=580 && p.life<=6 && !(p.bounces>=3 && Math.abs(p.vx)<70));ball=balls[0]||null;
 if(enemies.every(t=>t.dead)&&!roseCharges.length){finishClock+=dt;if(finishClock>.85)finished(true)}
 else if(!ball&&shots<=0&&!roseCharges.length){finishClock+=dt;if(finishClock>1.0)finished(false)}else finishClock=0;
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
function tick(now){let dt=last?Math.min(.05,Math.max(0,(now-last)/1000)):0;last=now;lag+=dt;let k=0;const step=1/120;while(lag>=step&&k<6){sim(step);lag-=step;k++}if(k===6 && lag>=step)lag=0;renderAlpha=lag/step;AfterHoursAudio.setScene(phase==="menu"||phase==="levels"||phase==="help"?0:stage);AfterHoursAudio.setPaused(phase==="pause"||document.hidden);AfterHoursAudio.advance(dt);paint();requestAnimationFrame(tick)}
requestAnimationFrame(tick);

// A real activity pause (not a change in audio focus) pauses gameplay and audio.
window.onNativePause=function(){cancelAim();if(phase==="play")phase="pause";AfterHoursAudio.setPaused(true)};
