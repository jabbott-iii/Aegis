package io.github.jabbottiii.aegis.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jabbottiii.aegis.BuildConfig
import io.github.jabbottiii.aegis.core.common.logging.AegisLogger
import io.github.jabbottiii.aegis.core.common.logging.LogPolicy
import io.github.jabbottiii.aegis.core.common.logging.PolicyLogger
import io.github.jabbottiii.aegis.logging.LogcatSink
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CommonModule {
    /** Release builds get [LogPolicy.RELEASE]: nothing at INFO or below, and no free text. */
    @Provides
    @Singleton
    fun provideLogger(): AegisLogger =
        PolicyLogger(
            sink = LogcatSink(),
            policy = if (BuildConfig.DEBUG) LogPolicy.DEBUG else LogPolicy.RELEASE,
        )

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()
}
