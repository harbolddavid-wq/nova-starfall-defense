"use strict";
let frame=0,phase="menu",stage=1,unlocked=1,score=0,best=0,shots=5,ball=null,drag=null,aiming=false,crates=[],enemies=[],particles=[],popups=[],buttons=[],finishClock=0,shake=0,soundOn=true,ctxAudio=null;
try{unlocked=clamp(parseInt(localStorage.getItem("velvet_unlocked")||"1",10)||1,1,5);best=parseInt(localStorage.getItem("velvet_best")||"0",10)||0}catch(e){}
function noise(freq=360,dur=.12,type="triangle",vol=.05){if(!soundOn)return;try{ctxAudio=ctxAudio||new(window.AudioContext||window.webkitAudioContext)();if(ctxAudio.state==="suspended")ctxAudio.resume();let o=ctxAudio.createOscillator(),g=ctxAudio.createGain();o.type=type;o.frequency.setValueAtTime(freq,ctxAudio.currentTime);o.frequency.exponentialRampToValueAtTime(Math.max(90,freq*.68),ctxAudio.currentTime+dur);g.gain.setValueAtTime(vol,ctxAudio.currentTime);g.gain.exponentialRampToValueAtTime(.001,ctxAudio.currentTime+dur);o.connect(g);g.connect(ctxAudio.destination);o.start();o.stop(ctxAudio.currentTime+dur)}catch(e){}}
function burst(x,y,color="#ff70b6",n=16){for(let i=0;i<n;i++){let a=rnd(0,TAU),v=rnd(40,175);particles.push({x,y,vx:Math.cos(a)*v,vy:Math.sin(a)*v-rnd(0,70),life:rnd(.4,.85),max:.85,size:rnd(2,6),color,heart:Math.random()<.35})}}
function popup(x,y,text,color="#ffe7b7"){popups.push({x,y,text,color,life:1.1})}
function addCrate(x,y,w,h,type="wood"){crates.push({x,y,w,h,type,hp:type==="crystal"?1:2,vx:0,vy:0,rot:0,spin:0,moving:false,dead:false})}
function addEnemy(x,y,kind){enemies.push({x,y,kind,seed:rnd(0,TAU),dead:false,r:25})}
function start(l){
 stage=clamp(l,1,5);phase="play";score=0;shots=5;ball=null;drag=null;aiming=false;crates=[];enemies=[];particles=[];popups=[];finishClock=0;shake=0;
 const rows=[
 [[666,354],[803,358]],
 [[649,362],[755,320],[859,362]],
 [[648,326],[752,375],[863,313]],
 [[632,360],[725,308],[819,360],[890,320]],
 [[620,342],[705,295],[789,363],[873,300],[924,363]]
 ];
 rows[stage-1].forEach(([x,y],i)=>{
  addCrate(x,y+43,75,17,i%2?"crystal":"wood");
  addCrate(x-32,y+62,18,46,"wood");
  addCrate(x+32,y+62,18,46,"wood");
  if(stage>2)addCrate(x,y-48,55,14,"crystal");
  addEnemy(x,y,stage+i);
 });
 if(stage>=4){addCrate(558,418,29,48,"wood");addCrate(578,381,45,16,"crystal")}
}
function charm(t){if(t.dead)return;t.dead=true;score+=750;shake=6;burst(t.x,t.y,"#fa80c1",22);burst(t.x,t.y,"#ffdc92",9);popup(t.x,t.y-45,"+750 CHARM!");noise(690,.24,"triangle",.07);setTimeout(()=>noise(1000,.21,"sine",.05),115)}
function hitBlock(b,p){b.hp--;b.moving=true;b.vx+=p.vx*.20+rnd(-35,35);b.vy+=p.vy*.11-140;b.spin+=rnd(-2.4,2.4);score+=95;shake=4;popup(b.x,b.y-20,"+95");burst(p.x,p.y,b.type==="crystal"?"#adf1fb":"#ffbaac",14);noise(170,.17,"triangle",.08);
 if(b.hp<=0){b.dead=true;burst(b.x,b.y,"#ffe1fa",12)}
 for(const t of enemies){if(!t.dead&&Math.hypot(t.x-b.x,t.y-b.y)<89)charm(t)}
 for(const other of crates){if(other!==b&&!other.dead&&Math.hypot(other.x-b.x,other.y-b.y)<65){other.moving=true;other.vx+=rnd(-45,45);other.vy-=55}}
 p.vx*=-.18;p.vy*=.64;
}
function shot(){if(!drag)return;let dx=launcher.x-drag.x,dy=launcher.y-drag.y;if(Math.hypot(dx,dy)<16){drag=null;return;}ball={x:drag.x,y:drag.y,vx:dx*11,vy:dy*11,r:17,life:0,bounces:0,trail:[]};shots--;drag=null;shake=2;burst(ball.x,ball.y,"#ff8ec1",10);noise(250,.21,"triangle",.08)}
function finished(win){if(phase!=="play")return;phase=win?"win":"lose";if(win){unlocked=Math.max(unlocked,Math.min(5,stage+1));best=Math.max(best,score);try{localStorage.setItem("velvet_unlocked",unlocked);localStorage.setItem("velvet_best",best)}catch(e){}burst(474,241,"#ffd599",65);for(let i=0;i<4;i++)setTimeout(()=>noise(510+i*116,.17,"triangle",.06),i*98)}else noise(160,.3,"sawtooth",.045)}
function sim(dt){
 frame++;shake*=.85;
 for(let i=particles.length-1;i>=0;i--){let a=particles[i];a.x+=a.vx*dt;a.y+=a.vy*dt;a.vy+=180*dt;a.life-=dt;if(a.life<=0)particles.splice(i,1)}
 for(let i=popups.length-1;i>=0;i--){let p=popups[i];p.y-=22*dt;p.life-=dt;if(p.life<=0)popups.splice(i,1)}
 if(phase!=="play")return;
 for(const b of crates){if(b.dead||!b.moving)continue;b.vy+=780*dt;b.x+=b.vx*dt;b.y+=b.vy*dt;b.rot+=b.spin*dt;b.vx*=.991;if(b.y+b.h/2>=G){b.y=G-b.h/2;b.vy*=-.2;b.vx*=.7;b.spin*=.8;if(Math.abs(b.vy)<20)b.vy=0;}}
 if(ball){
 let p=ball;p.life+=dt;p.vy+=820*dt;p.x+=p.vx*dt;p.y+=p.vy*dt;p.trail.push({x:p.x,y:p.y});if(p.trail.length>17)p.trail.shift();
 for(const t of enemies){if(!t.dead&&Math.hypot(p.x-t.x,p.y-t.y)<p.r+t.r){charm(t);p.vx*=.55;p.vy*=-.25;break}}
 for(const b of crates){if(b.dead)continue;let x=clamp(p.x,b.x-b.w/2,b.x+b.w/2),y=clamp(p.y,b.y-b.h/2,b.y+b.h/2);if((p.x-x)**2+(p.y-y)**2<(p.r+1)**2){hitBlock(b,p);break}}
 if(p.y+p.r>G){p.y=G-p.r;p.vy*=-.35;p.vx*=.7;p.bounces++}
 if(p.x>1020||p.x<-45||p.y>580||p.life>4.4||(p.bounces>=3&&Math.abs(p.vx)<90)){ball=null;finishClock=0}
 }
 if(enemies.every(t=>t.dead)){finishClock+=dt;if(finishClock>.65)finished(true)}
 else if(!ball&&shots<=0){finishClock+=dt;if(finishClock>1.25)finished(false)}
}
function screenPoint(e){let r=cv.getBoundingClientRect(),x=(e.clientX-r.left)*cv.width/r.width,y=(e.clientY-r.top)*cv.height/r.height;return{x:(x-ox)/sc,y:(y-oy)/sc}}
function findButton(p){for(let i=buttons.length-1;i>=0;i--){let b=buttons[i];if(p.x>=b.x&&p.x<=b.x+b.w&&p.y>=b.y&&p.y<=b.y+b.h)return b}return null}
function aimMove(p){if(!aiming)return;let dx=Math.min(p.x-launcher.x,2),dy=p.y-launcher.y,d=Math.hypot(dx,dy);if(d>108){dx=dx/d*108;dy=dy/d*108}drag={x:launcher.x+dx,y:launcher.y+dy}}
cv.addEventListener("pointerdown",e=>{e.preventDefault();try{cv.setPointerCapture(e.pointerId)}catch(x){}let p=screenPoint(e),b=findButton(p);if(b){noise(440,.09);b.run();return;}if(phase==="play"&&!ball&&shots>0&&p.x<370){aiming=true;drag={x:launcher.x,y:launcher.y};aimMove(p);noise(340,.09)}});
cv.addEventListener("pointermove",e=>{if(aiming){e.preventDefault();aimMove(screenPoint(e))}});
cv.addEventListener("pointerup",e=>{if(aiming){e.preventDefault();aiming=false;shot()}});
cv.addEventListener("pointercancel",()=>{aiming=false;drag=null});
document.addEventListener("visibilitychange",()=>{if(document.hidden&&phase==="play")phase="pause"});
document.addEventListener("keydown",e=>{if(e.key==="Escape"||e.key==="Backspace"){if(phase==="play")phase="pause";else if(phase==="pause")phase="play";else phase="menu"}});
let last=0,lag=0;
function tick(now){let dt=Math.min(.05,(now-last)/1000||.016);last=now;lag+=dt;let k=0;while(lag>=1/60&&k<4){sim(1/60);lag-=1/60;k++}paint();requestAnimationFrame(tick)}
requestAnimationFrame(tick);
