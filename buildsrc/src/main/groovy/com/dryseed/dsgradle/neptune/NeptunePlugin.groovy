package com.dryseed.dsgradle.neptune

import com.android.build.gradle.AppExtension
import com.android.build.gradle.internal.api.ApplicationVariantImpl
import com.android.builder.model.Version
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
        Log.i(" #### NeptunePlugin apply ####")

        this.mProject = project

        if (!mProject.plugins.hasPlugin("com.android.application")) {
            throw new GradleException("com.android.application not found, QYPlugin can be only apply to android application module")
        }

        mPluginExtension = mProject.extensions.create("neptune", QYPluginExtension)

        def android = project.extensions.getByType(AppExtension)
        def version = Version.ANDROID_GRADLE_PLUGIN_VERSION
        Log.i("current AGP version ${version}")

        project.afterEvaluate {
            android.applicationVariants.each { ApplicationVariantImpl variant ->
                mPluginExtension.with {
                    agpVersion = VersionNumber.parse(version)
                    packageName = variant.applicationId
                    versionName = variant.versionName
                    packagePath = packageName.replace('.'.charAt(0), File.separatorChar)
                }
                Log.i("[variantName:${variant.name}][agpVersion:${mPluginExtension.agpVersion}][packageName:${mPluginExtension.packageName}]" +
                        "[versionName:${mPluginExtension.versionName}][packagePath:${mPluginExtension.packagePath}]")

                createInstallPluginTask()
            }
        }
    }

    /**
     * 创建安装插件apk到宿主特定目录的任务
     */
    private void createInstallPluginTask() {

    }
}