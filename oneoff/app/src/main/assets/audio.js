/* Original procedural audio design. No sampled or third-party audio. */
(function(){
'use strict';
let ac=null, master=null, muted=false, lastShoot=0;
const now=()=>ac?ac.currentTime:0;
function init(){
  if (!ac) {
    const C=window.AudioContext||window.webkitAudioContext;
    if (!C) return false;
    ac=new C(); master=ac.createGain(); master.gain.value=0.22;
    master.connect(ac.destination);
  }
  if (ac.state==='suspended') ac.resume().catch(()=>{});
  return true;
}
function tone(freq, endFreq, duration, type, gain, delay=0){
  if (!init()||muted) return;
  const t=now()+delay, o=ac.createOscillator(), g=ac.createGain();
  o.type=type; o.frequency.setValueAtTime(Math.max(30,freq),t);
  o.frequency.exponentialRampToValueAtTime(Math.max(30,endFreq),t+duration);
  g.gain.setValueAtTime(0.0001,t);
  g.gain.exponentialRampToValueAtTime(Math.max(0.0002,gain),t+0.006);
  g.gain.exponentialRampToValueAtTime(0.0001,t+duration);
  o.connect(g); g.connect(master); o.start(t); o.stop(t+duration+0.015);
}
function noise(duration, gain, cutoff=1200, delay=0){
  if(!init()||muted)return;
  const t=now()+delay, len=Math.ceil(ac.sampleRate*duration), b=ac.createBuffer(1,len,ac.sampleRate), d=b.getChannelData(0);
  for(let i=0;i<len;i++) d[i]=(Math.random()*2-1)*(1-i/len);
  const src=ac.createBufferSource(), filter=ac.createBiquadFilter(), g=ac.createGain();
  src.buffer=b;filter.type='lowpass';filter.frequency.value=cutoff;
  g.gain.setValueAtTime(gain,t);g.gain.exponentialRampToValueAtTime(0.0001,t+duration);
  src.connect(filter);filter.connect(g);g.connect(master);src.start(t);src.stop(t+duration);
}
const sounds={
  shoot(){if(!ac||now()-lastShoot<0.06)return;lastShoot=now();tone(900,230,0.12,'triangle',0.19);tone(1200,650,0.06,'sine',0.07);},
  hit(){tone(270,90,0.13,'square',0.13);noise(0.11,0.1,1900);},
  explode(){tone(180,42,0.38,'sawtooth',0.18);noise(0.31,0.22,1150);},
  playerHit(){tone(330,72,0.5,'sawtooth',0.18);noise(0.32,0.15,650);},
  menu(){tone(600,880,0.09,'sine',0.13);tone(900,1100,0.08,'triangle',0.07,0.06);},
  power(){[450,650,900,1280].forEach((f,i)=>tone(f,f*1.2,0.16,'sine',0.12,i*0.075));},
  wave(){tone(220,360,0.3,'sine',0.13);tone(330,550,0.32,'triangle',0.1,0.14);},
  gameOver(){[330,247,196,130].forEach((f,i)=>tone(f,f*0.75,0.33,'triangle',0.15,i*0.19));},
  enemyShot(){tone(240,160,0.14,'sine',0.045);}
};
window.GameAudio={init,play(name){try{if(sounds[name])sounds[name]();}catch(e){}},isMuted:()=>muted,setMuted(v){muted=!!v;if(master)master.gain.value=muted?0:0.22;return muted;}};
})();