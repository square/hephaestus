package com.squareup.anvil.compiler

import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesBinding.Priority.NORMAL
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.annotations.MergeComponent
import com.squareup.anvil.annotations.MergeSubcomponent
import com.squareup.anvil.annotations.compat.MergeInterfaces
import com.squareup.anvil.annotations.compat.MergeModules
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.internal.argumentType
import com.squareup.anvil.compiler.internal.classDescriptorForType
import com.squareup.anvil.compiler.internal.fqName
import com.squareup.anvil.compiler.internal.getAnnotationValue
import dagger.Component
import dagger.Lazy
import dagger.MapKey
import dagger.Module
import dagger.Provides
import dagger.Subcomponent
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.internal.DoubleCheck
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.constants.ArrayValue
import org.jetbrains.kotlin.resolve.constants.BooleanValue
import org.jetbrains.kotlin.resolve.constants.EnumValue
import org.jetbrains.kotlin.resolve.constants.KClassValue
import org.jetbrains.kotlin.resolve.descriptorUtil.annotationClass
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.getSuperClassNotAny
import org.jetbrains.kotlin.resolve.descriptorUtil.getSuperInterfaces
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Qualifier

internal val mergeComponentFqName = MergeComponent::class.fqName
internal val mergeSubcomponentFqName = MergeSubcomponent::class.fqName
internal val mergeInterfacesFqName = MergeInterfaces::class.fqName
internal val mergeModulesFqName = MergeModules::class.fqName
internal val contributesToFqName = ContributesTo::class.fqName
internal val contributesBindingFqName = ContributesBinding::class.fqName
internal val contributesMultibindingFqName = ContributesMultibinding::class.fqName
internal val daggerComponentFqName = Component::class.fqName
internal val daggerSubcomponentFqName = Subcomponent::class.fqName
internal val daggerModuleFqName = Module::class.fqName
internal val daggerProvidesFqName = Provides::class.fqName
internal val daggerLazyFqName = Lazy::class.fqName
internal val injectFqName = Inject::class.fqName
internal val qualifierFqName = Qualifier::class.fqName
internal val mapKeyFqName = MapKey::class.fqName
internal val assistedFqName = Assisted::class.fqName
internal val assistedFactoryFqName = AssistedFactory::class.fqName
internal val assistedInjectFqName = AssistedInject::class.fqName
internal val providerFqName = Provider::class.fqName
internal val jvmSuppressWildcardsFqName = JvmSuppressWildcards::class.fqName
internal val publishedApiFqName = PublishedApi::class.fqName

internal val daggerDoubleCheckFqNameString = DoubleCheck::class.java.canonicalName

internal const val HINT_CONTRIBUTES_PACKAGE_PREFIX = "anvil.hint.merge"
internal const val HINT_BINDING_PACKAGE_PREFIX = "anvil.hint.binding"
internal const val HINT_MULTIBINDING_PACKAGE_PREFIX = "anvil.hint.multibinding"
internal const val MODULE_PACKAGE_PREFIX = "anvil.module"

internal const val ANVIL_MODULE_SUFFIX = "AnvilModule"

internal const val REFERENCE_SUFFIX = "_reference"
internal const val SCOPE_SUFFIX = "_scope"
internal val propertySuffixes = arrayOf(REFERENCE_SUFFIX, SCOPE_SUFFIX)

internal fun FqName.isAnvilModule(): Boolean {
  val name = asString()
  return name.startsWith(MODULE_PACKAGE_PREFIX) && name.endsWith(ANVIL_MODULE_SUFFIX)
}

internal fun AnnotationDescriptor.boundType(
  module: ModuleDescriptor,
  annotatedClass: ClassDescriptor,
  annotationFqName: FqName
): ClassDescriptor {
  (getAnnotationValue("boundType") as? KClassValue)
    ?.argumentType(module)
    ?.classDescriptorForType()
    ?.let { return it }

  val directSuperTypes = annotatedClass.getSuperInterfaces()
    .plus(
      annotatedClass.getSuperClassNotAny()
        ?.let { listOf(it) }
        ?: emptyList()
    )

  val boundType = directSuperTypes.singleOrNull()
  if (boundType != null) return boundType

  throw AnvilCompilationException(
    classDescriptor = annotatedClass,
    message = "${annotatedClass.fqNameSafe} contributes a binding, but does not " +
      "specify the bound type. This is only allowed with exactly one direct super type. " +
      "If there are multiple or none, then the bound type must be explicitly defined in " +
      "the @${annotationFqName.shortName()} annotation."
  )
}

internal fun AnnotationDescriptor.replaces(module: ModuleDescriptor): List<ClassDescriptor> {
  return (getAnnotationValue("replaces") as? ArrayValue)
    ?.value
    ?.map {
      it.argumentType(module).classDescriptorForType()
    }
    ?: emptyList()
}

internal fun AnnotationDescriptor.priority(): ContributesBinding.Priority {
  val enumValue = getAnnotationValue("priority") as? EnumValue ?: return NORMAL
  return ContributesBinding.Priority.valueOf(enumValue.enumEntryName.asString())
}

internal fun AnnotationDescriptor.ignoreQualifier(): Boolean {
  return (getAnnotationValue("ignoreQualifier") as? BooleanValue)?.value ?: false
}

internal fun AnnotationDescriptor.isMapKey(): Boolean {
  return annotationClass?.annotations?.hasAnnotation(mapKeyFqName) ?: false
}
