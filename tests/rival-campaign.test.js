'use strict';
// Behavior checks for the new character, powers, sound routing, and chapter UI.
const assert=require('assert'),fs=require('fs'),path=require('path'),{makeGame}=require('./campaign.test.js');
const game=makeGame(),run=game.run;
run(`unlocked=14;start(10);enemies=[];crates=[];addEnemy(350,300,1,null,'rival');
 balls=[newBall(300,300,400,0,'direct')];ball=balls[0];for(let i=0;i<55;i++)sim(1/120);`);
assert.strictEqual(run('enemies[0].hp'),2,'one physical shot removes only one heart');
assert.strictEqual(run('enemies[0].dead'),false,'one direct hit does not defeat the new character');
run(`let shotId=balls[0]?balls[0].shotId:1;charm(enemies[0],{key:'shot:'+shotId});`);
assert.strictEqual(run('enemies[0].hp'),2,'the same projectile cannot remove another heart');
run(`charm(enemies[0],{key:'shot:100'});charm(enemies[0],{key:'shot:101'});`);
assert.strictEqual(run('enemies[0].dead'),true,'three independent hits charm her');
run(`start(10);enemies=[];crates=[];addEnemy(350,300,1,null,'rival');let flower=newBall(350,300,0,0,'bloom');bloom(flower);`);
assert.strictEqual(run('enemies[0].dead'),true,'a Bloom burst charms her in one hit');
run(`start(10);enemies=[];crates=[];addCrate(350,300,28,28,'ward','x');addEnemy(380,300,1,'x','rival');
 charm(enemies[0],{key:'shot:1'});`);
assert.strictEqual(run('enemies[0].hp'),3,'intact seals protect all hearts');
run(`let flower2=newBall(350,300,0,0,'bloom');bloom(flower2);`);
assert.strictEqual(run('enemies[0].dead'),true,'Bloom can break a nearby seal and charm her in the same burst');
run(`start(10);enemies=[];crates=[];addEnemy(350,300,1,null,'rival');addCrate(350,300,18,50,'wood');
 crates[0].moving=true;crates[0].vx=80;for(let i=0;i<15;i++)moveCrates(1/120);`);
assert.strictEqual(run('enemies[0].hp'),2,'one piece of moving debris cannot repeatedly count every frame');

run(`let eventsHeard=[],stopped=[];AfterHoursAudio.cue=(name,volume)=>eventsHeard.push(name);AfterHoursAudio.stopCue=n=>stopped.push(n);
 start(10);enemies=enemies.filter(t=>t.type==='rival');soundClock=6;idleVoiceAt=0;updateGargoyleIdle();`);
assert(run(`eventsHeard.includes('rivalIdleA')`),'new character has a separate idle cue');
assert(run('enemies[0].voicePulse>0'),'the sounding character visibly reacts');
run(`charm(enemies[0],{key:'shot:1'});`);
assert(run(`eventsHeard.includes('rivalHitA')`),'new character has her own hit cue');
assert(run(`stopped.includes('rivalIdleA') && stopped.includes('voiceIdleA')`),'hits stop any idle voice first');
run(`soundClock+=.5;charm(enemies[0],{key:'shot:2'});`);
assert(run(`eventsHeard.includes('rivalHitB')`),'successive hits vary her reaction');
const dir=path.join(__dirname,'../app/src/main/assets/audio');
for(const name of ['rival-hit-a.ogg','rival-hit-b.ogg','rival-idle-a.ogg','rival-idle-b.ogg']){
 const bytes=fs.readFileSync(path.join(dir,name));assert(bytes.length>1500&&bytes.subarray(0,4).toString()==='OggS',`${name} is packaged`);
}

function tap(x,y){run('paint()');game.events.pointerdown({clientX:x,clientY:y,pointerId:1,preventDefault(){}})}
run('unlocked=14;phase="menu"');tap(400,464);
assert.strictEqual(run('phase'),'levels');assert.strictEqual(run('levelPage'),1,'opening the selector shows the latest unlocked page');
run('paint()');
assert.strictEqual(run('buttons.filter(b=>b.w===132).length'),5,'second page contains nights ten through fourteen');
tap(300,423);assert.strictEqual(run('levelPage'),0,'previous page remains accessible');
run('paint()');assert.strictEqual(run('buttons.filter(b=>b.w===132).length'),9,'first page still contains nine nights');
tap(650,423);assert.strictEqual(run('levelPage'),1,'next page opens by touch');
tap(330,190);assert.strictEqual(run('stage'),10,'night ten starts from the second page');
assert.strictEqual(run('powerCharges.bloom'),1,'new chapter provides a finite Bloom stock');
run(`selectShot('bloom');drag=Physics.pull(launcher,-90,30);shot()`);
assert.strictEqual(run('powerCharges.bloom'),0,'launch spends the new level Bloom once');
console.log('Rival: physical impacts, Bloom, seals, debris, distinct voices, chapter navigation and power limits passed.');
