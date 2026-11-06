package io.tracelink.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 静态资源缓存策略：入口 index.html 每次都要回源校验，避免浏览器拿旧页面继续引用旧哈希的 JS；
 * 带哈希的 /assets/** 产物可以长期缓存。单 jar 交付时页面与接口同端口，缓存策略写在这里才生效。
 */
@Component
public class StaticCacheHeaderFilter extends OncePerRequestFilter {

    private static final String ASSETS_CACHE = "public, max-age=31536000, immutable";
    private static final String ENTRY_CACHE = "no-cache";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (uri.startsWith("/assets/")) {
            response.setHeader("Cache-Control", ASSETS_CACHE);
        } else if ("/".equals(uri) || "/index.html".equals(uri)) {
            response.setHeader("Cache-Control", ENTRY_CACHE);
        }
        filterChain.doFilter(request, response);
    }
}
