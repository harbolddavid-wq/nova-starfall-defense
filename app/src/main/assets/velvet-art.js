"use strict";
const cv=document.getElementById("game"),c=cv.getContext("2d",{alpha:false}),W=960,H=540,TAU=2*Math.PI,G=450,launcher={x:158,y:368};
let sc=1,ox=0,oy=0;
function resize(){let d=Math.min(2,window.devicePixelRatio||1);cv.width=Math.round(innerWidth*d);cv.height=Math.round(innerHeight*d);sc=Math.min(cv.width/W,cv.height/H);ox=(cv.width-W*sc)/2;oy=(cv.height-H*sc)/2}
window.addEventListener("resize",resize);resize();
const clamp=(v,a,b)=>Math.max(a,Math.min(b,v)),rnd=(a,b)=>a+Math.random()*(b-a);
function fill(x,y,w,h,color){c.fillStyle=color;c.fillRect(x,y,w,h)}
function stroke(points,color,width=2){c.lineWidth=width;c.strokeStyle=color;c.beginPath();points.forEach((p,i)=>i?c.lineTo(p[0],p[1]):c.moveTo(p[0],p[1]));c.stroke()}
function dot(x,y,r,color){c.beginPath();c.arc(x,y,r,0,TAU);c.fillStyle=color;c.fill()}
function glow(x,y,r,color,a=1){c.save();c.globalAlpha=a;let g=c.createRadialGradient(x,y,0,x,y,r);g.addColorStop(0,color);g.addColorStop(1,"rgba(0,0,0,0)");dot(x,y,r,g);c.restore()}
function box(x,y,w,h,r,fillColor,border=null,bw=1){if(w<=0||h<=0)return;c.beginPath();c.roundRect(x,y,w,h,r);if(fillColor){c.fillStyle=fillColor;c.fill()}if(border){c.strokeStyle=border;c.lineWidth=bw;c.stroke()}}
function words(t,x,y,size=22,color="#fff",align="center",weight="700"){c.save();c.font=weight+" "+size+"px Georgia,serif";c.textAlign=align;c.textBaseline="middle";c.shadowColor="#120514";c.shadowBlur=8;c.shadowOffsetY=2;c.fillStyle=color;c.fillText(t,x,y);c.restore()}
function heart(x,y,s=1,color="#fa5b99",rot=0){c.save();c.translate(x,y);c.rotate(rot);c.scale(s,s);c.beginPath();c.moveTo(0,13);c.bezierCurveTo(-8,6,-16,1,-14,-8);c.bezierCurveTo(-12,-16,-3,-17,0,-10);c.bezierCurveTo(5,-17,14,-15,15,-7);c.bezierCurveTo(16,2,7,8,0,13);c.closePath();let g=c.createLinearGradient(-11,-12,9,10);g.addColorStop(0,"#fff0e6");g.addColorStop(.24,color);g.addColorStop(1,"#9c1355");c.fillStyle=g;c.shadowColor=color;c.shadowBlur=14;c.fill();c.lineWidth=1;c.shadowBlur=0;c.strokeStyle="#ffe1ea";c.stroke();dot(-5,-7,2,"#fff");c.restore()}
function wing(x,y,s=1,color="#1b0d25"){c.save();c.translate(x,y);c.scale(s,s);c.beginPath();c.moveTo(0,0);c.bezierCurveTo(-10,-12,-18,-1,-34,-18);c.lineTo(-29,8);c.lineTo(-19,0);c.lineTo(-14,17);c.lineTo(0,6);c.lineTo(14,17);c.lineTo(19,0);c.lineTo(29,8);c.lineTo(34,-18);c.bezierCurveTo(18,-1,10,-12,0,0);c.fillStyle=color;c.fill();c.restore()}
const stars=Array.from({length:128},()=>({x:rnd(0,W),y:rnd(0,320),r:rnd(.3,1.7),ph:rnd(0,TAU)}));
const embers=Array.from({length:45},()=>({x:rnd(0,W),y:rnd(0,460),s:rnd(.2,1),p:rnd(0,TAU)}));
function turret(x,foot,h){fill(x,foot-h,49,h,"#140c21");for(let i=0;i<4;i++)fill(x-6+i*15,foot-h-11,12,17,"#160c21");c.beginPath();c.moveTo(x-12,foot-h);c.lineTo(x+24,foot-h-40);c.lineTo(x+60,foot-h);c.fillStyle="#150b22";c.fill();box(x+19,foot-h+38,13,23,7,"#ae58a2","#7c3b75");glow(x+25,foot-h+49,28,"#ee74d2",.15)}
function scenery(){
 let sky=c.createLinearGradient(0,0,0,H);sky.addColorStop(0,"#0b0820");sky.addColorStop(.44,"#3c174b");sky.addColorStop(.8,"#853957");sky.addColorStop(1,"#2c152d");fill(0,0,W,H,sky);
 glow(749,117,230,"#b65dda",.33);glow(753,115,125,"#f7d9ef",.16);
 let moon=c.createRadialGradient(747,112,5,753,115,82);moon.addColorStop(0,"#fff8dd");moon.addColorStop(.73,"#f4d8ee");moon.addColorStop(1,"#ab87bc");dot(752,114,81,moon);c.globalAlpha=.09;dot(718,94,18,"#422445");dot(779,143,15,"#422445");c.globalAlpha=1;
 for(const s of stars){c.globalAlpha=.48+.4*Math.sin(frame*.014+s.ph);dot(s.x,s.y,s.r,"#fff1f6")}c.globalAlpha=1;
 c.fillStyle="#2a1331";c.beginPath();c.moveTo(0,420);for(let x=0;x<W+25;x+=23)c.lineTo(x,365+21*Math.sin(x*.012)+13*Math.cos(x*.06));c.lineTo(W,460);c.lineTo(0,460);c.fill();
 turret(14,420,133);turret(96,435,90);turret(494,429,119);turret(840,442,105);turret(914,445,160);
 c.fillStyle="#150c22";c.beginPath();c.moveTo(0,434);for(let x=0;x<W+25;x+=25)c.lineTo(x,411+12*Math.sin(x*.034));c.lineTo(W,510);c.lineTo(0,510);c.fill();
 for(let i=0;i<12;i++){let x=i*84;box(x+8,435,23,37,10,"#160d24","#543153");glow(x+19,446,17,"#f35daf",.16)}
 for(const e of embers){let y=(e.y-frame*.12*e.s+5000)%466;dot(e.x+Math.sin(frame*.02+e.p)*17,y,1.1,"#ffa8d2")}
}
function floorArt(){
 let g=c.createLinearGradient(0,G,0,H);g.addColorStop(0,"#6a3b50");g.addColorStop(.15,"#382339");g.addColorStop(1,"#180d20");fill(0,G,W,H-G,g);stroke([[0,G],[W,G]],"#ffc58d",3);stroke([[0,G+5],[W,G+5]],"#a75986",2);
 for(let y=468;y<H;y+=25){stroke([[0,y],[W,y]],"#744560",1);for(let x=((y/25|0)%2)*36-32;x<W;x+=72)stroke([[x,y],[x,y+25]],"#744560",1)}
 for(let i=0;i<18;i++){let x=i*56;glow(x+13,453,24,"#ef61a0",.15)}
}
function curtains(){c.save();let g=c.createLinearGradient(0,0,118,0);g.addColorStop(0,"#0e071b");g.addColorStop(.45,"#752148");g.addColorStop(.78,"#4a1338");g.addColorStop(1,"#17081e");c.beginPath();c.moveTo(0,0);c.lineTo(104,0);c.bezierCurveTo(92,80,40,105,0,131);c.fillStyle=g;c.fill();c.beginPath();c.moveTo(W,0);c.lineTo(W-104,0);c.bezierCurveTo(W-92,80,W-40,105,W,131);c.fill();stroke([[0,138],[48,112],[89,38]],"#e0a272",3);stroke([[W,138],[W-48,112],[W-89,38]],"#e0a272",3);c.restore();}
// Original After Hours character artwork is used in both the menu and playfield.
// The same artwork participates in the small idle movement, so the game never
// promises a character on the title screen that disappears during play.
const vesperArt = new Image();
vesperArt.src = "art/vesper-after-hours.webp";
function vesper(){
 if (!vesperArt.complete || !vesperArt.naturalWidth) return;
 c.save();
 c.translate(86, 445 + Math.sin(frame * .027) * 1.3);
 const breathe = 1 + Math.sin(frame * .027) * .005;
 c.scale(1 / breathe, breathe);
 c.drawImage(vesperArt, -81, -243, 162, 243);
 c.restore();
}
function slingshot(){
 stroke([[145,446],[145,362]],"#511b39",14);stroke([[145,362],[126,335]],"#9d5c63",13);stroke([[145,362],[173,333]],"#9d5c63",13);stroke([[145,443],[145,365]],"#f3b48c",2);
 let tip=drag||(!ball&&phase==="play"?launcher:null);
 if(tip){stroke([[126,335],[tip.x,tip.y]],"#ffbddd",3);stroke([[173,333],[tip.x,tip.y]],"#ffbddd",3)}
 if(!ball&&phase==="play"){let q=drag||launcher;glow(q.x,q.y,52,"#f94b9f",.29);heart(q.x,q.y,1.42,"#fa65b1",frame*.02);}
}
function gargoyle(t){if(t.dead)return;c.save();c.translate(t.x,t.y+Math.sin(frame*.044+t.seed)*2);wing(0,5,.9,"#211026");
 let g=c.createLinearGradient(-23,-25,23,25);g.addColorStop(0,t.kind%2?"#f3a9bc":"#d1baf0");g.addColorStop(1,t.kind%2?"#a84983":"#6c4b96");
 c.beginPath();c.ellipse(0,0,24,25,0,0,TAU);c.fillStyle=g;c.shadowColor="#ed78cd";c.shadowBlur=13;c.fill();c.shadowBlur=0;
 c.beginPath();c.moveTo(-19,-15);c.lineTo(-20,-43);c.lineTo(-3,-26);c.lineTo(11,-26);c.lineTo(21,-43);c.lineTo(19,-14);c.fillStyle="#422044";c.fill();
 dot(-9,-4,6,"#fff5ee");dot(10,-4,6,"#fff5ee");dot(-7,-3,3,"#1a1022");dot(12,-3,3,"#1a1022");stroke([[-11,12],[1,17],[13,11]],"#4b1b3c",2.6);
 dot(-16,7,3.6,"#ff859f");dot(16,7,3.6,"#ff859f");heart(0,-33,.59,"#ff6d9e");c.restore();}
