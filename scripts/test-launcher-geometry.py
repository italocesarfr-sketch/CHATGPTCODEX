#!/usr/bin/env python3
"""Real empty AVD accessibility capture; no ADB, credentials or hosted requests."""
import re,sys,unittest,xml.etree.ElementTree as E
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts/ci'))
from launcher_geometry import drawer_gesture,bounds,stable_icon

class LauncherGeometryTest(unittest.TestCase):
 def tree(self):return E.parse(ROOT/'test-fixtures/launcher-api30-empty.xml').getroot()
 def verify(self,root):
  x,y,ex,ey=drawer_gesture(root)
  hotseat=next(n for n in root.iter('node')if n.get('resource-id','').endswith(':id/hotseat'))
  l,t,r,b=bounds(hotseat);self.assertTrue(l<x<r and t<y<b);self.assertEqual(x,ex);self.assertLess(ey,y)
  for n in root.iter('node'):
   if n.get('clickable')=='true' or n.get('content-desc')=='Apps list':
    l,t,r,b=bounds(n);self.assertFalse(l<=x<r and t<=y<b)
  return x,y,ex,ey
 def test_actual_capture_starts_outside_shortcuts_scrim_and_navigation(self):
  gesture=self.verify(self.tree());self.assertEqual((7,567,7,160),gesture)
 def test_density_scaling_preserves_geometry(self):
  root=self.tree()
  for n in root.iter('node'):
   if n.get('bounds'):
    l,t,r,b=tuple(int(x)*2 for x in re.findall(r'\d+',n.get('bounds')));n.set('bounds',f'[{l},{t}][{r},{b}]')
  self.assertEqual(tuple(x*2 for x in drawer_gesture(self.tree())),self.verify(root))
 def test_unknown_or_full_hotseat_fails_closed_instead_of_tapping_an_app(self):
  root=self.tree();hotseat=next(n for n in root.iter('node')if n.get('resource-id','').endswith(':id/hotseat'))
  for n in list(hotseat):hotseat.remove(n)
  E.SubElement(hotseat,'node',{'bounds':hotseat.get('bounds'),'clickable':'true'})
  with self.assertRaises(ValueError):drawer_gesture(root)

 def test_drawer_target_requires_unique_clickable_icon_and_fresh_layout_signature(self):
  r=E.fromstring('<hierarchy><node resource-id="launcher:id/apps_list_view"><node text="Noctua" clickable="true" bounds="[15,323][85,438]"/><node text="Phone" clickable="true" bounds="[88,323][158,438]"/></node></hierarchy>')
  first=stable_icon(r,'Noctua','DRAWER');self.assertIsNotNone(first)
  target=next(n for n in r.iter('node')if n.get('text')=='Noctua')
  target.set('bounds','[88,323][158,438]');self.assertNotEqual(first,stable_icon(r,'Noctua','DRAWER'))
  target.set('clickable','false');self.assertIsNone(stable_icon(r,'Noctua','DRAWER'))
  self.assertIsNone(stable_icon(r,'Noctua','HOME'))

if __name__=='__main__':unittest.main(verbosity=2)
