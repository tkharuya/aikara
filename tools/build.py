#!/usr/bin/env python3
"""Build with an existing official Android SDK and ECJ; no downloaded dependencies."""
import argparse, os, pathlib, shutil, subprocess, zipfile
p=argparse.ArgumentParser()
for name in ['build-tools','android-jar','ecj','keystore','password-file','output']:p.add_argument('--'+name,required=True)
a=p.parse_args();root=pathlib.Path(__file__).resolve().parents[1];b=root/'build/manual';b.mkdir(parents=True,exist_ok=True)
bt=pathlib.Path(a.build_tools);jar=str(pathlib.Path(a.android_jar).resolve());app=root/'app/src/main'
def run(cmd):subprocess.run([str(x) for x in cmd],check=True,cwd=root)
for d in ['classes','gen','dex']:shutil.rmtree(b/d,ignore_errors=True);(b/d).mkdir()
run([bt/'aapt2','compile','--dir',app/'res','-o',b/'res.zip'])
run([bt/'aapt2','link','-o',b/'base.apk','-I',jar,'--manifest',app/'AndroidManifest.xml','--java',b/'gen','-0','wav','-A',app/'assets',b/'res.zip'])
sources=list((app/'java').rglob('*.java'))+list((b/'gen').rglob('*.java'))
run(['java','-jar',a.ecj,'-source','1.8','-target','1.8','-encoding','UTF-8','-bootclasspath',jar+os.pathsep+str(bt/'core-lambda-stubs.jar'),'-d',b/'classes',*sources])
with zipfile.ZipFile(b/'classes.jar','w',zipfile.ZIP_DEFLATED) as z:
    for f in (b/'classes').rglob('*.class'):z.write(f,f.relative_to(b/'classes'))
run([bt/'d8','--min-api','30','--lib',jar,'--output',b/'dex',b/'classes.jar'])
shutil.copy2(b/'base.apk',b/'unsigned.apk')
with zipfile.ZipFile(b/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as z:
    for f in (b/'dex').glob('*.dex'):z.write(f,f.name)
run([bt/'zipalign','-f','4',b/'unsigned.apk',b/'aligned.apk'])
output=pathlib.Path(a.output).resolve();output.parent.mkdir(parents=True,exist_ok=True)
run([bt/'apksigner','sign','--ks',a.keystore,'--ks-key-alias','aikara','--ks-pass','file:'+a.password_file,'--out',output,b/'aligned.apk'])
run([bt/'apksigner','verify','--verbose',output]);run([bt/'zipalign','-c','4',output]);print('APK:',output,'bytes:',output.stat().st_size)