function drawCrate(b){if(b.dead)return;c.save();c.translate(b.x,b.y);c.rotate(b.rot);let x=-b.w/2,y=-b.h/2,gl=c.createLinearGradient(x,y,x+b.w,y+b.h);
 if(b.type==="crystal"){gl.addColorStop(0,"#f3eeff");gl.addColorStop(.29,"#80e0ee");gl.addColorStop(.68,"#aa68dd");gl.addColorStop(1,"#5a356f")}
 else{gl.addColorStop(0,"#9c5979");gl.addColorStop(.45,"#4a2548");gl.addColorStop(1,"#25132f")}
 box(x,y,b.w,b.h,3,gl,b.type==="crystal"?"#e5f9ff":"#e8b38e",2);
 box(x+4,y+4,b.w-8,b.h-8,2,null,"#efbfd3",.65);
 if(b.type==="crystal")heart(0,0,.48,"#ff91cb");else{stroke([[x+5,y+5],[x+b.w-5,y+b.h-5]],"#b9809c",1.4);stroke([[x+b.w-5,y+5],[x+5,y+b.h-5]],"#b9809c",1.4);dot(0,0,2.5,"#ffdcad")}
 c.restore();}
function particlesDraw(){for(const p of particles){c.globalAlpha=clamp(p.life/p.max,0,1);if(p.heart)heart(p.x,p.y,p.size/17,p.color);else{glow(p.x,p.y,p.size*3,p.color,.44);dot(p.x,p.y,p.size,p.color)}}c.globalAlpha=1;for(const t of popups){c.globalAlpha=clamp(t.life,0,1);words(t.text,t.x,t.y,17,t.color)}c.globalAlpha=1}
function drawButton(label,x,y,w,h,run,hot=false){let g=c.createLinearGradient(x,y,x,y+h);g.addColorStop(0,hot?"#fc8cba":"#693567");g.addColorStop(1,hot?"#b92573":"#301833");c.save();c.shadowColor=hot?"#ff7cbd":"#000";c.shadowBlur=hot?20:9;box(x,y,w,h,14,g,hot?"#ffe0bd":"#c08c9c",2);c.restore();words(label,x+w/2,y+h/2,Math.min(27,h*.48),"#fff2ea");buttons.push({x,y,w,h,run})}
function hud(){box(12,11,308,61,13,"rgba(19,7,29,.86)","#ddad8f",1.4);words("NIGHT "+stage+"  ·  VELVET KEEP",26,30,17,"#ffdfbe","left");words("SCORE  "+score.toLocaleString(),26,56,17,"#ffc4db","left");for(let i=0;i<5;i++){c.globalAlpha=i<shots?1:.18;heart(431+i*35,38,.72)}c.globalAlpha=1;box(771,12,180,50,12,"rgba(19,7,29,.83)","#ddad8f");words("♥  "+enemies.filter(t=>!t.dead).length+" LEFT",791,38,20,"#fff3e2","left");drawButton("Ⅱ",703,13,55,50,()=>phase="pause");}
function projectileDraw(){if(!ball)return;for(let i=0;i<ball.trail.length;i++){let p=ball.trail[i];c.globalAlpha=.4*i/ball.trail.length;heart(p.x,p.y,.62,"#fc8fc9")}c.globalAlpha=1;glow(ball.x,ball.y,56,"#ff5cb9",.3);heart(ball.x,ball.y,1.35,"#f65aa1",frame*.04)}
function aimDots(){if(!drag)return;let vx=(launcher.x-drag.x)*11,vy=(launcher.y-drag.y)*11;for(let i=1;i<=15;i++){let t=i*.115;c.globalAlpha=.58*(1-i/17);dot(drag.x+vx*t,drag.y+vy*t+410*t*t,Math.max(1.4,5-i*.2),"#fff2ed")}c.globalAlpha=1}
function gameplay(){scenery();curtains();floorArt();vesper();slingshot();for(const b of crates)drawCrate(b);for(const t of enemies)gargoyle(t);projectileDraw();aimDots();particlesDraw();hud();if(!ball&&!drag&&shots>0)words("DRAG THE HEART BACK AND RELEASE!",326,511,18,"#ffddc8","left","400");}
function darkOverlay(){fill(0,0,W,H,"rgba(12,4,21,.80)");glow(488,236,265,"#e055a9",.18);box(246,89,468,386,24,"#2b1236","#dd9f9d",3);box(258,101,444,362,19,null,"#9d6b8c",1)}
function titleScreen(){scenery();curtains();floorArt();vesper();glow(493,211,275,"#f660a6",.16);box(235,117,539,189,23,"rgba(32,11,41,.86)","#edb08b",3);box(248,129,513,165,16,null,"#b37c98",1.3);words("VELVET",503,170,67,"#ffe1b3");words("FLING",503,239,85,"#ff7abb");words("A deliciously mischievous slingshot adventure",502,283,17,"#ffe3d4","center","400");words("FLING HEARTS  •  TOPPLE TOWERS  •  CHARM GARGOYLES",503,338,17,"#f5bfd6");drawButton("PLAY THE NIGHT",363,360,288,65,()=>start(Math.min(unlocked,5)),true);drawButton("HOW TO PLAY",403,441,206,46,()=>phase="help");words("OFFLINE  ·  NO ADS  ·  FIVE HANDCRAFTED LEVELS",504,520,15,"#f5cbd8","center","400")}
function helpScreen(){scenery();floorArt();vesper();darkOverlay();words("THE ART OF THE FLING",480,150,39,"#ffe0b5");words("1. Drag the glowing heart BACK from the slingshot.",480,237,20,"#ffe9df");words("2. Release to launch an enchanted kiss.",480,284,20,"#ffe9df");words("3. Knock down all the flirty little gargoyles.",480,331,20,"#ffe9df");drawButton("LET'S PLAY",345,392,268,54,()=>start(Math.min(unlocked,5)),true)}
function pauseScreen(){gameplay();darkOverlay();words("A MOMENT, DARLING",480,159,45,"#ffe3c0");words("Even wicked charmers need a breather.",480,219,19,"#ffd6e9","center","400");drawButton("CONTINUE",346,261,268,56,()=>phase="play",true);drawButton("RESTART LEVEL",346,332,268,52,()=>start(stage));drawButton("MAIN MENU",346,400,268,47,()=>phase="menu")}
function endScreen(win){gameplay();darkOverlay();words(win?"SCANDALOUS SUCCESS!":"THE NIGHT ISN'T OVER",480,153,win?40:38,"#ffe2bb");words(win?"Those gargoyles are absolutely smitten.":"Even immortal charm takes practice.",480,210,18,"#ffd5e9","center","400");words("YOUR SCORE",480,258,18,"#ffc2da");words(score.toLocaleString(),480,298,47,"#ffe6ab");if(win){for(let i=0;i<(shots>=3?3:shots>=1?2:1);i++)words("★",431+i*50,363,43,"#ffe3a0")}drawButton(win?"REPLAY":"TRY AGAIN",286,402,188,47,()=>start(stage));drawButton(win?(stage<5?"NEXT LEVEL":"PLAY AGAIN"):"MAIN MENU",492,402,188,47,()=>win?start(stage<5?stage+1:1):phase="menu",true)}
function paint(){c.setTransform(1,0,0,1,0,0);fill(0,0,cv.width,cv.height,"#100718");c.save();c.translate(ox,oy);c.scale(sc,sc);if(shake>1)c.translate(rnd(-shake,shake),rnd(-shake,shake));buttons=[];if(phase==="menu")titleScreen();else if(phase==="play")gameplay();else if(phase==="help")helpScreen();else if(phase==="pause")pauseScreen();else if(phase==="win")endScreen(true);else if(phase==="lose")endScreen(false);c.restore()}
