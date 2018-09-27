package com.dryseed.dsgradle.task

import com.android.build.gradle.internal.api.ApplicationVariantImpl
import com.android.build.gradle.tasks.ManifestProcessorTask
import com.android.build.gradle.tasks.MergeResources
import com.android.build.gradle.tasks.ProcessAndroidResources
import com.dryseed.dsgradle.neptune.QYPluginExtension
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.util.VersionNumber

class TaskUtil {
    /**
     * mergeDebugResources任务的作用是解压所有的aar包输出到
     * app/build/intermediates/exploded-aar，并且把所有的资源文件合并到
     * app/build/intermediates/res/merged/debug目录里
     *
     * @param project
     * @param appVariant
     * @return
     */
    static MergeResources getMergeResourcesTask(Project project, ApplicationVariantImpl appVariant) {
        def scope = appVariant.getVariantData().getScope()
        QYPluginExtension extension = project.extensions.findByType(QYPluginExtension.class)

        MergeResources mergeResTask
        if (extension.agpVersion >= VersionNumber.parse("3.0")) {
            mergeResTask = appVariant.getVariantData().mergeResourcesTask
        } else {
            String mergeTaskName = scope.getMergeResourcesTask().name
            mergeResTask = project.tasks.getByName(mergeTaskName) as MergeResources
        }

        return mergeResTask
    }

    /**
     * processDebugResources的作用
     * 1、调用aapt生成项目和所有aar依赖的R.java,输出到app/build/generated/source/r/debug目录
     * 2、生成资源索引文件app/build/intermediates/res/resources-debug.ap_
     * 3、把符号表输出到app/build/intermediates/symbols/debug/R.txt
     *
     * @param project
     * @param appVariant
     * @return
     */
    static ProcessAndroidResources getProcessAndroidResourcesTask(Project project,
                                                                  ApplicationVariantImpl appVariant) {
        def scope = appVariant.getVariantData().getScope()
        QYPluginExtension extension = project.extensions.findByType(QYPluginExtension.class)

        String processResTaskName = extension.agpVersion >= VersionNumber.parse("3.0") ?
                scope.getProcessResourcesTask().name : scope.getGenerateRClassTask().name
        ProcessAndroidResources processResTask = project.tasks.getByName(processResTaskName) as ProcessAndroidResources
        return processResTask
    }

    /**
     * processDebugManifest任务
     * 是把所有aar包里的AndroidManifest.xml中的节点，合并到
     * 项目的AndroidManifest.xml中，并根据app/build.gradle中当前buildType的
     * manifestPlaceholders配置内容替换manifest文件中的占位符，最后输出到
     * app/build/intermediates/manifests/full/debug/AndroidManifest.xml
     *
     * @param project
     * @param appVariant
     * @return
     */
    static ManifestProcessorTask getManifestProcessorTask(Project project,
                                                          ApplicationVariantImpl appVariant) {
        def scope = appVariant.getVariantData().getScope()
        QYPluginExtension extension = project.extensions.findByType(QYPluginExtension.class)

        ManifestProcessorTask manifestTask
        if (extension.agpVersion >= VersionNumber.parse("3.1")) {
            // AGP 3.1 返回的是ManifestProcessTask
            Object task = appVariant.getVariantData().getScope().manifestProcessorTask
            if (task instanceof ManifestProcessorTask) {
                manifestTask = (ManifestProcessorTask) task
            } else {
                throw new GradleException("ManifestProcessorTask unknown task type ${task.getClass().name}")
            }
        } else if (extension.agpVersion >= VersionNumber.parse("3.0")) {
            // AGP 3.0.1 返回的是AndroidTask类型, AndroidTask类在3.1中被删除了，这里使用反射创建
            Object task = appVariant.getVariantData().getScope().manifestProcessorTask
            try {
                Class<?> clazz = Class.forName("com.android.build.gradle.internal.scope.AndroidTask")
                if (clazz.isInstance(task)) {
                    String manifestTaskName = task.name
                    manifestTask = project.tasks.getByName(manifestTaskName) as ManifestProcessorTask
                } else {
                    throw new GradleException("ManifestProcessorTask unknown task type ${task.getClass().name}")
                }
            } catch (ClassNotFoundException e) {
                throw new GradleException("com.android.build.gradle.internal.scope.AndroidTask not found")
            }
        } else {
            def variantData = scope.getVariantData()
            def outputScope
            try {
                outputScope = variantData.getMainOutput().getScope()
            } catch (Throwable tr) {
                // 2.2.x
                outputScope = variantData.getOutputs().get(0).getScope()
            }
            String manifestTaskName = outputScope.getManifestProcessorTask().name
            manifestTask = project.tasks.getByName(manifestTaskName) as ManifestProcessorTask
        }
        return manifestTask
    }

    /**
     * transformClassesWithDexForDebug任务的作用
     * 是把包含所有class文件的jar包转换为dex，class文件越多转换的越慢
     * 输入的jar包路径是app/build/intermediates/transforms/jarMerging/debug/jars/1/1f/combined.jar
     * 输出dex的目录是build/intermediates/transforms/dex/debug/folders/1000/1f/main
     *
     * @param project
     * @param appVariant
     * @return
     */
    static Task getDexTask(Project project, ApplicationVariantImpl appVariant) {
        String varName = appVariant.name.capitalize()
        Task dexTask = project.tasks.findByName("transformClassesWithDexFor${varName}")
        if (dexTask == null) {
            // if still null, may lower gradle
            dexTask = project.tasks.findByName("dex${varName}")
        }
        return dexTask
    }
}
