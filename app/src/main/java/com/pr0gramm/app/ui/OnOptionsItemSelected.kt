package com.pr0gramm.app.ui

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

/**
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
annotation class OnOptionsItemSelected(val value: Int)
