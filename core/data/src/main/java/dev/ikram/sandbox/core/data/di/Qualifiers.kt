package dev.ikram.sandbox.core.data.di

import javax.inject.Qualifier

/** Lives as long as the process: downloads keep running when screens go away. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** For work that keeps a CPU busy: laying out and searching large text files. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher
