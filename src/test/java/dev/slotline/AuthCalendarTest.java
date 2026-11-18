package dev.slotline;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AuthCalendarTest {
    final Clock clock=Clock.fixed(Instant.parse("2030-03-01T00:00:00Z"),ZoneOffset.UTC);
    @Test void adminCookieIsSignedAndHasRestrictedAttributes(){
        Auth auth=new Auth("test-token",clock,true);String cookie=auth.login("test-token");
        assertTrue(cookie.contains("HttpOnly; SameSite=Strict"));assertTrue(cookie.contains("; Secure"));
        assertTrue(auth.authenticated(Map.of("cookie",cookie.split(";",2)[0])));
        assertFalse(auth.authenticated(Map.of("cookie",cookie.split(";",2)[0]+"tampered")));
        assertThrows(Problem.class,()->auth.login("wrong"));
    }
    @Test void cookieWritesNeedRequestHeaderAndCrossOriginIsRejected(){
        Auth auth=new Auth("test-token",clock,false);String cookie=auth.login("test-token").split(";",2)[0];
        assertThrows(Problem.class,()->auth.require(Map.of("cookie",cookie),true));
        assertDoesNotThrow(()->auth.require(Map.of("cookie",cookie,"x-slotline-request","1"),true));
        assertDoesNotThrow(()->auth.require(Map.of("authorization","Bearer test-token"),true));
        assertThrows(Problem.class,()->auth.require(Map.of("authorization","Bearer test-token","origin","https://evil.invalid","host","localhost"),true));
    }
    @Test void expiredSessionsAreRejected(){
        Auth before=new Auth("secret",clock,false);String cookie=before.login("secret").split(";",2)[0];
        Auth after=new Auth("secret",Clock.offset(clock,Duration.ofHours(13)),false);assertFalse(after.authenticated(Map.of("cookie",cookie)));
    }
    @Test void managementTokensAreStableAndBookingSpecific(){
        UUID a=UUID.randomUUID(),b=UUID.randomUUID();assertEquals(Crypto.managementToken("secret",a),Crypto.managementToken("secret",a));assertNotEquals(Crypto.managementToken("secret",a),Crypto.managementToken("secret",b));
    }
    @Test void calendarEscapesInjectionAndUsesStableUidAndSequence(){
        Map<String,Object>b=new HashMap<>();b.put("id",UUID.randomUUID());b.put("reference","SL-TEST");b.put("revision",2);b.put("service_name","Review\r\nEND:VEVENT\r\nBEGIN:VEVENT");b.put("provider_name","Provider, Inc; Team");b.put("provider_zone","Asia/Tbilisi");b.put("status","cancelled");b.put("starts_at",Instant.parse("2030-03-05T10:00:00Z"));b.put("ends_at",Instant.parse("2030-03-05T10:30:00Z"));
        String ics=CalendarFile.render(b,clock.instant());
        assertTrue(ics.contains("SEQUENCE:2\r\n"));assertTrue(ics.contains("STATUS:CANCELLED\r\n"));assertTrue(ics.contains("DTSTART:20300305T100000Z"));
        assertEquals(1,ics.lines().filter(line->line.equals("BEGIN:VEVENT")).count());assertFalse(ics.contains("manage_token"));
    }
    @Test void unicodeCalendarFoldingRespectsByteLimit(){
        String value="SUMMARY:"+"日本語の相談".repeat(30);String folded=CalendarFile.fold(value);
        for(String line:folded.split("\r\n"))assertTrue(line.getBytes(StandardCharsets.UTF_8).length<=75);
        assertEquals(value,folded.replace("\r\n ",""));
    }
    @Test void boundedJsonRejectsFractionalIntegersAndEmbeddedNulls(){
        assertThrows(Problem.class,()->Json.read("  "));
        assertThrows(Problem.class,()->Json.read("{} {}"));
        assertThrows(Problem.class,()->Json.integer(Json.read("{\"n\":1.5}"),"n",0,10));
        assertThrows(Problem.class,()->Json.text(Json.read("{\"name\":\"a\\u0000b\"}"),"name",80,true));
        assertThrows(Problem.class,()->Json.read("{\"name\":\""+"a".repeat(6000)+"\"}"));
    }
}
