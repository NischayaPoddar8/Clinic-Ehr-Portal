package com.clinic.ehr.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Spring Security 6 creates the CSRF token lazily. Reading it here makes sure the XSRF-TOKEN cookie
 * is sent with every response so the JavaScript frontend can echo it in the X-XSRF-TOKEN header.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        CsrfToken token = (CsrfToken) request.getAttribute("_csrf");
        if (token != null) {
            token.getToken(); // forces the token (and its cookie) to be generated
        }
        chain.doFilter(request, response);
    }
}
