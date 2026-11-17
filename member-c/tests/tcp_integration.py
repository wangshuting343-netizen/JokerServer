import socket,struct,time,sqlite3
I=lambda n:struct.pack('>i',n)
def exact(s,n):
 b=b''
 while len(b)<n:
  d=s.recv(n-len(b))
  if not d:raise EOFError()
  b+=d
 return b
def num(s):return struct.unpack('>i',exact(s,4))[0]
def string(s):return exact(s,num(s)).decode()
def send(s,t,text='',extra=b''):
 b=text.encode();s.sendall(I(t)+I(len(b))+b+extra)
def receive(s):
 t=num(s)
 if t==3:
  w,h=num(s),num(s);return t,([num(s) for _ in range(w*h)],exact(s,1))
 if t==4:return t,(string(s),num(s))
 if t==21:
  server=string(s);seq=exact(s,8);n=num(s);return t,[(string(s),string(s),num(s),num(s),string(s)) for _ in range(n)]
 if t==22:
  game,state,winner=string(s),string(s),string(s);n=num(s);return t,(game,state,winner,[(string(s),num(s)) for _ in range(n)])
 if t==24:return t,string(s)
 raise AssertionError(t)
s=socket.create_connection(('127.0.0.1',15446),timeout=6);s.settimeout(6);receive(s)
# Split Unicode NAME across TCP writes, then consecutive requests.
b="O'Brien 中文".encode();s.sendall(I(0)+I(len(b))+b[:2]);time.sleep(.05);s.sendall(b[2:]);assert receive(s)[1][0]=="O'Brien 中文"
send(s,5)
board=[13,13]+[0]*14
send(s,7,extra=b''.join(I(v) for v in board));assert receive(s)[0]==3;receive(s)
send(s,1,'LEFT')
seen=[]
while True:
 t,data=receive(s);seen.append((t,data))
 if t==22 and data[1]=='SAVED':break
assert any(t==22 and d[1]=='PENDING' for t,d in seen)
game=data[0]
send(s,25,game)
while True:
 t,data=receive(s)
 if t==22:
  assert data[0]==game and data[1]=='SAVED';break
send(s,8)
while True:
 t,data=receive(s)
 if t==21:break
assert data[0][1]=="O'Brien 中文"
other=socket.create_connection(('127.0.0.1',15446),timeout=6);other.settimeout(6);receive(other)
send(other,25,game)
assert receive(other)[0]==24, 'result must reject an unrelated session'
other.close()
s.close()
print('PASS TCP: fragmented Unicode NAME, start, upload, Joker end, PENDING/SAVED, server history')
print(seen)
