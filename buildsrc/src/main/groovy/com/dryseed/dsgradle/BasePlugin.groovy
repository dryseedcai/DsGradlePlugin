package com.dryseed.dsgradle

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.internal.variant.VariantFactory
import com.android.builder.core.VariantConfiguration
import com.android.builder.core.VariantType
import com.dryseed.dsgradle.utils.Log
import com.dryseed.dsgradle.utils.Reflect
import org.gradle.StartParameter
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.util.NameMatcher

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * @author caiminming
 */
abstract class BasePlugin implements Plugin<Project> {

    protected Project mProject
    boolean checkVariantFactoryInvoked

    @Override
    void apply(Project project) {
        Log.i(" #### BasePlugin apply ####")

        mProject = project

        project.extensions.create('virtualApk', VAExtention)

        AppPlugin appPlugin = project.plugins.findPlugin(AppPlugin)

        // 动态代理
        Reflect reflect = Reflect.on(appPlugin.variantManager)
        VariantFactory variantFactory = Proxy.newProxyInstance(this.class.classLoader, [VariantFactory.class] as Class[],
                new InvocationHandler() {
                    Object delegate = reflect.get('variantFactory')

                    @Override
                    Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if ('preVariantWork' == method.name) {
                            checkVariantFactoryInvoked = true
                            println("==> Evaluating VirtualApk's configurations...")
                            //判断是否是编译插件task
                            boolean isBuildingPlugin = evaluateBuildingPlugin(appPlugin, project)

                            beforeCreateAndroidTasks(isBuildingPlugin)
                        }

                        return method.invoke(delegate, args)
                    }
                })
        reflect.set('variantFactory', variantFactory)

    }

    protected abstract void beforeCreateAndroidTasks(boolean isBuildingPlugin)

    private boolean evaluateBuildingPlugin(AppPlugin appPlugin, Project project) {
        StartParameter startParameter = project.gradle.startParameter
        List<String> targetTaskNames = startParameter.taskNames
        targetTaskNames.each {
            //==> startParameter.taskNames : :app:assembleDebug
            //==> startParameter.taskNames : :buildsrc:assemble
            println("==> startParameter.taskNames : ${it}")
        }

        def pluginTasks = ['assemblePlugin'] as List<String>

        appPlugin.variantManager.buildTypes.each {
            def buildType = it.value.buildType
            if ('release' != buildType.name) {
                return
            }
            if (appPlugin.variantManager.productFlavors.isEmpty()) {
                return
            }

            appPlugin.variantManager.productFlavors.each {
                String variantName = VariantConfiguration.computeFullName(it.key, buildType, VariantType.DEFAULT, null)
                def variantPluginTaskName = createPluginTaskName("assemble${variantName.capitalize()}Plugin".toString())
                pluginTasks.add(variantPluginTaskName)
            }
        }

        boolean isBuildingPlugin = false
        NameMatcher nameMatcher = new NameMatcher()
        targetTaskNames.every {
            String taskName = nameMatcher.find(it, pluginTasks)
            if (taskName != null) {
                println("==> Found task name '${taskName}' by given name '${it}'")
                isBuildingPlugin = true
                return false
            }
            return true
        }

        return isBuildingPlugin
    }

    String createPluginTaskName(String name) {
        if (name == 'assembleReleasePlugin') {
            return '_assemblePlugin'
        }
        return name.replace('Release', '')
    }
}
