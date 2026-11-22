#!/usr/bin/env python3
"""Tap the real icon in a fresh CI Launcher; normal Application, no instrumentation/auth injection.
No hosted connection: network is disabled before install. Never targets existing AVDs.
"""
import argparse,json,os,re,subprocess,time,xml.etree.ElementTree as ET
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--apk',required=True);p.add_argument('--package',required=True,choices=['br.com.diariodeclasse','br.com.diariodeclasse.homologacao']);p.add_argument('--label',required=True,choices=['Noctua','NOcTuA Homologação']);p.add_argument('--output',required=True);p.add_argument('--input-control',action='store_true');a=p.parse_args()
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
def find_icon(root):return next((n for n in root.iter('node')if n.get('class')!='android.widget.EditText'and(n.get('text')==a.label or n.get('content-desc')==a.label)),None)
def tap(node):adb('shell','input','touchscreen','-d','0','tap',*center(node))
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
 # Capture fixed, non-sensitive device/input facts on this empty CI-only AVD.
 (out/'device-input.json').write_text(json.dumps(dict(sdk=adb('shell','getprop','ro.build.version.sdk').decode().strip(),display=adb('shell','wm','size').decode().strip(),serial=serial,avd=name))+'\n')
 if a.input_control:
  stage='INPUT_CONTROL'
  gallery=next(n for n in root.iter('node')if n.get('text')=='Gallery')
  tap(gallery)
  wait_for(lambda r:r if any('gallery' in n.get('package','')or 'permissioncontroller' in n.get('package','')for n in r.iter('node'))else None)
  (out/'input-control.png').write_bytes(adb('exec-out','screencap','-p'))
  adb('shell','input','keyevent','KEYCODE_HOME')
  root=wait_for(lambda r:r if any('launcher' in n.get('package','')for n in r.iter('node'))else None)
 icon=find_icon(root)
 surface='HOME'
 search=next((n for n in root.iter('node')if n.get('resource-id','')=='com.android.quicksearchbox:id/search_widget_text'),None)
 if icon is None and search is not None:
  # AOSP's normal launcher search indexes installed apps offline. The query is
  # public app branding; no Enter/Intent/component launches the target app.
  stage='LAUNCHER_SEARCH';surface='LAUNCHER_SEARCH'
  tap(search)
  wait_for(lambda r:r if any(n.get('class')=='android.widget.EditText'for n in r.iter('node'))else None)
  adb('shell','input','text','Noctua')
  icon=wait_for(find_icon)
 elif icon is None:
  stage='DRAWER'
  surface='DRAWER'
  hotseat=next((n for n in root.iter('node')if n.get('resource-id','').endswith(':id/hotseat')),None)
  if hotseat is not None:
   # Start in the hotseat's empty bottom padding, outside the narrow scrim
   # education handle and app shortcuts. All positions come from current bounds.
   bounds=list(map(int,re.findall(r'\d+',hotseat.get('bounds'))))
   children=[list(map(int,re.findall(r'\d+',n.get('bounds'))))for n in hotseat.iter('node')if n is not hotseat and n.get('bounds')]
   bottom=max(b[3]for b in children)
   x=str((bounds[0]+bounds[2])//2);y=str(bottom+max(1,(bounds[3]-bottom)//8))
   workspace=next(n for n in root.iter('node')if n.get('resource-id','').endswith(':id/workspace'))
   bounds=list(map(int,re.findall(r'\d+',workspace.get('bounds'))))
   adb('shell','input','touchscreen','-d','0','swipe',x,y,x,str(bounds[1]+(bounds[3]-bounds[1])//4),'150')
  else:
   adb('shell','input','keyevent','KEYCODE_ALL_APPS')
  (out/'drawer-after-input.png').write_bytes(adb('exec-out','screencap','-p'))
  icon=wait_for(find_icon)
 # The launch itself is an icon tap. No monkey, hardcoded component or am start.
 (out/'launcher-icon-found.png').write_bytes(adb('exec-out','screencap','-p'))
 stage='ICON_TAP'
 tap(icon)
 stage='LOGIN_SCREEN'
 root=wait_for(lambda r:r if any(n.get('package')==a.package for n in r.iter('node'))and all(any(n.get('text')==label or n.get('content-desc')==label for n in r.iter('node'))for label in ('Entrar','E-mail'))else None)
 (out/'login-from-launcher.png').write_bytes(adb('exec-out','screencap','-p'))
 result=dict(passed=True,application_id=a.package,launcher_surface=surface,launcher_intent_resolved=True,actual_icon_tapped=True,normal_Application=True,instrumentation=False,empty_login_screen=True,network_disabled=True,seconds=round(time.monotonic()-start,3),hosted_auth_executed=False)
 (out/'launcher-proof.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
except Exception as failure:
 try:(out/'launcher-failed.png').write_bytes(adb('exec-out','screencap','-p'))
 except Exception:pass
 try:(out/'launcher-failed.xml').write_bytes(ET.tostring(dump(),encoding='utf-8'))
 except Exception:pass
 try:
  focus=adb('shell','dumpsys','window').decode()
  (out/'window-focus.txt').write_text('\n'.join(l for l in focus.splitlines()if 'mCurrentFocus=' in l or 'mFocusedApp=' in l)+'\n')
 except Exception:pass
 try:(out/'system-errors.txt').write_bytes(adb('shell','logcat','-d','-v','brief','-s','AndroidRuntime:E','InputDispatcher:E','ActivityTaskManager:E'))
 except Exception:pass
 (out/'launcher-failure.json').write_text(json.dumps(dict(passed=False,stage=stage,error=type(failure).__name__,application_id=a.package,hosted_auth_executed=False))+'\n')
 raise
finally:
 adb('shell','am','force-stop',a.package)
 adb('shell','svc','wifi','enable');adb('shell','svc','data','enable')
