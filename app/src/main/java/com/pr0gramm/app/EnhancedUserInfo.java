package com.pr0gramm.app;

import com.google.common.base.Optional;
import com.pr0gramm.app.api.pr0gramm.response.Info;

import org.immutables.value.Value;

/**
 */
@Value.Immutable(builder = false)
public interface EnhancedUserInfo {
    @Value.Parameter(order = 1)
    Info getInfo();

    @Value.Parameter(order = 2)
    Optional<Graph> getBenisGraph();
}
