import { escape as e, dayInZone, addDays, monthDays, time, dateTime, validZone } from './format.js';
const app = document.querySelector('#app');
let providers = [], services = [], zones = [], providerId = '', serviceId = '';
let zone = Intl.DateTimeFormat().resolvedOptions().timeZone, day = dayInZone(new Date(), zone), month = day, selected = '', slots = [], loading = false, busy = false;
let booking = null, token = '', mode = 'public', admin = false, settings = null, calendar = [], blocks = [], rescheduling = false;
let draft = { guest_name: '', guest_email: '', note: '' }, requestId = crypto.randomUUID(), slotSequence = 0;
const provider = () => providers.find(p => p.id === providerId);
const service = () => services.find(s => s.id === serviceId);
async function api(path, method = 'GET', body) { const headers = { 'X-Slotline-Request': '1' }; if (body !== undefined)
    headers['Content-Type'] = 'application/json'; if (mode === 'manage')
    headers['X-Booking-Token'] = token; const response = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) }); const result = await response.json(); if (!response.ok)
    throw new Error(result.error || 'Request failed. Please retry.'); return result; }
function toast(message) { const box = document.querySelector('#toast'); box.textContent = message; box.hidden = false; setTimeout(() => box.hidden = true, 6000); }
function zoneField() { return `<label>Your time zone<input id="zone" list="zones" value="${e(zone)}" required><datalist id="zones">${zones.map(z => `<option value="${e(z)}">`).join('')}</datalist></label>`; }
function resetRequest() { requestId = crypto.randomUUID(); }
function heading(kicker, title, subtitle) { return `<section class="intro"><p class="eyebrow">${kicker}</p><h1>${title}</h1><p>${subtitle}</p></section>`; }
function render() {
    if (mode === 'admin') {
        renderAdmin();
        return;
    }
    if (mode === 'manage' && !rescheduling) {
        renderManagement();
        return;
    }
    const p = provider(), s = service();
    if (!p || !s)
        return;
    app.innerHTML = heading(rescheduling ? 'A LITTLE CHANGE OF PLANS' : 'A LITTLE TIME. A BIG NEXT STEP.', rescheduling ? 'Find a new time.' : 'Make room for your next idea.', rescheduling ? 'Your current appointment stays reserved until the new time is confirmed.' : 'Choose a conversation, find your person, and pick a time that works.') + `
<div class="booking-layout"><aside class="panel"><span class="step">01 / THE CONVERSATION</span><h2>What’s on your mind?</h2><div class="services">${services.map(item => `<button class="service ${s.id === item.id ? 'active' : ''}" data-service="${item.id}" ${rescheduling ? 'disabled' : ''}><span class="service-icon">${item.duration_minutes === 60 ? '◈' : item.duration_minutes === 30 ? '◷' : '↗'}</span><span><b>${e(item.name)}</b><small>${item.duration_minutes} min · ${item.buffer_minutes} min break after</small></span><span class="radio"></span></button>`).join('')}</div><p class="muted">${e(s.description)}</p><hr><span class="step">02 / YOUR PERSON</span><div class="people">${providers.filter(person => person.service_ids.includes(s.id)).map(person => `<button class="person ${p.id === person.id ? 'active' : ''}" data-provider="${person.id}" ${rescheduling ? 'disabled' : ''}><span class="avatar">${person.name.split(' ').map(n => n[0]).join('')}</span><span><b>${e(person.name)}</b><small>${e(person.role)}</small></span><span class="radio"></span></button>`).join('')}</div><div class="note"><b>Space to focus.</b><p>${e(p.description)}</p></div></aside>
<section class="panel schedule"><span class="step">03 / A TIME THAT FITS</span><div class="section-heading"><h2>Your calendar, your call.</h2><span class="pill">${s.duration_minutes} minute session</span></div><p class="muted">Calendar dates follow ${e(p.name.split(' ')[0])}’s time zone: <b>${e(p.zone)}</b>. Available times below use yours.</p><div class="picker"><div><div class="month-heading"><button id="prev-month" class="quiet" aria-label="Previous month">←</button><b>${new Intl.DateTimeFormat('en', { month: 'long', year: 'numeric', timeZone: 'UTC' }).format(new Date(month.slice(0, 7) + '-01T12:00:00Z'))}</b><button id="next-month" class="quiet" aria-label="Next month">→</button></div><div class="month-grid">${['M', 'T', 'W', 'T', 'F', 'S', 'S'].map(d => `<small>${d}</small>`).join('')}${monthDays(month).map(d => d ? `<button data-day="${d}" class="day ${d === day ? 'active' : ''}" ${d < dayInZone(new Date(), p.zone) || d > addDays(dayInZone(new Date(), p.zone), 90) ? 'disabled' : ''} aria-label="${d}" aria-pressed="${d === day}">${Number(d.slice(-2))}</button>` : '<span></span>').join('')}</div></div><div class="time-picker"><b>${e(new Intl.DateTimeFormat('en', { weekday: 'long', day: 'numeric', month: 'short', timeZone: 'UTC' }).format(new Date(day + 'T12:00:00Z')))}</b><div class="slots">${loading ? '<p class="muted">Checking live availability…</p>' : slots.length ? slots.map(slot => `<button data-slot="${slot.start}" class="slot ${selected === slot.start ? 'active' : ''}" title="${e(dateTime(slot.start, zone))}">${e(time(slot.start, zone))}<small>${dayInZone(new Date(slot.start), zone) !== day ? e(dayInZone(new Date(slot.start), zone)) : ''}</small></button>`).join('') : '<p class="empty">No available times on this day.<br>Try another date.</p>'}</div></div></div>${zoneField()}<hr><form id="booking-form"><div class="section-heading"><h2>${rescheduling ? 'Ready to move?' : 'Let’s make it yours.'}</h2><span class="step">${rescheduling ? '' : '04 / YOUR DETAILS'}</span></div>${rescheduling ? '' : `<div class="two"><label>Your name<input name="guest_name" required maxlength="80" autocomplete="name" value="${e(draft.guest_name)}"></label><label>Email address<input name="guest_email" type="email" required maxlength="254" autocomplete="email" value="${e(draft.guest_email)}"></label></div><label>Anything we should know? <span class="muted">Optional</span><textarea name="note" maxlength="500" rows="2" placeholder="A little context makes a better conversation.">${e(draft.note)}</textarea></label>`}<div class="summary"><div><b>${selected ? e(dateTime(selected, zone)) : 'Select a time above'}</b><small>${e(s.name)} with ${e(p.name)}</small></div><button ${!selected || busy ? 'disabled' : ''}>${busy ? 'Confirming…' : rescheduling ? 'Confirm new time →' : 'Confirm appointment →'}</button></div><p class="fine">Changes and cancellations are available until ${s.cancel_notice_minutes} minutes before the appointment. Save your private management link after booking. No email is sent.</p>${rescheduling ? '<button id="back-manage" type="button" class="quiet">Keep current appointment</button>' : ''}</form></section></div>`;
    app.querySelectorAll('[data-service]').forEach(b => b.onclick = () => { serviceId = b.dataset.service; if (!provider().service_ids.includes(serviceId))
        providerId = providers.find(p => p.service_ids.includes(serviceId)).id; selected = ''; resetRequest(); void loadSlots(); });
    app.querySelectorAll('[data-provider]').forEach(b => b.onclick = () => { providerId = b.dataset.provider; selected = ''; day = dayInZone(new Date(), provider().zone); month = day; resetRequest(); void loadSlots(); });
    app.querySelectorAll('[data-day]').forEach(b => b.onclick = () => { day = b.dataset.day; selected = ''; resetRequest(); void loadSlots(); });
    app.querySelectorAll('[data-slot]').forEach(b => b.onclick = () => { selected = b.dataset.slot; resetRequest(); render(); });
    for (const [id, delta] of [['prev-month', -1], ['next-month', 1]])
        app.querySelector('#' + id).onclick = () => { const d = new Date(month.slice(0, 7) + '-01T12:00:00Z'); d.setUTCMonth(d.getUTCMonth() + delta); month = d.toISOString().slice(0, 10); render(); };
    app.querySelector('#zone').onchange = event => { const input = event.target; if (!validZone(input.value) || !zones.includes(input.value)) {
        toast('Choose a supported IANA time zone.');
        input.value = zone;
        return;
    } zone = input.value; resetRequest(); render(); };
    const form = app.querySelector('#booking-form');
    form.oninput = () => { const data = new FormData(form); if (!rescheduling)
        draft = { guest_name: String(data.get('guest_name')), guest_email: String(data.get('guest_email')), note: String(data.get('note')) }; resetRequest(); };
    form.onsubmit = event => { event.preventDefault(); void submit(); };
    const back = app.querySelector('#back-manage');
    if (back)
        back.onclick = () => { rescheduling = false; render(); };
    if (busy)
        app.querySelectorAll('input,button,textarea').forEach(x => x.disabled = true);
}
async function loadSlots() { const sequence = ++slotSequence; loading = true; render(); try {
    const base = rescheduling ? `/api/${admin ? 'admin' : 'public'}/bookings/${booking.id}/slots?date=${day}` : `/api/public/slots?provider_id=${providerId}&service_id=${serviceId}&date=${day}`;
    const data = await api(base);
    if (sequence === slotSequence)
        slots = data.slots;
}
catch (error) {
    if (sequence === slotSequence) {
        slots = [];
        toast(error.message);
    }
}
finally {
    if (sequence === slotSequence) {
        loading = false;
        render();
    }
} }
async function submit() { if (busy || !selected)
    return; busy = true; render(); try {
    const data = rescheduling ? await api(`/api/${admin ? 'admin' : 'public'}/bookings/${booking.id}/reschedule`, 'POST', { request_id: requestId, starts_at: selected, guest_zone: zone }) : await api('/api/public/bookings', 'POST', { request_id: requestId, provider_id: providerId, service_id: serviceId, starts_at: selected, guest_zone: zone, ...draft });
    booking = data;
    if (data.manage_token) {
        token = data.manage_token;
        history.replaceState(null, '', `#manage=${data.id}.${token}`);
    }
    mode = admin ? 'admin' : 'manage';
    rescheduling = false;
    toast('Appointment confirmed.');
    if (admin)
        await loadAdmin();
}
catch (error) {
    toast(error.message);
}
finally {
    busy = false;
    render();
} }
function bookingPath() { return `/api/${admin ? 'admin' : 'public'}/bookings/${booking.id}`; }
function renderManagement() { if (!booking) {
    app.innerHTML = heading('YOUR APPOINTMENT', 'Opening your appointment…', 'Your private link gives access to your booking.');
    return;
} const b = booking; app.innerHTML = heading('YOUR TIME IS RESERVED', b.status === 'confirmed' ? 'You’re on the calendar.' : `Appointment ${e(b.status.replace('_', ' '))}.`, 'Keep this private link to view or change your appointment.') + `<section class="panel management"><div class="section-heading"><span class="pill ${b.status}">${e(b.status.replace('_', ' '))}</span><span class="muted">${e(b.reference)}</span></div><h2>${e(b.service_name)}</h2><p class="big-time">${e(dateTime(b.starts_at, zone))}</p><p>With <b>${e(b.provider_name)}</b> · ${e(b.guest_name)}</p><p class="muted">${e(b.guest_email)}</p>${zoneField()}<hr><div class="actions"><button id="ics" class="outline">↓ Add to calendar</button>${b.can_reschedule ? '<button id="move">Find another time →</button>' : ''}</div><label class="private-link">Your private management link<input readonly value="${e(location.href)}" aria-label="Private management link"></label><button id="copy" class="quiet">Copy private link</button><p class="fine">Anyone with this link can access this appointment. No email confirmation has been sent. Self-service changes close ${b.cancel_notice_minutes} minutes before the appointment.</p>${b.can_cancel ? '<details><summary>Cancel this appointment</summary><form id="cancel-form"><label>Reason (optional)<input name="reason" maxlength="200"></label><button class="danger">Confirm cancellation</button></form></details>' : ''}<div class="history"><h3>Appointment history</h3>${b.events.map(event => `<p><b>${e(event.action.replace('_', ' '))}</b><small>${e(dateTime(event.created_at, zone))}</small></p>`).join('')}</div></section>`; wireBooking(); app.querySelector('#zone').onchange = event => { const v = event.target.value; if (validZone(v) && zones.includes(v)) {
    zone = v;
    render();
}
else
    toast('Choose a supported time zone.'); }; app.querySelector('#copy').onclick = () => { void navigator.clipboard.writeText(location.href).then(() => toast('Private link copied.')).catch(() => toast('Select and copy the link above.')); }; }
