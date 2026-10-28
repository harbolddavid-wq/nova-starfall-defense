'use strict';
const assert=require('assert'),vm=require('vm'),fs=require('fs'),path=require('path');
const instances=[];const storage={};let granted=true,requests=0;
class FakeAudio{constructor(src){this.src=src;this.paused=true;this.ended=false;this.currentTime=0;instances.push(this)}play(){this.paused=false;return Promise.resolve()}pause(){this.paused=true}}
const env={Audio:FakeAudio,Date,document:{hidden:false},localStorage:{getItem:k=>storage[k],setItem:(k,v)=>storage[k]=v},NativeBridge:{acquireAudioFocus(){requests++;return granted}}};env.window=env;vm.createContext(env);
vm.runInContext(fs.readFileSync(path.join(__dirname,'../app/src/main/assets/after-hours-audio.js'),'utf8'),env);
const A=env.AfterHoursAudio;
assert.strictEqual(instances.length,0,'no sound before interaction');
A.gesture();const music=instances.find(a=>a.src.includes('waltz'));assert(music&&!music.paused,'music starts with focus on gesture');
A.setPaused(true);assert(music.paused,'pause stops music');
A.setPaused(false);assert(!music.paused,'continue resumes music');
A.setFocus(false);assert(music.paused,'focus loss stops music');
granted=false;A.gesture();assert(music.paused,'denied focus must remain silent');
granted=true;A.gesture();assert(!music.paused,'focus can be regained through interaction');
A.toggleMusic();assert(music.paused && storage.ah_music==='off');
A.cue('sling');const cue=instances.find(a=>a.src.includes('sling'));assert(cue&&!cue.paused,'effects remain independent of music');
A.toggleSfx();assert(cue.paused && storage.ah_sfx==='off','muting effects stops active effects');
let n=requests;A.gesture();assert.strictEqual(requests,n,'both muted should not grab audio focus');
console.log('Audio: user gesture, independent controls, pause and focus-loss behavior passed with fake services.');
