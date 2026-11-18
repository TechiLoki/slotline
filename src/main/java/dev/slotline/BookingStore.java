package dev.slotline;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.stream.*;

public final class BookingStore {
    final Database db;
    final Clock clock;
    private final String managementSecret;
    public BookingStore(Database db, Clock clock, String managementSecret) {
        this.db = db; this.clock = clock; this.managementSecret = managementSecret;
    }
    public static UUID uuid(String value) {
        try { if (!value.matches("[0-9a-fA-F-]{36}")) throw Problem.invalid("Invalid identifier."); return UUID.fromString(value); }
        catch (IllegalArgumentException e) { throw Problem.invalid("Invalid identifier."); }
    }
    private Map<String,Object> provider(Connection c, UUID id, boolean lock) throws SQLException {
        Map<String,Object> p = Sql.one(c, "SELECT * FROM slotline.providers WHERE id=?" + (lock ? " FOR UPDATE" : ""), id);
        if (p == null) throw Problem.missing();
        return p;
    }
    private SlotEngine.Service service(Connection c, UUID provider, UUID id, boolean lock) throws SQLException {
        Map<String,Object> s = Sql.one(c, "SELECT s.* FROM slotline.services s JOIN slotline.provider_services ps ON ps.service_id=s.id WHERE ps.provider_id=? AND s.id=?" + (lock ? " FOR SHARE OF s" : ""), provider, id);
        if (s == null) throw Problem.missing();
        return service(s);
    }
    private SlotEngine.Service service(Map<String,Object> s) {
        return new SlotEngine.Service(Sql.id(s,"id"), (String)s.get("name"), Sql.number(s,"duration_minutes"), Sql.number(s,"buffer_minutes"), Sql.number(s,"notice_minutes"), Sql.number(s,"horizon_days"), Sql.number(s,"cancel_notice_minutes"), (Boolean)s.get("active"));
    }
    private SlotEngine.Window hours(Connection c, UUID provider, LocalDate day) throws SQLException {
        Map<String,Object> h = Sql.one(c, "SELECT closed,opens,closes FROM slotline.date_overrides WHERE provider_id=? AND day=?", provider,day);
        if (h != null && Boolean.TRUE.equals(h.get("closed"))) return null;
        if (h == null) h = Sql.one(c, "SELECT opens,closes FROM slotline.weekly_hours WHERE provider_id=? AND dow=?", provider,day.getDayOfWeek().getValue());
        return h == null ? null : new SlotEngine.Window(LocalTime.parse((String)h.get("opens")),LocalTime.parse((String)h.get("closes")));
    }
    private List<SlotEngine.Slot> slots(Connection c, Map<String,Object> p, SlotEngine.Service s, LocalDate day, UUID exclude) throws SQLException {
        if (!Boolean.TRUE.equals(p.get("active"))) return List.of();
        ZoneId zone = SlotEngine.zone((String)p.get("zone"));
        Instant start = day.atStartOfDay(zone).toInstant(), end = day.plusDays(1).atStartOfDay(zone).toInstant();
        List<SlotEngine.Busy> busy = new ArrayList<>();
        for (Map<String,Object> b : Sql.rows(c, "SELECT starts_at,occupied_until FROM slotline.bookings WHERE provider_id=? AND status<>'cancelled' AND occupied_until>? AND starts_at<? AND (?::uuid IS NULL OR id<>?)",p.get("id"),start,end,exclude,exclude)) busy.add(new SlotEngine.Busy(Sql.instant(b,"starts_at"),Sql.instant(b,"occupied_until")));
        for (Map<String,Object> b : Sql.rows(c,"SELECT starts_at,ends_at FROM slotline.blocks WHERE provider_id=? AND ends_at>? AND starts_at<?",p.get("id"),start,end)) busy.add(new SlotEngine.Busy(Sql.instant(b,"starts_at"),Sql.instant(b,"ends_at")));
        return SlotEngine.generate(day,hours(c,Sql.id(p,"id"),day),zone,s,busy,clock.instant());
    }
    public Map<String,Object> catalogue() {
        return db.transaction(c -> Map.of("providers",Sql.rows(c,"SELECT p.id,p.name,p.role,p.zone,p.description,(SELECT jsonb_agg(ps.service_id ORDER BY ps.service_id) FROM slotline.provider_services ps WHERE ps.provider_id=p.id) AS service_ids FROM slotline.providers p WHERE p.active ORDER BY p.name"),"services",Sql.rows(c,"SELECT * FROM slotline.services WHERE active ORDER BY duration_minutes"),"zones",ZoneId.getAvailableZoneIds().stream().sorted().toList(),"now",clock.instant()));
    }
    public Map<String,Object> availability(UUID provider, UUID service, LocalDate day) {
        return db.transaction(c -> { Map<String,Object> p = provider(c,provider,false); SlotEngine.Service s = service(c,provider,service,false);
            return Map.of("date",day,"provider_zone",p.get("zone"),"slots",slots(c,p,s,day,null),"now",clock.instant()); });
    }
    private void requestLock(Connection c, UUID request) throws SQLException {
        Sql.one(c,"SELECT pg_advisory_xact_lock(hashtextextended(?,0))", "request:"+request);
    }
    private Map<String,Object> replay(Connection c, UUID key, String operation, String hash) throws SQLException {
        Map<String,Object> record = Sql.one(c,"SELECT * FROM slotline.request_keys WHERE id=?",key);
        if (record == null) return null;
        if (!operation.equals(record.get("operation")) || !hash.equals(record.get("body_hash"))) throw new Problem(409,"REQUEST_CONFLICT","Request ID already belongs to different booking details. Choose a new time/request.");
        return booking(c,Sql.id(record,"booking_id"),false);
    }
    private void remember(Connection c, UUID key, String operation, String hash, UUID id) throws SQLException {
        Sql.update(c,"INSERT INTO slotline.request_keys(id,body_hash,operation,booking_id) VALUES(?,?,?,?)",key,hash,operation,id);
    }
    private void event(Connection c, UUID id, String action, Object detail) throws SQLException {
        Sql.update(c,"INSERT INTO slotline.booking_events(booking_id,action,details) VALUES(?,?,?::jsonb)",id,action,Json.write(detail));
    }
    private Map<String,Object> booking(Connection c, UUID id, boolean lock) throws SQLException {
        Map<String,Object> b = Sql.one(c,"SELECT * FROM slotline.bookings WHERE id=?"+(lock?" FOR UPDATE":""),id);
        if (b == null) throw Problem.missing();
        return b;
    }
    private void owner(Map<String,Object> b, String token, boolean admin) {
        if (!admin && (token == null || token.length()>100 || !Crypto.equal((String)b.get("manage_hash"),Crypto.hash(token)))) throw Problem.missing();
    }
    private boolean canChange(Map<String,Object> b) {
        return "confirmed".equals(b.get("status")) && clock.instant().isBefore(Sql.instant(b,"starts_at").minusSeconds(Sql.number(b,"cancel_notice_minutes")*60L));
    }
    private Map<String,Object> view(Connection c, Map<String,Object> b) throws SQLException {
        Map<String,Object> result = new LinkedHashMap<>(b);
        result.remove("manage_hash");
        result.put("can_cancel",canChange(b)); result.put("can_reschedule",canChange(b));
        result.put("events",Sql.rows(c,"SELECT action,details,created_at FROM slotline.booking_events WHERE booking_id=? ORDER BY id",b.get("id")));
        return result;
    }
    public Map<String,Object> create(JsonNode input) {
        UUID provider = uuid(Json.text(input,"provider_id",36,true)), service = uuid(Json.text(input,"service_id",36,true));
        UUID key = uuid(Json.text(input,"request_id",36,true));
        Instant start = SlotEngine.instant(Json.text(input,"starts_at",60,true));
        String name = Json.text(input,"guest_name",80,true).replaceAll("\\s+"," ");
        String email = Json.text(input,"guest_email",254,true).toLowerCase(Locale.ROOT);
        if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw Problem.invalid("Enter a valid email address.");
        String zone = SlotEngine.zone(Json.text(input,"guest_zone",80,true)).getId();
        String note = Json.text(input,"note",500,false);
        String hash = Crypto.hash(Json.write(List.of(provider,service,start.toString(),name,email,zone,note)));
        return db.transaction(c -> {
            requestLock(c,key);
            Map<String,Object> old = replay(c,key,"create",hash);
            if (old != null) { Map<String,Object> result=view(c,old); result.put("manage_token",Crypto.managementToken(managementSecret,Sql.id(old,"id")));result.put("replayed",true);return result; }
            Map<String,Object> p = provider(c,provider,true); SlotEngine.Service s = service(c,provider,service,true);
            LocalDate day = start.atZone(SlotEngine.zone((String)p.get("zone"))).toLocalDate();
            if (slots(c,p,s,day,null).stream().noneMatch(slot->slot.start().equals(start))) throw Problem.conflict();
            UUID id = UUID.randomUUID();
            String reference = "SL-"+id.toString().replace("-","").substring(0,12).toUpperCase(Locale.ROOT);
            String token = Crypto.managementToken(managementSecret,id);
            Sql.update(c,"INSERT INTO slotline.bookings(id,reference,provider_id,service_id,provider_name,provider_zone,service_name,duration_minutes,buffer_minutes,cancel_notice_minutes,starts_at,ends_at,occupied_until,guest_name,guest_email,guest_zone,note,manage_hash) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id,reference,provider,service,p.get("name"),p.get("zone"),s.name(),s.duration(),s.buffer(),s.cancelNotice(),start,start.plusSeconds(s.duration()*60L),start.plusSeconds((s.duration()+s.buffer())*60L),name,email,zone,note,Crypto.hash(token));
            remember(c,key,"create",hash,id);event(c,id,"created",Map.of("starts_at",start,"actor","guest"));
            Map<String,Object> result=view(c,booking(c,id,false));result.put("manage_token",token);return result;
        });
    }
    public Map<String,Object> manage(UUID id, String token, boolean admin) {
        return db.transaction(c->{Map<String,Object>b=booking(c,id,false);owner(b,token,admin);return view(c,b);});
    }
    public Map<String,Object> managedAvailability(UUID id, String token, boolean admin, LocalDate day) {
        return db.transaction(c->{Map<String,Object>b=booking(c,id,false);owner(b,token,admin);if(!admin&&!canChange(b))throw Problem.invalid("The self-service change window has closed.");
            Map<String,Object>p=provider(c,Sql.id(b,"provider_id"),false);SlotEngine.Service current=service(c,Sql.id(b,"provider_id"),Sql.id(b,"service_id"),false);
            SlotEngine.Service snapshot=new SlotEngine.Service(current.id(),(String)b.get("service_name"),Sql.number(b,"duration_minutes"),Sql.number(b,"buffer_minutes"),current.notice(),current.horizon(),Sql.number(b,"cancel_notice_minutes"),current.active());
            return Map.of("date",day,"provider_zone",p.get("zone"),"slots",slots(c,p,snapshot,day,id),"now",clock.instant());
        });
    }
    public Map<String,Object> cancel(UUID id, String token, boolean admin, String reason) {
        return db.transaction(c->{Map<String,Object> initial=booking(c,id,false);owner(initial,token,admin);provider(c,Sql.id(initial,"provider_id"),true);Map<String,Object>b=booking(c,id,true);
            if ("cancelled".equals(b.get("status"))) return view(c,b);
            if (!"confirmed".equals(b.get("status"))) throw Problem.invalid("Only confirmed appointments can be cancelled.");
            if (!admin&&!canChange(b)) throw Problem.invalid("The self-service cancellation window has closed.");
            Sql.update(c,"UPDATE slotline.bookings SET status='cancelled',revision=revision+1,updated_at=now() WHERE id=?",id);
            event(c,id,"cancelled",Map.of("actor",admin?"staff":"guest","reason",reason));return view(c,booking(c,id,false));});
    }
    public Map<String,Object> reschedule(UUID id, String token, boolean admin, JsonNode input) {
        UUID key=uuid(Json.text(input,"request_id",36,true));Instant start=SlotEngine.instant(Json.text(input,"starts_at",60,true));
        String guestZone=SlotEngine.zone(Json.text(input,"guest_zone",80,true)).getId();String hash=Crypto.hash(Json.write(List.of(id,start.toString(),guestZone)));
        return db.transaction(c->{Map<String,Object>initial=booking(c,id,false);owner(initial,token,admin);requestLock(c,key);Map<String,Object>old=replay(c,key,"reschedule",hash);if(old!=null)return view(c,old);
            Map<String,Object>p=provider(c,Sql.id(initial,"provider_id"),true);Map<String,Object>b=booking(c,id,true);
            if(!"confirmed".equals(b.get("status"))||!admin&&!canChange(b))throw Problem.invalid("This booking is no longer eligible for rescheduling.");
            SlotEngine.Service current=service(c,Sql.id(b,"provider_id"),Sql.id(b,"service_id"),true);
            SlotEngine.Service snapshot=new SlotEngine.Service(current.id(),(String)b.get("service_name"),Sql.number(b,"duration_minutes"),Sql.number(b,"buffer_minutes"),current.notice(),current.horizon(),Sql.number(b,"cancel_notice_minutes"),current.active());
            LocalDate day=start.atZone(SlotEngine.zone((String)p.get("zone"))).toLocalDate();
            if(slots(c,p,snapshot,day,id).stream().noneMatch(slot->slot.start().equals(start)))throw Problem.conflict();
            Sql.update(c,"UPDATE slotline.bookings SET starts_at=?,ends_at=?,occupied_until=?,guest_zone=?,revision=revision+1,updated_at=now() WHERE id=?",start,start.plusSeconds(snapshot.duration()*60L),start.plusSeconds((snapshot.duration()+snapshot.buffer())*60L),guestZone,id);
            remember(c,key,"reschedule",hash,id);event(c,id,"rescheduled",Map.of("from",b.get("starts_at"),"to",start,"actor",admin?"staff":"guest"));return view(c,booking(c,id,false));});
    }
    public Map<String,Object> status(UUID id, String status) {
        if(!Set.of("completed","no_show").contains(status))throw Problem.invalid("Choose completed or no_show.");
        return db.transaction(c->{Map<String,Object>initial=booking(c,id,false);provider(c,Sql.id(initial,"provider_id"),true);Map<String,Object>b=booking(c,id,true);
            if(status.equals(b.get("status")))return view(c,b);
            if(!"confirmed".equals(b.get("status"))||clock.instant().isBefore(Sql.instant(b,"ends_at")))throw Problem.invalid("Only a finished confirmed appointment can be marked completed or no-show.");
            Sql.update(c,"UPDATE slotline.bookings SET status=?,revision=revision+1,updated_at=now() WHERE id=?",status,id);event(c,id,status,Map.of("actor","staff"));return view(c,booking(c,id,false));});
    }
    public Map<String,Object> calendar(Instant from, Instant to, UUID provider) {
        if(!from.isBefore(to)||Duration.between(from,to).compareTo(Duration.ofDays(32))>0)throw Problem.invalid("Choose a calendar range up to 32 days.");
        return db.transaction(c->Map.of("bookings",Sql.rows(c,"SELECT id,reference,provider_id,provider_name,provider_zone,service_id,service_name,starts_at,ends_at,occupied_until,guest_name,guest_email,guest_zone,status FROM slotline.bookings WHERE starts_at<? AND ends_at>? AND (?::uuid IS NULL OR provider_id=?) ORDER BY starts_at",to,from,provider,provider),"blocks",Sql.rows(c,"SELECT * FROM slotline.blocks WHERE starts_at<? AND ends_at>? AND (?::uuid IS NULL OR provider_id=?) ORDER BY starts_at",to,from,provider,provider),"now",clock.instant()));
    }
    public Map<String,Object> settings(UUID provider) {
        return db.transaction(c->Map.of("provider",provider(c,provider,false),"hours",Sql.rows(c,"SELECT dow,opens,closes FROM slotline.weekly_hours WHERE provider_id=? ORDER BY dow",provider),"overrides",Sql.rows(c,"SELECT * FROM slotline.date_overrides WHERE provider_id=? AND day>=? ORDER BY day LIMIT 100",provider,clock.instant().atZone(ZoneOffset.UTC).toLocalDate().minusDays(1)),"blocks",Sql.rows(c,"SELECT * FROM slotline.blocks WHERE provider_id=? AND ends_at>? ORDER BY starts_at LIMIT 100",provider,clock.instant())));
    }
    public Map<String,Object> saveHours(UUID id, JsonNode input) {
        JsonNode hours=input.path("hours");if(!hours.isArray()||hours.size()>7)throw Problem.invalid("Provide up to seven weekday schedules.");
        Map<Integer,SlotEngine.Window> windows=new TreeMap<>();
        for(JsonNode h:hours){int dow=Json.integer(h,"dow",1,7);SlotEngine.Window w=new SlotEngine.Window(SlotEngine.time(Json.text(h,"opens",5,true)),SlotEngine.time(Json.text(h,"closes",5,true)));if(!w.opens().isBefore(w.closes())||windows.put(dow,w)!=null)throw Problem.invalid("Schedules must be unique days with opening before closing.");}
        db.transaction(c->{provider(c,id,true);Sql.update(c,"DELETE FROM slotline.weekly_hours WHERE provider_id=?",id);for(var e:windows.entrySet())Sql.update(c,"INSERT INTO slotline.weekly_hours VALUES(?,?,?,?)",id,e.getKey(),e.getValue().opens(),e.getValue().closes());return null;});
        return settings(id);
    }
    public Map<String,Object> override(UUID id, JsonNode input) {
        LocalDate day=SlotEngine.date(Json.text(input,"date",10,true));if(!input.path("closed").isBoolean())throw Problem.invalid("closed must be true or false.");boolean closed=input.path("closed").booleanValue();
        SlotEngine.Window window=closed?null:new SlotEngine.Window(SlotEngine.time(Json.text(input,"opens",5,true)),SlotEngine.time(Json.text(input,"closes",5,true)));
        db.transaction(c->{Map<String,Object>p=provider(c,id,true);if(window!=null)SlotEngine.localRange(day,window,SlotEngine.zone((String)p.get("zone")));
            Sql.update(c,"INSERT INTO slotline.date_overrides VALUES(?,?,?,?,?) ON CONFLICT(provider_id,day)DO UPDATE SET closed=EXCLUDED.closed,opens=EXCLUDED.opens,closes=EXCLUDED.closes",id,day,closed,window==null?null:window.opens(),window==null?null:window.closes());return null;});return settings(id);
    }
    public Map<String,Object> removeOverride(UUID id, LocalDate day) {
        db.transaction(c->{provider(c,id,true);Sql.update(c,"DELETE FROM slotline.date_overrides WHERE provider_id=? AND day=?",id,day);return null;});return settings(id);
    }
    public Map<String,Object> block(UUID id, JsonNode input) {
        LocalDate day=SlotEngine.date(Json.text(input,"date",10,true));SlotEngine.Window hours=new SlotEngine.Window(SlotEngine.time(Json.text(input,"opens",5,true)),SlotEngine.time(Json.text(input,"closes",5,true)));
        String label=Json.text(input,"label",80,true);
        db.transaction(c->{Map<String,Object>p=provider(c,id,true);SlotEngine.Busy range=SlotEngine.localRange(day,hours,SlotEngine.zone((String)p.get("zone")));
            if(range.start().isBefore(clock.instant())||range.start().isAfter(clock.instant().plus(Duration.ofDays(365))))throw Problem.invalid("Choose a future block within one year.");
            if(Sql.one(c,"SELECT id FROM slotline.bookings WHERE provider_id=? AND status<>'cancelled' AND starts_at<? AND occupied_until>? LIMIT 1",id,range.end(),range.start())!=null)throw Problem.conflict();
            Sql.update(c,"INSERT INTO slotline.blocks(id,provider_id,starts_at,ends_at,label) VALUES(?,?,?,?,?)",UUID.randomUUID(),id,range.start(),range.end(),label);return null;});return settings(id);
    }
    public void removeBlock(UUID id) {
        db.transaction(c->{Map<String,Object>b=Sql.one(c,"SELECT * FROM slotline.blocks WHERE id=?",id);if(b!=null){provider(c,Sql.id(b,"provider_id"),true);Sql.update(c,"DELETE FROM slotline.blocks WHERE id=?",id);}return null;});
    }
}
