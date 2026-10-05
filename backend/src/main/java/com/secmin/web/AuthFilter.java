package com.secmin.web;

import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

@Provider
public class AuthFilter implements ContainerRequestFilter {

    @Inject
    CurrentUser currentUser;

    @Override
    public void filter(ContainerRequestContext ctx) {
        currentUser.resolve(ctx.getHeaderString("X-Account"));
    }
}
