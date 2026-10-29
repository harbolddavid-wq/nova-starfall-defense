'use strict';
// Exercise the same on-screen choice and pointer controls a player uses.
const assert=require('assert'),{makeGame}=require('./campaign.test.js');
const game=makeGame(),run=game.run;
function event(type,x,y,id=1){game.events[type]({clientX:x,clientY:y,pointerId:id,preventDefault(){}})}
function tap(x,y){run('paint()');event('pointerdown',x,y)}
function pull(dx,dy){event('pointerdown',158,368);event('pointermove',158+dx,368+dy);event('pointerup',158+dx,368+dy)}
run('start(1)');
assert.deepStrictEqual(JSON.parse(run('JSON.stringify(powerCharges)')),{bloom:2,split:1},'first night provides limited charges');
tap(495,506);assert.strictEqual(run('shotMode'),'bloom','Bloom can be selected by touch');
pull(-5,0);assert.strictEqual(run('powerCharges.bloom'),2,'short pull consumes no power');assert.strictEqual(run('shots'),5,'short pull consumes no heart');
event('pointerdown',158,368);event('pointermove',100,390);event('pointercancel',100,390);assert.strictEqual(run('powerCharges.bloom'),2,'cancelled pull consumes no power');
pull(-90,20);assert.strictEqual(run('ball.mode'),'bloom','selected Bloom launches');assert.strictEqual(run('powerCharges.bloom'),1,'a launch spends exactly one Bloom');
assert.strictEqual(run('selectShot("split")'),false,'selection cannot change while a shot is flying');
run('ball=null;balls=[]');pull(-90,20);assert.strictEqual(run('powerCharges.bloom'),0,'second Bloom exhausts its stock');assert.strictEqual(run('shotMode'),'direct','the next available heart defaults to Classic');
run('ball=null;balls=[];paint()');assert.strictEqual(run('buttons.some(b=>b.x===444 && b.y===487)'),false,'exhausted Bloom control is visibly disabled and cannot be selected');
tap(495,506);assert.strictEqual(run('shotMode'),'direct','tapping an exhausted power cannot select it');
run('shotMode="bloom"');pull(-90,20);assert.strictEqual(run('ball.mode'),'direct','even stale state cannot create an extra Bloom');assert.strictEqual(run('powerCharges.bloom'),0,'stock never goes negative');
run('start(1)');assert.strictEqual(run('powerCharges.bloom'),2,'restarting restores the level allocation');assert.strictEqual(run('powerCharges.split'),1,'restart restores Split as well');
tap(620,506);pull(-90,20);assert.strictEqual(run('ball.mode'),'split','Split launches from the on-screen selection');assert.strictEqual(run('powerCharges.split'),0,'launch spends a Split charge once');
run('for(let i=0;i<15;i++)sim(1/120)');assert.strictEqual(run('splitShot()'),true,'tap can split the in-flight projectile');assert.strictEqual(run('powerCharges.split'),0,'splitting does not spend a second charge');assert.strictEqual(run('balls.length'),3,'split creates three fragments');
run('unlocked=2;start(2)');assert.deepStrictEqual(JSON.parse(run('JSON.stringify(powerCharges)')),{bloom:1,split:2},'each new night has its own allocation');assert.strictEqual(run('shotMode'),'direct','new night starts with Classic');
console.log('Power uses: touch selection, cancellation, exhaustion, fallback, split and restart passed.');
