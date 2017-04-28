package com.pr0gramm.app.ui

import com.pr0gramm.app.util.justObservable
import org.mockito.stubbing.OngoingStubbing
import rx.Observable

infix fun <R> OngoingStubbing<Observable<R>>.doReturnObservable(value: R): OngoingStubbing<Observable<R>> {
    return this.thenReturn(value.justObservable())
}
