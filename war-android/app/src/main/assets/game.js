/* WAR — The Visual Challenge. Real rules, real moving illustrated cards, offline. */
(()=>{'use strict';
const $=id=>document.getElementById(id);
const K='war_visual_challenge_save_v1', P='war_visual_challenge_prefs_v1';
const defaultPrefs={sound:true,theme:'siege',speed:'cinematic',auto:false};
const getStored=(key,fallback)=>{try{return JSON.parse(localStorage.getItem(key))||fallback}catch{return fallback}};
const persist=(key,obj)=>{try{localStorage.setItem(key,JSON.stringify(obj))}catch{}};
let prefs={...defaultPrefs,...getStored(P,{})};prefs.auto=false;
let game;try{const saved=getStored(K,null);game=saved?new WarEngine.Game({snapshot:saved}):new WarEngine.Game();}catch{game=new WarEngine.Game();}
let busy=false,epoch=0,autoTimer=null,soundContext=null,wonRounds=0,lostRounds=0;
const soundButton=$('soundButton'),settingsButton=$('settingsButton'),helpButton=$('helpButton'),dealButton=$('dealButton'),dealLabel=$('dealLabel'),autoButton=$('autoButton'),newButton=$('newButton'),app=$('app'),arena=$('arena');
const stakePile=$('stakePile'); const message=$('battleMessage'),outcome=$('battleOutcome'),cpuCard=$('computerCard'),playerCard=$('playerCard'),cpuRotor=$('cpuRotor'),playerRotor=$('playerRotor'),cpuFace=$('cpuFace'),playerFace=$('playerFace'),clash=$('clashOverlay'),shade=$('dialogShade');
const delay=ms=>new Promise(r=>setTimeout(r,ms));
const speed=()=>prefs.speed==='swift'?.58:1;
const E=html=>html.replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const ART={14:'knight',13:'king',12:'queen',11:'jack'};
const assetPath=path=>window.__ART_DATA?.[path]||path;
const srcFor=c=>assetPath('assets/portrait_'+(ART[c.rank]||'scenic')+'.webp');
function cardFace(c){if(!c)return '';
 const name=WarEngine.rankName(c.rank),red=c.suit==='♥'||c.suit==='♦';
 const isPip=c.rank<11; const pips=isPip?`<div class="pip-field count-${c.rank} ${red?'red':''}">${Array.from({length:c.rank},()=>`<span>${c.suit}</span>`).join('')}</div>`:'';
 return `<div class="face-art ${isPip?'number-art':''}"><img src="${srcFor(c)}" alt="${E(name+' of '+c.suit)}" draggable="false">${isPip?'<div class="number-wash"></div>'+pips:''}</div><div class="face-gilt"></div><div class="face-index top ${red?'red':''}"><b>${name}</b><span class="suit">${c.suit}</span></div><div class="face-index bottom ${red?'red':''}"><b>${name}</b><span class="suit">${c.suit}</span></div><div class="face-etch" aria-hidden="true">⚜</div>`;
}
function applyPrefs(){app.classList.toggle('theme-siege',prefs.theme==='siege');app.classList.toggle('theme-storm',prefs.theme==='storm');soundButton.classList.toggle('muted',!prefs.sound);soundButton.textContent=prefs.sound?'♫':'♪̸';soundButton.setAttribute('aria-label',prefs.sound?'Mute sound':'Enable sound');autoButton.setAttribute('aria-pressed',String(!!prefs.auto));autoButton.textContent=prefs.auto?'Ⅱ STOP AUTO':'▶ AUTO BATTLE: OFF';persist(P,{...prefs,auto:false});}
function showCount(){const c=game.counts;$('computerCount').textContent=c.computer;$('playerCount').textContent=c.player;$('campaignFill').style.width=(c.player/52*100)+'%';$('roundLabel').textContent='ROUND '+Math.max(1,game.rounds);$('roundMarker').textContent='BATTLE '+String(Math.max(1,game.rounds)).padStart(2,'0');$('potCounter').innerHTML=`<span>✦</span> THE SPOILS <b>${c.pot}</b> CARDS <span>✦</span>`;$('record').textContent=`${game.wins.player} WON · ${game.wins.computer} LOST`;}
function updateDeal(text=null){if(game.finished){dealLabel.textContent='THE WAR IS OVER';dealButton.disabled=true;return;}dealButton.disabled=busy;dealLabel.textContent=busy?'THE CARDS ARE FALLING…':(text||(game.awaitingWar?'CONTINUE THE WAR':'TAP TO DEAL'));}
function initialDisplay(){if(game.lastPair){cpuFace.innerHTML=cardFace(game.lastPair.computer);playerFace.innerHTML=cardFace(game.lastPair.player);cpuCard.classList.add('flipped');playerCard.classList.add('flipped');cpuCard.classList.toggle('winner',game.lastWinner==='computer');playerCard.classList.toggle('winner',game.lastWinner==='player');}message.textContent=game.finished?'THE FINAL STANDARD HAS FALLEN':game.lastResult||'Two banners enter. Only one prevails.';outcome.textContent=game.lastResult||'Tap the banner below to begin.';showCount();updateDeal();if(game.finished)finishMatch();}
function beep(kind){if(!prefs.sound)return;try{
 const Audio=window.AudioContext||window.webkitAudioContext;if(!Audio)return;
 if(!soundContext)soundContext=new Audio();if(soundContext.state==='suspended')soundContext.resume();const t=soundContext.currentTime;
 const specs={deal:[[470,.07,.045,'triangle'],[690,.11,.050,'sine']],flip:[[320,.06,.11,'triangle']],win:[[440,.17,.09,'triangle'],[660,.14,.15,'sine'],[880,.14,.23,'triangle']],lose:[[260,.18,.06,'sawtooth'],[184,.23,.16,'triangle']],war:[[110,.37,.13,'sawtooth'],[165,.34,.18,'sawtooth'],[380,.30,.32,'triangle']],end:[[440,.3,.04,'triangle'],[550,.3,.16,'triangle'],[660,.35,.30,'sine'],[880,.39,.46,'sine']]};
 for(const [f,d,offset,shape] of specs[kind]||specs.deal){const o=soundContext.createOscillator(),g=soundContext.createGain();o.type=shape;o.frequency.setValueAtTime(f,t+offset);o.frequency.exponentialRampToValueAtTime(Math.max(42,f*(kind==='war'?.55:.91)),t+offset+d);g.gain.setValueAtTime(.001,t+offset);g.gain.exponentialRampToValueAtTime(kind==='war'?.17:.09,t+offset+.018);g.gain.exponentialRampToValueAtTime(.001,t+offset+d);o.connect(g);g.connect(soundContext.destination);o.start(t+offset);o.stop(t+offset+d+.02);}
 }catch(e){/* sound is optional */}}
const canvas=$('sparkCanvas'),ctx=canvas.getContext('2d',{alpha:true});let particles=[],animating=false,dpr=1;
function fitCanvas(){dpr=Math.min(2,window.devicePixelRatio||1);canvas.width=Math.round(innerWidth*dpr);canvas.height=Math.round(innerHeight*dpr);canvas.style.width=innerWidth+'px';canvas.style.height=innerHeight+'px';ctx.setTransform(dpr,0,0,dpr,0,0);}
fitCanvas();addEventListener('resize',fitCanvas);
function shower(mode,howMany=56){const rect=arena.getBoundingClientRect();const ox=rect.left+rect.width/2,oy=rect.top+rect.height*.55;const palette=mode==='war'?['#fff8de','#ffe28b','#ff6d2b','#82d6ff','#ebfaff']:mode==='player'?['#ffd78b','#ef5b39','#fbeeb6']:['#b6dfff','#48a7ef','#eee3ba'];
for(let i=0;i<howMany;i++){const angle=Math.random()*Math.PI*2,speed=(Math.random()*4+1)*(mode==='war'?2:1.1);particles.push({x:ox,y:oy,vx:Math.cos(angle)*speed,vy:Math.sin(angle)*speed-1.8,life:Math.random()*35+31,max:67,size:Math.random()*3.8+1.2,color:palette[Math.floor(Math.random()*palette.length)],rot:Math.random()*Math.PI*2});}
if(particles.length>260)particles.splice(0,particles.length-260);if(!animating){animating=true;requestAnimationFrame(drawSparks);}}
function drawSparks(){ctx.clearRect(0,0,innerWidth,innerHeight);ctx.globalCompositeOperation='lighter';particles=particles.filter(p=>p.life>0);for(const p of particles){p.x+=p.vx;p.y+=p.vy;p.vy+=.08;p.vx*=.988;p.life--;ctx.globalAlpha=Math.max(0,p.life/p.max);ctx.fillStyle=p.color;ctx.beginPath();ctx.arc(p.x,p.y,p.size*(p.life/p.max+.25),0,Math.PI*2);ctx.fill();}ctx.globalAlpha=1;ctx.globalCompositeOperation='source-over';if(particles.length)requestAnimationFrame(drawSparks);else animating=false;}
function setResultText(text){message.textContent=text;outcome.textContent=text;}
function cleanCards(){for(const c of [cpuCard,playerCard]){c.classList.remove('arriving','flipped','winner','loser','tied');} void arena.offsetWidth;}
async function animatePair(event,token){
 cleanCards();
 if(!event.pair){setResultText(event.reason);return;}
 cpuFace.innerHTML=cardFace(event.pair.computer);playerFace.innerHTML=cardFace(event.pair.player);
 cpuCard.classList.add('arriving');playerCard.classList.add('arriving');
 setResultText(event.type==='war'?'THE CLASH CONTINUES…':'THE CHALLENGERS ADVANCE…');
 beep('deal');await delay(450*speed());if(token!==epoch)return;
 cpuCard.classList.add('flipped');playerCard.classList.add('flipped');beep('flip');
 await delay(730*speed());if(token!==epoch)return;
 cpuCard.classList.remove('arriving');playerCard.classList.remove('arriving');
 if(event.winner){
   const won=event.winner; (won==='player'?playerCard:cpuCard).classList.add('winner');(won==='player'?cpuCard:playerCard).classList.add('loser');
   setResultText(event.reason+(event.claimed?' +'+event.claimed+' cards':' '));
   beep(won==='player'?'win':'lose');shower(won,won==='player'?70:47);
 }else{
  cpuCard.classList.add('tied');playerCard.classList.add('tied');setResultText('EQUAL RANKS. THE HORN SOUNDS…');
 }
 showCount();persist(K,game.snapshot());
}
async function warCinematic(event,token){
 if(token!==epoch)return;
 $('clashStakes').textContent=`⚔ ${event.potSize} CARDS NOW AT STAKE ⚔`;
 const buried=event.buried||{computer:0,player:0};
 let cards=[];for(const side of ['computer','player'])for(let i=0;i<buried[side];i++)cards.push({side,i});
 stakePile.innerHTML=cards.map((x,i)=>`<div class="stake-card ${x.side==='player'?'red':'blue'}" style="--i:${i}"><img src="${assetPath('assets/back_'+(x.side==='player'?'lion':'eagle')+'.webp')}" alt=""></div>`).join('');
 stakePile.classList.add('active');
 clash.classList.add('active');clash.setAttribute('aria-hidden','false');beep('war');shower('war',165);showCount();
 await delay(1650*speed());if(token!==epoch)return;
 stakePile.classList.remove('active');stakePile.innerHTML='';
 clash.classList.remove('active');clash.setAttribute('aria-hidden','true');await delay(190*speed());
}
async function turn(){
 if(busy||game.finished)return;
 const token=++epoch;busy=true;updateDeal();
 try{
   let event=game.next();await animatePair(event,token);if(token!==epoch)return;
   while(event.war&&!game.finished){const warEvent=game.next();await warCinematic(warEvent,token);if(token!==epoch)return;event=warEvent;await animatePair(event,token);if(token!==epoch)return;}
   showCount();if(event.finished){finishMatch();return;}
 }catch(e){setResultText('The cards slipped: '+e.message);console.error(e);}
 finally{if(token===epoch){busy=false;updateDeal();if(prefs.auto&&!game.finished)planAuto();}}
}
function planAuto(){clearTimeout(autoTimer);if(!prefs.auto||game.finished||busy||document.hidden)return;autoTimer=setTimeout(turn,prefs.speed==='swift'?200:650);}
function finishMatch(){prefs.auto=false;applyPrefs();clearTimeout(autoTimer);const won=game.champion==='player';$('finishTitle').textContent=won?'THE LION IS VICTORIOUS':'THE EAGLE PREVAILS';$('finishCopy').textContent=`${game.rounds} battles · ${game.warCount} wars · ${game.wins.player} victories for your banner.`;$('finishOverlay').classList.remove('hidden');dealButton.disabled=true;beep('end');shower('war',180);}
function newMatch(){epoch++;clearTimeout(autoTimer);prefs.auto=false;game=new WarEngine.Game();busy=false;wonRounds=0;lostRounds=0;persist(K,game.snapshot());applyPrefs();cleanCards();cpuFace.innerHTML='';playerFace.innerHTML='';$('finishOverlay').classList.add('hidden');clash.classList.remove('active');stakePile.classList.remove('active');stakePile.innerHTML='';setResultText('Two banners enter. Only one prevails.');showCount();updateDeal();}
function closeDialog(){shade.classList.add('hidden');shade.setAttribute('aria-hidden','true');}
function openDialog(type){if(type==='new'){
 $('dialogTitle').textContent='Raise fresh banners?';$('dialogBody').innerHTML='<p>Start a new war with 26 freshly shuffled cards on each side? This replaces the unfinished current match.</p><div class="dialog-row"><button class="dialog-toggle" id="cancelReset">Keep fighting</button><button class="dialog-toggle" id="confirmReset">New battle</button></div>';
 $('cancelReset')?.addEventListener('click',closeDialog);$('confirmReset')?.addEventListener('click',()=>{closeDialog();newMatch();});
 }else if(type==='settings'){
 $('dialogTitle').textContent='Dress the battlefield';$('dialogBody').innerHTML=`<p>Two original illustrated theaters. The cards, rules, and score stay the same.</p>
 <div class="theme-grid"><button data-theme="siege" class="theme-choice ${prefs.theme==='siege'?'active':''}"><img src="${assetPath('assets/siege_portrait.webp')}" alt="Red and blue heraldic siege artwork"><span>ROYAL SIEGE</span></button><button data-theme="storm" class="theme-choice ${prefs.theme==='storm'?'active':''}"><img src="${assetPath('assets/war_impact.webp')}" alt="Illustrated lightning and fire clash"><span>STORM OF CROWNS</span></button></div>
 <div class="dialog-row"><span>Sound effects</span><button class="dialog-toggle" id="soundToggle">${prefs.sound?'ON':'OFF'}</button></div><div class="dialog-row"><span>Animation pacing</span><button class="dialog-toggle" id="speedToggle">${prefs.speed==='cinematic'?'CINEMATIC':'SWIFT'}</button></div><p style="font-size:12px">All gameplay is offline. Settings and the current match save locally.</p>`;
 $('dialogBody').querySelectorAll('[data-theme]').forEach(b=>b.addEventListener('click',()=>{prefs.theme=b.dataset.theme;applyPrefs();openDialog('settings');}));
 $('soundToggle').addEventListener('click',()=>{prefs.sound=!prefs.sound;applyPrefs();$('soundToggle').textContent=prefs.sound?'ON':'OFF';});
 $('speedToggle').addEventListener('click',()=>{prefs.speed=prefs.speed==='cinematic'?'swift':'cinematic';applyPrefs();$('speedToggle').textContent=prefs.speed==='cinematic'?'CINEMATIC':'SWIFT';});
 }else{
 $('dialogTitle').textContent='Rules of WAR';$('dialogBody').innerHTML=`<p>Classic two-player War. <strong>No luck adjustments, no special abilities.</strong> Just illustrated cards, marching banners, and the draw.</p>
 <ol class="manual-list"><li>A complete 52-card deck is shuffled and divided evenly: <strong>26 cards each.</strong></li><li>Tap <strong>DEAL</strong>. Both players turn over their top cards simultaneously. Higher rank wins both. <strong>Aces are high.</strong></li><li>Equal ranks trigger <strong>WAR!</strong> Both sides bury up to three cards face down, reserving one to reveal. Higher new card wins the entire pot. More ties escalate the war.</li><li>If a side cannot reveal another card, it forfeits the pot. If both are completely exhausted together, the final face-up cards are broken by a suit hierarchy as the last-resort deadlock rule.</li><li>The victor puts won cards at the bottom of their deck. The first to claim all 52 cards wins. <strong>Auto Battle</strong> plays hands for you.</li></ol><p>Audio and illustrations work without internet. No betting, purchases, accounts or ads.</p>`;
 }
 shade.classList.remove('hidden');shade.setAttribute('aria-hidden','false');$('closeDialog').focus();}
// UI events with large touch targets, safely below Android system bars.
dealButton.addEventListener('click',turn);autoButton.addEventListener('click',()=>{prefs.auto=!prefs.auto;applyPrefs();if(prefs.auto){if(!busy)planAuto();}else clearTimeout(autoTimer);});newButton.addEventListener('click',()=>openDialog('new'));
$('finishRestart').addEventListener('click',newMatch);settingsButton.addEventListener('click',()=>openDialog('settings'));helpButton.addEventListener('click',()=>openDialog('help'));
soundButton.addEventListener('click',()=>{prefs.sound=!prefs.sound;applyPrefs();if(prefs.sound)beep('win');});$('closeDialog').addEventListener('click',closeDialog);shade.addEventListener('click',e=>{if(e.target===shade)closeDialog();});
document.addEventListener('keydown',e=>{if(e.key==='Escape')closeDialog();if(e.code==='Space'&&shade.classList.contains('hidden')){e.preventDefault();turn();}});
document.addEventListener('visibilitychange',()=>{if(document.hidden){clearTimeout(autoTimer);prefs.auto=false;applyPrefs();}else if(!busy)updateDeal();});
// Public only for deterministic browser regression testing: not gameplay-dependent.
window.WAR_STUDIO={get game(){return game}, setGameForTest:(g)=>{epoch++;clearTimeout(autoTimer);game=g;busy=false;cleanCards();initialDisplay();},turn,applyPrefs};
applyPrefs();initialDisplay();
})();