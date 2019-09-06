package xyz.rthqks.proc.inject


import dagger.BindsInstance
import dagger.Component
import dagger.android.AndroidInjector
import dagger.android.support.AndroidSupportInjectionModule
import xyz.rthqks.proc.ProcApp
import javax.inject.Singleton

@Singleton
@Component(modules = [AndroidSupportInjectionModule::class, AppModule::class])
interface AppComponent: AndroidInjector<ProcApp> {

    @Component.Builder
    interface Builder {
        @BindsInstance
        fun application(application: ProcApp): Builder

        fun build(): AppComponent
    }
}