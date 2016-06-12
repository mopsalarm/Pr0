package com.pr0gramm.app.services;

import com.pr0gramm.app.api.pr0gramm.Api;

import org.immutables.value.Value;

import java.util.List;

/**
 */
@Value.Immutable(builder = false)
public interface EnhancedUserInfo {
    @Value.Parameter(order = 1)
    Api.Info getInfo();

    @Value.Parameter(order = 2)
    List<Api.UserComments.UserComment> getComments();
}
