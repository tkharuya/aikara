#!/usr/bin/env python3
"""Original synthesized scale exercise; no third-party recording or composition."""
import array,math,pathlib,wave
out=pathlib.Path(__file__).resolve().parents[1]/'app/src/main/assets/demo.wav'
rate=22050;samples=array.array('h');notes=[60,62,64,65,67,69,71,72,72,71,69,67,65,64,62,60]
for i in range(int(rate*23.2)):
    t=i/rate;k=int((t-2)/1.2);v=0
    if t>=2 and 0<=k<len(notes):
        u=t-2-k*1.2
        if u<.95:
            env=min(1,u/.02,(.95-u)/.06);f=440*2**((notes[k]-69)/12)
            v=env*(.22*math.sin(2*math.pi*f*u)+.035*math.sin(4*math.pi*f*u))
    samples.append(int(v*32767))
out.parent.mkdir(parents=True,exist_ok=True)
with wave.open(str(out),'wb') as f:f.setnchannels(1);f.setsampwidth(2);f.setframerate(rate);f.writeframes(samples.tobytes())
print(out)
