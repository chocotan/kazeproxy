package io.loli.kaze.cache;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;

import org.littleshoot.proxy.HttpFilters;
import org.littleshoot.proxy.HttpFiltersSourceAdapter;

public class CacheFilter extends HttpFiltersSourceAdapter {

    private String cacheRegex;

    public CacheFilter(String cacheRegex) {
        this.cacheRegex = cacheRegex;
    }

    public HttpFilters filterRequest(HttpRequest originalRequest,
            ChannelHandlerContext ctx) {
        return new CacheFilterAdapter(originalRequest, ctx, cacheRegex);
    }

    @Override
    public int getMaximumRequestBufferSizeInBytes() {
        return 10*1024*1024;
    }
}
