'use strict';
// Searches ordinary direct shots, helping catch accidentally impossible levels.
// Finding a path is not a substitute for human balance/playtime testing.
const {makeGame}=require('../tests/campaign.test.js');const {run}=makeGame();
const result=run(`(()=>{
 function play(n,sequence){
  unlocked=LEVELS.length;start(n);shotMode='direct';
  for(const [dx,dy] of sequence){
   if(phase!=='play'||shots<=0||enemies.every(t=>t.dead))break;
   drag=Physics.pull(launcher,dx,dy);shot();
   for(let i=0;i<900&&(ball||roseCharges.length);i++)sim(1/120);
   for(let i=0;i<18&&phase==='play';i++)sim(1/120);
  }
  return {dead:enemies.filter(t=>t.dead).length,total:enemies.length,score};
 }
 const answers=[];
 for(let n=1;n<=LEVELS.length;n++){
  const seq=[];let current=play(n,seq);
  for(let turn=0;turn<LEVELS[n-1].shots && current.dead<current.total;turn++){
   let choice=null,rank=-1;
   for(let dx=-108;dx<=-54;dx+=9)for(let dy=-12;dy<=84;dy+=8){
    const candidate=[...seq,[dx,dy]],out=play(n,candidate);
    const value=out.dead*100000+out.score;
    if(value>rank){rank=value;choice=[dx,dy];}
    if(out.dead===out.total)break;
   }
   if(!choice)break;seq.push(choice);current=play(n,seq);
  }
  answers.push({level:n,shots:seq.length,solved:current.dead===current.total,dead:current.dead,total:current.total,sequence:seq});
 }
 return answers;
})()`);
for(const x of result)console.log(JSON.stringify(x));
if(result.some(x=>!x.solved))process.exitCode=1;
