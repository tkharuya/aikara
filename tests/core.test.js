const assert=require('node:assert/strict');
const C=require('../app/src/main/assets/core.js');let count=0;
function eq(a,b){if(typeof a==='number'&&typeof b==='number')assert.ok(Math.abs(a-b)<1e-9);else assert.deepEqual(a,b);count++;}
for(const u of ['https://www.youtube.com/watch?v=abcdefghijk&t=40','https://youtu.be/abcdefghijk?si=abc','https://m.youtube.com/shorts/abcdefghijk','https://www.youtube.com/live/abcdefghijk','曲です https://youtu.be/abcdefghijk','abcdefghijk'])eq(C.youtubeId(u),'abcdefghijk');
for(const u of ['https://youtube.com.evil.test/watch?v=abcdefghijk','https://notyoutube.com/watch?v=abcdefghijk','javascript:alert(1)','https://youtu.be/short','https://www.youtube.com/playlist?list=abcd'])eq(C.youtubeId(u),null);
const list=C.notes([{start:2,end:3,midi:64},{start:0,end:1,midi:60}]);eq(C.at(list,.5).midi,60);eq(C.at(list,1),null);eq(C.at(list,2.5).midi,64);eq(C.at(list,-1),null);
assert.throws(()=>C.notes([{start:0,end:2,midi:60},{start:1,end:3,midi:61}]));count++;
assert.throws(()=>C.notes([{start:0,end:0,midi:60}]));count++;
assert.throws(()=>C.notes([{start:-1,end:1,midi:60}]));count++;
assert.throws(()=>C.notes([{start:0,end:1,midi:Infinity}]));count++;
eq(C.cents(72,60,true),0);eq(C.cents(48,60,true),0);eq(C.cents(60.5,60,true),50);eq(C.cents(72,60,false),1200);
const s=new C.Score();eq(s.percent,null);s.add(0,60,60,true);s.add(.01,66,60,true);eq(s.percent,100);s.add(.1,-1,60,true);eq(s.percent,50);s.add(.15,80,null,true);eq(s.percent,50);s.add(.2,72,60,true);eq(s.percent,100*2/3);eq(s.bins.size,3);s.reset();eq(s.measured,0);
eq(C.parseGuide('start,end,midi\n0,1,60\n1,2,62').notes.length,2);eq(C.parseGuide(JSON.stringify({title:'a',notes:list,offset:1})).offset,1);
for(const malformed of ['null','[{"start":null,"end":1,"midi":60}]','start,end,midi\n,1,60','start,end,midi\n0,1,']){
  assert.throws(()=>C.parseGuide(malformed),/notes 配列|音程データ/);count++;
}
eq(C.noteName(60),'C4');eq(C.noteName(69),'A4');eq(C.timeLabel(125.5),'2:05');
console.log('PASS '+count+' core checks');
