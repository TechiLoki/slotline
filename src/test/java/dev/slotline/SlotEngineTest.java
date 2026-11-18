package dev.slotline;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SlotEngineTest {
    private SlotEngine.Service service(int duration,int buffer,int notice){return new SlotEngine.Service(UUID.randomUUID(),"Test",duration,buffer,notice,90,60,true);}
    @Test void halfOpenIntervalsAllowTouchingButNotOverlapping(){
        Instant start=Instant.parse("2030-03-05T10:00:00Z");
        SlotEngine.Busy busy=new SlotEngine.Busy(start,start.plusSeconds(2700));
        assertFalse(SlotEngine.overlaps(busy.end(),busy.end().plusSeconds(900),busy));
        assertTrue(SlotEngine.overlaps(start.plusSeconds(1800),busy.end().plusSeconds(900),busy));
    }
    @Test void buffersAndWorkingDayEndAreProtected(){
        LocalDate day=LocalDate.parse("2030-03-05");ZoneId zone=ZoneId.of("UTC");
        var busy=new SlotEngine.Busy(Instant.parse("2030-03-05T09:00:00Z"),Instant.parse("2030-03-05T09:45:00Z"));
        var slots=SlotEngine.generate(day,new SlotEngine.Window(LocalTime.of(9,0),LocalTime.of(11,0)),zone,service(30,15,0),List.of(busy),Instant.parse("2030-03-01T00:00:00Z"));
        assertEquals(List.of("2030-03-05T09:45:00Z","2030-03-05T10:00:00Z","2030-03-05T10:15:00Z"),slots.stream().map(s->s.start().toString()).toList());
    }
    @Test void noticePastDatesAndHorizonAreRespected(){
        var hours=new SlotEngine.Window(LocalTime.of(9,0),LocalTime.of(17,0));var s=service(30,0,30);Instant now=Instant.parse("2030-03-05T09:10:00Z");
        var slots=SlotEngine.generate(LocalDate.parse("2030-03-05"),hours,ZoneId.of("UTC"),s,List.of(),now);
        assertEquals(Instant.parse("2030-03-05T09:45:00Z"),slots.getFirst().start());
        assertTrue(SlotEngine.generate(LocalDate.parse("2030-03-04"),hours,ZoneId.of("UTC"),s,List.of(),now).isEmpty());
        assertTrue(SlotEngine.generate(LocalDate.parse("2031-03-05"),hours,ZoneId.of("UTC"),s,List.of(),now).isEmpty());
        assertTrue(SlotEngine.generate(LocalDate.parse("2030-03-05"),null,ZoneId.of("UTC"),s,List.of(),now).isEmpty());
    }
    @Test void springGapDoesNotCreateNonexistentWallTimes(){
        ZoneId zone=ZoneId.of("America/New_York");
        var slots=SlotEngine.generate(LocalDate.parse("2026-03-08"),new SlotEngine.Window(LocalTime.MIDNIGHT,LocalTime.of(4,0)),zone,service(15,0,0),List.of(),Instant.parse("2026-03-01T00:00:00Z"));
        assertEquals(12,slots.size());assertTrue(slots.stream().noneMatch(s->s.start().atZone(zone).getHour()==2));
    }
    @Test void autumnOverlapRetainsBothDistinctInstants(){
        ZoneId zone=ZoneId.of("America/New_York");
        var slots=SlotEngine.generate(LocalDate.parse("2026-11-01"),new SlotEngine.Window(LocalTime.MIDNIGHT,LocalTime.of(3,0)),zone,service(15,0,0),List.of(),Instant.parse("2026-10-20T00:00:00Z"));
        assertEquals(16,slots.size());
        var repeated=slots.stream().filter(s->s.start().atZone(zone).toLocalTime().equals(LocalTime.of(1,30))).toList();
        assertEquals(2,repeated.size());assertNotEquals(repeated.get(0).start(),repeated.get(1).start());
        assertEquals(16,slots.stream().map(SlotEngine.Slot::start).distinct().count());
    }
    @Test void durationMeansElapsedMinutesAcrossClockChange(){
        ZoneId zone=ZoneId.of("America/New_York");
        var slots=SlotEngine.generate(LocalDate.parse("2026-11-01"),new SlotEngine.Window(LocalTime.MIDNIGHT,LocalTime.of(4,0)),zone,service(30,0,0),List.of(),Instant.parse("2026-10-20T00:00:00Z"));
        var slot=slots.stream().filter(s->s.start().equals(Instant.parse("2026-11-01T05:45:00Z"))).findFirst().orElseThrow();
        assertEquals(Instant.parse("2026-11-01T06:15:00Z"),slot.end());
        assertEquals(30,Duration.between(slot.start(),slot.end()).toMinutes());
    }
    @Test void nonexistentBoundaryMakesTheDayUnavailable(){
        var slots=SlotEngine.generate(LocalDate.parse("2026-03-08"),new SlotEngine.Window(LocalTime.of(2,0),LocalTime.of(4,0)),ZoneId.of("America/New_York"),service(15,0,0),List.of(),Instant.parse("2026-03-01T00:00:00Z"));assertTrue(slots.isEmpty());
    }
    @Test void invalidZonesAndUnalignedSchedulesAreRejected(){
        assertThrows(Problem.class,()->SlotEngine.zone("Not/A_Zone"));assertThrows(Problem.class,()->SlotEngine.time("09:10"));assertThrows(Problem.class,()->SlotEngine.time("24:00"));assertThrows(Problem.class,()->SlotEngine.date("2030-02-30"));
    }
}
