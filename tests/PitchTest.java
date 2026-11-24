package jp.haruya.aikara;
import java.util.Random;
public final class PitchTest {
    static int checks;static double worst;static final Random random=new Random(24);
    static void require(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
    static float[] tone(double hz,double phase,boolean harmonic,double noise){float[] x=new float[1024];for(int i=0;i<x.length;i++){double a=2*Math.PI*hz*i/16000+phase;x[i]=(float)(.2*Math.sin(a)+(harmonic?.32*Math.sin(2*a)+.1*Math.sin(3*a):0)+(random.nextDouble()-.5)*noise);}return x;}
    public static void main(String[] args){PitchDetector yin=new PitchDetector(16000,1024);long start=System.nanoTime();
      for(double hz:new double[]{65.406,82.407,110,146.832,196,220,261.626,329.628,440,523.251,659.255,880,1046.502})for(int j=0;j<8;j++)for(boolean harmonic:new boolean[]{false,true}){
        PitchDetector.Result r=yin.detect(tone(hz,j*.7,harmonic,.008),.008);double error=Math.abs(1200*Math.log(r.hz/hz)/Math.log(2));worst=Math.max(worst,error);require(r.hz>0&&error<15,"Pitch "+hz+" detected "+r.hz+" error="+error);require(r.confidence>.8,"low confidence");}
      require(yin.detect(new float[1024],.008).hz==0,"silence must be unvoiced");
      float[] dc=new float[1024];java.util.Arrays.fill(dc,.5f);require(yin.detect(dc,.008).hz==0,"DC must be unvoiced");
      for(int j=0;j<20;j++){float[] noise=new float[1024];for(int i=0;i<noise.length;i++)noise[i]=(float)(random.nextGaussian()*.03);require(yin.detect(noise,.008).hz==0,"noise false positive");}
      float[] quiet=tone(220,0,false,0);for(int i=0;i<quiet.length;i++)quiet[i]*=.01;require(yin.detect(quiet,.008).hz==0,"gate must reject low amplitude");
      float[] frame=tone(440,0,true,0);long perf=System.nanoTime();for(int j=0;j<2000;j++)yin.detect(frame,.008);double ms=(System.nanoTime()-perf)/1e6/2000;
      System.out.printf("PASS %d checks; worst %.3f cents; %.3f ms/frame on build host; total %.0f ms%n",checks,worst,ms,(System.nanoTime()-start)/1e6);
    }
}
