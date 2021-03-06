package com.squareup.anvil.compiler.codegen.dagger

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.codegen.PrivateCodeGenerator
import com.squareup.anvil.compiler.internal.classesAndInnerClass
import com.squareup.anvil.compiler.internal.hasAnnotation
import com.squareup.anvil.compiler.mergeComponentFqName
import com.squareup.anvil.compiler.mergeInterfacesFqName
import com.squareup.anvil.compiler.mergeModulesFqName
import com.squareup.anvil.compiler.mergeSubcomponentFqName
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

@AutoService(CodeGenerator::class)
internal class AnvilMergeAnnotationDetectorCheck : PrivateCodeGenerator() {

  override fun isApplicable(context: AnvilContext): Boolean = context.disableComponentMerging

  override fun generateCodePrivate(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  ) {
    val clazz = projectFiles
      .classesAndInnerClass(module)
      .firstOrNull {
        it.hasAnnotation(mergeComponentFqName, module) ||
          it.hasAnnotation(mergeSubcomponentFqName, module) ||
          it.hasAnnotation(mergeInterfacesFqName, module) ||
          it.hasAnnotation(mergeModulesFqName, module)
      }

    if (clazz != null) {
      throw AnvilCompilationException(
        message = "This Gradle module is configured to ONLY generate code with " +
          "the `disableComponentMerging` flag. However, this module contains code that " +
          "uses Anvil @Merge* annotations. That's not supported.",
        element = clazz
      )
    }
  }
}
