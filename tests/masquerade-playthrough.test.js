'use strict';
// Real pointer event routes through the new levels. Routes show reachability,
// not physical-device feel, soundtrack quality, playtime or player enjoyment.
const assert=require('assert'),{makeGame}=require('./campaign.test.js');
const g=makeGame({velvet_ah_unlocked:'19',velvet_ah_campaign_v1:'1',velvet_ah_stars:'{"14":2,"19":3}',velvet_ah_scores:'{"19":6500}',ah_music:'off'}),run=g.run;
const routes=[
 [['bloom',-72,28],['direct',-90,28]],
 [['direct',-99,28],['direct',-54,28],['bloom',-108,20]],
 [['direct',-72,20],['bloom',-108,36],['direct',-90,28]],
 [['direct',-54,44],['bloom',-108,28],['direct',-90,20],['bloom',-72,28]],
 [['bloom',-54,52],['direct',-81,20],['direct',-108,28],['bloom',-108,20]]
];
function event(type,x,y){g.events[type]({clientX:x,clientY:y,pointerId:1,preventDefault(){}})}
function tap(x,y){run('paint()');event('pointerdown',x,y)}
assert.strictEqual(run('unlocked'),20,'the completed Night 19 migrates to Night 20');
assert.strictEqual(run('stageStars[19]'),3,'existing stars persist');
assert.strictEqual(run('stageScores[19]'),6500,'existing scores persist');
assert.strictEqual(g.store.ah_music,'off','music choice persists');
run('start(20)');
for(let n=20;n<=24;n++){
 assert.strictEqual(run('stage'),n,`night ${n} starts`);
 for(const [mode,dx,dy] of routes[n-20]){
  if(run('enemies.every(t=>t.dead)'))break;
  tap(mode==='bloom'?495:375,505);assert.strictEqual(run('shotMode'),mode,`night ${n} selects ${mode}`);
  event('pointerdown',158,368);event('pointermove',158+dx,368+dy);event('pointerup',158+dx,368+dy);
  assert(run('ball'),`night ${n} launches through touch controls`);
  run('for(let i=0;i<900&&(ball||roseCharges.length);i++)sim(1/120);for(let i=0;i<18&&phase==="play";i++)sim(1/120)');
 }
 assert(run('enemies.every(t=>t.dead)'),`night ${n} has a clear route within its shot budget`);
 run('for(let i=0;i<150&&phase==="play";i++)sim(1/120)');assert.strictEqual(run('phase'),'win',`night ${n} wins`);
 tap(586,425);
 if(n<24)assert.strictEqual(run('stage'),n+1,'next unlocked night starts');
 else assert.strictEqual(run('phase'),'menu','the unfinished slice returns to the menu');
}
const reopen=makeGame(g.store);assert.strictEqual(reopen.run('unlocked'),24,'new progress persists');
for(let n=20;n<=24;n++)assert(reopen.run(`stageStars[${n}]`)>0,`night ${n} completion persists`);
assert(!Object.keys(g.store).some(k=>/sloth.*unlock|campaign.*complete/i.test(k)),'finishing the work-in-progress slice does not falsely unlock a full-game reward');
console.log('Masquerade: five touch-driven clears, progression, preserved settings and unfinished-game state passed.');
