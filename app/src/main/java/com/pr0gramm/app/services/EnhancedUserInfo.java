package com.pr0gramm.app.services;

import com.google.common.base.Optional;
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
    Optional<Graph> getBenisGraph();

    @Value.Parameter(order = 3)
    List<Api.UserComments.UserComment> getComments();
}
