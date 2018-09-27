package com.dryseed.dsgradle.neptune

import com.android.build.gradle.AppExtension
import com.android.build.gradle.internal.api.ApplicationVariantImpl
import com.android.build.gradle.internal.scope.VariantScope
import com.android.builder.model.Version
import com.dryseed.dsgradle.neptune.dex.RClassTransform
import com.dryseed.dsgradle.neptune.hooker.TaskHookerManager
import com.dryseed.dsgradle.task.InstallPlugin
import com.dryseed.dsgradle.task.TaskFactory
import com.dryseed.dsgradle.utils.Log
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.util.VersionNumber

class NeptunePlugin implements Plugin<Project> {

    Project mProject
    QYPluginExtension mPluginExtension

    @Override
    void apply(Project project) {
        Log.i("==> #### NeptunePlugin apply ####")

        this.mProject = project

        if (!mProject.plugins.hasPlugin("com.android.application")) {
            throw new GradleException("com.android.application not found, QYPlugin can be only apply to android application module")
        }

        mPluginExtension = mProject.extensions.create("neptune", QYPluginExtension)

        def android = project.extensions.getByType(AppExtension)
        def version = Version.ANDROID_GRADLE_PLUGIN_VERSION
        Log.i("==> current AGP version ${version}")

        project.afterEvaluate {
            android.applicationVariants.each { ApplicationVariantImpl variant ->
                mPluginExtension.with {
                    agpVersion = VersionNumber.parse(version)
                    packageName = variant.applicationId
                    versionName = variant.versionName
                    packagePath = packageName.replace('.'.charAt(0), File.separatorChar)
                }
                Log.i("afterEvaluate ==> [variantName:${variant.name}][agpVersion:${mPluginExtension.agpVersion}][packageName:${mPluginExtension.packageName}]" +
                        "[versionName:${mPluginExtension.versionName}][packagePath:${mPluginExtension.packagePath}]")

                createInstallPluginTask(variant)
            }

            checkConfig()
        }

        // 注册修改Class的Transform
        if (mPluginExtension.agpVersion >= VersionNumber.parse("3.0")) {
            android.registerTransform(new RClassTransform(project))
        }

        // 注册hook task相关任务
        TaskHookerManager taskHooker = new TaskHookerManager(project)
        taskHooker.registerTaskHooker()
    }

    /**
     * 检查配置项
     */
    private void checkConfig() {
        if (!mPluginExtension.pluginMode) {
            // not in plugin compile mode, close all the feature
            mPluginExtension.stripResource = false
            mPluginExtension.dexModify = false
        }

        if (mPluginExtension.packageId <= 0x01 || mPluginExtension.packageId > 0x7F) {
            throw new GradleException("invalid package Id 0x${Integer.toHexString(mPluginExtension.packageId)}")
        }

        if (mPluginExtension.packageId != 0x7F && mPluginExtension.pluginMode) {
            mPluginExtension.stripResource = true
        }

        String parameters = "afterEvaluate ==> plugin config parameters: " +
                "pluginMode=${mPluginExtension.pluginMode}, " +
                "packageId=0x${Integer.toHexString(mPluginExtension.packageId)}, " +
                "stripResource=${mPluginExtension.stripResource}, " +
                "dexModify=${mPluginExtension.dexModify}"
        Log.i(parameters)
    }

    /**
     * 创建安装插件apk到宿主特定目录的任务
     */
    private void createInstallPluginTask(ApplicationVariantImpl variant) {
        Log.i("afterEvaluate ==> createInstallPluginTask")
        if (mPluginExtension.pluginMode && mPluginExtension.hostPackageName != null && mPluginExtension.hostPackageName.length() > 0) {
            TaskFactory taskFactory = new TaskFactory(mProject.getTasks())
            VariantScope scope = variant.getVariantData().getScope()
            InstallPlugin installTask = taskFactory.create(new InstallPlugin.ConfigAction(variant))
            // it might be AndroidTask, this class removed in AGP 3.1
            String assembleTaskName = scope.getAssembleTask().getName()
            installTask.dependsOn(assembleTaskName)
            Log.i("afterEvaluate ==> installTask.dependsOn(assembleTaskName) : [assembleTaskName:${assembleTaskName}][installTask:${installTask.name}]")
        }
    }
}