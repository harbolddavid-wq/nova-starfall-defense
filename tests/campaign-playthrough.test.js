'use strict';
const assert=require('assert'),{makeGame}=require('./campaign.test.js');
const g=makeGame(),run=g.run;
function event(type,x,y,id=1){g.events[type]({clientX:x,clientY:y,pointerId:id,preventDefault(){}})}
// Sample ordinary-shot clear routes, generated independently by the search
// tool. These prove reachability and navigation, not enjoyable playtime.
const routes=[
 [[-90,20],[-81,36]],
 [[-54,36],[-81,36]],
 [[-90,20],[-108,36]],
 [[-108,52]],
 [[-72,44],[-99,36],[-108,44],[-81,36]],
 [[-99,44],[-108,36]],
 [[-72,36],[-99,28],[-72,28],[-81,28]],
 [[-108,44],[-108,28],[-81,20],[-81,36]],
 [[-63,44],[-108,44],[-90,36],[-108,20]]
];
run('start(1);shotMode="direct";paint()');
for(let level=1;level<=routes.length;level++){
 assert.strictEqual(run('stage'),level,`stage ${level} starts in order`);
 for(const [dx,dy] of routes[level-1]){
  if(run('enemies.every(t=>t.dead)'))break;
  run('paint()');event('pointerdown',158,368);event('pointermove',158+dx,368+dy);event('pointerup',158+dx,368+dy);
  assert(run('ball'),`stage ${level} launches through the pointer controls`);
  run('for(let i=0;i<900&&(ball||roseCharges.length);i++)sim(1/120);for(let i=0;i<18&&phase==="play";i++)sim(1/120)');
 }
 assert.strictEqual(run('enemies.every(t=>t.dead)'),true,`stage ${level} can be cleared`);
 run('for(let i=0;i<150&&phase==="play";i++)sim(1/120);paint()');
 assert.strictEqual(run('phase'),'win',`stage ${level} reaches its win screen`);
 event('pointerdown',586,425);
 if(level<routes.length)assert.strictEqual(run('stage'),level+1,'next button starts the newly unlocked stage');
 else assert.strictEqual(run('phase'),'menu','last win returns to menu');
}
assert.strictEqual(run('unlocked'),9,'all nine stages unlocked');
assert.strictEqual(Object.keys(JSON.parse(g.store.velvet_ah_stars)).length,9,'each completion persists a star record');
const reopened=makeGame(g.store);assert.strictEqual(reopened.run('unlocked'),9,'all progress survives reopening');
reopened.run('phase="levels";paint()');
assert.strictEqual(reopened.run('buttons.filter(b=>b.w===132).length'),9,'all unlocked stages can be replayed from the selection screen');
console.log('Campaign: pointer-controlled clears, next-stage navigation and reload passed for nine stages.');
