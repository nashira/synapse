package com.rthqks.synapse.inject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Binds
import dagger.MapKey
import dagger.Module
import javax.inject.Inject
import javax.inject.Provider
import kotlin.reflect.KClass

@ActivityScope
class ViewModelFactory @Inject constructor(
    private val viewModels: MutableMap<Class<out ViewModel>, Provider<ViewModel>>
) :
    ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        viewModels[modelClass]?.get() as T
}

@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER
)
@kotlin.annotation.Retention(AnnotationRetention.RUNTIME)
@MapKey
internal annotation class ViewModelKey(val value: KClass<out ViewModel>)

@Module
abstract class ViewModels {

    @Binds
    internal abstract fun bindViewModelFactory(factory: ViewModelFactory): ViewModelProvider.Factory

//    @Binds
//    @IntoMap
//    @ViewModelKey(NetworkListViewModel::class)
//    internal abstract fun provideNetworkListViewModel(networkListViewModel: NetworkListViewModel): ViewModel
//
//    @Binds
//    @IntoMap
//    @ViewModelKey(NetworkViewModel::class)
//    internal abstract fun provideExecNetworkViewModel(networkViewModel: NetworkViewModel): ViewModel
//
//    @Binds
//    @IntoMap
//    @ViewModelKey(BuilderViewModel::class)
//    internal abstract fun provideBuilderViewModel(viewModel: BuilderViewModel): ViewModel
}