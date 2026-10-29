/* Six hand-authored opening-night puzzles. Coordinates are logical game units.
   Block types use the same collision rules in every level. */
const AFTER_HOURS_LEVELS = Object.freeze([
 {name:'THE FIRST FLING', hint:'A little cover can hide a lovely target.', shots:5,
  blocks:[[520,420,24,60,'wood'],[695,414,18,72,'wood'],[779,414,18,72,'wood'],[737,372,100,16,'crystal'],[835,423,18,54,'wood']],
  targets:[[577,395],[737,338],[884,412]]},
 {name:'GLASS WALTZ', hint:'Glass breaks quickly; aim for a useful opening.', shots:5,
  blocks:[[530,404,18,92,'wood'],[610,404,18,92,'wood'],[570,355,100,16,'crystal'],[760,411,18,78,'wood'],[844,411,18,78,'wood'],[802,369,100,16,'crystal'],[735,425,18,50,'wood']],
  targets:[[570,322],[690,405],[802,336],[900,405]]},
 {name:'ROSE DUET', hint:'A rose crate can wake its neighbors.', shots:5,
  blocks:[[505,420,20,60,'wood'],[610,434,32,32,'rose'],[709,425,18,50,'wood'],[826,434,32,32,'rose'],[877,378,100,16,'crystal']],
  targets:[[545,405],[660,392],[770,405],[885,345]]},
 {name:'THE CHAIN', hint:'Break the golden seal to free its guarded guests.', shots:5,
  blocks:[[490,421,20,58,'wood'],[600,434,32,32,'rose'],[690,434,32,32,'rose'],[780,434,32,32,'rose'],[845,400,18,100,'wood'],[858,339,84,16,'crystal'],[900,270,28,28,'ward','gold']],
  targets:[[550,402],[655,377],[748,382],[860,305,'gold'],[912,412,'gold']]},
 {name:'HIGH AND LOW', hint:'Break the seal; some paths reward a split.', shots:6,
  blocks:[[500,419,20,62,'wood'],[572,371,98,16,'crystal'],[533,410,18,78,'wood'],[611,410,18,78,'wood'],[705,425,18,50,'wood'],[784,370,100,16,'crystal'],[744,410,18,78,'wood'],[824,410,18,78,'wood'],[870,425,18,50,'wood'],[885,285,28,28,'ward','high']],
  targets:[[572,338],[665,405],[784,337],[887,344,'high'],[913,410,'high']]},
 {name:'CLOSING CURTAIN', hint:'A little planning gives the roses room to shine.', shots:6,
  blocks:[[490,421,20,58,'wood'],[565,379,100,16,'crystal'],[525,415,18,70,'wood'],[605,415,18,70,'wood'],[665,434,32,32,'rose'],[755,434,32,32,'rose'],[830,376,100,16,'crystal'],[790,414,18,72,'wood'],[870,414,18,72,'wood'],[915,425,18,50,'wood'],[905,302,28,28,'ward','finale']],
  targets:[[565,346],[622,402],[705,379],[775,405],[830,343],[910,378,'finale']]}
]);
