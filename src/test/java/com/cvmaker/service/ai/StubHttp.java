package com.cvmaker.service.ai;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

/**
 * An {@link OkHttpClient} whose interceptor answers every call with a canned
 * response and records the request, so no connection is ever opened. It also
 * collects the lines written to an {@link ApiLog}.
 */
public final class StubHttp {

    public final List<Request> requests = new CopyOnWriteArrayList<>();
    public final List<String> requestBodies = new CopyOnWriteArrayList<>();
    public final List<String> logLines = new CopyOnWriteArrayList<>();

    private volatile int status = 200;
    private volatile String body = "{}";

    public StubHttp respondWith(int status, String body) {
        this.status = status;
        this.body = body;
        return this;
    }

    public OkHttpClient client() {
        return new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    Request request = chain.request();
                    requests.add(request);
                    requestBodies.add(bodyOf(request));
                    return new Response.Builder()
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .code(status)
                            .message(status == 200 ? "OK" : "Error")
                            .body(ResponseBody.create(body, MediaType.get("application/json")))
                            .build();
                })
                .build();
    }

    /**
     * A log that writes into {@link #logLines}.
     */
    public ApiLog log(boolean debug) {
        return new ApiLog(logLines::add, debug);
    }

    public String allLogText() {
        return String.join("\n", logLines);
    }

    private static String bodyOf(Request request) throws IOException {
        if (request.body() == null) {
            return "";
        }
        Buffer buffer = new Buffer();
        request.body().writeTo(buffer);
        return buffer.readUtf8();
    }
}
