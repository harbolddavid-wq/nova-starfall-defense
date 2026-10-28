'use strict';
const assert=require('assert');
const P=require('../app/src/main/assets/after-hours-physics.js');
const anchor={x:158,y:368};
let p=P.pull(anchor,-1000,500);
assert(Math.abs(Math.hypot(p.x-anchor.x,p.y-anchor.y)-108)<1e-9);
assert(P.pull(anchor,40,0).x===anchor.x,'cannot pull forwards');
for(let i=0;i<1000;i++){
 const d=P.pull(anchor,-Math.random()*180,(Math.random()-.5)*160),v=P.velocity(anchor,d),t=Math.random()*2;
 const expected=P.pointAt(d,v,t),q={x:d.x,y:d.y,vx:v.x,vy:v.y};
 for(let j=0;j<120;j++)P.move(q,t/120);
 assert(Math.abs(q.x-expected.x)<1e-7 && Math.abs(q.y-expected.y)<1e-7,'preview and flight diverged');
}
const box={x:100,y:100,w:4,h:40};
let hit=P.sweepBox(0,100,200,0,3,box);
assert(hit && Math.abs(hit.t-.475)<1e-9 && hit.nx===-1,'fast shot skipped thin wall');
assert.strictEqual(P.sweepBox(0,140,200,0,3,box),null,'miss should stay a miss');
hit=P.sweepBox(100,0,0,200,3,box);
assert(hit && hit.ny===-1,'vertical contact normal');
assert(P.sweepBox(100,100,2,0,3,box),'overlap needs a recoverable normal');
assert(Math.abs(P.sweepCircle(0,0,100,0,50,0,10)-.4)<1e-9);
assert.strictEqual(P.sweepCircle(0,0,100,0,50,20,10),null);
assert.deepStrictEqual(P.reflect(100,20,-1,0,.5),{x:-50,y:20});
assert.deepStrictEqual(P.reflect(-100,20,-1,0,.5),{x:-100,y:20});
console.log('Physics: 1,000 flight/preview comparisons and contact edge cases passed.');
