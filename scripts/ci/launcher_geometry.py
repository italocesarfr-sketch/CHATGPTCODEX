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


def stable_icon(root,label,surface):
    """Only a unique clickable icon in a fully opened, internally consistent layout.
    Include every app position: package indexing can move the target between frames.
    """
    if surface=='DRAWER':
        containers=[n for n in root.iter('node')if n.get('resource-id','').endswith(':id/apps_list_view')]
        if len(containers)!=1:return None
        nodes=list(containers[0].iter('node'))
    else:nodes=list(root.iter('node'))
    icons=[n for n in nodes if n.get('clickable')=='true' and n.get('class')!='android.widget.EditText'
           and (n.get('text')or n.get('content-desc'))]
    targets=[n for n in icons if n.get('text')==label or n.get('content-desc')==label]
    if len(targets)!=1:return None
    b=bounds(targets[0])
    if b[0]<0 or b[1]<0:return None
    return tuple(sorted((n.get('text',''),n.get('content-desc',''),bounds(n))for n in icons))
