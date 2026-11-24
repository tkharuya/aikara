'use strict';
const App=(()=>{
  const C=KaraCore,$=id=>document.getElementById(id),KEY='aikara.v1';
  const demo={id:'demo',title:'音階トレーニング',kind:'demo',uri:'asset:demo.wav',notes:C.demoNotes,guideKind:'exact',offset:0};
  let data={songs:[demo],selected:'demo',settings:{latency:100,gate:-42,octave:true}};
  try{const saved=JSON.parse(localStorage.getItem(KEY)||'null');if(saved&&Array.isArray(saved.songs)){const clean=saved.songs.filter(s=>s&&typeof s.id==='string'&&typeof s.title==='string'&&['youtube','local','demo'].includes(s.kind)).map(s=>({...s,notes:Array.isArray(s.notes)&&s.notes.length?C.notes(s.notes):[],offset:Number.isFinite(s.offset)?s.offset:0}));if(!clean.some(s=>s.id==='demo'))clean.unshift(demo);data={...data,...saved,songs:clean,settings:{...data.settings,...saved.settings}};}}catch(e){}
  let current=data.songs.find(s=>s.id===data.selected)||data.songs[0];let page='sing',nativeReady=false,playing=false,sourceReady=false,mic=false,duration=23,lastTime=0,lastTick=performance.now(),freeOrigin=performance.now();
  let hz=0,midi=-1,rms=0,pitchStamp=0,trace=[],key=0,loopA=null,loopB=null,loopSeeking=false,loopAt=0,seeking=false,analysis=null,toastTimer;
  let score=new C.Score();const ctx=$('pitch-chart').getContext('2d');let low=57,high=75,frameStamp=0;
  function command(action,args={}){if(window.Native&&typeof Native.command==='function')Native.command(JSON.stringify({action,...args}));else if(action!=='panel'&&action!=='gate')toast('この機能は Android アプリで利用できます');}
  function save(){data.selected=current.id;try{localStorage.setItem(KEY,JSON.stringify(data));}catch(e){toast('保存容量が不足しています。不要な曲を削除するかデータを書き出してください');}}
  function toast(message){$('toast').textContent=message||'操作できませんでした';$('toast').hidden=false;clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('toast').hidden=true,6500);}
  function ask(title,value='',isConfirm=false){return new Promise(resolve=>{const dlg=$('prompt-dialog');$('prompt-title').textContent=title;$('prompt-input').value=value;$('prompt-input').hidden=isConfirm;dlg.returnValue='';dlg.onclose=()=>resolve(dlg.returnValue==='ok'?(isConfirm?true:$('prompt-input').value):null);dlg.showModal();if(!isConfirm)$('prompt-input').focus();});}
  function timeline(){return Math.max(0,Math.min(duration||Infinity,lastTime+(playing?Math.min(.25,(performance.now()-lastTick)/1000):0)));}
  function activeNotes(){return current.notes||[];}
  function targetAt(t){const n=C.at(activeNotes(),t-(current.offset||0));return n?n.midi+key:null;}
  function displayPitch(m,t){const target=targetAt(t);return data.settings.octave&&target!==null?m+Math.round((target-m)/12)*12:m;}
  function reset(){score.reset();trace=[];updateScore();}
  function updateScore(){const p=score.percent;$('score').textContent=p===null?'—':p.toFixed(0);$('score-detail').textContent=score.measured?'計測 '+score.measured.toFixed(1)+' 秒 · ±50 cent':'±50 cent・無音も判定';}
  function updateTransport(){$('play').textContent=playing?'Ⅱ 一時停止':'▶ 歌い始める';$('mic-toggle').classList.toggle('on',mic);$('mic-state').classList.toggle('on',mic);$('mic-state').innerHTML=mic?'<b></b>マイク ON':'<b></b>マイク OFF';}
  function updateSong(){
    $('song-title').textContent=current.title;$('song-subtitle').textContent=current.kind==='demo'?'オリジナル練習音源 · C4 – C5':current.kind==='youtube'?'YouTube · 公式プレーヤー':'端末音源 · オフライン再生';
    $('youtube-url').value=current.kind==='youtube'?'https://youtu.be/'+current.videoId:'';
    $('guide-status').textContent=!activeNotes().length?'未登録 · マイクの音程表示は利用可能':current.guideKind==='estimated'?'自動推定 '+activeNotes().length+' 音 · 伴奏入りは誤検出あり':current.guideKind==='exact'?'練習用の正確な音程データ':activeNotes().length+' 音 · 読み込み済み';
    $('chart-empty').hidden=!!activeNotes().length;
    $('guide-offset').value=(current.offset||0).toFixed(2);$('guide-offset-label').textContent=(current.offset||0).toFixed(2)+' 秒';
    $('key-down').disabled=$('key-up').disabled=current.kind==='youtube';updateKey();renderLibrary();
  }
  function loadSong(song){current=song;playing=false;sourceReady=false;lastTime=0;lastTick=performance.now();key=0;loopA=loopB=null;loopSeeking=false;duration=song.kind==='demo'?23.2:0;reset();updateLoop();updateSong();updateTransport();save();
    if(nativeReady){if(song.kind==='youtube')command('youtube',{id:song.videoId});else if(song.kind==='demo')command('demo');else command('local',{uri:song.uri});}
  }
  function tab(name){page=name;for(const id of ['sing','library','settings'])$(id).hidden=id!==name;for(const button of document.querySelectorAll('nav button'))button.classList.toggle('active',button.dataset.tab===name);command('panel',{show:name==='sing'});window.scrollTo(0,0);if(name==='library')renderLibrary();}
  function loadYoutube(text){const id=C.youtubeId(text);if(!id){toast('YouTube の動画 URL を入力してください');return;}
    let song=data.songs.find(s=>s.kind==='youtube'&&s.videoId===id);if(!song){song={id:'yt-'+id,title:'YouTube '+id,kind:'youtube',videoId:id,notes:[],offset:0};data.songs.push(song);}tab('sing');loadSong(song);toast('上部の動画をタップして再生。曲名は「曲名」から変更できます');
  }
  function renderLibrary(){const list=$('song-list');list.replaceChildren();for(const song of data.songs){const row=document.createElement('div');row.className='song-item'+(song.id===current.id?' selected':'');const b=document.createElement('button');b.className='song-select';const h=document.createElement('h2');h.textContent=song.title;const p=document.createElement('p');p.textContent=(song.kind==='youtube'?'YouTube':song.kind==='demo'?'練習音源':'端末音源')+' · '+(song.notes.length?song.notes.length+' 音のお手本':'お手本なし');b.append(h,p);b.onclick=()=>{tab('sing');loadSong(song);};row.append(b);
    if(song.id!=='demo'){const remove=document.createElement('button');remove.className='remove';remove.textContent='×';remove.setAttribute('aria-label',song.title+'を削除');remove.onclick=async()=>{if(await ask('「'+song.title+'」をライブラリから削除しますか？','',true)){data.songs=data.songs.filter(s=>s.id!==song.id);if(current.id===song.id)loadSong(data.songs[0]);save();renderLibrary();}};row.append(remove);}list.append(row);}}
  function seek(t){const time=Math.max(0,Math.min(duration||Infinity,t));trace=[];lastTime=time;lastTick=performance.now();command('seek',{time});}
  function updateKey(){$('key-label').textContent=key===0?'原曲':(key>0?'+':'')+key+' 半音';}
  function updateLoop(){$('loop-label').textContent=loopA===null?'再生位置で A → B を設定すると繰り返します。':loopB===null?'A '+C.timeLabel(loopA)+' · 終点 B を設定してください':'A '+C.timeLabel(loopA)+' → B '+C.timeLabel(loopB)+' を繰り返し';}
  function setOffset(value){if(!Number.isFinite(value))return;current.offset=Math.round(value*100)/100;save();reset();updateSong();}
  function setAnalysis(show,message='',progress=0){$('analysis-state').hidden=!show;$('analysis-text').textContent=message;$('analysis-progress').value=progress;$('analyze').disabled=show;}
  function event(name,d){
    if(name==='nativeReady'){nativeReady=true;command('gate',{value:Math.pow(10,data.settings.gate/20)});loadSong(current);return;}
    if(name==='sourceReady'){sourceReady=true;if(d.duration)duration=d.duration;return;}
    if(name==='time'){
      if(d.duration>0)duration=d.duration;if(Math.abs(d.time-timeline())>1)trace=[];lastTime=d.time;lastTick=performance.now();playing=d.playing;updateTransport();
      if(loopSeeking&&d.time<loopA+.6)loopSeeking=false;
      if(loopB!==null&&playing&&d.time>=loopB&&(!loopSeeking||performance.now()-loopAt>2000)){loopSeeking=true;loopAt=performance.now();seek(loopA);}return;
    }
    if(name==='paused'||name==='ended'){lastTime=timeline();playing=false;updateTransport();if(name==='ended'&&score.percent!==null)toast('音程一致率 '+score.percent.toFixed(0)+'% · 計測 '+score.measured.toFixed(1)+' 秒');return;}
    if(name==='mic'){mic=d.on;if(!mic){midi=-1;hz=0;rms=0;}$('mic-toggle').setAttribute('aria-pressed',String(mic));updateTransport();return;}
    if(name==='pitch'){
      if(!mic)return;hz=d.hz;midi=d.midi;rms=d.rms;pitchStamp=performance.now();
      const t=timeline()-data.settings.latency/1000;const target=targetAt(t);
      if(playing){score.add(t,midi,target,data.settings.octave);if(midi>=0)trace.push({t,m:displayPitch(midi,t)});else trace.push({t,m:null});updateScore();}
      else if(midi>=0)trace.push({t:(performance.now()-freeOrigin)/1000,m:midi,free:true});return;
    }
    if(name==='share'){loadYoutube(d.text);return;}
    if(name==='localPicked'){const id='file-'+Date.now().toString(36);const song={id,title:d.name,kind:'local',uri:d.uri,notes:[],offset:0};data.songs.push(song);loadSong(song);return;}
    if(name==='analysis'){analysis=d.song;setAnalysis(true,'解析中 '+d.progress+'% · '+d.name,d.progress);return;}
    if(name==='analysisCancelled'){if(analysis===d.song){setAnalysis(false);analysis=null;toast('解析を中止しました');}return;}
    if(name==='analysisError'){if(analysis===d.song){setAnalysis(false);analysis=null;}toast(d.message);return;}
    if(name==='guide'){
      const song=data.songs.find(s=>s.id===d.song);if(song){try{song.notes=C.notes(d.notes);song.guideKind='estimated';save();if(song.id===current.id){reset();updateSong();}toast('お手本を推定しました。音程と開始位置を確認してください');}catch(e){toast(e.message);}}
      if(analysis===d.song){analysis=null;setAnalysis(false);}return;
    }
    if(name==='notesImported'){const song=data.songs.find(s=>s.id===d.song);if(!song)return;try{const guide=C.parseGuide(d.text);song.notes=guide.notes;song.guideKind=guide.guideKind;song.offset=guide.offset;if(guide.title)song.title=guide.title.slice(0,150);save();reset();updateSong();toast('音程データを読み込みました');}catch(e){toast(e.message);}return;}
    if(name==='pitchKey'){key=d.value;reset();updateKey();return;}
    if(name==='error'||name==='saved'){toast(d.message);return;}
  }
  function paint(stamp){
    requestAnimationFrame(paint);if(stamp-frameStamp<30||document.hidden||page!=='sing')return;frameStamp=stamp;
    const canvas=$('pitch-chart'),w=canvas.clientWidth,h=canvas.clientHeight,dpr=Math.min(window.devicePixelRatio||1,2);
    if(canvas.width!==Math.round(w*dpr)||canvas.height!==Math.round(h*dpr)){canvas.width=Math.round(w*dpr);canvas.height=Math.round(h*dpr);}ctx.setTransform(dpr,0,0,dpr,0,0);ctx.clearRect(0,0,w,h);
    const t=timeline(),left=34,right=w-8,top=12,bottom=h-16,nowX=left+(right-left)*.25,seconds=8,scale=(right-left)/seconds;
    const x=time=>nowX+(time-t)*scale;const ns=activeNotes(),offset=current.offset||0;
    const nearby=ns.filter(n=>n.end+offset>t-2&&n.start+offset<t+6);let min=57,max=75;
    if(nearby.length){min=Math.min(...nearby.map(n=>n.midi+key))-3;max=Math.max(...nearby.map(n=>n.midi+key))+3;if(max-min<16){const mid=(max+min)/2;min=mid-8;max=mid+8;}}
    else if(midi>=0){min=Math.floor(midi/12)*12-5;max=min+24;}
    low+=(min-low)*.14;high+=(max-high)*.14;const y=m=>bottom-(m-low)/(high-low)*(bottom-top);
    ctx.font='9px system-ui';ctx.textAlign='right';
    for(let m=Math.floor(low);m<=high;m++){const yy=y(m);const isC=m%12===0;ctx.strokeStyle=isC?'#36465f':'#203149';ctx.lineWidth=isC?1:.5;ctx.beginPath();ctx.moveTo(left,yy);ctx.lineTo(right,yy);ctx.stroke();if(m%2===0){ctx.fillStyle=isC?'#b8c4d9':'#697e9e';ctx.fillText(C.noteName(m),left-8,yy+3);}}
    for(let s=Math.floor(t-2);s<t+6;s++){if(s<0)continue;ctx.strokeStyle='#1d2c43';ctx.beginPath();ctx.moveTo(x(s),top);ctx.lineTo(x(s),bottom);ctx.stroke();}
    ctx.save();ctx.beginPath();ctx.rect(left,top,right-left,bottom-top);ctx.clip();
    for(const n of nearby){const a=x(n.start+offset),b=x(n.end+offset);const yy=y(n.midi+key);const hh=Math.min(8,Math.max(4,(bottom-top)/(high-low)*.62));ctx.fillStyle=n.start+offset<=t&&n.end+offset>t?'#92ffe6':'#62cbb7';ctx.beginPath();ctx.roundRect(a,yy-hh/2,Math.max(2,b-a-1),hh,hh/2);ctx.fill();}
    trace=trace.filter(p=>p.free?(!playing&&(performance.now()-freeOrigin)/1000-p.t<9):p.t>t-12).slice(-1600);
    ctx.strokeStyle='#b6a4ff';ctx.lineWidth=2.5;ctx.lineJoin='round';ctx.lineCap='round';ctx.beginPath();let prev=null;
    for(const p of trace){if(playing&&p.free||!playing&&!p.free)continue;const px=p.free?nowX+(p.t-(performance.now()-freeOrigin)/1000)*scale:x(p.t);if(p.m===null){prev=null;continue;}const py=y(p.m);if(!prev||p.t-prev.t>.15||Math.abs(p.m-prev.m)>5)ctx.moveTo(px,py);else ctx.lineTo(px,py);prev=p;}ctx.stroke();ctx.restore();
    ctx.strokeStyle='#dfe8ff70';ctx.lineWidth=1;ctx.setLineDash([3,4]);ctx.beginPath();ctx.moveTo(nowX,top-4);ctx.lineTo(nowX,bottom+4);ctx.stroke();ctx.setLineDash([]);
    const voiced=mic&&midi>=0&&performance.now()-pitchStamp<300;
    if(voiced){const yy=y(playing?displayPitch(midi,t-data.settings.latency/1000):midi);if(yy>=top&&yy<=bottom){ctx.fillStyle='#b6a4ff';ctx.shadowColor='#b6a4ff';ctx.shadowBlur=10;ctx.beginPath();ctx.arc(nowX,yy,4,0,Math.PI*2);ctx.fill();ctx.shadowBlur=0;}}
    $('current-note').textContent=voiced?C.noteName(midi):'—';const target=targetAt(t-data.settings.latency/1000);
    $('current-cents').textContent=voiced?(target!==null&&playing?((C.cents(midi,target,data.settings.octave)>=0?'+':'')+Math.round(C.cents(midi,target,data.settings.octave))+' cent'):Math.round(hz)+' Hz'):(mic?'声を出してください':'声を出すと音程を表示');
    const db=rms>0&&mic?20*Math.log10(rms):-80;$('level').style.width=Math.max(0,Math.min(100,(db+60)/50*100))+'%';$('input-db').textContent=db>-80?Math.round(db)+' dB':'— dB';
    $('range-label').textContent=C.noteName(Math.ceil(low))+' — '+C.noteName(Math.floor(high));$('time-now').textContent=C.timeLabel(t);$('time-end').textContent=C.timeLabel(duration);if(!seeking){$('seek').max=duration||1;$('seek').value=t;}
  }
  $('load-youtube').onclick=()=>loadYoutube($('youtube-url').value);$('youtube-url').onkeydown=e=>{if(e.key==='Enter'){e.target.blur();loadYoutube(e.target.value);}};
  $('search-youtube').onclick=async()=>{const q=await ask('YouTube で探す曲名');if(q&&q.trim())command('search',{query:q.trim()});};
  $('pick-local').onclick=()=>command('pick',{kind:'local'});
  $('rename').onclick=async()=>{const title=await ask('曲名を変更',current.title);if(title&&title.trim()){current.title=title.trim().slice(0,150);save();updateSong();}};
  $('play').onclick=()=>{if(playing)command('pause');else{if(!sourceReady&&nativeReady){toast('音源を準備しています。YouTube は上部の動画も操作できます');return;}document.querySelector('.chart-card').scrollIntoView({block:'start',behavior:'smooth'});command('play',{microphone:true});}};
  $('mic-toggle').onclick=()=>command('mic',{on:!mic});$('rewind').onclick=()=>seek(timeline()-5);
  $('seek').oninput=()=>{seeking=true;$('time-now').textContent=C.timeLabel(Number($('seek').value));};$('seek').onchange=()=>{seek(Number($('seek').value));seeking=false;};
  $('analyze').onclick=()=>command('pick',{kind:'guide',song:current.id});$('import-guide').onclick=()=>command('pick',{kind:'notes',song:current.id});$('cancel-analysis').onclick=()=>command('cancelAnalysis');
  $('export-guide').onclick=()=>command('export',{data:JSON.stringify({format:'aikara-guide-v1',title:current.title,youtubeId:current.videoId||'',notes:current.notes,guideKind:current.guideKind||'imported',offset:current.offset||0},null,2)});
  $('guide-offset').onchange=()=>setOffset(Number($('guide-offset').value));$('offset-minus').onclick=()=>setOffset((current.offset||0)-.1);$('offset-plus').onclick=()=>setOffset((current.offset||0)+.1);
  $('latency').value=data.settings.latency;$('latency-label').textContent=data.settings.latency+' ms';$('latency').oninput=()=>{data.settings.latency=Number($('latency').value);$('latency-label').textContent=data.settings.latency+' ms';reset();save();};
  $('gate').value=data.settings.gate;$('gate-label').textContent=data.settings.gate+' dB';$('gate').oninput=()=>{data.settings.gate=Number($('gate').value);$('gate-label').textContent=data.settings.gate+' dB';command('gate',{value:Math.pow(10,data.settings.gate/20)});save();};
  $('octave').checked=data.settings.octave;$('octave').onchange=()=>{data.settings.octave=$('octave').checked;reset();save();};$('reset-score').onclick=()=>{reset();toast('計測をリセットしました');};
  $('key-down').onclick=()=>{if(key>-12)command('pitch',{value:key-1});};$('key-up').onclick=()=>{if(key<12)command('pitch',{value:key+1});};
  $('set-a').onclick=()=>{loopA=timeline();loopB=null;updateLoop();};$('set-b').onclick=()=>{if(loopA===null){toast('先に A を設定してください');return;}if(timeline()<loopA+.3){toast('B は A より後に設定してください');return;}loopB=timeline();updateLoop();};$('clear-loop').onclick=()=>{loopA=loopB=null;updateLoop();};
  $('help').onclick=()=>$('help-dialog').showModal();$('close-help').onclick=()=>$('help-dialog').close();
  $('youtube-terms').onclick=()=>command('external',{url:'https://www.youtube.com/t/terms'});$('google-privacy').onclick=()=>command('external',{url:'https://policies.google.com/privacy'});
  for(const b of document.querySelectorAll('nav button'))b.onclick=()=>tab(b.dataset.tab);
  updateSong();updateTransport();requestAnimationFrame(paint);
  return {event,back(){if($('help-dialog').open)$('help-dialog').close();else if($('prompt-dialog').open)$('prompt-dialog').close();else if(page!=='sing')tab('sing');else command('background');}};
})();
window.App=App;
