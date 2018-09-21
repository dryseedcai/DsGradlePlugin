package com.dryseed.dsgradle

import com.android.build.gradle.internal.api.ApplicationVariantImpl
import com.android.build.gradle.internal.ide.ArtifactDependencyGraph
import com.android.build.gradle.internal.pipeline.TransformTask
import com.android.build.gradle.internal.publishing.AndroidArtifacts
import com.android.build.gradle.internal.transforms.ProGuardTransform
import com.android.build.gradle.tasks.ProcessAndroidResources
import com.dryseed.dsgradle.utils.FileBinaryCategory
import com.dryseed.dsgradle.utils.FileUtil
import com.dryseed.dsgradle.utils.Log
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier

class DsPlugin extends BasePlugin {
    Project mProject
    File mVaHostDir
    File mHostDir
    boolean isBuildingPlugin = false

    void apply(Project project) {
        super.apply(project)

        Log.i(" #### DsPlugin apply ####")

        mProject = project
        mVaHostDir = new File(project.getBuildDir(), "VAHost")

        project.afterEvaluate {

            project.android.applicationVariants.each { ApplicationVariantImpl variant ->
                // api : https://chaosleong.gitbooks.io/gradle-for-android/content/advanced_build_customization/manipulating_tasks.html
                // [variantName:debug][variantDescription:Debug build]
                // [variantName:release][variantDescription:Release build]
                Log.i("==> [variantName:${variant.name}][variantDescription:${variant.description}]")

                generateDependencies(variant)
                backupHostR(variant)
                backupProguardMapping(variant)
            }
        }

        // Plugin
        mHostDir = new File(mProject.rootDir, "host")
        if (!mHostDir.exists()) {
            mHostDir.mkdirs()
        }
    }

    @Override
    protected void beforeCreateAndroidTasks(boolean isBuildingPlugin) {
        Log.i("==> beforeCreateAndroidTasks")
        this.isBuildingPlugin = isBuildingPlugin
        if (!isBuildingPlugin) {
            Log.i("==> Skipped all VirtualApk's configurations!")
            return
        }

        checkConfig()
    }

    /**
     * Check the plugin apk related config infos
     * 1. copy build/VAHost/Host_R.txt to host/Host_R.txt
     * 2. copy build/VAHost/versions.txt to host/versions.txt
     * 3. copy build/VAHost/mapping.txt to host/mapping.txt
     */
    private void checkConfig() {
        int packageId = getVirtualApk().packageId
        if (packageId == 0) {
            def err = new StringBuilder('you should set the packageId in build.gradle,\n ')
            err.append('please declare it in application project build.gradle:\n')
            err.append('    virtualApk {\n')
            err.append('        packageId = 0xXX \n')
            err.append('    }\n')
            err.append('apply for the value of packageId.\n')
            throw new InvalidUserDataException(err.toString())
        }
        if (packageId >= 0x7f || packageId <= 0x01) {
            throw new IllegalArgumentException('the packageId must be in [0x02, 0x7E].')
        }

        String targetHost = getVirtualApk().targetHost
        if (!targetHost) {
            def err = new StringBuilder('\nyou should specify the targetHost in build.gradle, e.g.: \n')
            err.append('    virtualApk {\n')
            err.append('        //when target Host in local machine, value is host application directory\n')
            err.append('        targetHost = ../xxxProject/app \n')
            err.append('    }\n')
            throw new InvalidUserDataException(err.toString())
        }

        Log.i("==> targetHost:${targetHost}")
        File hostLocalDir = new File(targetHost)
        if (!hostLocalDir.exists()) {
            def err = "The directory of host application doesn't exist! Dir: ${hostLocalDir.canonicalPath}"
            throw new InvalidUserDataException(err)
        }
        Log.i("==> targetHostPath:${hostLocalDir.getAbsolutePath()}")

        File hostR = new File(hostLocalDir, "build/VAHost/Host_R.txt")
        if (hostR.exists()) {
            def dst = new File(mHostDir, "Host_R.txt")
            use(FileBinaryCategory) {
                dst << hostR
            }
        } else {
            def err = new StringBuilder("Can't find ${hostR.canonicalPath}, please check up your host application\n")
            err.append("  need apply com.didi.virtualapk.host in build.gradle of host application\n ")
            throw new InvalidUserDataException(err.toString())
        }

        File hostVersions = new File(hostLocalDir, "build/VAHost/versions.txt")
        if (hostVersions.exists()) {
            def dst = new File(mHostDir, "versions.txt")
            use(FileBinaryCategory) {
                dst << hostVersions
            }
        } else {
            def err = new StringBuilder("Can't find ${hostVersions.canonicalPath}, please check up your host application\n")
            err.append("  need apply com.didi.virtualapk.host in build.gradle of host application \n")
            throw new InvalidUserDataException(err.toString())
        }

        File hostMapping = new File(hostLocalDir, "build/VAHost/mapping.txt")
        if (hostMapping.exists()) {
            def dst = new File(mHostDir, "mapping.txt")
            use(FileBinaryCategory) {
                dst << hostMapping
            }
        }
    }

