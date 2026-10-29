'use strict';
const assert=require('assert'),vm=require('vm'),fs=require('fs'),path=require('path'),crypto=require('crypto');
const root=path.join(__dirname,'../app/src/main/assets');
const names=['menu',...Array.from({length:9},(_,i)=>String(i+1).padStart(2,'0'))];
const hashes=new Set();for(const n of names){
 const data=fs.readFileSync(path.join(root,'audio',`evening-${n}.ogg`));
 assert(data.length>5000&&data.subarray(0,4).toString()==='OggS',`music ${n} is packaged`);
 hashes.add(crypto.createHash('sha256').update(data).digest('hex'));
}
assert.strictEqual(hashes.size,10,'menu and all nine levels have distinct music files');
const instances=[],storage={};
class FakeAudio{constructor(src){this.src=src;this.paused=true;this.ended=false;this.volume=1;this.currentTime=0;instances.push(this)}play(){this.paused=false;return Promise.resolve()}pause(){this.paused=true}}
const env={Audio:FakeAudio,Date,document:{hidden:false},localStorage:{getItem:k=>storage[k]??null,setItem:(k,v)=>storage[k]=String(v)}};env.window=env;vm.createContext(env);
vm.runInContext(fs.readFileSync(path.join(root,'after-hours-audio.js'),'utf8'),env);
const A=env.AfterHoursAudio;
async function main(){
 A.setScene(0);A.cue('voiceHitA');assert.strictEqual(instances.length,0,'no audio before a player gesture');
 A.gesture();const menu=instances.find(a=>a.src.endsWith('evening-menu.ogg'));assert(menu&&!menu.paused,'menu music starts on gesture');
 A.setScene(1);const one=instances.find(a=>a.src.endsWith('evening-01.ogg'));assert(one&&!one.paused&&one.volume===0,'first level starts its own score quietly');
 await Promise.resolve();for(let i=0;i<8;i++)A.advance(.1);
 assert(menu.paused&&!one.paused&&one.volume>.18,'level score fades in and menu score stops');
 A.setScene(2);const two=instances.find(a=>a.src.endsWith('evening-02.ogg'));await Promise.resolve();A.advance(.1);
 assert(!one.paused&&!two.paused,'old and new pieces overlap during the transition');
 A.setPaused(true);assert(one.paused&&two.paused,'pause stops both sides of a transition');
 A.setPaused(false);assert(!two.paused,'resume continues the selected level score');
 A.toggleMusic();assert(two.paused&&storage.ah_music==='off','music mute stops playback');
 A.setScene(5);const five=instances.find(a=>a.src.endsWith('evening-05.ogg'));assert(five.paused,'changing levels while muted stays silent');
 A.toggleMusic();assert(!five.paused&&storage.ah_music==='on','unmuting starts the selected score');
 A.setScene(6);await Promise.resolve();A.advance(.1);A.setScene(7);await Promise.resolve();for(let i=0;i<8;i++)A.advance(.1);
 const playing=instances.filter(a=>a.src.includes('evening-')&&!a.paused);
 assert.strictEqual(playing.length,1,'rapid scene changes do not leave overlapping music');
 assert(playing[0].src.endsWith('evening-07.ogg'),'the most recent level keeps playing');
 A.cue('voiceHitA');const voice=instances.find(a=>a.src.endsWith('voice-hit-a.ogg'));assert(voice&&!voice.paused,'voice effects still work with the new score system');
 A.toggleSfx();assert(voice.paused&&storage.ah_sfx==='off','effects mute still stops character voice');
 const {makeGame}=require('./campaign.test.js'),g=makeGame();
 g.run(`let observedScenes=[];AfterHoursAudio.setScene=n=>observedScenes.push(n);unlocked=9;for(let n=1;n<=9;n++)start(n);`);
 assert.deepStrictEqual(JSON.parse(g.run('JSON.stringify(observedScenes)')),[1,2,3,4,5,6,7,8,9],'every level selects its own score');
 g.run('phase="menu";tick(1000)');assert.strictEqual(g.run('observedScenes[observedScenes.length-1]'),0,'returning to menu selects its score');
 console.log('Music: distinct level files, scene routing, crossfade, rapid switching, pause and independent mute passed.');
}
main().catch(e=>{console.error(e);process.exitCode=1});
