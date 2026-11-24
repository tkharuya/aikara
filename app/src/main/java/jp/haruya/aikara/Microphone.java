package jp.haruya.aikara;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Process;

final class Microphone {
    interface Listener { void pitch(PitchDetector.Result result); void error(String message); }
    private volatile boolean running;
    private volatile AudioRecord recorder;
    private Thread worker;
    volatile double gate=.008;
    synchronized void start(Listener listener) {
        if(running)return;running=true;
        worker=new Thread(()->capture(listener),"aikara-mic");worker.start();
    }
    private void capture(Listener listener) {
        AudioRecord r=null;
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
            int size=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
            if(size<0) throw new IllegalStateException("16 kHz の録音に対応していません");
            r=new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(size,8192));
            recorder=r;
            if(r.getState()!=AudioRecord.STATE_INITIALIZED)throw new IllegalStateException("マイクを初期化できません");
            r.startRecording();
            if(r.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)throw new IllegalStateException("マイクを開始できません");
            float[] frame=new float[1024];short[] chunk=new short[320];int available=0;
            PitchDetector detector=new PitchDetector(16000,1024);
            while(running) {
                int read=r.read(chunk,0,chunk.length,AudioRecord.READ_BLOCKING);
                if(read<0) { if(running)throw new IllegalStateException("マイク入力が中断されました ("+read+")");break; }
                if(read==0)continue;
                System.arraycopy(frame,read,frame,0,frame.length-read);
                for(int i=0;i<read;i++)frame[frame.length-read+i]=chunk[i]/32768f;
                available+=read;if(available>=frame.length)listener.pitch(detector.detect(frame,gate));
            }
        } catch(Exception e) { if(running)listener.error(e.getMessage()==null?"マイクを使用できません":e.getMessage()); }
        finally { running=false;recorder=null;if(r!=null){try{r.stop();}catch(Exception ignored){} r.release();} }
    }
    synchronized void stop() {
        running=false;
        AudioRecord r=recorder;if(r!=null)try{r.stop();}catch(Exception ignored){}
        if(worker!=null)try{worker.join(700);}catch(InterruptedException e){Thread.currentThread().interrupt();}
        worker=null;
    }
}
