package dev.slotline;

import java.time.*;
import java.time.zone.ZoneRules;
import java.util.*;

public final class SlotEngine {
    public record Service(UUID id, String name, int duration, int buffer, int notice, int horizon, int cancelNotice, boolean active) {}
    public record Window(LocalTime opens, LocalTime closes) {}
    public record Busy(Instant start, Instant end) {}
    public record Slot(Instant start, Instant end) {}
    private SlotEngine() {}
    public static ZoneId zone(String value) {
        try { if(!ZoneId.getAvailableZoneIds().contains(value)) throw Problem.invalid("Choose an IANA timezone from the list."); return ZoneId.of(value); }
        catch (DateTimeException e) { throw Problem.invalid("Choose a valid IANA timezone."); }
    }
    public static LocalDate date(String value) {
        try { return LocalDate.parse(value); }
        catch (DateTimeException e) { throw Problem.invalid("Date must be YYYY-MM-DD."); }
    }
    public static Instant instant(String value) {
        try { return Instant.parse(value); }
        catch (DateTimeException e) { throw Problem.invalid("Start/end must be an absolute ISO timestamp with UTC Z."); }
    }
    public static LocalTime time(String value) {
        try {
            if (!value.matches("\\d{2}:\\d{2}")) throw Problem.invalid("Use HH:mm times.");
            LocalTime time = LocalTime.parse(value);
            if (time.getMinute() % 15 != 0) throw Problem.invalid("Schedule boundaries must align to 15 minutes.");
            return time;
        } catch (DateTimeException e) { throw Problem.invalid("Invalid clock time."); }
    }
    public static List<Instant> occurrences(LocalDate date, LocalTime time, ZoneId zone) {
        LocalDateTime local = date.atTime(time);
        return zone.getRules().getValidOffsets(local).stream().map(local::toInstant).sorted().toList();
    }
    public static Busy localRange(LocalDate date, Window hours, ZoneId zone) {
        if (!hours.opens().isBefore(hours.closes())) throw Problem.invalid("Opening time must be before closing time on the same date.");
        List<Instant> starts = occurrences(date, hours.opens(), zone);
        List<Instant> ends = occurrences(date, hours.closes(), zone);
        if (starts.isEmpty() || ends.isEmpty()) throw Problem.invalid("A schedule boundary falls inside a daylight-saving gap.");
        return new Busy(starts.getFirst(), ends.getLast());
    }
    public static boolean overlaps(Instant start, Instant end, Busy busy) {
        return start.isBefore(busy.end()) && end.isAfter(busy.start());
    }
    public static List<Slot> generate(LocalDate date, Window hours, ZoneId zone, Service service, List<Busy> busy, Instant now) {
        if (hours == null || !service.active()) return List.of();
        LocalDate today = now.atZone(zone).toLocalDate();
        if (date.isBefore(today) || date.isAfter(today.plusDays(service.horizon()))) return List.of();
        Busy work;
        try { work = localRange(date, hours, zone); }
        catch (Problem ignored) { return List.of(); }
        List<Slot> slots = new ArrayList<>();
        for (LocalTime time = hours.opens(); time.isBefore(hours.closes()); ) {
            for (Instant start : occurrences(date, time, zone)) {
                Instant end = start.plusSeconds(service.duration() * 60L);
                Instant occupiedEnd = end.plusSeconds(service.buffer() * 60L);
                if (!start.isBefore(now.plusSeconds(service.notice() * 60L)) && !start.isBefore(work.start()) && !occupiedEnd.isAfter(work.end()) && busy.stream().noneMatch(b -> overlaps(start, occupiedEnd, b))) slots.add(new Slot(start, end));
            }
            int minutes = time.getHour() * 60 + time.getMinute() + 15;
            if (minutes >= 1440) break;
            time = LocalTime.of(minutes / 60, minutes % 60);
        }
        slots.sort(Comparator.comparing(Slot::start));
        return List.copyOf(slots);
    }
}
