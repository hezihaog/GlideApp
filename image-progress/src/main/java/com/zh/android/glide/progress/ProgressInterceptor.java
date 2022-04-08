package com.zh.android.glide.progress;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 进度拦截器
 */
public class ProgressInterceptor implements Interceptor {
    private static final Map<Object, ProgressListener> listenerMap = new HashMap<>();

    public static void addListener(Object model, ProgressListener listener) {
        listenerMap.put(model, listener);
    }

    public static void removeListener(Object model) {
        listenerMap.remove(model);
    }

    public static ProgressListener getListener(Object model) {
        return listenerMap.get(model);
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        Response response = chain.proceed(request);
        String url = request.url().toString();
        ResponseBody body = response.body();
        return response.newBuilder().body(new ProgressResponseBody(url, body)).build();
    }
}