package dev.slotline;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="TEST_DATABASE_URL",matches=".+")
class DatabaseTest {
    Database db,admin;BookingStore store;UUID provider,service;
    Clock clock=Clock.fixed(Instant.parse("2030-03-01T00:00:00Z"),ZoneOffset.UTC);
    final Instant start=Instant.parse("2030-03-05T10:00:00Z");
    @BeforeEach void fixtures(){
        String url=System.getenv("TEST_DATABASE_URL");
        db=new Database(url,Main.env("DATABASE_USER","slotline_app"),Main.env("DATABASE_PASSWORD","local-app-password"));
        admin=new Database(url,Main.env("TEST_ADMIN_USER","postgres"),Main.env("TEST_ADMIN_PASSWORD","local-db-password"));
        store=new BookingStore(db,clock,"test-management-secret");provider=UUID.randomUUID();service=UUID.randomUUID();
        admin.transaction(c->{
            Sql.update(c,"INSERT INTO slotline.providers(id,name,role,zone)VALUES(?,?,?,?)",provider,"Test provider "+provider,"Test","UTC");
            Sql.update(c,"INSERT INTO slotline.services(id,name,description,duration_minutes,buffer_minutes,notice_minutes,horizon_days,cancel_notice_minutes)VALUES(?,?,?,30,15,0,60,60)",service,"Test service","Synthetic integration fixture");
            Sql.update(c,"INSERT INTO slotline.provider_services VALUES(?,?)",provider,service);
            for(int day=1;day<=7;day++)Sql.update(c,"INSERT INTO slotline.weekly_hours VALUES(?,?,?,?)",provider,day,LocalTime.of(9,0),LocalTime.of(17,0));return null;
        });
    }
    JsonNode body(UUID key,Instant time){return Json.read(Json.write(Map.of("request_id",key,"provider_id",provider,"service_id",service,"starts_at",time,"guest_name","Test Guest","guest_email","guest@example.test","guest_zone","Asia/Tbilisi","note","Private test note")));}
    Map<String,Object> create(Instant time){return store.create(body(UUID.randomUUID(),time));}
    @Test void idempotentCreateDoesNotMakeAnotherBooking(){
        UUID key=UUID.randomUUID();var first=store.create(body(key,start));var again=store.create(body(key,start));assertEquals(first.get("id"),again.get("id"));assertEquals(first.get("manage_token"),again.get("manage_token"));
        var changed=body(key,start.plusSeconds(3600));Problem e=assertThrows(Problem.class,()->store.create(changed));assertEquals(409,e.status);assertEquals("REQUEST_CONFLICT",e.code);
    }
    @Test void concurrentRequestsForOneSlotHaveOnlyOneWinner()throws Exception{
        ExecutorService workers=Executors.newFixedThreadPool(2);CountDownLatch gate=new CountDownLatch(1);
        try{
            Callable<Integer> attempt=()->{gate.await();try{create(start);return 201;}catch(Problem e){return e.status;}};
            Future<Integer>a=workers.submit(attempt),b=workers.submit(attempt);gate.countDown();
            var results=new ArrayList<>(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS)));results.sort(Integer::compareTo);assertEquals(List.of(201,409),results);
        }finally{workers.shutdownNow();}
    }
    @Test void databaseExclusionProtectsDirectWritesAndAllowsTouchingRanges(){
        var first=create(start);UUID id=(UUID)first.get("id");
        Problem e=assertThrows(Problem.class,()->db.transaction(c->Sql.update(c,"INSERT INTO slotline.bookings(id,reference,provider_id,service_id,provider_name,provider_zone,service_name,duration_minutes,buffer_minutes,cancel_notice_minutes,starts_at,ends_at,occupied_until,guest_name,guest_email,guest_zone,manage_hash) SELECT gen_random_uuid(),?,provider_id,service_id,provider_name,provider_zone,service_name,duration_minutes,buffer_minutes,cancel_notice_minutes,starts_at+interval '1 minute',ends_at+interval '1 minute',occupied_until+interval '1 minute',guest_name,guest_email,guest_zone,manage_hash FROM slotline.bookings WHERE id=?","RAW-"+UUID.randomUUID(),id)));
        assertEquals(409,e.status);assertNotNull(create(start.plusSeconds(45*60)));
    }
    @Test void cancellationReleasesSlotAndCanBeRepeated(){
        var first=create(start);UUID id=(UUID)first.get("id");String token=(String)first.get("manage_token");
        assertEquals("cancelled",store.cancel(id,token,false,"Changed plans").get("status"));assertEquals("cancelled",store.cancel(id,token,false,"Again").get("status"));assertNotNull(create(start));
    }
    @Test void failedReschedulePreservesTheOriginalSlot(){
        var first=create(start);create(start.plusSeconds(3600));UUID id=(UUID)first.get("id");String token=(String)first.get("manage_token");
        var input=Json.read(Json.write(Map.of("request_id",UUID.randomUUID(),"starts_at",start.plusSeconds(3600),"guest_zone","UTC")));
        assertEquals(409,assertThrows(Problem.class,()->store.reschedule(id,token,false,input)).status);
        assertEquals(start,store.manage(id,token,false).get("starts_at"));
    }
    @Test void rescheduleIsAtomicAndReplaySafe(){
        var first=create(start);UUID id=(UUID)first.get("id");String token=(String)first.get("manage_token");
        var input=Json.read(Json.write(Map.of("request_id",UUID.randomUUID(),"starts_at",start.plusSeconds(3600),"guest_zone","Europe/London")));
        var moved=store.reschedule(id,token,false,input);assertEquals(start.plusSeconds(3600),moved.get("starts_at"));assertEquals(moved.get("starts_at"),store.reschedule(id,token,false,input).get("starts_at"));assertNotNull(create(start));
    }
    @Test void ownershipAndPublicCatalogueProtectGuestData(){
        var first=create(start);UUID id=(UUID)first.get("id");assertEquals(404,assertThrows(Problem.class,()->store.manage(id,"wrong",false)).status);
        String catalogue=Json.write(store.catalogue());assertFalse(catalogue.contains("guest@example.test"));assertFalse(catalogue.contains("Private test note"));assertFalse(first.containsKey("manage_hash"));
    }
    @Test void overridesAndPrivateBlocksRemoveAvailableTimes(){
        store.override(provider,Json.read("{\"date\":\"2030-03-05\",\"closed\":true}"));assertEquals(409,assertThrows(Problem.class,()->create(start)).status);
        store.removeOverride(provider,LocalDate.parse("2030-03-05"));store.block(provider,Json.read("{\"date\":\"2030-03-05\",\"opens\":\"10:00\",\"closes\":\"11:00\",\"label\":\"Private time\"}"));assertEquals(409,assertThrows(Problem.class,()->create(start)).status);assertNotNull(create(start.plusSeconds(3600)));
    }
    @Test void completionCannotReleaseAnUnfinishedBuffer(){
        var first=create(start);UUID id=(UUID)first.get("id");assertEquals(422,assertThrows(Problem.class,()->store.status(id,"completed")).status);
        BookingStore after=new BookingStore(db,Clock.fixed(start.plusSeconds(30*60),ZoneOffset.UTC),"test-management-secret");after.status(id,"completed");
        assertEquals(409,assertThrows(Problem.class,()->after.create(body(UUID.randomUUID(),start.plusSeconds(30*60)))).status);assertNotNull(after.create(body(UUID.randomUUID(),start.plusSeconds(45*60))));
    }
    @Test void scheduleChangesKeepExistingAppointmentsAndAppCannotDeleteBookings(){
        var first=create(start);store.saveHours(provider,Json.read("{\"hours\":[]}"));assertEquals("confirmed",store.manage((UUID)first.get("id"),(String)first.get("manage_token"),false).get("status"));
        Boolean allowed=db.transaction(c->(Boolean)Sql.one(c,"SELECT has_table_privilege(current_user,'slotline.bookings','DELETE') AS allowed").get("allowed"));assertFalse(allowed);
    }
}
