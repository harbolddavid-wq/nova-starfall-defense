'use strict';
// Behavioral tests: the new shield is a separate step from health, including
// Bloom and split projectiles, and the new opponent's movement pauses safely.
const assert=require('assert'),fs=require('fs'),path=require('path'),{makeGame}=require('./campaign.test.js');
const g=makeGame(),run=g.run;
run(`unlocked=24;start(21);enemies=[];crates=[];addEnemy(350,300,1,null,'rival',{shield:true});
 balls=[newBall(300,300,400,0,'direct')];ball=balls[0];for(let i=0;i<55;i++)sim(1/120);`);
assert.strictEqual(run('enemies[0].shielded'),false,'a physical hit removes the shield');
assert.strictEqual(run('enemies[0].hp'),3,'shield impact does not remove a health heart');
run(`charm(enemies[0],{key:'shot:1'});`);
assert.strictEqual(run('enemies[0].hp'),3,'one shot cannot break the shield and remove health');
run(`charm(enemies[0],{key:'shot:2'});charm(enemies[0],{key:'shot:3'});charm(enemies[0],{key:'shot:4'});`);
assert.strictEqual(run('enemies[0].dead'),true,'three later classic impacts charm the rival');

run(`start(21);enemies=[];crates=[];addEnemy(350,300,1,null,'rival',{shield:true});
 let flower=newBall(350,300,0,0,'bloom');charm(enemies[0],{bloom:true,key:'shot:'+flower.shotId});bloom(flower);`);
assert.strictEqual(run('enemies[0].shielded'),false,'Bloom may remove a shield');
assert.strictEqual(run('enemies[0].hp'),3,'the same Bloom burst cannot also damage her health');
assert.strictEqual(run('enemies[0].dead'),false,'she remains after that first burst');
run(`let flower2=newBall(350,300,0,0,'bloom');bloom(flower2);`);
assert.strictEqual(run('enemies[0].dead'),true,'a later Bloom charms the unshielded rival');

run(`start(21);enemies=[];crates=[];addEnemy(350,300,1,null,'rival',{shield:true});
 charm(enemies[0],{key:'shot:55'});charm(enemies[0],{key:'shot:55'});charm(enemies[0],{key:'shot:55'});`);
assert.strictEqual(run('enemies[0].hp'),3,'split fragments from one shot cannot pass through a shield');
run(`start(21);enemies=[];crates=[];addEnemy(350,300,1,null,'rival',{shield:true});addCrate(340,300,32,32,'rose');
 damageBlock(crates[0],{x:330,y:300,vx:100,vy:0,shotId:77});updateRoseCharges(.2);
 charm(enemies[0],{key:'shot:77',shotId:77});`);
assert.strictEqual(run('enemies[0].shielded'),false,'a rose reaction can break a shield');
assert.strictEqual(run('enemies[0].hp'),3,'the shot that started the rose cannot also damage health');
run(`charm(enemies[0],{key:'shot:78',shotId:78});`);
assert.strictEqual(run('enemies[0].hp'),2,'a subsequent shot may remove health');
run(`start(21);enemies=[];crates=[];addEnemy(350,300,1,null,'rival',{shield:true});addCrate(350,300,18,50,'wood');
 charm(enemies[0],{key:'shot:81',shotId:81});crates[0].moving=true;crates[0].vx=80;crates[0].sourceShot=81;moveCrates(1/120);`);
assert.strictEqual(run('enemies[0].hp'),3,'debris from the shield-breaking shot cannot damage health');
run(`start(21);enemies=[];crates=[];addCrate(500,300,28,28,'ward','test');addEnemy(350,300,1,'test','rival',{shield:true});
 charm(enemies[0],{key:'shot:1'});`);
assert.strictEqual(run('enemies[0].shielded'),true,'linked seal also protects the shield');
assert.strictEqual(run('enemies[0].hp'),3,'linked seal protects health');

run(`start(20);enemies=[];crates=[];addEnemy(400,300,1,null,'phantom',{range:24,rate:.8});let beforeMotion=enemies[0].x;for(let i=0;i<120;i++)sim(1/120);`);
assert(run('enemies[0].x>beforeMotion+10'),'the new opponent moves across the arena');
run(`let pausedX=enemies[0].x;phase='pause';for(let i=0;i<120;i++)sim(1/120);`);
assert.strictEqual(run('enemies[0].x'),run('pausedX'),'the opponent remains still while paused');
run(`phase='play';sim(1/120);`);
assert(Math.abs(run('enemies[0].x-pausedX'))<1,'resuming does not jump the moving opponent');

run(`let heard=[],stopped=[];AfterHoursAudio.cue=(name,volume)=>heard.push(name);AfterHoursAudio.stopCue=n=>stopped.push(n);
 start(20);enemies=enemies.filter(t=>t.type==='phantom');soundClock=6;idleVoiceAt=0;updateGargoyleIdle();`);
assert(run(`heard.includes('phantomIdleA')`),'new opponent has a distinct idle sound');
run(`charm(enemies[0],{key:'shot:1'});`);
assert(run(`heard.includes('phantomHitA')`),'new opponent has a distinct impact sound');
assert(run(`stopped.includes('phantomIdleA')`),'impact interrupts idle sound');
const dir=path.join(__dirname,'../app/src/main/assets/audio');
for(const f of ['phantom-hit-a.ogg','phantom-hit-b.ogg','phantom-idle-a.ogg','phantom-idle-b.ogg']){
 const b=fs.readFileSync(path.join(dir,f));assert(b.length>1000&&b.subarray(0,4).toString()==='OggS',`${f} is packaged`);
}
console.log('Masquerade: shield-first impacts, Bloom/split, motion, pause and distinct sound events passed.');
