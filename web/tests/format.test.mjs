import {test} from 'node:test';
import assert from 'node:assert/strict';
import {escape,dayInZone,addDays,monthDays,time,validZone} from '../dist/format.js';
test('escapes hostile guest names as plain text',()=>assert.equal(escape('<img src=x onerror="x">&'), '&lt;img src=x onerror=&quot;x&quot;&gt;&amp;'));
test('provider day may differ from the visitor day',()=>{const instant=new Date('2026-11-18T01:00:00Z');assert.equal(dayInZone(instant,'America/New_York'),'2026-11-17');assert.equal(dayInZone(instant,'Asia/Tbilisi'),'2026-11-18');});
test('calendar uses Monday-first cells and real leap days',()=>{assert.equal(monthDays('2024-02-15').filter(Boolean).length,29);assert.deepEqual(monthDays('2026-11-18').slice(0,7),[null,null,null,null,null,null,'2026-11-01']);});
test('day arithmetic crosses years without local DST shifts',()=>{assert.equal(addDays('2026-12-31',1),'2027-01-01');assert.equal(addDays('2026-03-08',1),'2026-03-09');});
test('repeated wall-clock times include distinct UTC offsets',()=>assert.notEqual(time('2026-11-01T05:30:00Z','America/New_York'),time('2026-11-01T06:30:00Z','America/New_York')));
test('time zone input rejects unknown zones',()=>{assert.equal(validZone('Asia/Tbilisi'),true);assert.equal(validZone('Made/Up'),false);});
