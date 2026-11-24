(function(root,factory){const core=factory();if(typeof module==='object'&&module.exports)module.exports=core;else root.KaraCore=core;})(typeof globalThis!=='undefined'?globalThis:this,function(){
  'use strict';
  function youtubeId(text){
    const s=String(text||'').trim();if(/^[A-Za-z0-9_-]{11}$/.test(s))return s;
    const match=s.match(/https?:\/\/[^\s<>]+/);if(!match)return null;
    try{const u=new URL(match[0]);let id=null;const host=u.hostname.toLowerCase();
      if(host==='youtu.be')id=u.pathname.split('/')[1];
      else if(host==='youtube.com'||host.endsWith('.youtube.com')){const p=u.pathname.split('/').filter(Boolean);id=p[0]==='watch'?u.searchParams.get('v'):['shorts','live','embed'].includes(p[0])?p[1]:null;}
      return /^[A-Za-z0-9_-]{11}$/.test(id||'')?id:null;
    }catch(e){return null;}
  }
  function noteNumber(value){
    if(typeof value==='number')return value;
    if(typeof value==='string'&&value.trim())return Number(value);
    return NaN;
  }
  function notes(input){
    if(!Array.isArray(input))throw new Error('notes 配列がありません');
    const result=input.map((n,i)=>{const start=noteNumber(n?.start),end=noteNumber(n?.end),midi=noteNumber(n?.midi);
      if(!Number.isFinite(start)||!Number.isFinite(end)||!Number.isFinite(midi)||start<0||end<=start||midi<0||midi>127)throw new Error('音程データの '+(i+1)+' 行目が不正です');return {start,end,midi};}).sort((a,b)=>a.start-b.start);
    for(let i=1;i<result.length;i++)if(result[i].start<result[i-1].end-.001)throw new Error('音符が重複しています。単旋律のデータを指定してください');
    if(!result.length)throw new Error('音符がありません');return result;
  }
  function at(list,time){let lo=0,hi=list.length-1;while(lo<=hi){const mid=(lo+hi)>>1,n=list[mid];if(time<n.start)hi=mid-1;else if(time>=n.end)lo=mid+1;else return n;}return null;}
  function cents(midi,target,octave){let d=midi-target;if(octave)d=((d+6)%12+12)%12-6;return d*100;}
  function noteName(m){if(!Number.isFinite(m)||m<0)return '—';const n=Math.round(m);return ['C','C♯','D','D♯','E','F','F♯','G','G♯','A','A♯','B'][((n%12)+12)%12]+(Math.floor(n/12)-1);}
  function timeLabel(t){if(!Number.isFinite(t))return '0:00';return Math.floor(Math.max(0,t)/60)+':'+String(Math.floor(Math.max(0,t)%60)).padStart(2,'0');}
  class Score {
    constructor(){this.reset();}
    reset(){this.bins=new Map();this.sum=0;this.hits=0;}
    add(t,midi,target,octave){if(target===null||!Number.isFinite(t))return;const bin=Math.floor(t*20);if(this.bins.has(bin))return;
      const error=midi>=0?Math.abs(cents(midi,target,octave)):Infinity;const hit=error<=50;const value=Math.max(0,100-error/2);
      this.bins.set(bin,{hit,value});this.hits+=hit?1:0;this.sum+=value;
    }
    get measured(){return this.bins.size*.05;}
    get percent(){return this.bins.size?this.hits/this.bins.size*100:null;}
  }
  function parseGuide(text){let parsed;try{parsed=JSON.parse(text);}catch(e){const lines=text.replace(/^\uFEFF/,'').trim().split(/\r?\n/);if(!/^start[,\t]end[,\t]midi$/i.test(lines[0].trim()))throw new Error('JSON または start,end,midi の CSV を選んでください');parsed=lines.slice(1).filter(l=>l.trim()).map(l=>{const v=l.split(/[,\t]/);return {start:v[0],end:v[1],midi:v[2]};});}
    if(!parsed||typeof parsed!=='object')throw new Error('notes 配列がありません');
    return {notes:notes(Array.isArray(parsed)?parsed:parsed.notes),title:typeof parsed.title==='string'?parsed.title:'',youtubeId:youtubeId(parsed.youtubeId||''),guideKind:typeof parsed.guideKind==='string'?parsed.guideKind:'imported',offset:Number.isFinite(parsed.offset)?parsed.offset:0};}
  const demoNotes=[60,62,64,65,67,69,71,72,72,71,69,67,65,64,62,60].map((m,i)=>({start:2+i*1.2,end:2+i*1.2+.95,midi:m}));
  return {youtubeId,notes,at,cents,noteName,timeLabel,Score,parseGuide,demoNotes};
});
