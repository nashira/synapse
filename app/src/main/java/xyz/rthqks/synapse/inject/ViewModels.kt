package xyz.rthqks.synapse.inject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Binds
import dagger.MapKey
import dagger.Module
import dagger.multibindings.IntoMap
import xyz.rthqks.synapse.ui.browse.GraphListViewModel
import xyz.rthqks.synapse.ui.build.BuilderViewModel
import xyz.rthqks.synapse.ui.edit.EditGraphViewModel
import xyz.rthqks.synapse.ui.exec.ExecGraphViewModel
import javax.inject.Inject
import javax.inject.Provider
import kotlin.reflect.KClass

@ActivityScope
class ViewModelFactory @Inject constructor(
    private val viewModels: MutableMap<Class<out ViewModel>, Provider<ViewModel>>
) :
    ViewModelProvider.Factory {

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

    @Binds
    @IntoMap
    @ViewModelKey(EditGraphViewModel::class)
    internal abstract fun provideEditGraphViewModel(graphViewModel: EditGraphViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(GraphListViewModel::class)
    internal abstract fun provideGraphListViewModel(graphListViewModel: GraphListViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(ExecGraphViewModel::class)
    internal abstract fun provideExecGraphViewModel(execGraphViewModel: ExecGraphViewModel): ViewModel

    @Binds
    @IntoMap
    @ViewModelKey(BuilderViewModel::class)
    internal abstract fun provideBuilderViewModel(viewModel: BuilderViewModel): ViewModel
}