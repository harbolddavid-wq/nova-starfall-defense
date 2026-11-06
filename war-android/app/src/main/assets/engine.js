/* WAR: The Visual Challenge — deterministic, offline two-player classic War engine.
   Copyright (c) 2026 David & Nova. Independent from Nova Card Collection. */
(function(root,factory){ const api=factory(); if(typeof module!=='undefined'&&module.exports)module.exports=api; root.WarEngine=api; })(typeof window!=='undefined'?window:globalThis,function(){
'use strict';
const SUITS=['♠','♥','♦','♣'];
const RANK_NAMES={11:'J',12:'Q',13:'K',14:'A'};
const freshDeck=()=>SUITS.flatMap((suit,s)=>Array.from({length:13},(_,i)=>({rank:i+2,suit,id:s*13+i+2})));
const rankName=n=>RANK_NAMES[n]||String(n);
const suitOrder={'♠':4,'♥':3,'♦':2,'♣':1};
function shuffle(array,random=Math.random){let out=array.slice();for(let i=out.length-1;i>0;i--){const j=Math.floor(random()*(i+1));[out[i],out[j]]=[out[j],out[i]];}return out;}
function verifyDecks(a,b,pot=[]){const all=[...a,...b,...pot.map(p=>p.card)];if(all.length!==52)throw new Error('Card conservation violation: '+all.length);const ids=new Set(all.map(x=>x.id));if(ids.size!==52)throw new Error('Duplicate or missing card');}
class Game{
  constructor(opts={}){
    if(opts.snapshot){this.load(opts.snapshot);return;}
    const decks=opts.decks;
    if(decks){this.player=decks.player.map(x=>({...x}));this.computer=decks.computer.map(x=>({...x}));verifyDecks(this.player,this.computer);}
    else{const deck=shuffle(freshDeck(),opts.random||Math.random);this.player=deck.slice(0,26);this.computer=deck.slice(26);}
    this.pot=[];this.awaitingWar=false;this.rounds=0;this.warCount=0;this.wins={player:0,computer:0};this.finished=false;this.champion=null;this.lastPair=null;this.lastWinner=null;this.lastResult='The banners are raised. Tap to deal.';
    this.lastSuitPair=null;this.lastEvent=null;this.seen=new Set();this.cycleBreaks=0;this.random=opts.random||Math.random;
    this.assertConserved();
  }
  assertConserved(){verifyDecks(this.player,this.computer,this.pot);return true;}
  get counts(){return{player:this.player.length,computer:this.computer.length,pot:this.pot.length};}
  snapshot(){return {version:1,player:this.player,computer:this.computer,pot:this.pot,awaitingWar:this.awaitingWar,rounds:this.rounds,warCount:this.warCount,wins:this.wins,finished:this.finished,champion:this.champion,lastPair:this.lastPair,lastWinner:this.lastWinner,lastResult:this.lastResult,lastSuitPair:this.lastSuitPair,cycleBreaks:this.cycleBreaks};}
  load(o){if(!o||o.version!==1)throw new Error('Unsupported game save');this.player=o.player.map(x=>({...x}));this.computer=o.computer.map(x=>({...x}));this.pot=o.pot.map(x=>({side:x.side,card:{...x.card},facedown:!!x.facedown}));this.awaitingWar=!!o.awaitingWar;this.rounds=o.rounds||0;this.warCount=o.warCount||0;this.wins=o.wins||{player:0,computer:0};this.finished=!!o.finished;this.champion=o.champion||null;this.lastPair=o.lastPair||null;this.lastWinner=o.lastWinner||null;this.lastResult=o.lastResult||'';this.lastSuitPair=o.lastSuitPair||null;this.lastEvent=null;this.cycleBreaks=o.cycleBreaks||0;this.random=Math.random;this.seen=new Set();this.assertConserved();}
  add(side,card,facedown=false){this.pot.push({side,card,facedown});}
  claim(winner,reason){
    const score=this.pot.length;
    const claimed=this.pot.map(x=>x.card);
    this[winner].push(...claimed);
    this.pot=[];this.awaitingWar=false;this.lastWinner=winner;this.wins[winner]++;
    this.lastResult=reason;
    if(!this.player.length||!this.computer.length){this.finished=true;this.champion=this.player.length?'player':'computer';this.lastResult=(this.champion==='player'?'Victory! Your banner':'Defeat. The rival banner')+' claims all 52 cards.';}
    this.assertConserved();
    return {winner,claimed:score,reason:this.lastResult,finished:this.finished,champion:this.champion};
  }
  next(){
    if(this.finished)throw new Error('Match complete');
    const warStep=this.awaitingWar;
    let buried={player:0,computer:0};
    if(!warStep){
      if(!this.player.length||!this.computer.length)throw new Error('No cards for opening duel');
      this.rounds++;
    }else{
      this.warCount++;
      // Each side puts up to THREE down, always reserving one for the face-up challenger.
      for(const side of ['player','computer']){
        const n=Math.min(3,Math.max(0,this[side].length-1));
        for(let i=0;i<n;i++){this.add(side,this[side].shift(),true);buried[side]++;}
      }
      // A player without a reveal card forfeits the war. If both are out,
      // the final previously revealed distinct suits decide a rare terminal deadlock.
      if(!this.player.length||!this.computer.length){
        let winner;
        if(this.player.length&&!this.computer.length)winner='player';
        else if(this.computer.length&&!this.player.length)winner='computer';
        else winner=(suitOrder[this.lastSuitPair?.player?.suit]||0)>(suitOrder[this.lastSuitPair?.computer?.suit]||0)?'player':'computer';
        const result=this.claim(winner,'No cards left to reveal; '+(winner==='player'?'your standard':'the rival')+' takes the contested pot.');
        this.lastEvent={type:'war',buried,pair:null,potSize:result.claimed,...result};
        return this.lastEvent;
      }
    }
    const pair={player:this.player.shift(),computer:this.computer.shift()};
    this.add('player',pair.player);this.add('computer',pair.computer);
    this.lastPair=pair;this.lastSuitPair=pair;
    let winner=null,reason='';
    const potSize=this.pot.length;
    if(pair.player.rank>pair.computer.rank){winner='player';reason=rankName(pair.player.rank)+' takes the field!';}
    else if(pair.computer.rank>pair.player.rank){winner='computer';reason='The rival '+rankName(pair.computer.rank)+' wins the clash.';}
    if(winner){const result=this.claim(winner,reason);this.lastEvent={type:warStep?'war':'duel',pair,buried,potSize,...result};return this.lastEvent;}
    this.awaitingWar=true;this.lastWinner=null;
    this.lastResult='WAR! Equal '+rankName(pair.player.rank)+'s—stakes rise.';
    this.assertConserved();
    this.lastEvent={type:warStep?'war':'duel',pair,buried,potSize,winner:null,reason:this.lastResult,finished:false,war:true};
    return this.lastEvent;
  }
}
return {Game,freshDeck,shuffle,verifyDecks,rankName,SUITS};
});