'use strict';
// Pointer routes show that every new night is reachable and clearable; they
// do not claim to measure the time or enjoyment of a real player's experience.
const assert=require('assert'),{makeGame}=require('./campaign.test.js');
const g=makeGame({velvet_ah_unlocked:'14',velvet_ah_campaign_v1:'1',velvet_ah_stars:'{"9":2,"14":3}',velvet_ah_scores:'{"14":5000}',ah_music:'off'}),run=g.run;
const routes=[
 [['bloom',-90,28],['bloom',-108,28],['direct',-108,20]],
 [['bloom',-63,44],['direct',-90,20],['direct',-72,20]],
 [['bloom',-54,52],['direct',-90,20],['direct',-81,20]],
 [['bloom',-63,36],['direct',-108,44],['direct',-63,44],['direct',-81,36]],
 [['direct',-63,44],['bloom',-90,36],['bloom',-108,20]]
];
function event(type,x,y){g.events[type]({clientX:x,clientY:y,pointerId:1,preventDefault(){}})}
function tap(x,y){run('paint()');event('pointerdown',x,y)}
assert.strictEqual(run('unlocked'),15,'completed old finale opens the new chapter');
assert.strictEqual(run('stageStars[14]'),3,'old stars survive the update');
assert.strictEqual(run('stageScores[14]'),5000,'old scores survive the update');
assert.strictEqual(g.store.ah_music,'off','music preference survives the update');
run('start(15)');
for(let n=15;n<=19;n++){
 assert.strictEqual(run('stage'),n,`night ${n} starts`);
 for(const [mode,dx,dy] of routes[n-15]){
  if(run('enemies.every(t=>t.dead)'))break;
  tap(mode==='bloom'?495:375,505);
  assert.strictEqual(run('shotMode'),mode,`night ${n} selects ${mode}`);
  event('pointerdown',158,368);event('pointermove',158+dx,368+dy);event('pointerup',158+dx,368+dy);
  assert(run('ball'),`night ${n} launches via the pointer controls`);
  run('for(let i=0;i<900&&(ball||roseCharges.length);i++)sim(1/120);for(let i=0;i<18&&phase==="play";i++)sim(1/120)');
 }
 assert(run('enemies.every(t=>t.dead)'),`night ${n} can be cleared within its allotted hearts`);
 run('for(let i=0;i<150&&phase==="play";i++)sim(1/120)');assert.strictEqual(run('phase'),'win',`night ${n} wins`);
 tap(586,425);
 if(n<19)assert.strictEqual(run('stage'),n+1,'next night becomes available');
 else assert.strictEqual(run('phase'),'menu','finale returns to the menu');
}
const reopened=makeGame(g.store);assert.strictEqual(reopened.run('unlocked'),19,'all completed progress survives reload');
for(let n=15;n<=19;n++)assert(reopened.run(`stageStars[${n}]`)>0,`night ${n} completion persists`);
tap(400,464);assert.strictEqual(run('levelPage'),2,'latest unlocked night opens its selector page');
run('paint()');assert.strictEqual(run('buttons.filter(b=>b.w===132).length'),1,'last page contains the nineteenth night');
tap(300,423);run('paint()');assert.strictEqual(run('levelPage'),1,'previous page opens');
assert.strictEqual(run('buttons.filter(b=>b.w===132).length'),9,'middle page contains nine playable nights');
tap(650,423);tap(330,190);assert.strictEqual(run('stage'),19,'nineteenth night can be replayed from the selector');
console.log('Encore: five pointer-controlled clears, save migration, three-page navigation and replay passed.');
