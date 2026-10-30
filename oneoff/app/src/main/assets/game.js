/* NEBULA SWARM — original offline arcade shooter. 2026. */
(() => {
'use strict';
const canvas=document.getElementById('game'), ctx=canvas.getContext('2d',{alpha:false});
const W=420,H=900, TAU=Math.PI*2, audio=window.GameAudio;
const clamp=(v,a,b)=>Math.max(a,Math.min(b,v));
const rand=(a,b)=>a+Math.random()*(b-a);
const irand=(a,b)=>Math.floor(rand(a,b+1));
let state='title', time=0, last=0, score=0, high=0, wave=0, lives=3, combo=0;
let enemies=[], shots=[], hostile=[], particles=[], popups=[], powerups=[], stars=[];
let player={x:W/2,y:738,invuln:0,rapid:0}, fleetDir=1, fireCooldown=0, enemyCooldown=1;
let waveWait=0, waveBanner=0, shake=0, flash=0, drag=false, dragMode='', pointerX=W/2;
let autoFire=true, fireHeld=false, keyLeft=false,keyRight=false,keyFire=false, mute=false;
let bg=null, highAvailable=true;
try{high=Number(localStorage.getItem('nebula-swarm-best')||0)||0;}catch(e){highAvailable=false;}
function resize(){
 const d=Math.min(2.4,window.devicePixelRatio||1),w=window.innerWidth,h=window.innerHeight;
 canvas.width=Math.round(w*d);canvas.height=Math.round(h*d);
 canvas.style.width=w+'px';canvas.style.height=h+'px';
 ctx.setTransform(canvas.width/W,0,0,canvas.height/H,0,0);
}
window.addEventListener('resize',resize);resize();
function background(){
 bg=document.createElement('canvas');bg.width=W;bg.height=H;
 const b=bg.getContext('2d'),gr=b.createLinearGradient(0,0,0,H);
 gr.addColorStop(0,'#060a20');gr.addColorStop(.47,'#0b1430');gr.addColorStop(.78,'#111b36');gr.addColorStop(1,'#050b1a');
 b.fillStyle=gr;b.fillRect(0,0,W,H);
 const clouds=[[65,210,240,'#225b7e',.23],[385,430,210,'#773c87',.22],[130,635,230,'#25578b',.16],[340,80,170,'#5c2a79',.13]];
 for(const [x,y,r,col,opacity] of clouds){
  b.globalAlpha=opacity;const g=b.createRadialGradient(x,y,0,x,y,r);
  g.addColorStop(0,col);g.addColorStop(.5,col);g.addColorStop(1,'#03091b00');
  b.fillStyle=g;b.fillRect(x-r,y-r,r*2,r*2);
 }
 b.globalAlpha=1;
 for(let i=0;i<20;i++){
  b.strokeStyle=`rgba(75,175,217,${rand(.018,.055)})`;b.lineWidth=rand(.4,1.2);
  b.beginPath();b.moveTo(rand(-100,450),0);b.lineTo(rand(-100,550),H);b.stroke();
 }
}
background();
for(let i=0;i<105;i++)stars.push({x:rand(0,W),y:rand(0,H),r:rand(.35,1.6),speed:rand(5,38),tw:rand(0,TAU),a:rand(.22,.8)});
function sound(name){audio.play(name);}
function start(){
 state='playing';score=0;wave=0;lives=3;combo=0;player={x:W/2,y:738,invuln:1.2,rapid:0};
 enemies=[];shots=[];hostile=[];particles=[];popups=[];powerups=[];
 fireCooldown=0;enemyCooldown=1.3;waveWait=.5;waveBanner=0;shake=0;flash=0;
 sound('menu');
}
function spawnWave(){
 wave++;enemies=[];shots=[];hostile=[];fleetDir=wave%2?-1:1;
 const rows=Math.min(5,3+Math.floor((wave-1)/3)),cols=7;
 for(let r=0;r<rows;r++)for(let c=0;c<cols;c++){
  const type=(r+Math.floor(c/2)+wave)%3;
  enemies.push({x:56+c*51,y:155+r*48,type,hp:type===0?1:type===1?2:3,maxHp:type===0?1:type===1?2:3,
   phase:rand(0,TAU),hit:0,alive:true,points:[110,180,260][type],size:type===2?19:17});
 }
 if(wave%4===0)enemies.push({x:210,y:118,type:3,hp:12+wave*2,maxHp:12+wave*2,phase:0,hit:0,alive:true,points:2500,size:37});
 waveBanner=2.0;enemyCooldown=1.4;sound('wave');
}
function burst(x,y,color,count=12,power=1){
 for(let i=0;i<count;i++){
  const a=rand(0,TAU),sp=rand(45,250)*power,life=rand(.28,.78);
  particles.push({x,y,vx:Math.cos(a)*sp,vy:Math.sin(a)*sp,life,max:life,size:rand(1.3,4.5),color});
 }
}
function textPop(x,y,t,color='#d9faff',size=15){popups.push({x,y,t,color,size,life:.95});}
function fire(){
 if(state!=='playing'||waveWait>0)return;
 const rapid=player.rapid>0;
 shots.push({x:player.x-11,y:player.y-27,vy:-700,damage:1,glow:0});
 shots.push({x:player.x+11,y:player.y-27,vy:-700,damage:1,glow:1});
 if(rapid)shots.push({x:player.x,y:player.y-38,vy:-780,damage:1,glow:2});
 burst(player.x,player.y-30,'#9cfaff',3,.27);sound('shoot');
}
function enemyFire(){
 const live=enemies.filter(e=>e.alive&&e.y<720);if(!live.length)return;
 let e=live[irand(0,live.length-1)];
 if(Math.random()<.55){const sorted=live.filter(v=>Math.abs(v.x-player.x)<85);if(sorted.length)e=sorted[irand(0,sorted.length-1)];}
 const aim=clamp((player.x-e.x)/Math.max(160,player.y-e.y),-.48,.48);
 hostile.push({x:e.x,y:e.y+e.size,vy:240+wave*13,vx:aim*130,life:4,kind:e.type});
 if(e.type===3){hostile.push({x:e.x-18,y:e.y+20,vy:245+wave*10,vx:-65,life:4,kind:3});hostile.push({x:e.x+18,y:e.y+20,vy:245+wave*10,vx:65,life:4,kind:3});}
 if(Math.random()<.16)sound('enemyShot');
}
function takeHit(){
 if(player.invuln>0)return;
 lives--;player.invuln=1.65;shake=11;flash=.18;combo=0;
 burst(player.x,player.y,'#75e7ff',30,1.3);sound('playerHit');
 if(lives<=0){state='over';shake=18;sound('gameOver');
  if(score>high){high=score;try{localStorage.setItem('nebula-swarm-best',String(high));}catch(e){}}
 }
}
function killEnemy(e){
 e.alive=false;const col=['#67f4eb','#ee8cf8','#ffbb73','#ff6cae'][e.type];
 burst(e.x,e.y,col,e.type===3?50:17,e.type===3?1.6:.8);
 sound(e.type===3?'explode':'hit');
 combo++;const bonus=Math.min(200,Math.floor(combo/5)*20);
 const gained=e.points+bonus;score+=gained;textPop(e.x,e.y-12,'+'+gained,col,e.type===3?23:15);
 if(Math.random()<.07+(e.type===3?.65:0)){
  powerups.push({x:e.x,y:e.y,type:lives<3&&Math.random()<.48?'heal':'rapid',vy:100,phase:rand(0,TAU)});
 }
 if(!enemies.some(v=>v.alive)){waveWait=2.0;textPop(210,340,'SECTOR CLEAR','#92f5e5',27);}
}
function update(dt){
 time+=dt;
 for(const s of stars){s.y+=s.speed*dt*(state==='playing'?1.5:.65);if(s.y>H+3){s.y=-3;s.x=rand(0,W);}s.tw+=dt*rand(.1,.3);}
 for(let i=particles.length-1;i>=0;i--){const p=particles[i];p.x+=p.vx*dt;p.y+=p.vy*dt;p.vx*=Math.pow(.94,dt*60);p.vy*=Math.pow(.94,dt*60);p.life-=dt;if(p.life<=0)particles.splice(i,1);}
 for(let i=popups.length-1;i>=0;i--){const p=popups[i];p.y-=24*dt;p.life-=dt;if(p.life<=0)popups.splice(i,1);}
 shake=Math.max(0,shake-dt*33);flash=Math.max(0,flash-dt);
 if(state!=='playing')return;
 if(waveWait>0){waveWait-=dt;if(waveWait<=0)spawnWave();}
 waveBanner=Math.max(0,waveBanner-dt);
 player.invuln=Math.max(0,player.invuln-dt);player.rapid=Math.max(0,player.rapid-dt);
 if(drag&&dragMode==='move')player.x+=(pointerX-player.x)*Math.min(1,dt*22);
 if(keyLeft)player.x-=350*dt;if(keyRight)player.x+=350*dt;
 player.x=clamp(player.x,30,W-30);
 fireCooldown-=dt;
 if((autoFire||fireHeld||keyFire)&&fireCooldown<=0){fire();fireCooldown=player.rapid>0?.11:.25;}
 if(waveWait>0)return;
 const alive=enemies.filter(e=>e.alive);
 if(alive.length){
  const speed=30+wave*5+Math.max(0,35-alive.length)*.72;
  let dx=fleetDir*speed*dt;
  let edge=false;
  for(const e of alive)if(e.x+dx-e.size<22||e.x+dx+e.size>W-22){edge=true;break;}
  if(edge){fleetDir*=-1;dx=fleetDir*speed*dt;for(const e of alive)e.y+=15;}
  for(const e of alive){e.x+=dx;e.phase+=dt*(1.6+wave*.1);e.hit=Math.max(0,e.hit-dt);if(e.y>player.y-38){takeHit();e.y=160;}}
 }
 enemyCooldown-=dt;
 if(enemyCooldown<=0&&alive.length){enemyFire();enemyCooldown=Math.max(.33,1.35-wave*.045)*rand(.7,1.28);}
 for(let i=shots.length-1;i>=0;i--){
  const b=shots[i];b.y+=b.vy*dt;
  if(b.y<-30){shots.splice(i,1);continue;}
  let hit=false;
  for(const e of enemies){
   if(!e.alive)continue;
   if(Math.abs(b.x-e.x)<e.size+5&&Math.abs(b.y-e.y)<e.size+8){
    e.hp-=b.damage;e.hit=.12;burst(b.x,b.y,'#fff3c0',4,.3);
    if(e.hp<=0)killEnemy(e);else sound('hit');hit=true;break;
   }
  }
  if(hit)shots.splice(i,1);
 }
 for(let i=hostile.length-1;i>=0;i--){
  const b=hostile[i];b.x+=b.vx*dt;b.y+=b.vy*dt;b.life-=dt;
  if(b.y>H+30||b.x<-30||b.x>W+30||b.life<=0){hostile.splice(i,1);continue;}
  if(Math.abs(b.x-player.x)<22&&Math.abs(b.y-player.y)<23){hostile.splice(i,1);takeHit();}
 }
 for(let i=powerups.length-1;i>=0;i--){
  const p=powerups[i];p.y+=p.vy*dt;p.phase+=dt*4;
  if(p.y>H+20){powerups.splice(i,1);continue;}
  if(Math.abs(p.x-player.x)<29&&Math.abs(p.y-player.y)<32){
   if(p.type==='heal'){lives=Math.min(3,lives+1);textPop(player.x,player.y-38,'HULL +1','#b6ffb4');}
   else{player.rapid=10;textPop(player.x,player.y-38,'TRIPLE SHOT','#ffe89b');}
   burst(p.x,p.y,p.type==='heal'?'#baffbd':'#ffe18a',18,.65);sound('power');powerups.splice(i,1);
  }
 }
}
function line(x1,y1,x2,y2,color,width=1){ctx.strokeStyle=color;ctx.lineWidth=width;ctx.beginPath();ctx.moveTo(x1,y1);ctx.lineTo(x2,y2);ctx.stroke();}
function poly(points,fill,stroke,width=1){ctx.beginPath();ctx.moveTo(points[0][0],points[0][1]);for(let i=1;i<points.length;i++)ctx.lineTo(points[i][0],points[i][1]);ctx.closePath();if(fill){ctx.fillStyle=fill;ctx.fill();}if(stroke){ctx.strokeStyle=stroke;ctx.lineWidth=width;ctx.stroke();}}
function label(t,x,y,size=14,color='#e1f6ff',align='center',weight=700){
 ctx.fillStyle=color;ctx.font=`${weight} ${size}px system-ui,Roboto,sans-serif`;ctx.textAlign=align;ctx.textBaseline='middle';ctx.fillText(t,x,y);
}
function glow(x,y,r,color,alpha=.25){
 const g=ctx.createRadialGradient(x,y,0,x,y,r);g.addColorStop(0,color);g.addColorStop(1,'transparent');ctx.globalAlpha=alpha;ctx.fillStyle=g;ctx.beginPath();ctx.arc(x,y,r,0,TAU);ctx.fill();ctx.globalAlpha=1;
}
function roundRect(x,y,w,h,r,fill,stroke,lineWidth=1){
 ctx.beginPath();ctx.roundRect(x,y,w,h,r);if(fill){ctx.fillStyle=fill;ctx.fill();}if(stroke){ctx.strokeStyle=stroke;ctx.lineWidth=lineWidth;ctx.stroke();}
}
function ship(x,y,s=1,blink=false){
 if(blink&&Math.sin(time*22)<0)return;
 ctx.save();ctx.translate(x,y);ctx.scale(s,s);
 glow(0,8,47,'#4fe8ff',.25);
 const flame=18+Math.sin(time*24)*8;
 poly([[-10,14],[-6,flame+14],[0,20],[6,flame+14],[10,14]],'#48caff');
 poly([[-9,16],[-3,flame+9],[0,20],[3,flame+9],[9,16]],'#ffe0a8');
 poly([[-32,17],[-18,-4],[-11,6],[0,-29],[11,6],[18,-4],[32,17],[13,14],[7,26],[-7,26],[-13,14]],'#2b668a','#8ee9fb',2);
 poly([[-12,10],[0,-30],[12,10],[7,16],[-7,16]],'#d5f7ff','#65e8ff',1.5);
 poly([[-7,-3],[0,-21],[7,-3],[0,4]],'#f7a3dd','#fff4fa',1);
 line(-26,16,-13,10,'#f69edb',3);line(26,16,13,10,'#f69edb',3);
 ctx.restore();
}
function enemyArt(e,x=e.x,y=e.y,s=1){
 const t=e.type;ctx.save();ctx.translate(x,y);ctx.scale(s,s);ctx.rotate(Math.sin(e.phase)*.055);
 const c=['#72e9e2','#e8a0f7','#ffc17c','#ff7ba8'][t];
 if(e.hit>0)glow(0,0,40,'#ffffff',.5);else glow(0,0,t===3?62:32,c,.12);
 if(t===0){
  poly([[-19,-9],[-6,-5],[0,-19],[6,-5],[19,-9],[12,10],[0,18],[-12,10]],'#154c65',c,2);
  poly([[-15,-6],[-5,-1],[-10,10]],'#56d9e4');poly([[15,-6],[5,-1],[10,10]],'#56d9e4');
  poly([[-5,-2],[0,-12],[5,-2],[0,8]],'#fff2be');
  line(-21,4,-28,13,c,2);line(21,4,28,13,c,2);
 }else if(t===1){
  poly([[-3,-9],[-20,-16],[-23,0],[-11,12],[-2,7],[0,17],[2,7],[11,12],[23,0],[20,-16],[3,-9]],'#492858',c,2);
  poly([[-19,-11],[-7,-5],[-11,6],[-20,-1]],'#b67ac9');poly([[19,-11],[7,-5],[11,6],[20,-1]],'#b67ac9');
  ctx.fillStyle='#ffecff';ctx.fillRect(-7,0,4,3);ctx.fillRect(3,0,4,3);
  line(-9,-13,-15,-20,c,2);line(9,-13,15,-20,c,2);
 }else if(t===2){
  poly([[-18,-14],[18,-14],[24,-3],[17,17],[-17,17],[-24,-3]],'#62412e',c,2.5);
  poly([[-13,-8],[13,-8],[16,5],[0,12],[-16,5]],'#a66a3c','#ffe3a8',1);
  poly([[-7,-4],[0,-9],[7,-4],[0,6]],'#fff0c2');
  line(-21,4,-29,8,'#ffad6c',3);line(21,4,29,8,'#ffad6c',3);
 }else{
  poly([[-39,-16],[-26,-29],[-8,-21],[0,-40],[8,-21],[26,-29],[39,-16],[31,17],[14,27],[0,20],[-14,27],[-31,17]],'#672b52',c,3);
  poly([[-24,-12],[-10,-14],[-12,11],[-28,7]],'#ff8bb5');poly([[24,-12],[10,-14],[12,11],[28,7]],'#ff8bb5');
  poly([[-10,-12],[0,-25],[10,-12],[0,13]],'#fff0bd','#ffe4e4',1);
  for(let i=0;i<4;i++)line(-23+i*15,22,-25+i*15,31,c,2);
  roundRect(-29,-45,58,4,2,'#28354c');roundRect(-29,-45,58*(e.hp/e.maxHp),4,2,'#ff8ba7');
 }
 ctx.restore();
}
function drawBackground(){
 ctx.drawImage(bg,0,0,W,H);
 for(const s of stars){const a=s.a*(.76+.24*Math.sin(time*1.6+s.tw));ctx.globalAlpha=a;ctx.fillStyle=s.r>1.1?'#b8efff':'#e0dfff';ctx.fillRect(s.x,s.y,s.r,s.r*2.4);}ctx.globalAlpha=1;
 ctx.strokeStyle='#7bdbff10';ctx.lineWidth=1;
 for(let y=100;y<790;y+=40)line(18,y,402,y,'#8dc7e50a');
 for(let x=35;x<400;x+=45)line(x,95,x,795,'#8dc7e508');
 line(16,96,16,792,'#7be1ef2d');line(404,96,404,792,'#7be1ef2d');
 ctx.globalAlpha=.045;for(let y=0;y<H;y+=4){ctx.fillStyle='#000';ctx.fillRect(0,y,W,1);}ctx.globalAlpha=1;
}
function hud(){
 roundRect(16,18,388,65,17,'#08172cd9','#73dff044',1);
 label('SCORE',35,34,10,'#80cbd9','left',800);label(score.toLocaleString(),35,59,21,'#e7fbff','left',850);
 label('BEST',155,34,10,'#80cbd9','left',800);label(high.toLocaleString(),155,59,16,'#e7fbff','left',750);
 label('WAVE',269,34,10,'#80cbd9','left',800);label(String(wave).padStart(2,'0'),269,59,20,'#e7fbff','left',800);
 roundRect(337,26,26,27,8,'#17334a','#63d8e9',1);
 label(mute?'×':'♫',350,40,17,'#c5f9ff');
 roundRect(370,26,26,27,8,'#17334a','#63d8e9',1);
 label('Ⅱ',383,40,15,'#c5f9ff');
 for(let i=0;i<3;i++){
  const x=336+i*20,y=69;poly([[x,y-6],[x+5,y-2],[x+10,y-6],[x+12,y],[x+5,y+8],[x-2,y]],i<lives?'#ff8db3':'#263e56',i<lives?'#ffc3d7':'#44647c',.8);
 }
 if(player.rapid>0){roundRect(146,91,128,18,8,'#5c456d99');roundRect(148,93,124*(player.rapid/10),14,6,'#ffe397');label('TRIPLE SHOT',210,101,10,'#251d37');}
}
function controlBar(){
 roundRect(17,811,386,73,19,'#09192bdc','#6fd4eb3b',1);
 roundRect(28,822,132,50,13,autoFire?'#23596b':'#202e42',autoFire?'#7df5e6':'#637d9d',1.3);
 label('AUTO FIRE',94,839,12,autoFire?'#c6fff6':'#8ca6b9');label(autoFire?'ON':'OFF',94,858,11,autoFire?'#8df5d2':'#ffd6a0');
 label('DRAG TO MOVE',226,839,11,'#88b7c8');
 label('◀     ▶',226,858,18,'#d0eff5');
 roundRect(319,822,72,50,15,'#5a315c','#f5a4db',1.6);glow(355,847,34,'#fa83ca',.19);label('FIRE',355,846,14,'#ffe9f9');
}
function button(t,x,y,w,h,accent='#73eced'){
 glow(x+w/2,y+h/2,w*.65,accent,.11);
 roundRect(x,y,w,h,15,'#18364fdc',accent,2);
 label(t,x+w/2,y+h/2,18,'#efffff');
}
function titleScene(){
 const pulse=.5+.5*Math.sin(time*1.4);
 glow(210,236,190,'#bd5cff',.18+pulse*.04);
 glow(210,316,180,'#1ce9ef',.13);
 label('N E B U L A',210,158,40,'#ecfaff','center',900);
 label('S W A R M',210,204,46,'#7af1eb','center',900);
 label('THE LAST LIGHT IN THE SECTOR',210,247,11,'#b5a7d7','center',700);
 for(let i=0;i<7;i++){const e={x:60+i*50,y:335+Math.sin(time*1.4+i*.5)*10,type:i%3,phase:time+i,hit:0,hp:1,maxHp:1};enemyArt(e,e.x,e.y,1.02);}
 ship(210,485,1.85);
 label('ONE PILOT  •  ENDLESS WAVES',210,552,12,'#bbd8e7');
 button('LAUNCH MISSION',48,597,324,62);
 label('Drag to steer  •  Auto-fire enabled',210,690,14,'#d4e8f5');
 label('Tap FIRE for manual bursts  •  Collect power cores',210,717,12,'#94b7cc');
 roundRect(130,758,160,43,13,'#13273e','#79c9d6',1);
 label(mute?'SOUND: OFF':'SOUND: ON',210,779,13,'#d1eff5');
 label('OFFLINE  /  ORIGINAL ART & AUDIO',210,842,11,'#668ea6');
}
function pauseScene(){
 ctx.fillStyle='#040918b9';ctx.fillRect(0,0,W,H);
 glow(210,390,170,'#68d7ff',.15);
 label('MISSION PAUSED',210,335,32,'#e4f9ff');
 label('Sector '+wave+'  •  Score '+score.toLocaleString(),210,383,16,'#9bc7d6');
 button('RESUME',56,446,308,62);
 button('RESTART',56,530,308,57,'#ec8bbf');
 label('Tap the top-right sound icon to mute',210,634,13,'#91b5c6');
}
function overScene(){
 ctx.fillStyle='#05081cda';ctx.fillRect(0,0,W,H);
 glow(210,330,195,'#ed598a',.16);
 label('SIGNAL LOST',210,282,40,'#ffafcb');
 label('SECTOR '+wave+' CLEARED? NOT YET.',210,337,13,'#c6a9c9');
 label('FINAL SCORE',210,397,14,'#9abfd0');
 label(score.toLocaleString(),210,441,48,'#effbff');
 label('BEST '+high.toLocaleString(),210,491,16,'#92f1e7');
 button('TRY AGAIN',55,555,310,65);
 label('Every star is worth defending.',210,670,14,'#a7bcd5');
}
function render(){
 ctx.setTransform(canvas.width/W,0,0,canvas.height/H,0,0);
 ctx.save();
 if(shake>0)ctx.translate(rand(-shake,shake),rand(-shake,shake));
 drawBackground();
 if(state==='title'){titleScene();}
 else{
  for(const e of enemies)if(e.alive)enemyArt(e);
  for(const b of shots){glow(b.x,b.y,17,b.glow?'#ec8dfd':'#80eaff',.32);line(b.x,b.y+14,b.x,b.y-15,b.glow?'#f4adff':'#c9ffff',3.6);}
  for(const b of hostile){glow(b.x,b.y,19,'#ff9e92',.28);line(b.x,b.y-9,b.x,b.y+10,'#ffa18c',3.4);}
  for(const p of powerups){
   glow(p.x,p.y,31,p.type==='heal'?'#9affb0':'#ffe292',.35);
   ctx.save();ctx.translate(p.x,p.y);ctx.rotate(p.phase);poly([[0,-14],[12,0],[0,14],[-12,0]],p.type==='heal'?'#4fcb9c':'#e4a752','#f9fff0',2);ctx.restore();
   label(p.type==='heal'?'+':'⚡',p.x,p.y,15,'#fff');
  }
  ship(player.x,player.y,1,player.invuln>0);
  for(const p of particles){ctx.globalAlpha=clamp(p.life/p.max,0,1);glow(p.x,p.y,p.size*3,p.color,.18);ctx.fillStyle=p.color;ctx.fillRect(p.x-p.size/2,p.y-p.size/2,p.size,p.size);}ctx.globalAlpha=1;
  for(const p of popups){ctx.globalAlpha=clamp(p.life/.65,0,1);label(p.t,p.x,p.y,p.size,p.color);ctx.globalAlpha=1;}
  hud();controlBar();
  if(waveBanner>0&&state==='playing'){
   ctx.globalAlpha=Math.min(1,waveBanner,.8);roundRect(80,365,260,83,18,'#101b3ee8','#7df4f1',1.3);
   label('INCOMING WAVE',210,387,13,'#8bd6df');label(String(wave).padStart(2,'0'),210,423,37,'#f0ffff');ctx.globalAlpha=1;
  }
  if(state==='paused')pauseScene();
  if(state==='over')overScene();
 }
 if(flash>0){ctx.globalAlpha=flash*2.5;ctx.fillStyle='#d7f5ff';ctx.fillRect(0,0,W,H);ctx.globalAlpha=1;}
 ctx.restore();
}
function toXY(e){const r=canvas.getBoundingClientRect();return{x:(e.clientX-r.left)/r.width*W,y:(e.clientY-r.top)/r.height*H};}
function down(e){
 e.preventDefault();const {x,y}=toXY(e);audio.init();
 if(state==='title'){if(y>750&&y<816){mute=audio.setMuted(!mute);return;}if(y>540){start();return;}return;}
 if(state==='over'){if(y>525&&y<665){start();return;}return;}
 if(state==='paused'){
  if(y>430&&y<520){state='playing';sound('menu');return;}
  if(y>520&&y<615){start();return;}
  if(y<85&&x>330&&x<368){mute=audio.setMuted(!mute);return;}
  return;
 }
 if(y<83&&x>330&&x<369){mute=audio.setMuted(!mute);sound('menu');return;}
 if(y<83&&x>369){state='paused';fireHeld=false;drag=false;sound('menu');return;}
 if(y>810&&x<170){autoFire=!autoFire;sound('menu');return;}
 drag=true;pointerX=clamp(x,30,W-30);
 if(y>805&&x>305){dragMode='fire';fireHeld=true;}
 else{dragMode='move';fireHeld=true;player.x=pointerX;}
 try{canvas.setPointerCapture(e.pointerId);}catch(err){}
}
function move(e){if(!drag)return;e.preventDefault();const p=toXY(e);if(dragMode==='move')pointerX=clamp(p.x,30,W-30);}
function up(e){if(drag){drag=false;fireHeld=false;dragMode='';}try{canvas.releasePointerCapture(e.pointerId);}catch(err){}}
canvas.addEventListener('pointerdown',down,{passive:false});canvas.addEventListener('pointermove',move,{passive:false});canvas.addEventListener('pointerup',up);canvas.addEventListener('pointercancel',up);
window.addEventListener('keydown',e=>{
 if(['ArrowLeft','ArrowRight',' ','ArrowUp'].includes(e.key))e.preventDefault();
 if(e.key==='ArrowLeft'||e.key.toLowerCase()==='a')keyLeft=true;
 if(e.key==='ArrowRight'||e.key.toLowerCase()==='d')keyRight=true;
 if(e.key===' '||e.key==='ArrowUp')keyFire=true;
 if(e.key.toLowerCase()==='p'||e.key==='Escape'){if(state==='playing')state='paused';else if(state==='paused')state='playing';}
 if(e.key.toLowerCase()==='m')mute=audio.setMuted(!mute);
 if(e.key==='Enter'&&(state==='title'||state==='over'))start();
});
window.addEventListener('keyup',e=>{if(e.key==='ArrowLeft'||e.key.toLowerCase()==='a')keyLeft=false;if(e.key==='ArrowRight'||e.key.toLowerCase()==='d')keyRight=false;if(e.key===' '||e.key==='ArrowUp')keyFire=false;});
window.nativePause=()=>{if(state==='playing')state='paused';};
window.nativeBack=()=>{if(state==='playing')state='paused';else if(state==='paused')state='playing';};
document.addEventListener('visibilitychange',()=>{if(document.hidden&&state==='playing')state='paused';});
function frame(ms){try{if(!last)last=ms;const dt=clamp((ms-last)/1000,0,.035);last=ms;update(dt);render();}catch(e){console.error('Nebula Swarm game error',e);document.getElementById('fatal').hidden=false;}requestAnimationFrame(frame);}
requestAnimationFrame(frame);
window.__nebulaTest={getState:()=>({state,score,high,wave,lives,enemies:enemies.filter(e=>e.alive).length,shots:shots.length,hostile:hostile.length,autoFire,mute}),start,spawnWave,hit:takeHit,step:update};
})();