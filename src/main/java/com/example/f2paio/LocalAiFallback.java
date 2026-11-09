package com.example.f2paio;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
/** Local fallback is credential-free and runs only on the asynchronous planner thread. */
public final class LocalAiFallback {
    private static final HttpClient CLIENT=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    public static String generate(String prompt,int maxTokens,String model) throws Exception {
        var body=new com.google.gson.JsonObject(); body.addProperty("model",model);
        var message=new com.google.gson.JsonObject(); message.addProperty("role","user"); message.addProperty("content",prompt);
        var messages=new com.google.gson.JsonArray(); messages.add(message); body.add("messages",messages);
        body.addProperty("stream",false); body.addProperty("think",false); body.addProperty("format","json");
        var options=new com.google.gson.JsonObject(); options.addProperty("num_predict",maxTokens); body.add("options",options);
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:11434/api/chat"))
            .timeout(Duration.ofSeconds(60)).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        var pending=CLIENT.sendAsync(request,info->new LimitedBody());
        try {
            var response=pending.get(60,java.util.concurrent.TimeUnit.SECONDS);
            if(response.statusCode()!=200) return null;
            var json=FilesStore.JSON.fromJson(new String(response.body(),java.nio.charset.StandardCharsets.UTF_8),com.google.gson.JsonObject.class);
            return json.has("message")?json.getAsJsonObject("message").get("content").getAsString():null;
        } finally { pending.cancel(true); }
    }
    private LocalAiFallback() { }
}
