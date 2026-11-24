package jp.haruya.aikara;
import android.content.Context;
import android.media.*;
import android.net.Uri;
import java.nio.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

/** Streaming decoder + monophonic guide estimate. It is deliberately not a vocal separator. */
final class MelodyAnalyzer {
    interface Listener { void progress(int percent); }
    static final class Point { final double time, midi;Point(double t,double m){time=t;midi=m;} }
    static JSONArray analyze(Context context,Uri uri,AtomicBoolean cancel,Listener listener)throws Exception {
        MediaExtractor extractor=new MediaExtractor();MediaCodec codec=null;
        try {
            extractor.setDataSource(context,uri,null);MediaFormat format=null;
            for(int i=0;i<extractor.getTrackCount();i++) {
                MediaFormat f=extractor.getTrackFormat(i);
                if(f.getString(MediaFormat.KEY_MIME).startsWith("audio/")){extractor.selectTrack(i);format=f;break;}
            }
            if(format==null)throw new IllegalArgumentException("音声トラックがありません");
            long duration=format.containsKey(MediaFormat.KEY_DURATION)?format.getLong(MediaFormat.KEY_DURATION):0;
            codec=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            codec.configure(format,null,null,0);codec.start();
            int rate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE),channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            int encoding=AudioFormat.ENCODING_PCM_16BIT;
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();boolean inputDone=false,outputDone=false;
            Sampler sampler=new Sampler();int lastProgress=-1;long lastOutput=System.nanoTime();
            while(!outputDone&&!cancel.get()) {
                if(!inputDone){int input=codec.dequeueInputBuffer(10000);if(input>=0){ByteBuffer b=codec.getInputBuffer(input);int n=extractor.readSampleData(b,0);
                    if(n<0){codec.queueInputBuffer(input,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true;}
                    else {codec.queueInputBuffer(input,0,n,extractor.getSampleTime(),0);extractor.advance();}}}
                int output=codec.dequeueOutputBuffer(info,10000);
                if(output==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){MediaFormat f=codec.getOutputFormat();rate=f.getInteger(MediaFormat.KEY_SAMPLE_RATE);channels=f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);if(f.containsKey(MediaFormat.KEY_PCM_ENCODING))encoding=f.getInteger(MediaFormat.KEY_PCM_ENCODING);}
                else if(output>=0) {
                    lastOutput=System.nanoTime();
                    if(info.size>0) {
                        ByteBuffer b=codec.getOutputBuffer(output).duplicate().order(ByteOrder.LITTLE_ENDIAN);b.position(info.offset);b.limit(info.offset+info.size);
                        int bytes=encoding==AudioFormat.ENCODING_PCM_FLOAT?4:encoding==AudioFormat.ENCODING_PCM_16BIT?2:0;
                        if(bytes==0)throw new IllegalArgumentException("この音声形式の解析には対応していません");
                        if(!sampler.started){sampler.base=Math.max(0,info.presentationTimeUs/1e6);sampler.started=true;}
                        while(b.remaining()>=bytes*channels&&!cancel.get()) {
                            float value=0;for(int c=0;c<channels;c++)value+=bytes==4?b.getFloat():b.getShort()/32768f;
                            sampler.input(value/channels,rate);
                        }
                    }
                    outputDone=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;codec.releaseOutputBuffer(output,false);
                    if(duration>0){int p=(int)Math.min(99,info.presentationTimeUs*100/duration);if(p!=lastProgress){lastProgress=p;listener.progress(p);}}
                }
                if(System.nanoTime()-lastOutput>30000000000L)throw new IllegalStateException("音声の読み込みが止まりました");
            }
            if(cancel.get())throw new InterruptedException("解析を中止しました");
            JSONArray notes=segment(sampler.points);
            if(notes.length()==0)throw new IllegalArgumentException("音程を検出できませんでした。歌声のみの音源で試してください");
            listener.progress(100);return notes;
        } finally {if(codec!=null){try{codec.stop();}catch(Exception ignored){}codec.release();}extractor.release();}
    }
    static final class Sampler {
        final float[] ring=new float[1024],frame=new float[1024];final PitchDetector detector=new PitchDetector(16000,1024);
        final ArrayList<Point> points=new ArrayList<>();long inCount,outCount;double nextPos,base;float previous;boolean started;int cursor,hop;
        void input(float sample,int rate) {
            if(!Float.isFinite(sample))sample=0;
            while(nextPos<=inCount) {
                double f=inCount==0?1:nextPos-(inCount-1);
                float value=(float)(previous+(sample-previous)*Math.max(0,Math.min(1,f)));
                ring[cursor]=value;cursor=(cursor+1)%1024;outCount++;hop++;
                if(outCount>=1024 && hop>=320){hop=0;for(int j=0;j<1024;j++)frame[j]=ring[(cursor+j)%1024];
                    PitchDetector.Result r=detector.detect(frame,.004);
                    points.add(new Point(base+(outCount-512)/16000.0,r.confidence>=.82?r.midi():-1));}
                nextPos+=rate/16000.0;
            }
            previous=sample;inCount++;
        }
    }
    static JSONArray segment(ArrayList<Point> points)throws JSONException {
        JSONArray notes=new JSONArray();int pitch=-1;double start=0,last=0;
        for(int i=0;i<=points.size();i++) {
            int p=-1;double t=last+.02;
            if(i<points.size()) {
                Point pt=points.get(i);t=pt.time;
                if(pt.midi>=0) {double val=pt.midi;
                    if(i>0&&i+1<points.size()&&points.get(i-1).midi>=0&&points.get(i+1).midi>=0){double[] a={points.get(i-1).midi,val,points.get(i+1).midi};Arrays.sort(a);val=a[1];}
                    p=(int)Math.round(val);
                }
            }
            if(p!=pitch){if(pitch>=0 && last+.02-start>=.075){JSONObject n=new JSONObject();n.put("start",Math.max(0,start-.01));n.put("end",last+.01);n.put("midi",pitch);notes.put(n);}pitch=p;start=t;}
            last=t;
        }
        return notes;
    }
}
