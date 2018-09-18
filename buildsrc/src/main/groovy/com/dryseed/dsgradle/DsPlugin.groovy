package com.dryseed.dsgradle

import com.dryseed.dsgradle.utils.FileUtil
import org.gradle.api.Plugin
import org.gradle.api.Project
import com.android.build.gradle.internal.api.ApplicationVariantImpl
import com.android.build.gradle.tasks.ProcessAndroidResources

class DsPlugin implements Plugin<Project> {
    Project mProject
    File mVaHostDir

    void apply(Project project) {
        println(" #### DsPlugin ####")

        mProject = project
        mVaHostDir = new File(project.getBuildDir(), "VAHost")

        project.afterEvaluate {

            project.android.applicationVariants.each { ApplicationVariantImpl variant ->
                // api : https://chaosleong.gitbooks.io/gradle-for-android/content/advanced_build_customization/manipulating_tasks.html
                // [variantName:debug][variantDescription:Debug build]
                // [variantName:release][variantDescription:Release build]
                println("==> [variantName:${variant.name}][variantDescription:${variant.description}]")

                generateDependencies(variant)
                backupHostR(variant)
            }
        }
    }

    /**
     * Save R symbol file
     */
    def backupHostR(ApplicationVariantImpl applicationVariant) {
        /*mProject.tasks.each {
            println("[taskName:${it.name}]")
        }*/

        final ProcessAndroidResources aaptTask = mProject.tasks["process${applicationVariant.name.capitalize()}Resources"]
        aaptTask.doLast {
            mProject.copy {
                from aaptTask.textSymbolOutputFile
                into mVaHostDir
                rename { "Host_R.txt" }
            }
        }
    }

    /**
     * Generate ${project.buildDir}/VAHost/versions.txt
     */
    def generateDependencies(ApplicationVariantImpl applicationVariant) {
        // applicationVariant.javaCompile : 编译 Java 源代码的 task
        applicationVariant.javaCompile.doLast {

            FileUtil.saveFile(mVaHostDir, "allVersions", {
                List<String> deps = new ArrayList<String>()

                mProject.configurations.each {
                    def configName = it.name

                    if (!it.canBeResolved) {
                        println("==> [configurationName:${configName}] NOT READY")
                        deps.add("${configName} -> NOT READY")
                        return
                    }

                    println("==> [configurationName:${it.name}] HAS READY")
                    try {
                        it.resolvedConfiguration.resolvedArtifacts.each {
                            println("==> [configName:${configName}][id:${it.moduleVersion.id}][type:${it.type}][ext:${it.extension}]")
                            deps.add("${configName} -> id: ${it.moduleVersion.id}, type: ${it.type}, ext: ${it.extension}")
                        }
                    } catch (Exception e) {
                        deps.add("${configName} -> ${e}")
                    }
                }

                Collections.sort(deps)
                return deps
            })

        }

    }
}