package com.plugin.hello

import com.android.annotations.NonNull
import com.android.build.gradle.AppExtension
import com.android.build.gradle.api.BaseVariantOutput
import com.android.build.gradle.internal.ExtraModelInfo
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.dependency.SourceSetManager
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.options.ProjectOptions
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException

class HelloPlugin implements Plugin<Project> {

    @Override
    void apply(Project target) {
        println target.plugins
        if (!target.plugins.hasPlugin("com.android.application")) {
            throw new ProjectConfigurationException("该插件只能注册到普通组件中去，不能注册到Library组件中去")
        }

//        def android = target.extensions.findByType(AppExtension.class)
//        android.registerTransform(new HelloTransform(target))

        println "Hello Plugin启动了！！！"
        target.android.registerTransform(new HelloTransform(target))
    }

//    @Override
//    protected AppExtension createExtension(
//            @NonNull DslScope dslScope,
//            @NonNull ProjectOptions projectOptions,
//            @NonNull GlobalScope globalScope,
//            @NonNull NamedDomainObjectContainer<BuildType> buildTypeContainer,
//            @NonNull DefaultConfig defaultConfig,
//            @NonNull NamedDomainObjectContainer<ProductFlavor> productFlavorContainer,
//            @NonNull NamedDomainObjectContainer<SigningConfig> signingConfigContainer,
//            @NonNull NamedDomainObjectContainer<BaseVariantOutput> buildOutputs,
//            @NonNull SourceSetManager sourceSetManager,
//            @NonNull ExtraModelInfo extraModelInfo) {
//
//    }
}