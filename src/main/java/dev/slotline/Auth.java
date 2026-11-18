package dev.slotline;

import java.time.*;
import java.util.*;

public final class Auth {
    private final String token;
    private final Clock clock;
    private final boolean secure;
    public Auth(String token, Clock clock, boolean secure) { this.token=token;this.clock=clock;this.secure=secure; }
    public void origin(Map<String,String> headers) {
        String origin=headers.getOrDefault("origin",""),host=headers.getOrDefault("host","");
        if(!origin.isEmpty()&&!origin.equals("http://"+host)&&!origin.equals("https://"+host))throw new Problem(403,"ORIGIN_REJECTED","Cross-origin requests are not allowed.");
    }
    public boolean bearer(Map<String,String> headers) { return Crypto.equal(headers.getOrDefault("authorization",""),"Bearer "+token); }
    public boolean authenticated(Map<String,String> headers) {
        if(bearer(headers))return true;
        String value=Arrays.stream(headers.getOrDefault("cookie","").split(";")).map(String::strip).filter(s->s.startsWith("slotline_session=")).map(s->s.substring("slotline_session=".length())).findFirst().orElse("");
        String[] parts=value.split("\\.");if(parts.length!=2)return false;
        try {long time=Long.parseLong(parts[0]),now=clock.instant().getEpochSecond();return time>=now-43200&&time<=now+30&&Crypto.equal(parts[1],Crypto.sign(token,"session:"+time));}
        catch(NumberFormatException e){return false;}
    }
    public void require(Map<String,String> headers, boolean write) {
        origin(headers);
        if(!authenticated(headers))throw new Problem(401,"SIGN_IN_REQUIRED","Sign in to the staff workspace.");
        if(write&&!bearer(headers)&&!"1".equals(headers.get("x-slotline-request")))throw new Problem(403,"REQUEST_REJECTED","Missing request protection header.");
    }
    public String login(String supplied) {
        if(!Crypto.equal(token,supplied))throw new Problem(401,"BAD_CREDENTIAL","Incorrect workspace token.");
        long time=clock.instant().getEpochSecond();return "slotline_session="+time+"."+Crypto.sign(token,"session:"+time)+"; Path=/; HttpOnly; SameSite=Strict; Max-Age=43200"+(secure?"; Secure":"");
    }
    public String logout() { return "slotline_session=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0"+(secure?"; Secure":""); }
}
