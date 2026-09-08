package com.secureportal.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Spring Security 6's CSRF token resolution is lazy: the token cookie only
 * actually gets written if something reads {@code CsrfToken.getToken()}
 * during the request. That used to happen for free whenever a Thymeleaf form
 * rendered a hidden CSRF field. With no server-rendered forms left, nothing
 * would ever trigger it — the frontend's JS would find no cookie to read on
 * its very first visit. This filter forces that read on every request.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
