package jp.haruya.aikara;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

public class MainActivity extends Activity {
    private WebView ui,video;private LinearLayout root;private MediaPlayer player;
    private View fullscreen;private WebChromeClient.CustomViewCallback fullscreenCallback;
    private final Handler handler=new Handler(Looper.getMainLooper());private final Microphone mic=new Microphone();
    private boolean ready,prepared,micOn,closing,playAfterPermission;private String source="none",videoId="",pendingShare="",pickKind="",pickSong="",exportText="";
    private AtomicBoolean cancelAnalysis;private boolean isForeground;private AudioManager audioManager;private AudioFocusRequest focus;
    private static final int PICK=10,MIC=11,EXPORT=12;private static final String UI_ORIGIN="https://aikara.local/";
    @Override public void onCreate(Bundle b){
        super.onCreate(b);audioManager=(AudioManager)getSystemService(AUDIO_SERVICE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(9,15,29));
        root.setOnApplyWindowInsetsListener((view,insets)->{android.graphics.Insets x=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());view.setPadding(x.left,x.top,x.right,x.bottom);return insets;});
        setContentView(root);
        video=new WebView(this);video.setBackgroundColor(Color.BLACK);video.setVisibility(View.GONE);
        root.addView(video,new LinearLayout.LayoutParams(-1,dp(210)));
        video.getSettings().setJavaScriptEnabled(true);video.getSettings().setDomStorageEnabled(true);video.getSettings().setMediaPlaybackRequiresUserGesture(true);
        video.getSettings().setAllowFileAccess(false);video.getSettings().setAllowContentAccess(false);
        CookieManager.getInstance().setAcceptThirdPartyCookies(video,true);
        video.addJavascriptInterface(new PlayerEvents(),"PlayerEvents");
        video.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest r){if(r.isForMainFrame()){external(r.getUrl().toString());return true;}return false;}});
        video.setWebChromeClient(new WebChromeClient(){
            @Override public void onShowCustomView(View v,CustomViewCallback cb){if(fullscreen!=null){cb.onCustomViewHidden();return;}fullscreen=v;fullscreenCallback=cb;((android.widget.FrameLayout)getWindow().getDecorView()).addView(v,new android.widget.FrameLayout.LayoutParams(-1,-1));}
            @Override public void onHideCustomView(){closeFullscreen();}
        });
        ui=new WebView(this);ui.setBackgroundColor(Color.rgb(9,15,29));root.addView(ui,new LinearLayout.LayoutParams(-1,0,1));
        WebSettings settings=ui.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setBlockNetworkLoads(true);
        ui.addJavascriptInterface(new Bridge(),"Native");
        ui.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView w,WebResourceRequest request){
                Uri u=request.getUrl();String p=u.getPath();
                if("https".equals(u.getScheme())&&"aikara.local".equals(u.getHost())&&p!=null&&!p.contains("..")){
                    try{String name=p.substring(1);String mime=name.endsWith(".js")?"text/javascript":name.endsWith(".css")?"text/css":"text/html";return new WebResourceResponse(mime,"UTF-8",getAssets().open(name));}catch(IOException ignored){}
                }
                return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
            }
            @Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest r){return true;}
            @Override public void onPageFinished(WebView w,String url){if(!UI_ORIGIN.concat("index.html").equals(url))return;ready=true;emit("nativeReady",new JSONObject());deliverShare();}
        });
        resizePanels();ui.loadUrl(UI_ORIGIN+"index.html");readShare(getIntent());handler.post(clock);
    }
    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
    private void closeFullscreen(){if(fullscreen!=null){((android.widget.FrameLayout)getWindow().getDecorView()).removeView(fullscreen);fullscreen=null;WebChromeClient.CustomViewCallback cb=fullscreenCallback;fullscreenCallback=null;if(cb!=null)cb.onCustomViewHidden();}}
    private void resizePanels(){android.util.DisplayMetrics d=getResources().getDisplayMetrics();boolean wide=d.widthPixels>d.heightPixels*1.2&&d.widthPixels/d.density>600;
        root.setOrientation(wide?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
        video.setLayoutParams(wide?new LinearLayout.LayoutParams((int)(d.widthPixels*.42),-1):new LinearLayout.LayoutParams(-1,dp(210)));
        ui.setLayoutParams(wide?new LinearLayout.LayoutParams(0,-1,1):new LinearLayout.LayoutParams(-1,0,1));
    }
    @Override public void onConfigurationChanged(android.content.res.Configuration c){super.onConfigurationChanged(c);resizePanels();}
    private JSONObject obj(Object... a){JSONObject j=new JSONObject();try{for(int i=0;i<a.length;i+=2)j.put((String)a[i],a[i+1]);}catch(JSONException ignored){}return j;}
    private void emit(String name,JSONObject data){handler.post(()->{if(ready&&!closing)ui.evaluateJavascript("window.App&&App.event("+JSONObject.quote(name)+","+data.toString()+")",null);});}
    private void fail(String s){emit("error",obj("message",s));}
    private final Runnable clock=new Runnable(){public void run(){if(closing)return;try{if(isForeground&&prepared&&player!=null)emit("time",obj("time",player.getCurrentPosition()/1000.0,"duration",player.getDuration()/1000.0,"playing",player.isPlaying()));}catch(Exception ignored){}handler.postDelayed(this,80);}};
    public final class Bridge {
        @JavascriptInterface public void command(String json){if(json==null||json.length()>8000000)return;handler.post(()->{try{dispatch(new JSONObject(json));}catch(Exception e){fail("操作できませんでした: "+e.getMessage());}});}
    }
    /** This untrusted-player bridge intentionally exposes telemetry only. No files or microphone. */
    public final class PlayerEvents {
        @JavascriptInterface public void tick(String id,double time,double duration,int state){if(!Double.isFinite(time)||!Double.isFinite(duration)||time<0||duration<0)return;handler.post(()->{if(!source.equals("youtube")||!videoId.equals(id))return;emit("time",obj("time",time,"duration",duration,"playing",state==1&&isForeground));});}
        @JavascriptInterface public void error(String id,int code){handler.post(()->{if(!videoId.equals(id))return;String msg=code==101||code==150?"この動画は埋め込み再生が許可されていません。別の動画を選んでください。":code==153?"YouTube がアプリ識別を受け付けませんでした。WebView を更新して再読み込みしてください。":code==100?"動画が削除されたか非公開です。":"YouTube を再生できません ("+code+")";fail(msg);});}
    }
    private void dispatch(JSONObject j)throws Exception {
        String action=j.optString("action");
        switch(action){
            case "youtube":loadYoutube(j.getString("id"));break;
            case "local":loadLocal(j.getString("uri"),j.optBoolean("autoplay",false));break;
            case "demo":loadLocal("asset:demo.wav",false);break;
            case "play":if(j.optBoolean("microphone",false)&&!micOn)startMic(true);else play();break;
            case "pause":pause();break;
            case "background":pause();moveTaskToBack(true);break;
            case "seek":double sec=j.optDouble("time",0);if(Double.isFinite(sec)){if(source.equals("youtube"))video.evaluateJavascript("if(window.player&&player.seekTo)player.seekTo("+Math.max(0,sec)+",true)",null);else if(prepared)player.seekTo((long)(Math.max(0,sec)*1000),MediaPlayer.SEEK_CLOSEST);}break;
            case "mic":if(j.optBoolean("on"))startMic(false);else stopMic();break;
            case "gate":mic.gate=Math.max(.001,Math.min(.15,j.optDouble("value",.008)));break;
            case "pick":pause();pickKind=j.optString("kind","local");pickSong=j.optString("song");Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(pickKind.equals("notes")?"*/*":"audio/*");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,PICK);break;
            case "cancelAnalysis":if(cancelAnalysis!=null)cancelAnalysis.set(true);break;
            case "export":exportText=j.getString("data");Intent out=new Intent(Intent.ACTION_CREATE_DOCUMENT);out.addCategory(Intent.CATEGORY_OPENABLE);out.setType("application/json");out.putExtra(Intent.EXTRA_TITLE,"aikara-song.json");startActivityForResult(out,EXPORT);break;
            case "search":external("https://www.youtube.com/results?search_query="+Uri.encode(j.optString("query")+" カラオケ"));break;
            case "external":external(j.getString("url"));break;
            case "panel":if(source.equals("youtube")){boolean show=j.optBoolean("show");if(!show)pause();video.setVisibility(show?View.VISIBLE:View.GONE);}break;
            case "pitch":int semitones=Math.max(-12,Math.min(12,j.optInt("value")));if(!source.equals("youtube")&&prepared){boolean was=player.isPlaying();try{player.setPlaybackParams(new PlaybackParams().setSpeed(1).setPitch((float)Math.pow(2,semitones/12.0)));if(!was)player.pause();emit("pitchKey",obj("value",semitones));}catch(Exception e){fail("この音源・端末ではキー変更ができません");}}else fail("キー変更は端末の音源で使えます");break;
        }
    }
    private void external(String url){try{Uri u=Uri.parse(url);String h=u.getHost();if(!"https".equals(u.getScheme())||h==null)return;if(!(h.equals("youtu.be")||h.equals("youtube.com")||h.endsWith(".youtube.com")||h.equals("policies.google.com")))return;startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception e){fail("リンクを開けません");}}
    private void loadYoutube(String id)throws Exception{
        if(!id.matches("[A-Za-z0-9_-]{11}"))throw new IllegalArgumentException("YouTube URL を確認してください");
        pause();releaseLocal();source="youtube";videoId=id;video.setVisibility(View.VISIBLE);video.onResume();
        String html=readAsset("youtube.html").replace("__VIDEO_ID__",id);
        video.loadDataWithBaseURL("https://jp.haruya.aikara/",html,"text/html","UTF-8",null);emit("sourceReady",obj("kind","youtube"));
    }
    private void loadLocal(String uri,boolean autoplay)throws Exception{
        pause();releaseLocal();video.loadUrl("about:blank");video.setVisibility(View.GONE);source="local";videoId="";
        player=new MediaPlayer();MediaPlayer active=player;active.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
        if(uri.equals("asset:demo.wav")){android.content.res.AssetFileDescriptor f=getAssets().openFd("demo.wav");active.setDataSource(f.getFileDescriptor(),f.getStartOffset(),f.getLength());f.close();}
        else {Uri u=Uri.parse(uri);if(!"content".equals(u.getScheme()))throw new IllegalArgumentException("端末の音源を選んでください");active.setDataSource(this,u);}
        active.setOnPreparedListener(p->{if(player!=p)return;prepared=true;emit("sourceReady",obj("kind","local","duration",p.getDuration()/1000.0));if(autoplay)play();});
        active.setOnCompletionListener(p->{if(player!=p)return;emit("ended",new JSONObject());abandonFocus();});
        active.setOnErrorListener((p,what,extra)->{if(player==p){prepared=false;fail("音源を読み込めません。ファイルを選び直してください ("+what+")");}return true;});
        active.prepareAsync();
    }
    private void play(){
        if(source.equals("youtube")){video.evaluateJavascript("if(window.player&&player.playVideo)player.playVideo()",null);return;}
        if(!prepared||player==null){fail("音源の準備ができていません");return;}
        if(focus==null)focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()).setOnAudioFocusChangeListener(change->{if(change<=0)pause();}).build();
        if(audioManager.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED)player.start();else fail("他のアプリが音声を使用しています");
    }
    private void pause(){if(video!=null)video.evaluateJavascript("if(window.player&&player.pauseVideo)player.pauseVideo()",null);if(prepared&&player!=null)try{player.pause();}catch(Exception ignored){}abandonFocus();emit("paused",new JSONObject());}
    private void abandonFocus(){if(focus!=null)audioManager.abandonAudioFocusRequest(focus);}
    private void releaseLocal(){prepared=false;if(player!=null){player.release();player=null;}}
    private void startMic(boolean andPlay){
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){playAfterPermission=andPlay;requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MIC);return;}
        if(!isForeground)return;
        micOn=true;emit("mic",obj("on",true));
        mic.start(new Microphone.Listener(){public void pitch(PitchDetector.Result r){if(!micOn)return;emit("pitch",obj("hz",r.hz,"midi",r.midi(),"confidence",r.confidence,"rms",r.rms));}public void error(String s){handler.post(()->{stopMic();fail(s);});}});
        if(andPlay)play();
    }
    private void stopMic(){micOn=false;mic.stop();emit("mic",obj("on",false));}
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants){super.onRequestPermissionsResult(code,permissions,grants);if(code==MIC){boolean p=playAfterPermission;playAfterPermission=false;if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)startMic(p);else{fail("音程表示にはマイクの許可が必要です。音源の再生は利用できます。");if(p)play();}}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();
        if(request==EXPORT){String content=exportText;new Thread(()->{try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException();out.write(content.getBytes(StandardCharsets.UTF_8));emit("saved",obj("message","曲と音程バーを書き出しました"));}catch(Exception e){fail("書き出しに失敗しました");}},"aikara-export").start();return;}
        if(request!=PICK)return;
        try{getContentResolver().takePersistableUriPermission(uri,data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
        String name=fileName(uri);String kind=pickKind, song=pickSong;
        if(kind.equals("local")){emit("localPicked",obj("uri",uri.toString(),"name",name));return;}
        if(kind.equals("notes")){new Thread(()->{try(InputStream in=getContentResolver().openInputStream(uri)){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>8000000)throw new IOException("音程データが大きすぎます");out.write(b,0,n);}emit("notesImported",obj("song",song,"name",name,"text",out.toString("UTF-8")));}catch(Exception e){fail("音程データを読めません: "+e.getMessage());}},"aikara-import").start();return;}
        if(cancelAnalysis!=null)cancelAnalysis.set(true);AtomicBoolean token=new AtomicBoolean(false);cancelAnalysis=token;emit("analysis",obj("progress",0,"song",song,"name",name));
        new Thread(()->{try{JSONArray notes=MelodyAnalyzer.analyze(this,uri,token,p->{if(!token.get())emit("analysis",obj("progress",p,"song",song,"name",name));});if(!token.get())emit("guide",obj("song",song,"notes",notes,"name",name));}catch(Exception e){if(token.get())emit("analysisCancelled",obj("song",song));else emit("analysisError",obj("song",song,"message",e.getMessage()));}},"aikara-analysis").start();
    }
    private String fileName(Uri u){try(Cursor c=getContentResolver().query(u,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())return c.getString(0);}catch(Exception ignored){}return "音源";}
    private String readAsset(String name)throws IOException{try(InputStream in=getAssets().open(name)){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}}
    private void readShare(Intent intent){if(Intent.ACTION_SEND.equals(intent.getAction())){String t=intent.getStringExtra(Intent.EXTRA_TEXT);if(t!=null&&t.length()<10000)pendingShare=t;}deliverShare();}
    private void deliverShare(){if(ready&&!pendingShare.isEmpty()){emit("share",obj("text",pendingShare));pendingShare="";}}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);readShare(intent);}
    @Override protected void onResume(){super.onResume();isForeground=true;if(ui!=null)ui.onResume();if(video!=null)video.onResume();}
    @Override protected void onPause(){isForeground=false;pause();stopMic();if(ui!=null)ui.onPause();if(video!=null)video.onPause();super.onPause();}
    @Override protected void onDestroy(){closing=true;if(cancelAnalysis!=null)cancelAnalysis.set(true);handler.removeCallbacksAndMessages(null);mic.stop();releaseLocal();if(ui!=null){ui.removeJavascriptInterface("Native");ui.destroy();}if(video!=null){video.removeJavascriptInterface("PlayerEvents");video.destroy();}super.onDestroy();}
    @Override public void onBackPressed(){if(fullscreen!=null){closeFullscreen();return;}if(ui!=null)ui.evaluateJavascript("window.App&&App.back()",null);}
}