function wireBooking() { const move = app.querySelector('#move'); if (move)
    move.onclick = () => { providerId = booking.provider_id; serviceId = booking.service_id; day = dayInZone(new Date(booking.starts_at), booking.provider_zone); month = day; selected = ''; rescheduling = true; resetRequest(); if (admin) {
        mode = 'public';
    } void loadSlots(); }; const cancel = app.querySelector('#cancel-form'); if (cancel)
    cancel.onsubmit = event => { event.preventDefault(); void action('/cancel', { reason: String(new FormData(cancel).get('reason') || '') }); }; const ics = app.querySelector('#ics'); if (ics)
    ics.onclick = () => { void downloadCalendar(); }; app.querySelectorAll('[data-status]').forEach(button => button.onclick = () => { void action('/status', { status: button.dataset.status }); }); }
async function action(path, body) { if (busy)
    return; busy = true; try {
    booking = await api(bookingPath() + path, 'POST', body);
    toast('Appointment updated.');
    if (admin)
        await loadAdmin();
    else
        render();
}
catch (error) {
    toast(error.message);
}
finally {
    busy = false;
} }
async function downloadCalendar() { try {
    const headers = {};
    if (!admin)
        headers['X-Booking-Token'] = token;
    const response = await fetch(bookingPath() + '/calendar.ics', { headers });
    if (!response.ok)
        throw new Error('Could not download calendar.');
    const url = URL.createObjectURL(await response.blob());
    const a = document.createElement('a');
    a.href = url;
    a.download = `${booking.reference}.ics`;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
}
catch (error) {
    toast(error.message);
} }
async function loadAdmin() { const from = new Date(day + 'T00:00:00Z').toISOString(), to = new Date(addDays(day, 7) + 'T00:00:00Z').toISOString(); const [data, config] = await Promise.all([api(`/api/admin/calendar?from=${from}&to=${to}&provider_id=${providerId}`), api(`/api/admin/providers/${providerId}`)]); calendar = data.bookings; blocks = data.blocks; settings = config; mode = 'admin'; render(); }
function renderAdmin() {
    const p = provider();
    if (!settings || !p) {
        app.innerHTML = '<p>Loading workspace…</p>';
        return;
    }
    app.innerHTML = heading('THE STAFF WORKSPACE', 'A little order to your week.', 'Manage appointments and protect time for focused work.') + `<div class="toolbar"><label>Team member<select id="staff-provider">${providers.map(p => `<option value="${p.id}" ${p.id === providerId ? 'selected' : ''}>${e(p.name)} · ${e(p.zone)}</option>`).join('')}</select></label><label>Week starting (UTC)<input id="staff-day" type="date" value="${day}"></label><button id="refresh" class="outline">Refresh</button><button id="logout" class="quiet">Sign out</button></div><div class="stats"><div><b>${calendar.filter(b => b.status === 'confirmed').length}</b><span>confirmed this week</span></div><div><b>${calendar.filter(b => b.status === 'completed').length}</b><span>completed sessions</span></div><div><b>${blocks.length}</b><span>protected blocks</span></div></div><div class="admin-layout"><section class="panel"><div class="section-heading"><h2>Your appointments</h2><span class="pill">7 days · UTC range</span></div><p class="fine">Appointment times use ${e(p.zone)}. The selected seven-day range begins at midnight UTC.</p>${calendar.length ? calendar.map(b => `<button class="appointment" data-booking="${b.id}"><span class="appointment-time">${e(dateTime(b.starts_at, p.zone))}</span><span><b>${e(b.guest_name)}</b><small>${e(b.service_name)}</small></span><span class="pill ${b.status}">${e(b.status.replace('_', ' '))}</span></button>`).join('') : '<div class="empty">An open week.<br>Appointments appear here when guests book.</div>'}${booking ? `<div class="booking-detail"><div class="section-heading"><h3>${e(booking.reference)}</h3><button id="close-detail" class="quiet">Close</button></div><b>${e(booking.guest_name)}</b><p>${e(booking.guest_email)}</p><p>${e(dateTime(booking.starts_at, booking.provider_zone))}</p><p>${e(booking.note || 'No notes supplied.')}</p><div class="actions"><button id="ics" class="outline">↓ Calendar</button>${booking.status === 'confirmed' ? `<button id="move">Reschedule</button><button data-status="completed" class="outline" ${new Date(booking.ends_at) > new Date() ? 'disabled' : ''}>Completed</button><button data-status="no_show" class="outline" ${new Date(booking.ends_at) > new Date() ? 'disabled' : ''}>No-show</button>` : ''}</div>${booking.status === 'confirmed' ? '<form id="cancel-form"><label>Cancellation reason<input name="reason" maxlength="200"></label><button class="danger">Cancel appointment</button></form>' : ''}<h3>History</h3>${booking.events.map(event => `<p class="fine">${e(event.action)} · ${e(dateTime(event.created_at, p.zone))}</p>`).join('')}</div>` : ''}</section><aside class="panel"><h2>Working hours</h2><p class="fine">All hours below use ${e(p.zone)}. Updating availability preserves existing appointments.</p><form id="hours-form">${['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'].map((name, i) => { const h = settings.hours.find(x => x.dow === i + 1); return `<div class="hours-row"><label><input type="checkbox" name="day${i + 1}" ${h ? 'checked' : ''}>${name.slice(0, 3)}</label><input type="time" step="900" name="open${i + 1}" value="${h?.opens.slice(0, 5) || '09:00'}" aria-label="${name} opens"><input type="time" step="900" name="close${i + 1}" value="${h?.closes.slice(0, 5) || '17:00'}" aria-label="${name} closes"></div>`; }).join('')}<button class="outline">Save weekly hours</button></form><hr><h3>Date exceptions</h3><form id="override-form"><label>Date<input type="date" name="date" required value="${day}"></label><label class="check"><input type="checkbox" name="closed" checked>Closed all day</label><div class="two"><label>Opens<input type="time" step="900" name="opens" value="09:00" required></label><label>Closes<input type="time" step="900" name="closes" value="17:00" required></label></div><button class="outline">Save exception</button></form>${settings.overrides.map(o => `<div class="list-row"><span>${o.day} · ${o.closed ? 'Closed' : e(o.opens.slice(0, 5) + '–' + o.closes.slice(0, 5))}</span><button class="quiet" data-remove-override="${o.day}" aria-label="Remove exception ${o.day}">×</button></div>`).join('')}<hr><h3>Protect time</h3><form id="block-form"><label>Date<input name="date" type="date" required value="${day}"></label><div class="two"><label>From<input name="opens" type="time" step="900" required value="12:00"></label><label>Until<input name="closes" type="time" step="900" required value="13:00"></label></div><label>Private label<input name="label" maxlength="80" required placeholder="Lunch, focus time, out of office…"></label><button class="outline">Add time block</button></form>${settings.blocks.map(b => `<div class="list-row"><span><b>${e(b.label)}</b><small>${e(dateTime(b.starts_at, p.zone))}–${e(time(b.ends_at, p.zone))}</small></span><button class="quiet" data-remove-block="${b.id}" aria-label="Remove time block">×</button></div>`).join('')}</aside></div>`;
    app.querySelector('#staff-provider').onchange = event => { providerId = event.target.value; booking = null; void guarded(loadAdmin); };
    app.querySelector('#staff-day').onchange = event => { day = event.target.value; void guarded(loadAdmin); };
    app.querySelector('#refresh').onclick = () => void guarded(loadAdmin);
    app.querySelector('#logout').onclick = () => void guarded(async () => { await api('/api/admin/logout', 'POST', {}); admin = false; mode = 'public'; booking = null; void loadSlots(); });
    app.querySelectorAll('[data-booking]').forEach(button => button.onclick = () => void guarded(async () => { booking = await api(`/api/admin/bookings/${button.dataset.booking}`); render(); }));
    const close = app.querySelector('#close-detail');
    if (close)
        close.onclick = () => { booking = null; render(); };
    wireBooking();
    const hours = app.querySelector('#hours-form');
    hours.onsubmit = event => { event.preventDefault(); const f = new FormData(hours); void saveSettings('/hours', 'PUT', { hours: Array.from({ length: 7 }, (_, i) => i + 1).filter(i => f.has('day' + i)).map(i => ({ dow: i, opens: f.get('open' + i), closes: f.get('close' + i) })) }); };
    for (const [formId, path] of [['override-form', '/overrides'], ['block-form', '/blocks']]) {
        const form = app.querySelector('#' + formId);
        form.onsubmit = event => { event.preventDefault(); const f = new FormData(form); const data = {}; f.forEach((v, k) => data[k] = v); if (path === '/overrides')
            data.closed = f.has('closed'); void saveSettings(path, 'POST', data); };
    }
    app.querySelectorAll('[data-remove-override]').forEach(button => button.onclick = () => void saveSettings('/overrides?date=' + button.dataset.removeOverride, 'DELETE'));
    app.querySelectorAll('[data-remove-block]').forEach(button => button.onclick = () => void guarded(async () => { await api('/api/admin/blocks/' + button.dataset.removeBlock, 'DELETE'); await loadAdmin(); }));
}
async function guarded(work) { try {
    await work();
}
catch (error) {
    toast(error.message);
} }
async function saveSettings(path, method, body) { await guarded(async () => { await api(`/api/admin/providers/${providerId}` + path, method, body); await loadAdmin(); toast('Availability updated.'); }); }
async function openHash() { const match = location.hash.match(/^#manage=([0-9a-f-]{36})\.([A-Za-z0-9_-]+)$/i); if (!match)
    return false; mode = 'manage'; admin = false; rescheduling = false; booking = null; token = match[2]; render(); try {
    booking = await api('/api/public/bookings/' + match[1]);
    zone = booking.guest_zone;
    render();
}
catch (error) {
    app.innerHTML = heading('PRIVATE APPOINTMENT', 'This link could not be opened.', e(error.message));
} return true; }
document.querySelector('#public-nav').onclick = () => { history.replaceState(null, '', '/'); mode = 'public'; admin = false; booking = null; rescheduling = false; selected = ''; day = dayInZone(new Date(), provider().zone); month = day; void loadSlots(); };
document.querySelector('#admin-nav').onclick = () => { document.querySelector('#login').showModal(); };
document.querySelector('#close-login').onclick = () => document.querySelector('#login').close();
document.querySelector('#login-form').onsubmit = event => { event.preventDefault(); const form = event.target; void guarded(async () => { await api('/api/admin/login', 'POST', { token: new FormData(form).get('token') }); form.reset(); document.querySelector('#login').close(); admin = true; mode = 'admin'; booking = null; rescheduling = false; day = dayInZone(new Date(), 'UTC'); await loadAdmin(); }); };
window.addEventListener('hashchange', () => void openHash());
void guarded(async () => { const data = await api('/api/public/catalogue'); providers = data.providers; services = data.services; zones = data.zones.filter(validZone); if (!zones.includes(zone))
    zone = 'UTC'; providerId = providers[0].id; serviceId = services.find(s => s.duration_minutes === 30).id; day = dayInZone(new Date(), provider().zone); month = day; if (!await openHash())
    await loadSlots(); });
