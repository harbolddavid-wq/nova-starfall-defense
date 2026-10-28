'use strict';
const assert=require('assert'),vm=require('vm'),fs=require('fs'),path=require('path');
const instances=[],storage={};let focusRequests=0;
class FakeAudio{constructor(src){this.src=src;this.paused=true;this.ended=false;this.currentTime=0;instances.push(this)}play(){this.paused=false;return Promise.resolve()}pause(){this.paused=true}}
const env={Audio:FakeAudio,Date,document:{hidden:false},localStorage:{getItem:k=>storage[k],setItem:(k,v)=>storage[k]=v},NativeBridge:{acquireAudioFocus(){focusRequests++;return true}}};env.window=env;vm.createContext(env);
vm.runInContext(fs.readFileSync(path.join(__dirname,'../app/src/main/assets/after-hours-audio.js'),'utf8'),env);
const A=env.AfterHoursAudio;
assert.strictEqual(instances.length,0,'no sound before interaction');
A.gesture();const music=instances.find(a=>a.src.includes('waltz'));assert(music&&!music.paused,'music starts on gesture');
assert.strictEqual(focusRequests,0,'WebView must not compete with a native focus request');
A.setPaused(true);assert(music.paused,'pause stops music');
A.setPaused(false);assert(!music.paused,'continue resumes music');
music.pause();A.setPaused(false);assert(music.paused,'an external media pause must not be forced back on every frame');
A.gesture();assert(!music.paused,'a new player gesture can resume audio');
A.toggleMusic();assert(music.paused&&storage.ah_music==='off');
A.cue('sling');const cue=instances.find(a=>a.src.includes('sling'));assert(cue&&!cue.paused,'effects remain independent of music');
A.toggleSfx();assert(cue.paused&&storage.ah_sfx==='off','muting effects stops active effects');
console.log('Audio: gesture, independent controls, pause and no competing native focus passed with fake services.');