    private VAExtention getVirtualApk() {
        return mProject.virtualApk
    }

    /**
     * Save proguard mapping
     */
    def backupProguardMapping(ApplicationVariantImpl applicationVariant) {
        if (applicationVariant.buildType.minifyEnabled) {
            TransformTask proguardTask = mProject.tasks["transformClassesAndResourcesWithProguardFor${applicationVariant.name.capitalize()}"]
            ProGuardTransform proGuardTransform = proguardTask.transform
            File mappingFile = proGuardTransform.mappingFile

            proguardTask.doLast {
                Log.i("==> generate ${mappingFile.name}")
                mProject.copy {
                    from mappingFile
                    into mVaHostDir
                }
            }
        }
    }

    /**
     * Save R symbol file
     */
    def backupHostR(ApplicationVariantImpl applicationVariant) {
        /*mProject.tasks.each {
            Log.i("[taskName:${it.name}]")
        }*/

        final ProcessAndroidResources aaptTask = mProject.tasks["process${applicationVariant.name.capitalize()}Resources"]
        aaptTask.doLast {
            Log.i("==> generate Host_R.txt")
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
                        Log.i("==> [configurationName:${configName}] NOT READY")
                        deps.add("${configName} -> NOT READY")
                        return
                    }

                    Log.i("==> [configurationName:${it.name}] HAS READY")
                    try {
                        it.resolvedConfiguration.resolvedArtifacts.each {
                            Log.i("==> [configName:${configName}][id:${it.moduleVersion.id}][type:${it.type}][ext:${it.extension}]")
                            deps.add("${configName} -> id: ${it.moduleVersion.id}, type: ${it.type}, ext: ${it.extension}")
                        }
                    } catch (Exception e) {
                        deps.add("${configName} -> ${e}")
                    }
                }

                Collections.sort(deps)
                return deps
            })


            FileUtil.saveFile(mVaHostDir, "versions", {
                List<String> deps = new ArrayList<String>()
                Log.i("Used compileClasspath: ${applicationVariant.name}")
                Set<ArtifactDependencyGraph.HashableResolvedArtifactResult> compileArtifacts = ArtifactDependencyGraph.getAllArtifacts(
                        applicationVariant.variantData.scope, AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH, null)

                compileArtifacts.each { ArtifactDependencyGraph.HashableResolvedArtifactResult artifact ->
                    ComponentIdentifier id = artifact.id.componentIdentifier
                    if (id instanceof ProjectComponentIdentifier) {
                        deps.add("${id.projectPath.replace(':', '')}:${ArtifactDependencyGraph.getVariant(artifact)}:unspecified ${artifact.file.length()}")

                    } else if (id instanceof ModuleComponentIdentifier) {
                        deps.add("${id.group}:${id.module}:${id.version} ${artifact.file.length()}")

                    } else {
                        deps.add("${artifact.id.displayName.replace(':', '')}:unspecified:unspecified ${artifact.file.length()}")
                    }
                }

                Collections.sort(deps)
                return deps
            })

        }

    }
}