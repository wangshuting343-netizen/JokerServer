import subprocess,time,os,shutil
from pathlib import Path
root=Path(__file__).resolve().parents[1]
java=str(Path(os.environ['JAVA_HOME'])/'bin/java.exe') if os.environ.get('JAVA_HOME') else shutil.which('java')
if not java: raise RuntimeError('Set JAVA_HOME or put Java on PATH')
log=open(root/'evidence/server-test.log','w',encoding='utf-8')
p=subprocess.Popen([java,'--enable-native-access=ALL-UNNAMED','-Dbj.tcp=15446','-Dbj.db=evidence/tcp-test.db','-cp','build;lib/*','JokerServer'],cwd=root,stdout=log,stderr=log,creationflags=subprocess.CREATE_NO_WINDOW)
try:
 time.sleep(2)
 subprocess.run(['python',str(root/'tests/tcp_integration.py')],check=True,timeout=20)
finally:p.terminate();p.wait(timeout=10);log.close()
