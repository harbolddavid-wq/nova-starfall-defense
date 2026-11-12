'use strict';
(() => {
const $=s=>document.getElementById(s), canvas=$('sky'),ctx=canvas.getContext('2d');
const params=new URLSearchParams(location.search),motion=params.get('motion')!=='0',nativeAudio=params.get('audio')==='native';
const pendingSounds=new Set();let nativeStarted=false;
function nativeCue(cue){const img=new Image();pendingSounds.add(img);img.onload=img.onerror=()=>pendingSounds.delete(img);img.src='/_audio?session='+encodeURIComponent(params.get('session')||'')+'&cue='+cue+'&seq='+Math.random();}
let sound=params.get('sound')!=='0',audio=null,master=null,ambient=null,started=false,paused=false,hidden=false;
let w=1,h=1,dpr=1,t=0,last=0,raf=0,carry=false,pointer={x:0,y:0},dragStart=null,trail=[],particles=[],guideUntil=0,winFlash=0;
const key='starwell.save.v1';
const chapters=[
 {name:'THE LANTERN',color:'#7df7e5',points:[[.30,.24],[.69,.24],[.72,.46],[.50,.65],[.28,.46]],edges:[[0,1],[1,2],[2,3],[3,4],[4,0],[0,3],[1,3]],title:'A light left on.',text:'The first thing the sky remembers is a window.\nSomeone stayed awake until you came home.'},
 {name:'THE WANDERER',color:'#b9a0ff',points:[[.27,.59],[.39,.35],[.56,.20],[.70,.36],[.72,.60]],edges:[[0,1],[1,2],[2,3],[3,4],[1,3],[0,4]],title:'The long way around.',text:'Not every wandering light is lost.\nSome are finding a place wide enough to become themselves.'},
 {name:'THE HEART',color:'#ffc48e',points:[[.50,.64],[.24,.37],[.34,.22],[.66,.22],[.76,.37]],edges:[[0,1],[1,2],[2,3],[3,4],[4,0],[2,0],[3,0]],title:'Love remains.',text:'Even after the names of the stars are forgotten,\nthe spaces between them remember how to hold the light.'}
];
let save={chapter:0,lit:0,ending:false,free:[]};
try{let s=JSON.parse(localStorage.getItem(key));if(s&&Number.isInteger(s.chapter)&&s.chapter>=0&&s.chapter<3&&Number.isInteger(s.lit)&&s.lit>=0&&s.lit<=5){save={chapter:s.chapter,lit:s.lit,ending:s.ending===true&&s.chapter===2&&s.lit===5,free:Array.isArray(s.free)?s.free.filter(p=>Array.isArray(p)&&p.length===2&&p.every(v=>Number.isFinite(v)&&v>=0&&v<=1)).slice(-90):[]};}}catch(e){}
function persist(){try{localStorage.setItem(key,JSON.stringify(save));return true;}catch(e){$('hint').textContent='Storage is full. Keep this world open to hold your progress.';return false;}}
function resize(){const b=canvas.getBoundingClientRect();w=b.width;h=b.height;dpr=Math.min(devicePixelRatio||1,2);canvas.width=w*dpr;canvas.height=h*dpr;ctx.setTransform(dpr,0,0,dpr,0,0);draw();}
new ResizeObserver(resize).observe(canvas);
function well(){return{x:w*.5,y:h*.87};}function point(n){const p=chapters[save.chapter].points[n];return{x:p[0]*w,y:p[1]*h};}
function update(){const roman=['I','II','III'];$('chapter').textContent=save.ending?'EPILOGUE · YOUR OWN SKY':`CHAPTER ${roman[save.chapter]} · ${chapters[save.chapter].name}`;$('progress').innerHTML=Array.from({length:5},(_,i)=>`<i class="${i<save.lit?'lit':''}"></i>`).join('');$('progress').setAttribute('aria-label',`${save.lit} of 5 stars awake`);$('mute').textContent=sound?'Sound on':'Sound off';$('mute').setAttribute('aria-pressed',String(!sound));$('hint').textContent=save.ending?'Tap anywhere to leave a star. Make a sky of your own.':'Drag from the well to the glowing star — or tap each in turn.';$('message').textContent=save.ending?'The sky is yours now.':`${save.lit} / 5 stars awake · ${save.lit===0?'Touch the bright well below.':'The next star is waiting.'}`;}
function audioStart(){
 if(nativeAudio){if(sound&&!nativeStarted){nativeStarted=true;nativeCue('start');}return;}
 if(!audio){try{audio=new (window.AudioContext||window.webkitAudioContext)();master=audio.createGain();master.gain.value=sound?.65:0;master.connect(audio.destination);ambient=audio.createGain();ambient.gain.value=.22;ambient.connect(master);[330,440,550,660].forEach((f,i)=>{let o=audio.createOscillator(),g=audio.createGain();o.type='sine';o.frequency.value=f;g.gain.value=.22/(i+1);o.connect(g);g.connect(ambient);o.start();});}catch(e){audio=null;}}
 if(audio&&sound)audio.resume().catch(()=>{});
}
function tone(note=0,celebrate=false){if(!sound)return;audioStart();if(nativeAudio){nativeCue(celebrate?'success':'note'+(note%7));return;}if(!audio)return;const notes=[261.63,329.63,392,493.88,523.25,659.25,783.99];const now=audio.currentTime;let sequence=celebrate?[0,2,4,5,6]:[note%7];sequence.forEach((n,i)=>{let o=audio.createOscillator(),g=audio.createGain();o.type='sine';o.frequency.value=notes[n];o.connect(g);g.connect(master);let at=now+i*.13;g.gain.setValueAtTime(0,at);g.gain.linearRampToValueAtTime(.28,at+.012);g.gain.exponentialRampToValueAtTime(.001,at+1.5);o.start(at);o.stop(at+1.6);});}
function burst(x,y,color,num=28){if(!motion)return;for(let i=0;i<num;i++){let a=Math.PI*2*i/num,s=20+Math.random()*70;particles.push({x,y,vx:Math.cos(a)*s,vy:Math.sin(a)*s,life:1,color});}if(particles.length>200)particles.splice(0,particles.length-200);}
function story(tag,title,text,button){$('storytag').textContent=tag;$('storytitle').textContent=title;$('storytext').textContent=text;$('continue').textContent=button;$('smallnote').textContent='Your sky saves itself.';$('veil').classList.remove('hidden');carry=false;}
function complete(){const c=chapters[save.chapter];tone(0,true);story('CONSTELLATION AWAKENED',c.title,c.text,save.chapter<2?'Follow the next light':'Keep this light');}
function deposit(x,y){if(save.ending||save.lit>=5)return false;let p=point(save.lit);if(Math.hypot(x-p.x,y-p.y)>Math.max(28,Math.min(w,h)*.09))return false;save.lit++;carry=false;persist();tone(save.lit-1);burst(p.x,p.y,chapters[save.chapter].color);winFlash=1;update();if(save.lit===5)complete();return true;}
$('continue').addEventListener('click',()=>{
 audioStart();started=true;if(!nativeAudio)tone(0,true);
 if(save.lit===5&&!save.ending){if(save.chapter<2){save.chapter++;save.lit=0;persist();}else{save.ending=true;persist();tone(0,true);}}
 $('veil').classList.add('hidden');update();resize();
});
if(save.ending){$('storytitle').textContent='Your sky remembers.';$('storytext').textContent='The constellations are awake.\nCome leave a little more light.';$('continue').textContent='Return to your sky';}
else if(save.lit===5){const c=chapters[save.chapter];$('storytag').textContent='CONSTELLATION AWAKENED';$('storytitle').textContent=c.title;$('storytext').textContent=c.text;$('continue').textContent=save.chapter<2?'Follow the next light':'Keep this light';}
else if(save.lit>0||save.chapter>0){$('storytitle').textContent='Your light is still here.';$('storytext').textContent='The well kept your place.\nThe sky has been waiting.';$('continue').textContent='Continue the constellation';}
function coords(e){let r=canvas.getBoundingClientRect();return{x:e.clientX-r.left,y:e.clientY-r.top};}
canvas.addEventListener('pointerdown',e=>{
 if(!started||!$('veil').classList.contains('hidden')||paused)return;e.preventDefault();audioStart();pointer=coords(e);try{canvas.setPointerCapture(e.pointerId);}catch(e){}dragStart={...pointer};
 if(save.ending){save.free.push([pointer.x/w,pointer.y/h]);save.free=save.free.slice(-90);persist();burst(pointer.x,pointer.y,'#c9b7ff',16);tone(save.free.length);return;}
 const o=well();if(Math.hypot(pointer.x-o.x,pointer.y-o.y)<Math.max(38,w*.13)){carry=true;tone(0);$('hint').textContent='Now carry your light to the glowing star.';}else if(carry){deposit(pointer.x,pointer.y);}else{$('hint').textContent='Touch the bright well at the bottom first.';guideUntil=t+3;}
});
canvas.addEventListener('pointermove',e=>{if(!dragStart)return;pointer=coords(e);if(carry&&motion){trail.push({...pointer,life:1});if(trail.length>35)trail.shift();}});
canvas.addEventListener('pointerup',e=>{pointer=coords(e);if(carry&&dragStart&&Math.hypot(pointer.x-dragStart.x,pointer.y-dragStart.y)>12){if(!deposit(pointer.x,pointer.y)){carry=false;$('hint').textContent='The light returned to the well. Try the glowing star.';}}dragStart=null;});
canvas.addEventListener('pointercancel',()=>{dragStart=null;carry=false;});
$('guide').onclick=()=>{guideUntil=t+5;$('hint').textContent=save.ending?'Tap the sky to add a star. Your constellation stays here.':'Touch the bright well below, then the numbered glowing star.';tone(1);};
$('mute').onclick=()=>{sound=!sound;try{localStorage.setItem('starwell.sound',String(sound));}catch(e){}if(nativeAudio)nativeCue(sound?'on':'off');audioStart();if(master)master.gain.setTargetAtTime(sound?.65:0,audio.currentTime,.08);update();};
// Host preference wins when changed; an in-world mute survives player re-entry until then.
try{let lastHost=localStorage.getItem('starwell.hostSound'),current=params.get('sound')!=='0'?'true':'false';if(!nativeAudio&&lastHost===current&&localStorage.getItem('starwell.sound')!==null)sound=localStorage.getItem('starwell.sound')==='true';else localStorage.removeItem('starwell.sound');localStorage.setItem('starwell.hostSound',current);}catch(e){}
let resetConfirm=false,resetTimer;
$('reset').onclick=()=>{if(!resetConfirm){resetConfirm=true;$('reset').textContent='Reset sky?';clearTimeout(resetTimer);resetTimer=setTimeout(()=>{resetConfirm=false;$('reset').textContent='Start over';},3500);return;}resetConfirm=false;clearTimeout(resetTimer);$('reset').textContent='Start over';save={chapter:0,lit:0,ending:false,free:[]};carry=false;trail=[];particles=[];persist();update();story('A FRESH NIGHT','Every sky begins\nwith one light.','The well is full again.\nLet’s see what it remembers.','Wake the sky');};
function glow(x,y,r,color,a=1){ctx.globalAlpha=a;let g=ctx.createRadialGradient(x,y,0,x,y,r);g.addColorStop(0,color);g.addColorStop(1,'transparent');ctx.fillStyle=g;ctx.beginPath();ctx.arc(x,y,r,0,Math.PI*2);ctx.fill();ctx.globalAlpha=1;}
function star(x,y,r,color,phase){ctx.save();ctx.translate(x,y);ctx.rotate(phase);ctx.fillStyle=color;ctx.beginPath();ctx.moveTo(0,-r);ctx.quadraticCurveTo(r*.18,-r*.18,r,0);ctx.quadraticCurveTo(r*.18,r*.18,0,r);ctx.quadraticCurveTo(-r*.18,r*.18,-r,0);ctx.quadraticCurveTo(-r*.18,-r*.18,0,-r);ctx.fill();ctx.restore();}
function draw(){
 ctx.clearRect(0,0,w,h);let col=chapters[save.chapter].color;
 // Breathing nebula, paired planetary discs, drifting stellar depth.
 glow(w*.48+Math.sin(t*.12)*w*.08,h*.4,w*.65,'#28365d',.42);glow(w*.75,h*.56,w*.40,'#503969',.18);
 for(let i=0;i<95;i++){let x=(i*137.5+Math.sin(i)*48)%w,y=(i*79.9)%h;ctx.globalAlpha=.25+(Math.sin(i+t*.5)+1)*.22;ctx.fillStyle=i%4?'#b7c9ef':col;ctx.beginPath();ctx.arc(x,y,i%11===0?1.5:.65,0,7);ctx.fill();}ctx.globalAlpha=1;
 let px=w*.82,py=h*.13,pr=Math.min(w,h)*.07;glow(px,py,pr*2.3,'#9f8bd9',.14);let g=ctx.createLinearGradient(px-pr,py-pr,px+pr,py+pr);g.addColorStop(0,'#7787ac');g.addColorStop(.5,'#253852');g.addColorStop(1,'#10162b');ctx.fillStyle=g;ctx.beginPath();ctx.arc(px,py,pr,0,7);ctx.fill();ctx.strokeStyle='#8d9db64f';ctx.lineWidth=1;ctx.beginPath();ctx.ellipse(px,py,pr*1.7,pr*.4,-.3,0,7);ctx.stroke();
 const c=chapters[save.chapter];
 ctx.lineCap='round';ctx.lineWidth=1.5;
 for(const [a,b]of c.edges){if(a>=save.lit||b>=save.lit)continue;let p=point(a),q=point(b);ctx.strokeStyle=col+'66';ctx.beginPath();ctx.moveTo(p.x,p.y);ctx.lineTo(q.x,q.y);ctx.stroke();if(motion){let f=(t*.25+a*.2)%1;glow(p.x+(q.x-p.x)*f,p.y+(q.y-p.y)*f,6,col,.65);}}
 for(let i=0;i<5;i++){let p=point(i),lit=i<save.lit,next=i===save.lit;let pulse=1+Math.sin(t*2.5+i)*.12;if(lit){glow(p.x,p.y,23*pulse,col,.45);star(p.x,p.y,7,col,t*.12);}else{ctx.strokeStyle=next?col:'#52638177';ctx.lineWidth=next?1.5:1;ctx.beginPath();ctx.arc(p.x,p.y,next?18*pulse:7,0,7);ctx.stroke();if(next){glow(p.x,p.y,30,col,.13);ctx.fillStyle=col;ctx.font='12px system-ui';ctx.textAlign='center';ctx.textBaseline='middle';ctx.fillText(String(i+1),p.x,p.y);if(guideUntil>t){ctx.font='10px system-ui';ctx.fillText('BRING LIGHT HERE',p.x,Math.max(14,p.y-32));}}}}
 if(!save.ending){let o=well();for(let k=3;k>0;k--){ctx.strokeStyle=col+(k===1?'88':'22');ctx.lineWidth=1;ctx.beginPath();ctx.ellipse(o.x,o.y,22+k*6,8+k*3,Math.sin(t*.3)*.15,0,7);ctx.stroke();}glow(o.x,o.y,44,col,.45);star(o.x,o.y,10+Math.sin(t*2)*2,'#fff4da',t*.2);ctx.font='9px system-ui';ctx.fillStyle='#9eb3cf';ctx.textAlign='center';ctx.fillText('THE WELL',o.x,o.y+34);if(carry){ctx.setLineDash([2,7]);ctx.strokeStyle=col+'77';ctx.beginPath();ctx.moveTo(o.x,o.y);ctx.quadraticCurveTo(o.x,pointer.y,pointer.x,pointer.y);ctx.stroke();ctx.setLineDash([]);glow(pointer.x,pointer.y,24,col,.6);star(pointer.x,pointer.y,7,'#ffffff',t);}}
 for(let i=0;i<save.free.length;i++){let p=save.free[i];star(p[0]*w,p[1]*h,3+(i%3),'#d3bfff',Math.sin(t*.4+i)*.3);if(i>0){let q=save.free[i-1];ctx.strokeStyle='#aa91dc33';ctx.beginPath();ctx.moveTo(p[0]*w,p[1]*h);ctx.lineTo(q[0]*w,q[1]*h);ctx.stroke();}}
 if(motion){for(const p of trail)glow(p.x,p.y,7,col,p.life*.5);for(const p of particles){ctx.globalAlpha=p.life;star(p.x,p.y,2.3,p.color,t);ctx.globalAlpha=1;}}
 if(winFlash>0){ctx.strokeStyle=col;ctx.globalAlpha=winFlash*.3;ctx.lineWidth=2;ctx.strokeRect(3,3,w-6,h-6);ctx.globalAlpha=1;}
}
function frame(now){raf=0;if(paused||hidden)return;let dt=Math.min((now-last)/1000||0,.045);last=now;if(motion)t+=dt;else t=0;trail.forEach(p=>p.life-=dt*1.8);trail=trail.filter(p=>p.life>0);particles.forEach(p=>{p.x+=p.vx*dt;p.y+=p.vy*dt;p.life-=dt*.65;});particles=particles.filter(p=>p.life>0);winFlash=Math.max(0,winFlash-dt);draw();raf=requestAnimationFrame(frame);}
function schedule(){if(!raf&&!paused&&!hidden){last=performance.now();raf=requestAnimationFrame(frame);}}
window.holodeckPause=()=>{paused=true;carry=false;dragStart=null;if(audio)audio.suspend().catch(()=>{});if(raf)cancelAnimationFrame(raf);raf=0;};
window.holodeckResume=()=>{paused=false;if(started&&audio&&sound)audio.resume().catch(()=>{});schedule();};
document.addEventListener('visibilitychange',()=>{hidden=document.hidden;if(hidden){if(audio)audio.suspend().catch(()=>{});if(raf)cancelAnimationFrame(raf);raf=0;}else{if(!paused&&started&&audio&&sound)audio.resume().catch(()=>{});schedule();}});
// Read-only diagnostics for automated playtests. All moves use the real pointer handlers.
window.Starwell={snapshot:()=>({chapter:save.chapter,lit:save.lit,ending:save.ending,free:save.free.length,started,paused,sound,motion,well:well(),target:save.lit<5?point(save.lit):null,width:w,height:h,canvas:JSON.parse(JSON.stringify(canvas.getBoundingClientRect().toJSON())),audio:nativeAudio?(started?'native':'unstarted'):audio?audio.state:'unstarted'})};
update();resize();schedule();
})();
