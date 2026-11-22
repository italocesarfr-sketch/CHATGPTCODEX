"""Measured Launcher3 gesture geometry; no device, network or application state."""
import re

def bounds(node):
    value=tuple(map(int,re.findall(r'\d+',node.get('bounds',''))))
    if len(value)!=4 or value[2]<=value[0] or value[3]<=value[1]:
        raise ValueError('Invalid launcher bounds')
    return value

def drawer_gesture(root):
    hotseat=next(n for n in root.iter('node')if n.get('resource-id','').endswith(':id/hotseat'))
    workspace=next(n for n in root.iter('node')if n.get('resource-id','').endswith(':id/workspace'))
    h=bounds(hotseat);w=bounds(workspace)
    controls=[bounds(n)for n in hotseat.iter('node')if n.get('clickable')=='true']
    if not controls:raise ValueError('No measured hotseat controls')
    # The gesture must begin inside the hotseat but outside shortcut touch regions.
    left=min(b[0]for b in controls);top=min(b[1]for b in controls)
    if left-h[0]>=4:
        x=(h[0]+left)//2;y=(top+max(b[3]for b in controls))//2
    elif top-h[1]>=4:
        x=(h[0]+h[2])//2;y=(h[1]+top)//2
    else:raise ValueError('No safe empty hotseat padding')
    if any(l<=x<r and t<=y<b for l,t,r,b in controls):raise ValueError('Gesture overlaps a shortcut')
    return (x,y,x,w[1]+(w[3]-w[1])//4)
