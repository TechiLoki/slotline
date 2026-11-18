export function escape(value) { return String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])); }
export function dayInZone(date, zone) { const p = new Intl.DateTimeFormat('en-CA', { timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(date); return `${p.find(x => x.type === 'year').value}-${p.find(x => x.type === 'month').value}-${p.find(x => x.type === 'day').value}`; }
export function addDays(day, count) { const d = new Date(day + 'T12:00:00Z'); d.setUTCDate(d.getUTCDate() + count); return d.toISOString().slice(0, 10); }
export function monthDays(day) { const d = new Date(day.slice(0, 7) + '-01T12:00:00Z'); const offset = (d.getUTCDay() + 6) % 7; const out = Array(offset).fill(null); while (d.toISOString().slice(0, 7) === day.slice(0, 7)) {
    out.push(d.toISOString().slice(0, 10));
    d.setUTCDate(d.getUTCDate() + 1);
} return out; }
export function time(value, zone) { return new Intl.DateTimeFormat('en-GB', { timeZone: zone, hour: '2-digit', minute: '2-digit', timeZoneName: 'shortOffset' }).format(new Date(value)); }
export function dateTime(value, zone) { return new Intl.DateTimeFormat('en-GB', { timeZone: zone, weekday: 'short', day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit', timeZoneName: 'shortOffset' }).format(new Date(value)); }
export function validZone(zone) { try {
    new Intl.DateTimeFormat('en', { timeZone: zone }).format();
    return true;
}
catch {
    return false;
} }
