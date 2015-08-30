package io.loli.kaze.cache;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;

import java.util.stream.Collectors;

import javax.cache.Cache;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.littleshoot.proxy.HttpFilters;
import org.littleshoot.proxy.HttpFiltersAdapter;
import org.littleshoot.proxy.HttpFiltersSourceAdapter;

public class CacheFilter extends HttpFiltersSourceAdapter {
    private String cacheRegex;
    private Cache<String, KazeCacheData> cache;

    public CacheFilter(String cacheRegex) {
        this.cacheRegex = cacheRegex;
        cache = KazeCacheFactory.getCache(CacheFilter.class.getName());
    }

    public HttpFilters filterRequest(HttpRequest originalRequest,
            ChannelHandlerContext ctx) {
        return new HttpFiltersAdapter(originalRequest) {
            @Override
            public HttpResponse requestPre(HttpObject httpObject) {
                if (httpObject instanceof HttpRequest) {
                    String method = originalRequest.getMethod().toString();
                    if ("GET".equals(method)) {
                        String url = originalRequest.getUri();
                        String allString = url;
                        if (allString.matches(cacheRegex)) {
                            String key = method + allString;
                            KazeCacheData data = cache.get(DigestUtils
                                    .md5Hex(key));
                            // if status is 200 or 304
                            if (data != null) {
                                int status = data.getStatus();
                                if (status == 200 || status == 304) {
                                    ByteBuf buf = Unpooled.copiedBuffer(data
                                            .getData());
                                    HttpResponse resp = new DefaultFullHttpResponse(
                                            HttpVersion.HTTP_1_1,
                                            HttpResponseStatus.valueOf(304),
                                            buf);
                                    data.getHeaders()
                                            .entrySet()
                                            .forEach(
                                                    entry -> {
                                                        resp.headers()
                                                                .add(entry
                                                                        .getKey(),
                                                                        entry.getValue());
                                                    });
                                    return resp;
                                }
                            }
                        }
                    }
                }
                return null;
            }

            private ThreadLocal<KazeCacheData> context = new ThreadLocal<>();

            @Override
            public HttpObject responsePre(HttpObject httpObject) {
                String method = originalRequest.getMethod().toString();
                String url = originalRequest.getUri();
                String key = method + url;
                if (url.matches(cacheRegex) && "GET".equals(method)) {

                    if (httpObject instanceof HttpResponse) {
                        HttpResponse resp = (HttpResponse) httpObject;
                        if (resp.getStatus().code() == 200) {
                            KazeCacheData data = new KazeCacheData();
                            data.setContentType(resp.headers().get(
                                    "Content-Type"));
                            data.setStatus(resp.getStatus().code());

                            data.setHeaders(resp
                                    .headers()
                                    .entries()
                                    .stream()
                                    .collect(
                                            Collectors.toMap(
                                                    entry -> entry.getKey(),
                                                    entry -> entry.getValue())));
                            context.set(data);
                        }
                    }
                    if (httpObject instanceof HttpContent) {
                        KazeCacheData data = context.get();
                        if (data != null) {
                            HttpContent content = (HttpContent) httpObject;
                            ByteBuf copy = content.content().copy();

                            ByteBufToBytes reader = new ByteBufToBytes(
                                    copy.readableBytes());
                            reader.reading(copy);

                            if (data.getData() == null) {
                                data.setData(reader.readFull());
                            } else {
                                data.setData(ArrayUtils.addAll(data.getData(),
                                        reader.readFull()));
                            }
                            if (httpObject instanceof LastHttpContent) {
                                cache.put(DigestUtils.md5Hex(key), data);
                                context.remove();
                            }
                        }
                    }
                }

                return httpObject;
            }

            @Override
            public HttpObject responsePost(HttpObject httpObject) {

                return httpObject;
            }
        };
    }
}
