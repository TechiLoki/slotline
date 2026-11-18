import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
const base=process.env.APP_URL||'http://localhost:8092';
const admin=process.env.ADMIN_TOKEN||'slotline-local-admin';
const provider='10000000-0000-0000-0000-000000000001';
const service='20000000-0000-0000-0000-000000000001';
async function call(path,{method='GET',body,token,staff=false}={}){const headers={};if(body)headers['Content-Type']='application/json';if(token)headers['X-Booking-Token']=token;if(staff)headers.Authorization='Bearer '+admin;const r=await fetch(base+path,{method,headers,body:body?JSON.stringify(body):undefined});const text=await r.text();return {status:r.status,body:r.headers.get('content-type')?.includes('application/json')?JSON.parse(text):text};}
assert.equal((await call('/readyz')).status,200);
assert.equal((await call('/api/admin/calendar?from=2030-01-01T00:00:00Z&to=2030-01-02T00:00:00Z')).status,401);
const catalogue=await call('/api/public/catalogue');assert.equal(catalogue.status,200);assert.ok(catalogue.body.providers.length>=3);assert.ok(!JSON.stringify(catalogue.body).includes('guest_email'));
let slots=[];
for(let offset=2;offset<=10;offset++){const day=new Date(Date.now()+offset*86400000).toISOString().slice(0,10);const r=await call(`/api/public/slots?provider_id=${provider}&service_id=${service}&date=${day}`);assert.equal(r.status,200);if(r.body.slots.length>=9){slots=r.body.slots;break;}}
assert.ok(slots.length>=9,'Need at least nine future slots in seeded availability');
const body=(start,request_id=randomUUID())=>({request_id,provider_id:provider,service_id:service,starts_at:start,guest_name:'Smoke Test',guest_email:'smoke@example.test',guest_zone:'Asia/Tbilisi',note:'Synthetic integration fixture'});
const bookings=[];
try{
 const firstBody=body(slots[0].start), competingBody=body(slots[0].start);
 const attempts=await Promise.all([call('/api/public/bookings',{method:'POST',body:firstBody}),call('/api/public/bookings',{method:'POST',body:competingBody})]);
 assert.deepEqual(attempts.map(x=>x.status).sort(),[201,409]);const first=attempts.find(x=>x.status===201).body;bookings.push(first);
 // Replay whichever request won, preserving exact body and management token.
 const winningBody=attempts[0].status===201?firstBody:competingBody;
 const replay=await call('/api/public/bookings',{method:'POST',body:winningBody});assert.equal(replay.body.id,first.id);assert.equal(replay.body.manage_token,first.manage_token);
 assert.equal((await call('/api/public/bookings',{method:'POST',body:{...winningBody,guest_name:'Changed'}})).status,409);
 const path='/api/public/bookings/'+first.id;
 assert.equal((await call(path,{token:'wrong'})).status,404);
 const ics=await call(path+'/calendar.ics',{token:first.manage_token});assert.equal(ics.status,200);assert.match(ics.body,/BEGIN:VCALENDAR/);assert.ok(!ics.body.includes(first.manage_token));
 // Another appointment more than the 45-minute occupied interval away.
 const second=await call('/api/public/bookings',{method:'POST',body:body(slots[4].start)});assert.equal(second.status,201);bookings.push(second.body);
 const failed=await call(path+'/reschedule',{method:'POST',token:first.manage_token,body:{request_id:randomUUID(),starts_at:slots[4].start,guest_zone:'UTC'}});assert.equal(failed.status,409);
 assert.equal((await call(path,{token:first.manage_token})).body.starts_at,first.starts_at);
 const move={request_id:randomUUID(),starts_at:slots[slots.length-1].start,guest_zone:'UTC'};
 const moved=await call(path+'/reschedule',{method:'POST',token:first.manage_token,body:move});assert.equal(moved.status,200);assert.equal(new Date(moved.body.starts_at).getTime(),new Date(move.starts_at).getTime());
 assert.equal((await call(path+'/reschedule',{method:'POST',token:first.manage_token,body:move})).body.revision,moved.body.revision);
 const replacement=await call('/api/public/bookings',{method:'POST',body:body(first.starts_at)});assert.equal(replacement.status,201);bookings.push(replacement.body);
 const cancelled=await call(path+'/cancel',{method:'POST',token:first.manage_token,body:{reason:'Smoke complete'}});assert.equal(cancelled.body.status,'cancelled');
 console.log('PASS: HTTP concurrency, ownership, retries, atomic reschedule, slot release, ICS and admin authentication.');
}finally{for(const b of bookings){const r=await call('/api/admin/bookings/'+b.id+'/cancel',{method:'POST',staff:true,body:{reason:'Smoke test cleanup'}});assert.equal(r.status,200);}}
