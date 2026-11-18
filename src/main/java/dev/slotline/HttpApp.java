package dev.slotline;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.regex.*;

public final class HttpApp implements HttpHandler {
    private final BookingStore store;
    private final Auth auth;
    private static final Pattern BOOKING=Pattern.compile("/api/(public|admin)/bookings/([0-9a-fA-F-]{36})(?:/(slots|reschedule|cancel|status|calendar\\.ics))?");
    private static final Pattern SETTINGS=Pattern.compile("/api/admin/providers/([0-9a-fA-F-]{36})(?:/(hours|overrides|blocks))?");
    public HttpApp(BookingStore store, Auth auth){this.store=store;this.auth=auth;}
    private static Map<String,String> headers(HttpExchange exchange){Map<String,String>result=new HashMap<>();exchange.getRequestHeaders().forEach((k,v)->result.put(k.toLowerCase(Locale.ROOT),String.join(",",v)));return result;}
    private static Map<String,String> query(URI uri){Map<String,String>values=new HashMap<>();if(uri.getRawQuery()!=null)for(String part:uri.getRawQuery().split("&")){String[]p=part.split("=",2);values.put(URLDecoder.decode(p[0],StandardCharsets.UTF_8),p.length==2?URLDecoder.decode(p[1],StandardCharsets.UTF_8):"");}return values;}
    private static String required(Map<String,String> query,String key){String v=query.getOrDefault(key,"");if(v.isEmpty()||v.length()>100)throw Problem.invalid("Missing or invalid "+key+".");return v;}
    private static void send(HttpExchange x,int status,Object value)throws IOException{
        byte[]bytes=Json.write(value).getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");x.sendResponseHeaders(status,bytes.length);try(OutputStream out=x.getResponseBody()){out.write(bytes);}
    }
    private boolean asset(HttpExchange x,String path)throws IOException{
        Map<String,String>files=Map.of("/","index.html","/app.js","app.js","/format.js","format.js","/style.css","style.css");String file=files.get(path);if(file==null)return false;
        try(InputStream input=getClass().getResourceAsStream("/www/"+file)){
            if(input==null)throw new Problem(503,"ASSETS_MISSING","Build the web assets before starting the server.");
            byte[]bytes=input.readAllBytes();String mime=file.endsWith(".html")?"text/html":file.endsWith(".css")?"text/css":"text/javascript";
            x.getResponseHeaders().set("Content-Type",mime+"; charset=utf-8");x.sendResponseHeaders(200,bytes.length);try(OutputStream out=x.getResponseBody()){out.write(bytes);}return true;
        }
    }
    @Override public void handle(HttpExchange x)throws IOException{
        Headers output=x.getResponseHeaders();output.set("Cache-Control","no-store");output.set("X-Content-Type-Options","nosniff");output.set("Referrer-Policy","no-referrer");output.set("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self' data:; object-src 'none'; frame-ancestors 'none'; base-uri 'self'");
        try{
            String path=x.getRequestURI().getPath(),method=x.getRequestMethod();Map<String,String>h=headers(x),q=query(x.getRequestURI());
            if(method.equals("GET")&&asset(x,path))return;
            if(path.equals("/healthz")){send(x,200,Map.of("ok",true));return;}
            if(path.equals("/readyz")){store.db.transaction(c->Sql.one(c,"SELECT 1"));send(x,200,Map.of("ready",true));return;}
            auth.origin(h);
            boolean write=!method.equals("GET");
            JsonNode input=Json.MAPPER.createObjectNode();
            if(write){byte[]body=x.getRequestBody().readNBytes(65537);if(body.length>65536)throw new Problem(413,"BODY_TOO_LARGE","Maximum request size is 64 KiB.");if(body.length>0){if(!h.getOrDefault("content-type","").toLowerCase(Locale.ROOT).startsWith("application/json"))throw new Problem(415,"JSON_REQUIRED","Use application/json requests.");input=Json.read(new String(body,StandardCharsets.UTF_8));if(!input.isObject())throw Problem.invalid("Expected a JSON object.");}}
            if(path.equals("/api/admin/login")&&method.equals("POST")){output.set("Set-Cookie",auth.login(Json.text(input,"token",200,true)));send(x,200,Map.of("ok",true));return;}
            if(path.startsWith("/api/admin/"))auth.require(h,write);
            if(path.equals("/api/admin/logout")&&method.equals("POST")){output.set("Set-Cookie",auth.logout());send(x,200,Map.of("ok",true));return;}
            if(path.equals("/api/public/catalogue")&&method.equals("GET")){send(x,200,store.catalogue());return;}
            if(path.equals("/api/public/slots")&&method.equals("GET")){send(x,200,store.availability(BookingStore.uuid(required(q,"provider_id")),BookingStore.uuid(required(q,"service_id")),SlotEngine.date(required(q,"date"))));return;}
            if(path.equals("/api/public/bookings")&&method.equals("POST")){send(x,201,store.create(input));return;}
            if(path.equals("/api/admin/calendar")&&method.equals("GET")){send(x,200,store.calendar(SlotEngine.instant(required(q,"from")),SlotEngine.instant(required(q,"to")),q.getOrDefault("provider_id","").isEmpty()?null:BookingStore.uuid(q.get("provider_id"))));return;}
            Matcher booking=BOOKING.matcher(path);
            if(booking.matches()){
                boolean admin=booking.group(1).equals("admin");UUID id=BookingStore.uuid(booking.group(2));String action=Objects.toString(booking.group(3),"");String token=h.getOrDefault("x-booking-token","");
                if(method.equals("GET")&&action.isEmpty()){send(x,200,store.manage(id,token,admin));return;}
                if(method.equals("GET")&&action.equals("slots")){send(x,200,store.managedAvailability(id,token,admin,SlotEngine.date(required(q,"date"))));return;}
                if(method.equals("POST")&&action.equals("cancel")){send(x,200,store.cancel(id,token,admin,Json.text(input,"reason",200,false)));return;}
                if(method.equals("POST")&&action.equals("reschedule")){send(x,200,store.reschedule(id,token,admin,input));return;}
                if(admin&&method.equals("POST")&&action.equals("status")){send(x,200,store.status(id,Json.text(input,"status",20,true)));return;}
                if(method.equals("GET")&&action.equals("calendar.ics")){
                    Map<String,Object>b=store.manage(id,token,admin);byte[]bytes=CalendarFile.render(b,store.clock.instant()).getBytes(StandardCharsets.UTF_8);
                    output.set("Content-Type","text/calendar; charset=utf-8");output.set("Content-Disposition","attachment; filename=\"slotline-"+id+".ics\"");x.sendResponseHeaders(200,bytes.length);try(OutputStream stream=x.getResponseBody()){stream.write(bytes);}return;
                }
            }
            Matcher settings=SETTINGS.matcher(path);
            if(settings.matches()){
                UUID id=BookingStore.uuid(settings.group(1));String action=Objects.toString(settings.group(2),"");
                if(method.equals("GET")&&action.isEmpty()){send(x,200,store.settings(id));return;}
                if(method.equals("PUT")&&action.equals("hours")){send(x,200,store.saveHours(id,input));return;}
                if(method.equals("POST")&&action.equals("overrides")){send(x,200,store.override(id,input));return;}
                if(method.equals("DELETE")&&action.equals("overrides")){send(x,200,store.removeOverride(id,SlotEngine.date(required(q,"date"))));return;}
                if(method.equals("POST")&&action.equals("blocks")){send(x,201,store.block(id,input));return;}
            }
            if(path.matches("/api/admin/blocks/[0-9a-fA-F-]{36}")&&method.equals("DELETE")){store.removeBlock(BookingStore.uuid(path.substring(path.lastIndexOf('/')+1)));send(x,200,Map.of("deleted",true));return;}
            throw new Problem(404,"NOT_FOUND","Route not found.");
        }catch(Problem e){send(x,e.status,Map.of("error",e.getMessage(),"code",e.code));}
        catch(IllegalArgumentException e){send(x,422,Map.of("error","Invalid request or date value.","code","INVALID_INPUT"));}
        catch(Exception e){System.err.println("HTTP request failed: "+e.getClass().getSimpleName());send(x,500,Map.of("error","The request could not be completed. Retry with the same request ID.","code","INTERNAL_ERROR"));}
        finally{x.close();}
    }
}
