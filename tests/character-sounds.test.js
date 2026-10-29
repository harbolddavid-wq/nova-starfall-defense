'use strict';
// Connect sound decisions to real gameplay events without claiming an ear test.
const assert=require('assert'),fs=require('fs'),path=require('path'),{makeGame}=require('./campaign.test.js');
const g=makeGame(),run=g.run;
run(`let soundEvents=[],stoppedVoices=[];
 AfterHoursAudio.cue=(name,volume)=>soundEvents.push({name,volume});
 AfterHoursAudio.stopCue=name=>stoppedVoices.push(name);
 start(1);soundEvents=[];stoppedVoices=[];`);
run('soundClock=5;idleVoiceAt=4;updateGargoyleIdle()');
assert.strictEqual(run('soundEvents.filter(e=>e.name.startsWith("voiceIdle")).length'),1,'a living gargoyle occasionally makes a sound');
assert.strictEqual(run('enemies.filter(e=>e.voicePulse>0).length'),1,'the character making the sound subtly moves');
run('updateGargoyleIdle()');assert.strictEqual(run('soundEvents.length'),1,'idle voices have a long spacing');
run('idleVoiceAt=0;aiming=true;updateGargoyleIdle()');assert.strictEqual(run('soundEvents.length'),1,'no idle chatter while aiming');
run('aiming=false;ball={};updateGargoyleIdle();ball=null');assert.strictEqual(run('soundEvents.length'),1,'no idle chatter during flight');
run('roseCharges=[{remaining:1}];updateGargoyleIdle();roseCharges=[]');assert.strictEqual(run('soundEvents.length'),1,'idle chatter waits for a chain reaction');
run('phase="pause";idleVoiceAt=0;sim(.1)');assert.strictEqual(run('soundEvents.length'),1,'no idle sounds while paused');
run('start(1);soundEvents=[];stoppedVoices=[];charm(enemies[0])');
assert.strictEqual(run('soundEvents.filter(e=>e.name==="voiceHitA").length'),1,'hitting a target triggers a reaction');
run('charm(enemies[1])');assert.strictEqual(run('soundEvents.filter(e=>e.name.startsWith("voiceHit")).length'),1,'simultaneous hits do not create a chorus');
run('soundClock+=.5;charm(enemies[2])');assert.strictEqual(run('soundEvents.filter(e=>e.name==="voiceHitB").length'),1,'separated hits vary their reaction');
assert(run('stoppedVoices.includes("voiceIdleA") && stoppedVoices.includes("voiceIdleB")'),'hit interrupts idle voice');
run('start(1);soundEvents=[];drag=Physics.pull(launcher,-90,20);shot()');
assert(run('soundEvents.some(e=>e.name==="sling")'),'a launched heart triggers the launcher cue');
run(`start(1);soundEvents=[];damageBlock(crates[0],{x:510,y:400,vx:300,vy:0});damageBlock(crates.find(b=>b.type==='crystal'),{x:700,y:350,vx:300,vy:0});addCrate(440,300,20,40,'iron');damageBlock(crates[crates.length-1],{x:430,y:300,vx:300,vy:0});`);
for(const cue of ['wood','glass','iron'])assert(run(`soundEvents.some(e=>e.name==='${cue}')`),`${cue} has a distinct impact cue`);
const audioDir=path.join(__dirname,'../app/src/main/assets/audio');
for(const f of ['voice-hit-a.ogg','voice-hit-b.ogg','voice-idle-a.ogg','voice-idle-b.ogg','sling.ogg','wood.ogg','glass.ogg','iron.ogg']){
 const b=fs.readFileSync(path.join(audioDir,f));assert(b.length>1500&&b.subarray(0,4).toString()==='OggS',`${f} is a packaged Ogg asset`);
}
console.log('Character sounds: hit/idle scheduling, anti-spam, action feedback and packaged effects passed.');
