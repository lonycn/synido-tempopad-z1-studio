import sys,time,subprocess
import Quartz as Q
import ApplicationServices as A

def click(x,y,n=1):
 for i in range(n):
  for t in [Q.kCGEventLeftMouseDown,Q.kCGEventLeftMouseUp]:
   e=Q.CGEventCreateMouseEvent(None,t,(x,y),Q.kCGMouseButtonLeft)
   Q.CGEventSetIntegerValueField(e,Q.kCGMouseEventClickState,i+1)
   Q.CGEventPost(Q.kCGHIDEventTap,e);time.sleep(.06)

def key(k,cmd=False,shift=False):
 for down in [True,False]:
  e=Q.CGEventCreateKeyboardEvent(None,k,down)
  Q.CGEventSetFlags(e,(Q.kCGEventFlagMaskCommand if cmd else 0)|(Q.kCGEventFlagMaskShift if shift else 0))
  Q.CGEventPost(Q.kCGHIDEventTap,e)
 e=Q.CGEventCreateKeyboardEvent(None,k,False);Q.CGEventSetFlags(e,0);Q.CGEventPost(Q.kCGHIDEventTap,e)
 time.sleep(.2)

def text(s):
 for down in [True,False]:
  e=Q.CGEventCreateKeyboardEvent(None,0,down);Q.CGEventSetFlags(e,0);Q.CGEventKeyboardSetUnicodeString(e,len(s),s);Q.CGEventPost(Q.kCGHIDEventTap,e)
 time.sleep(.2)

def attr(e,n):
 err,v=A.AXUIElementCopyAttributeValue(e,n,None);return v if err==0 else None

def dump(pid,depth=7):
 def walk(e,d):
  if d>depth:return
  vals=[str(attr(e,n)) for n in ['AXRole','AXTitle','AXDescription','AXValue','AXPosition','AXSize']]
  print(' '*d+' | '.join(vals))
  for c in attr(e,'AXChildren') or []:walk(c,d+1)
 walk(A.AXUIElementCreateApplication(pid),0)
if __name__=='__main__':
 if sys.argv[1]=='click':click(float(sys.argv[2]),float(sys.argv[3]),int(sys.argv[4]) if len(sys.argv)>4 else 1)
 elif sys.argv[1]=='key':key(int(sys.argv[2]),'cmd' in sys.argv,'shift' in sys.argv)
 elif sys.argv[1]=='text':text(sys.argv[2])
 elif sys.argv[1]=='dump':dump(int(sys.argv[2]),int(sys.argv[3]) if len(sys.argv)>3 else 7)
