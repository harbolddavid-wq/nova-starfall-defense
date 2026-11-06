const assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path');
const enginePath=fs.existsSync(path.join(__dirname,'../app/src/main/assets/engine.js'))?'../app/src/main/assets/engine.js':'../android/app/src/main/assets/engine.js';
const {Game,freshDeck,shuffle,verifyDecks}=require(enginePath);
const deck=freshDeck();
const assertFull=game=>{game.assertConserved();assert.equal(game.player.length+game.computer.length+game.pot.length,52);};
function forceTops(r1,r2,{playerSize=26}={}){
 let p=deck.find(c=>c.rank===r1),c=deck.find(c=>c.rank===r2&&c.id!==p.id);let rest=deck.filter(x=>x!==p&&x!==c);
 return {player:[p,...rest.slice(0,playerSize-1)],computer:[c,...rest.slice(playerSize-1)]};
}
{
let g=new Game({decks:forceTops(14,2)});let e=g.next();assert.equal(e.winner,'player');assert.equal(e.claimed,2);assert.equal(g.player.length,27);assert.equal(g.computer.length,25);assertFull(g);
}
{
let g=new Game({decks:forceTops(2,14)});let e=g.next();assert.equal(e.winner,'computer');assert.equal(e.claimed,2);assert.equal(g.player.length,25);assertFull(g);
}
{
let g=new Game({decks:forceTops(9,9)});let e=g.next();assert.equal(e.winner,null);assert(g.awaitingWar);let f=g.next();assert.equal(f.type,'war');assert.equal(f.buried.player,3);assert.equal(f.buried.computer,3);assert.equal(f.potSize,10);assertFull(g);
}
{
let all=deck.slice();let a=all.find(x=>x.rank===6&&x.suit==='♠'),b=all.find(x=>x.rank===6&&x.suit==='♥');let rest=all.filter(x=>x!==a&&x!==b);
let g=new Game({decks:{player:[a],computer:[b,...rest]}});g.next();assert(g.awaitingWar);let e=g.next();assert.equal(e.winner,'computer');assert.equal(e.finished,true);assert.equal(g.computer.length,52);assertFull(g);
}
{
// Extremely rare case: both stacks end on the same rank with no further reveal card.
let a=deck.find(x=>x.rank===5&&x.suit==='♠'),b=deck.find(x=>x.rank===5&&x.suit==='♥'),rest=deck.filter(x=>x!==a&&x!==b);
let g=new Game({decks:{player:[...rest.slice(0,25),a],computer:[...rest.slice(25),b]}});
// Reach terminal tie through a controlled state with 50 cards already committed to the pot.
g.player=[a];g.computer=[b];g.pot=rest.map((x,i)=>({side:i%2?'player':'computer',card:x,facedown:true}));g.assertConserved();g.next();assert(g.awaitingWar);let e=g.next();assert.equal(e.finished,true);assert.equal(g.player.length,52);assertFull(g);
}
{
let g=new Game();let h=new Game({snapshot:g.snapshot()});assert.deepEqual(h.counts,g.counts);assertFull(h);
for(let i=0;i<3000&&!h.finished;i++){h.next();assertFull(h);} // no duplication/loss in long games
}
{
let g=new Game({random:()=>.3});assert.equal(g.player.length,26);assert.equal(g.computer.length,26);verifyDecks(g.player,g.computer);
}
console.log('WAR rules tests passed: ranks, win, tie, face-down stakes, insufficient cards, simultaneous exhaustion, save/restore, 3000-step conservation');