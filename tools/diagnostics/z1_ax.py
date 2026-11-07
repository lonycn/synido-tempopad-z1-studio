from synido_ui import *
import os

def app(pid):return A.AXUIElementCreateApplication(pid)
def allnodes(e,depth=12):
 yield e
 if depth:
  for c in attr(e,'AXChildren') or []:yield from allnodes(c,depth-1)
def find(pid,title=None,role=None,desc=None):
 return next((e for e in allnodes(app(pid)) if (title is None or attr(e,'AXTitle')==title) and (role is None or attr(e,'AXRole')==role) and (desc is None or attr(e,'AXDescription')==desc)),None)
def press(pid,title,role=None):
 e=find(pid,title,role);return A.AXUIElementPerformAction(e,'AXPress') if e else 'not found'
def brief(pid):
 for w in attr(app(pid),'AXWindows') or []:
  print('WINDOW',attr(w,'AXTitle'))
  for e in allnodes(w,9):
   r=attr(e,'AXRole');t=attr(e,'AXTitle');v=attr(e,'AXValue');d=attr(e,'AXDescription')
   if r in ['AXButton','AXTextField','AXMenuButton','AXCheckBox','AXPopUpButton','AXRadioButton'] or (r=='AXStaticText' and v): print(r,t,d,v,attr(e,'AXPosition'))
def shot(path='/tmp/z1-ui.png'):
 subprocess.run(['/usr/sbin/screencapture','-x',path]);subprocess.run(['sips','-Z','1800',path],stdout=subprocess.DEVNULL)
if __name__=='__main__':
 if sys.argv[1]=='brief':brief(int(sys.argv[2]))
 elif sys.argv[1]=='press':print(press(int(sys.argv[2]),sys.argv[3]))
 elif sys.argv[1]=='shot':shot(sys.argv[2] if len(sys.argv)>2 else '/tmp/z1-ui.png')
