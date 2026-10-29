'use strict';
// Reachability through the same touch controls as the player. This does not
// substitute for judging challenge, animation, audio or enjoyment on a phone.
const assert=require('assert'),{makeGame}=require('./campaign.test.js');
const g=makeGame({velvet_ah_unlocked:'9',velvet_ah_campaign_v1:'1',velvet_ah_stars:'{"9":2}'}),run=g.run;
const routes=[
 [['bloom',-108,28],['direct',-90,28]],
 [['bloom',-72,36],['direct',-72,28],['direct',-72,20]],
 [['direct',-81,44],['bloom',-108,28],['direct',-108,20]],
 [['direct',-90,20],['bloom',-108,36]],
 [['bloom',-63,44],['bloom',-108,20],['direct',-108,20]]
];
function event(type,x,y){g.events[type]({clientX:x,clientY:y,pointerId:1,preventDefault(){}})}
function tap(x,y){run('paint()');event('pointerdown',x,y)}
run('start(10)');
for(let level=10;level<=14;level++){
 assert.strictEqual(run('stage'),level,`night ${level} begins`);
 for(const [mode,dx,dy] of routes[level-10]){
  if(run('enemies.every(t=>t.dead)'))break;
  tap(mode==='bloom'?495:375,505);
  assert.strictEqual(run('shotMode'),mode,`night ${level} selects ${mode}`);
  event('pointerdown',158,368);event('pointermove',158+dx,368+dy);event('pointerup',158+dx,368+dy);
  assert(run('ball'),`night ${level} launches through the pointer controls`);
  run('for(let i=0;i<900&&(ball||roseCharges.length);i++)sim(1/120);for(let i=0;i<18&&phase==="play";i++)sim(1/120)');
 }
 assert.strictEqual(run('enemies.every(t=>t.dead)'),true,`night ${level} can be cleared within its budget`);
 run('for(let i=0;i<150&&phase==="play";i++)sim(1/120)');
 assert.strictEqual(run('phase'),'win',`night ${level} reaches its win screen`);
 tap(586,425);
 if(level<14)assert.strictEqual(run('stage'),level+1,'next opens the unlocked night');
 else assert.strictEqual(run('stage'),15,'the former finale leads into the encore');
}
assert.strictEqual(run('unlocked'),15,'the encore unlocks after night fourteen');
const reopened=makeGame(g.store);
assert.strictEqual(reopened.run('unlocked'),15,'chapter progress survives reopening');
for(let n=10;n<=14;n++)assert(reopened.run(`stageStars[${n}]`)>0,`night ${n} retained its completion`);
console.log('New chapter: five pointer-controlled clears, progression and reload passed.');
