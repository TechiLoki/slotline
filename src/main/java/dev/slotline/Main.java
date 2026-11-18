package dev.slotline;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.concurrent.*;

public final class Main {
    public static String env(String key,String fallback){String value=System.getenv(key);return value==null||value.isBlank()?fallback:value;}
    public static void main(String[] args)throws Exception{
        if(args.length>0&&args[0].equals("--health")){
            HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            try{var response=client.send(HttpRequest.newBuilder(URI.create("http://localhost:8080/readyz")).timeout(Duration.ofSeconds(3)).GET().build(),HttpResponse.BodyHandlers.discarding());System.exit(response.statusCode()==200?0:1);}catch(Exception e){System.exit(1);}return;
        }
        Database database=new Database(env("DATABASE_URL","jdbc:postgresql://db:5432/slotline?connectTimeout=3&socketTimeout=10"),env("DATABASE_USER","slotline_app"),env("DATABASE_PASSWORD","local-app-password"));
        Clock clock=Clock.systemUTC();BookingStore store=new BookingStore(database,clock,env("MANAGEMENT_SECRET","slotline-local-management-secret"));
        Auth auth=new Auth(env("ADMIN_TOKEN","slotline-local-admin"),clock,"true".equals(env("COOKIE_SECURE","false")));
        HttpServer server=HttpServer.create(new InetSocketAddress("0.0.0.0",Integer.parseInt(env("PORT","8080"))),64);
        ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor();server.setExecutor(executor);server.createContext("/",new HttpApp(store,auth));
        Runtime.getRuntime().addShutdownHook(new Thread(()->{server.stop(5);executor.close();}));
        server.start();System.out.println("Slotline listening on "+server.getAddress().getPort());
    }
}
