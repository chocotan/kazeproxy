package io.loli.kaze.cache;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import javax.cache.Cache;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.littleshoot.proxy.HttpFiltersAdapter;

public class CacheFilterAdapter extends HttpFiltersAdapter {

    private static final Logger logger = Logger
            .getLogger(CacheFilterAdapter.class);
    private Cache<String, KazeCacheData> cache;

    private String cacheRegex;

    public CacheFilterAdapter(HttpRequest originalRequest,
            ChannelHandlerContext ctx, String cacheRegex) {
        super(originalRequest, ctx);
        this.cacheRegex = cacheRegex;
        cache = KazeCacheFactory.getCache(CacheFilter.class.getName());
    }

    @Override
    public HttpResponse requestPre(HttpObject httpObject) {
        if (httpObject instanceof HttpRequest) {
            String method = originalRequest.getMethod().toString();
            if ("GET".equals(method)) {
                String url = originalRequest.getUri();
                String allString = url;
                if (allString.matches(cacheRegex)) {
                    String key = method + allString;
                    KazeCacheData data = cache.get(DigestUtils.md5Hex(key));
                    // if status is 200 or 304
                    if (data != null) {
                        if (isRequestCacheable(originalRequest)) {
                            ByteBuf buf = Unpooled.copiedBuffer(data.getData());
                            DefaultFullHttpResponse resp = new DefaultFullHttpResponse(
                                    HttpVersion.HTTP_1_1,
                                    HttpResponseStatus.valueOf(200));
                            resp.content().writeBytes(buf);
                            buf.release();
                            data.getHeaders()
                                    .entrySet()
                                    .forEach(
                                            entry -> {
                                                resp.headers().add(
                                                        entry.getKey(),
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
    @Override
    public HttpResponse requestPost(HttpObject httpObject) {
        return null;
    }

    // @Override
    // public HttpResponse requestPost(HttpObject httpObject) {
    // ChannelPipeline pipeline = ctx.pipeline();
    // pipeline.addLast(new HttpServerCodec(409600, 8192, 8192000));
    // return super.requestPost(httpObject);
    // }

    private ThreadLocal<KazeCacheData> context = new ThreadLocal<>();

    @Override
    public HttpObject responsePre(HttpObject httpObject) {
        String method = originalRequest.getMethod().toString();
        String url = originalRequest.getUri();
        String key = method + url;
        if (url.matches(cacheRegex) && "GET".equals(method)) {

            if (httpObject instanceof HttpResponse) {
                HttpResponse resp = (HttpResponse) httpObject;
                if (isCacheable(originalRequest, resp)) {
                    KazeCacheData data = new KazeCacheData();
                    data.setContentType(resp.headers().get("Content-Type"));
                    data.setStatus(resp.getStatus().code());

                    data.setHeaders(resp
                            .headers()
                            .entries()
                            .stream()
                            .collect(
                                    Collectors.toMap(entry -> entry.getKey(),
                                            entry -> entry.getValue())));
                    context.set(data);
                }
            }
            if (httpObject instanceof HttpContent) {
                KazeCacheData data = context.get();
                if (data != null) {
                    HttpContent content = (HttpContent) httpObject;
                    ByteBuf copy = content.content().duplicate();

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

    private boolean isRequestCacheable(final HttpRequest httpRequest) {

        final String requestControl = httpRequest.headers().get(
                HttpHeaders.Names.CACHE_CONTROL);
        final Set<String> cacheControl = new HashSet<String>();
        cacheControl.add(requestControl);

        // We should really follow all the caching rules from:
        // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html
        //
        // The effect is not caching some things we could.
        if (!cacheControl.isEmpty()) {
            if (cacheControl.contains(HttpHeaders.Values.NO_CACHE)) {
                logger.info("No cache header");
                return false;
            }
            if (cacheControl.contains(HttpHeaders.Values.PRIVATE)) {
                logger.info("Private header");
                return false;
            }
            if (cacheControl.contains(HttpHeaders.Values.NO_STORE)) {
                logger.info("No store header");
                return false;
            }
            if (cacheControl.contains(HttpHeaders.Values.MUST_REVALIDATE)) {
                logger.info("Not caching with 'must revalidate' header");
                return false;
            }
            if (cacheControl.contains(HttpHeaders.Values.PROXY_REVALIDATE)) {
                logger.info("Not caching with 'proxy revalidate' header");
                return false;
            }
        }
        return true;
    }

    private boolean isCacheable(final HttpRequest httpRequest,
            final HttpResponse httpResponse) {
        final HttpResponseStatus responseStatus = httpResponse.getStatus();
        final boolean cachableStatus;
        final int status = responseStatus.code();

        // For rules on this, see:
        // http://tools.ietf.org/html/rfc2616#section-13.4
        //
        // We can't cache 206 responses unless we can support the Range
        // header in requests. That would be a fantastic extension.
        switch (status) {
        case 200:
        case 203:
        case 300:
        case 301:
        case 410:
            cachableStatus = true;
            break;
        default:
            cachableStatus = false;
            break;
        }
        if (!cachableStatus) {
            logger.info("HTTP status is not cachable:" + status);
            return false;
        }

        // Don't use the cache if the request has cookies -- security violation.
        if (httpResponse.headers().contains(HttpHeaders.Names.SET_COOKIE)) {
            logger.info("Response contains set cookie header");
            return false;
        }
        if (httpResponse.headers().contains(HttpHeaders.Names.SET_COOKIE2)) {
            logger.info("Response contains set cookie2 header");
            return false;
        }

        /*
         * if (httpRequest.containsHeader(HttpHeaders.Names.COOKIE)) {
         * logger.info("Request contains Cookie header"); return false; }
         */

        final String responseControl = httpResponse.headers().get(
                HttpHeaders.Names.CACHE_CONTROL);
        final String requestControl = httpRequest.headers().get(
                HttpHeaders.Names.CACHE_CONTROL);
        final Set<String> cacheControl = new HashSet<String>();
        cacheControl.add(requestControl);
        cacheControl.add(responseControl);

        // We should really follow all the caching rules from:
        // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html
        //
        // The effect is not caching some things we could.
        if (!cacheControl.isEmpty()) {
            if (cacheControl.contains(HttpHeaders.Values.NO_CACHE)) {
                logger.info("No cache header");
                return false;
            }
            if (cacheControl.contains(HttpHeaders.Values.PRIVATE)) {
                logger.info("Private header");
                return false;
            }
            if (cacheControl.contains(HttpHeaders.Values.NO_STORE)) {
                logger.info("No store header");
                return false;
            }
            if (cacheControl.contains(HttpHeaders.Values.MUST_REVALIDATE)) {
                logger.info("Not caching with 'must revalidate' header");
                return false;
            }
            if (cacheControl.contains(HttpHeaders.Values.PROXY_REVALIDATE)) {
                logger.info("Not caching with 'proxy revalidate' header");
                return false;
            }
        }
        if (httpResponse != null) {
            final String responsePragma = httpResponse.headers().get(
                    HttpHeaders.Names.PRAGMA);
            if (StringUtils.isNotBlank(responsePragma)
                    && responsePragma.contains(HttpHeaders.Values.NO_CACHE)) {
                logger.info("Not caching with response pragma no cache");
                return false;
            }
        }

        final String requestPragma = httpRequest.headers().get(
                HttpHeaders.Names.PRAGMA);
        if (StringUtils.isNotBlank(requestPragma)
                && requestPragma.contains(HttpHeaders.Values.NO_CACHE)) {
            logger.info("Not caching with request pragma no cache");
            return false;
        }

        logger.info("Got cachable response!");
        return true;
    }

}
