#!/usr/bin/env python3
"""Audita a procedência e configuração Android sem editar ou regenerar a imagem."""
from pathlib import Path
import hashlib,json,struct,xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
res=root/'app/src/main/res'
original=root/'docs/brand/noctua-original.png'
packaged=res/'drawable-nodpi/noctua_logo.png'
metadata=json.loads((root/'docs/brand/noctua-oficial.json').read_text())
raw=original.read_bytes()
assert raw==packaged.read_bytes(), 'A arte no app deve ser o próprio arquivo original.'
assert hashlib.sha256(raw).hexdigest()==metadata['pngSha256']=='ef7129c4a637fed0361645f3356c2597de2703cd524178ff7a9ba44b0ff84baa'
assert raw[:8]==b'\x89PNG\r\n\x1a\n' and struct.unpack('>II',raw[16:24])==(1254,1254)
ns='{http://schemas.android.com/apk/res/android}'
app=ET.parse(root/'app/src/main/AndroidManifest.xml').getroot().find('application')
assert app.get(ns+'icon')=='@mipmap/ic_launcher' and app.get(ns+'roundIcon')=='@mipmap/ic_launcher_round'
for name in ('ic_launcher','ic_launcher_round'):
    icon=ET.parse(res/f'mipmap-anydpi-v26/{name}.xml').getroot()
    assert icon.tag=='adaptive-icon'
    assert icon.find('foreground').get(ns+'drawable')=='@drawable/noctua_launcher_foreground'
    assert icon.find('background').get(ns+'drawable')=='@color/noctua_icon_background'
    assert ET.parse(res/f'mipmap-anydpi/{name}.xml').getroot().get(ns+'src')=='@drawable/noctua_logo'
foreground=ET.parse(res/'drawable/noctua_launcher_foreground.xml').getroot()
assert foreground.tag=='inset' and foreground.find('bitmap').get(ns+'src')=='@drawable/noctua_logo'
edges=[float(foreground.get(ns+key).rstrip('%'))/100 for key in ('insetLeft','insetTop','insetRight','insetBottom')]
assert max(edges)-min(edges)<1e-8
image_size=108*(1-2*edges[0]);assert abs(image_size-66)<1e-5
# A zona segura tem diâmetro 66 dp. Pontos extremos da coruja/N ficam dentro dela.
for x,y in ((247,170),(1006,170),(300,1064),(953,1064)):
    dx=(x-627)/1254*image_size;dy=(y-627)/1254*image_size
    assert dx*dx+dy*dy<33*33, (x,y)
diary=(root/'app/src/main/java/br/com/diariodeclasse/ui/DiaryApp.kt').read_text()
assert 'R.drawable.ic_owl' not in diary and 'R.drawable.ic_diary' not in diary
assert 'R.drawable.noctua_logo' in diary and 'ContentScale.Fit' in diary
assert not (res/'drawable/ic_owl.xml').exists() and not (res/'drawable/ic_diary.xml').exists()
print('Marca oficial: PNG original intacto; launcher padrão/adaptativo, ícone redondo e zona segura conferidos.')
