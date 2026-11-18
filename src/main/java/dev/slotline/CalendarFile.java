package dev.slotline;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class CalendarFile {
    private static final DateTimeFormatter UTC=DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private CalendarFile(){}
    static String escape(String value){return value.replace("\\","\\\\").replace("\r\n","\n").replace("\r","\n").replace("\n","\\n").replace(";","\\;").replace(",","\\,");}
    static String fold(String value){
        StringBuilder result=new StringBuilder();int length=0;
        for(int cp:value.codePoints().toArray()){
            String character=new String(Character.toChars(cp));int size=character.getBytes(StandardCharsets.UTF_8).length;
            if(length+size>75){result.append("\r\n ");length=1;}
            result.append(character);length+=size;
        }
        return result.toString();
    }
    public static String render(Map<String,Object> booking, Instant now){
        String description="Booking reference: "+booking.get("reference")+"\nProvider timezone: "+booking.get("provider_zone");
        List<String> lines=List.of("BEGIN:VCALENDAR","VERSION:2.0","PRODID:-//Slotline//Booking MVP//EN","CALSCALE:GREGORIAN","METHOD:PUBLISH","BEGIN:VEVENT",
            "UID:"+booking.get("id")+"@slotline.local","SEQUENCE:"+booking.get("revision"),"DTSTAMP:"+UTC.format(now),
            "DTSTART:"+UTC.format((Instant)booking.get("starts_at")),"DTEND:"+UTC.format((Instant)booking.get("ends_at")),
            "SUMMARY:"+escape(booking.get("service_name")+" with "+booking.get("provider_name")),"DESCRIPTION:"+escape(description),
            "LOCATION:Online session","STATUS:"+("cancelled".equals(booking.get("status"))?"CANCELLED":"CONFIRMED"),"END:VEVENT","END:VCALENDAR");
        return lines.stream().map(CalendarFile::fold).reduce((a,b)->a+"\r\n"+b).orElse("")+"\r\n";
    }
}
