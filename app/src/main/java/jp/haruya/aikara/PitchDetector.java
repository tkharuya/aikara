package jp.haruya.aikara;

/** YIN fundamental-frequency estimator. No network, models or stored microphone audio. */
public final class PitchDetector {
    public static final class Result {
        public final double hz, confidence, rms;
        Result(double h, double c, double r) { hz=h; confidence=c; rms=r; }
        public double midi() { return hz>0 ? 69+12*Math.log(hz/440.0)/Math.log(2) : -1; }
    }
    private final int sampleRate, minLag, maxLag;
    private final double[] difference;
    public PitchDetector(int rate, int frameSize) {
        sampleRate=rate; minLag=Math.max(2,rate/1100); maxLag=Math.min(rate/60,frameSize/2-1);
        difference=new double[maxLag+2];
    }
    public Result detect(float[] frame, double gate) {
        int n=frame.length; double mean=0, energy=0;
        for(float x:frame) mean+=x; mean/=n;
        for(float x:frame) energy+=(x-mean)*(x-mean);
        double rms=Math.sqrt(energy/n);
        if(rms<gate) return new Result(0,0,rms);
        int window=n-maxLag; double running=0;
        difference[0]=1;
        for(int tau=1;tau<=maxLag;tau++) {
            double sum=0;
            for(int j=0;j<window;j++) { double d=frame[j]-frame[j+tau]; sum+=d*d; }
            running+=sum; difference[tau]=running>1e-20 ? sum*tau/running : 1;
        }
        int lag=-1;
        for(int t=minLag;t<maxLag;t++) if(difference[t]<.15) {
            while(t+1<=maxLag && difference[t+1]<difference[t]) t++;
            lag=t;break;
        }
        if(lag<0) { int best=minLag; for(int t=minLag+1;t<=maxLag;t++) if(difference[t]<difference[best]) best=t;
            if(difference[best]>.28) return new Result(0,0,rms); lag=best; }
        double refined=lag;
        if(lag>1 && lag<maxLag) {
            double a=difference[lag-1], b=difference[lag], c=difference[lag+1];
            double denom=2*(2*b-c-a); if(Math.abs(denom)>1e-12) refined+=(c-a)/denom;
        }
        double hz=sampleRate/refined;
        return hz<60 || hz>1100 ? new Result(0,0,rms) : new Result(hz,Math.max(0,1-difference[lag]),rms);
    }
}
