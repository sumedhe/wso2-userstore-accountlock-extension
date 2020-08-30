package org.wso2.sample.accountlock.filter;

import org.wso2.carbon.identity.core.util.IdentityUtil;

import javax.servlet.*;
import java.io.IOException;

public class UserStoreAccountLockFilter implements Filter {

    private static final String UPDATED_ALL_USERSTORES_KEY = "UPDATED_ALL_USERSTORES";

    private FilterConfig filterConfig = null;

    @Override
    public void init(FilterConfig filterConfig) {
        this.filterConfig = filterConfig;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {

       if (IdentityUtil.threadLocalProperties.get() != null) {
           IdentityUtil.threadLocalProperties.get().remove(UPDATED_ALL_USERSTORES_KEY);
       }

        filterChain.doFilter(servletRequest, servletResponse);
    }

    @Override
    public void destroy() {

    }
}