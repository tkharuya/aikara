package jp.haruya.aikara;
public final class ResamplerTest {
  public static void main(String[] args){int count=0;
    for(int rate:new int[]{8000,16000,22050,44100,48000,96000}){
      MelodyAnalyzer.Sampler s=new MelodyAnalyzer.Sampler();
      for(int i=0;i<rate*2;i++)s.input((float)(.25*Math.sin(2*Math.PI*220*i/rate)),rate);
      if(s.points.size()<90)throw new AssertionError("too few frames for "+rate);
      for(MelodyAnalyzer.Point p:s.points){if(Math.abs(p.midi-57)>.12)throw new AssertionError("rate="+rate+" midi="+p.midi);count++;}
      if(Math.abs(s.points.get(0).time-.032)>.001)throw new AssertionError("window midpoint offset");
      if(s.points.get(s.points.size()-1).time>2)throw new AssertionError("timestamp drift");
    }
    System.out.println("PASS "+count+" pitch frames after streaming resampling at six input sample rates");
  }
}
