#!/usr/bin/env python3
"""Tap the real icon in a fresh CI Launcher; normal Application, no instrumentation/auth injection.
No hosted connection: network is disabled before install. Never targets existing AVDs.
"""
import argparse,json,os,re,subprocess,time,xml.etree.ElementTree as ET
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--apk',required=True);p.add_argument('--package',required=True,choices=['br.com.diariodeclasse','br.com.diariodeclasse.homologacao']);p.add_argument('--label',required=True,choices=['Noctua','NOcTuA Homologação']);p.add_argument('--output',required=True);a=p.parse_args()
assert os.environ.get('GITHUB_ACTIONS')=='true' and os.environ.get('CI')=='true'
def adb(*args,timeout=30):return subprocess.run(['adb',*args],check=True,capture_output=True,timeout=timeout).stdout
serial=adb('get-serialno').decode().strip();assert serial.startswith('emulator-')
name=adb('emu','avd','name').decode().splitlines()[0].strip()
assert name in [f'noctua_commercial_auto_{os.environ["GITHUB_RUN_ID"]}_{os.environ["GITHUB_RUN_ATTEMPT"]}',f'noctua_onboarding_launcher_{os.environ["GITHUB_RUN_ID"]}_{os.environ["GITHUB_RUN_ATTEMPT"]}']
assert adb('shell','getprop','ro.kernel.qemu').strip()==b'1'
out=Path(a.output);out.mkdir(parents=True,exist_ok=True)
def dump():
 adb('shell','uiautomator','dump','/sdcard/noctua-launcher-proof.xml')
 return ET.fromstring(adb('exec-out','cat','/sdcard/noctua-launcher-proof.xml'))
def wait_for(predicate):
 deadline=time.monotonic()+30
 while time.monotonic()<deadline:
  try:
   root=dump();result=predicate(root)
   if result is not None:return result
  except (ET.ParseError,subprocess.SubprocessError):pass
  time.sleep(.2)
 raise AssertionError('Launcher/UI readiness condition not satisfied')
def center(node):
 values=list(map(int,re.findall(r'\d+',node.get('bounds',''))));assert len(values)==4
 x,y,x2,y2=values;assert x2>x and y2>y
 return str((x+x2)//2),str((y+y2)//2)
def find_icon(root):return next((n for n in root.iter('node')if n.get('text')==a.label or n.get('content-desc')==a.label),None)
start=time.monotonic();stage="INSTALL"
adb('shell','svc','wifi','disable');adb('shell','svc','data','disable')
try:
 adb('install',a.apk,timeout=90)
 stage='RESOLVE'
 resolved=adb('shell','cmd','package','resolve-activity','--brief','-a','android.intent.action.MAIN','-c','android.intent.category.LAUNCHER','-p',a.package).decode()
 assert a.package+'/' in resolved and 'MainActivity' in resolved
 stage='HOME'
 adb('shell','input','keyevent','KEYCODE_HOME')
 root=wait_for(lambda r:r if any('launcher' in n.get('package','').lower()for n in r.iter('node'))else None)
 (out/'launcher-home.xml').write_bytes(ET.tostring(root,encoding='utf-8'))
 icon=find_icon(root)
 if icon is None:
  stage='DRAWER'
  # This AOSP image exposes a narrow gesture affordance, not a tap button.
  # Start the gesture at its measured bounds, above the hotseat app shortcuts.
  handle=next((n for n in root.iter('node')if n.get('content-desc')=='Apps list'),None)
  if handle is not None:
   x,y=center(handle)
   workspace=next(n for n in root.iter('node')if n.get('resource-id','').endswith(':id/workspace'))
   bounds=list(map(int,re.findall(r'\d+',workspace.get('bounds'))))
   adb('shell','input','swipe',x,y,x,str(bounds[1]+(bounds[3]-bounds[1])//4),'200')
  else:
   adb('shell','input','keyevent','KEYCODE_ALL_APPS')
  icon=wait_for(find_icon)
 # The launch itself is an icon tap. No monkey, hardcoded component or am start.
 stage='ICON_TAP'
 adb('shell','input','tap',*center(icon))
 stage='LOGIN_SCREEN'
 root=wait_for(lambda r:r if any(n.get('package')==a.package for n in r.iter('node'))and any(n.get('text')=='Entrar'for n in r.iter('node'))and any(n.get('text')=='E-mail'for n in r.iter('node'))else None)
 (out/'login-from-launcher.png').write_bytes(adb('exec-out','screencap','-p'))
 result=dict(passed=True,application_id=a.package,launcher_intent_resolved=True,actual_icon_tapped=True,normal_Application=True,instrumentation=False,empty_login_screen=True,network_disabled=True,seconds=round(time.monotonic()-start,3),hosted_auth_executed=False)
 (out/'launcher-proof.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
except Exception as failure:
 try:(out/'launcher-failed.png').write_bytes(adb('exec-out','screencap','-p'))
 except Exception:pass
 try:(out/'launcher-failed.xml').write_bytes(ET.tostring(dump(),encoding='utf-8'))
 except Exception:pass
 (out/'launcher-failure.json').write_text(json.dumps(dict(passed=False,stage=stage,error=type(failure).__name__,application_id=a.package,hosted_auth_executed=False))+'\n')
 raise
finally:
 adb('shell','am','force-stop',a.package)
 adb('shell','svc','wifi','enable');adb('shell','svc','data','enable')
