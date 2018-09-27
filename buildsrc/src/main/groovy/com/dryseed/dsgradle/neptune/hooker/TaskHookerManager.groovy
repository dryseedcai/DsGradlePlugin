package com.dryseed.dsgradle.neptune.hooker

import com.android.build.gradle.AndroidGradleOptions
import com.android.build.gradle.AppExtension
import com.android.build.gradle.api.ApkVariant
import com.android.build.gradle.internal.api.ApplicationVariantImpl
import com.android.build.gradle.tasks.ManifestProcessorTask
import com.android.build.gradle.tasks.MergeResources
import com.android.build.gradle.tasks.ProcessAndroidResources
import com.dryseed.dsgradle.neptune.QYPluginExtension
import com.dryseed.dsgradle.task.TaskUtil
import com.dryseed.dsgradle.utils.Log
import com.google.common.io.Files
import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.util.VersionNumber

class TaskHookerManager {
    private Project project
    /** Android Config information */
    private AppExtension android

    private QYPluginExtension qyplugin

    TaskHookerManager(Project project) {
        this.project = project
        android = project.extensions.findByType(AppExtension)
        qyplugin = project.extensions.findByType(QYPluginExtension)
    }

    void registerTaskHooker() {
        project.afterEvaluate {
            android.applicationVariants.all {
                ApplicationVariantImpl applicationVariant ->
                    // mergeDebugResources
                    MergeResources mergeResTask = TaskUtil.getMergeResourcesTask(project, applicationVariant)

                    // processDebugResources
                    ProcessAndroidResources processResTask = TaskUtil.getProcessAndroidResourcesTask(project, applicationVariant)

                    // processDebugManifest
                    ManifestProcessorTask manifestTask = TaskUtil.getManifestProcessorTask(project, applicationVariant)

                    // transformClassesWithDexForDebug
                    Task dexTask = TaskUtil.getDexTask(project, applicationVariant)
                    Log.i("dexTask : ${dexTask}")

                    hookMergeResourceTask(mergeResTask, processResTask)

                    hookProcessResourceTask(processResTask, applicationVariant)
            }
        }
    }

    /**
     * hook合并Resource的task，将public.xml文件拷贝到build/intermediates/res/merged/{variant.name}目录
     */
    private void hookMergeResourceTask(MergeResources mergeResTask, ProcessAndroidResources processResTask) {
        // 合并资源任务
        mergeResTask.doLast {
            if (isAapt2Enable(processResTask)) {
                Log.i "afterEvaluate ==> mergeResTask.doLast : ${mergeResTask.name}.doLast aapt2 is enabled, compile public.xml to .flat file"
                handleAapt2(mergeResTask)
            } else {
                Log.i "afterEvaluate ==> mergeResTask.doLast : ${mergeResTask.name}.doLast aapt2 is disabled, use legacy aapt1"
                handleAapt(mergeResTask)
            }
        }
    }

    /**
     * hook aapt生成arsc和R.java文件的task，重写arsc文件剔除多余的文件
     */
    private void hookProcessResourceTask(ProcessAndroidResources processResTask, ApkVariant apkVariant) {
        if (!getPluginExtension().stripResource) {
            Log.i "afterEvaluate ==> hookProcessResourceTask : No need to strip host resources from plugin arsc file"
            return
        }
        // 处理资源任务
        processResTask.doLast { ProcessAndroidResources par ->
            // rewrite resource
            Log.i "afterEvaluate ==> processResTask.doLast : ${processResTask.name} doLast execute start, rewrite generated arsc file"
            reWriteArscFile(processResTask, apkVariant)
        }
    }

    /**
     * 处理aapt编译之后的产物
     * 1. 解压resource_{variant.name}.ap_文件到目录，该文件是一个Zip包，包含编译后的AndroidManifest、res目录和resources.arsc文件
     * 2. 收集插件apk的全量资源和宿主的资源，计算出最终需要保留在插件apk里的资源，根据packageId给插件独有的资源重新分配id
     * 3. 从插件res目录中删除宿主的资源，修改资源id和关联的xml文件
     * 4. 从resource_{variant_name}.ap_压缩文件中删除有变动的资源，然后通过aapt add命令重新添加进该文件
     * 5. 重新生成R.java，该文件含有全量的资源id
     *
     * @param par
     * @param variant
     */
    private void reWriteArscFile(ProcessAndroidResources par, ApkVariant variant) {

        boolean isAbove3 = getPluginExtension().agpVersion >= VersionNumber.parse("3.0")
        File apFile
        if (isAbove3) {
            apFile = new File(par.resPackageOutputFolder, "resources-${variant.name}.ap_")
        } else {
            apFile = par.packageOutputFile
        }
        def resourcesDir = new File(apFile.parentFile, Files.getNameWithoutExtension(apFile.name))
        Log.i("afterEvaluate ==> processResTask.doLast reWriteArscFile : [resourcesDir:${resourcesDir.getAbsolutePath()}]")

        /** clean up last build resources */
        resourcesDir.deleteDir()

        /** back up original ap-file */
        File backupFile = new File(apFile.getParentFile(), "${Files.getNameWithoutExtension(apFile.name)}-original.${Files.getFileExtension(apFile.name)}")
        backupFile.delete()
        project.copy {
            from apFile
            into apFile.getParentFile()
            rename { backupFile.name }
        }

        /** Unzip resourece-${variant.name}.ap_ to resourceDir */
        project.copy {
            from project.zipTree(apFile)
            into resourcesDir

            include 'AndroidManifest.xml'
            include 'resources.arsc'
            include 'res/**/*'
        }
//        /** collect host resource and plugin resources */
//        ResourceCollector resourceCollector = new ResourceCollector(project, par, variant)
//        resourceCollector.collect()
//
//        def retainedTypes = convertResourcesForAapt(resourceCollector.pluginResources)
//        def retainedStyleables = convertStyleablesForAapt(resourceCollector.pluginStyleables)
//        def resIdMap = resourceCollector.resIdMap
//
//        def rSymbolFile = isAbove3 ? par.textSymbolOutputFile : new File(par.textSymbolOutputDir, 'R.txt')
//        def libRefTable = ["${pluginExt.packageId}": (isAbove3 ? par.originalApplicationId : par.packageForR)]
//
//        def filteredResources = [] as HashSet<String>
//        def updatedResources = [] as HashSet<String>
//
//        def aapt = new Aapt(resourcesDir, rSymbolFile, android.buildToolsRevision)
//
//        /** Delete host resources, must do it before aapt#filterPackage */
//        aapt.filterResources(retainedTypes, filteredResources)
//        /** Modify the arsc file, and replace ids of related xml files */
//        aapt.filterPackage(retainedTypes, retainedStyleables, pluginExt.packageId, resIdMap, libRefTable, updatedResources)
//
//        /**
//         * Delete filtered entries (host resources) and then add updated resources into resourece-${variant-name}.ap_
//         * Cause there is no 'aapt upate; command supported, so for the updated resources
//         * we also delete first and run 'aapt add' later
//         */
//        ZipUtil.with(apFile).deleteAll(filteredResources + updatedResources)
//        /** Dump filtered and updated Resources to file */
//        dump(filteredResources, updatedResources)
//
//        // Windows cmd有最大长度限制，如果updatedResources文件特别多，会出现执行aapt.exe异常
//        // Windows cmd最大限制8191个字符，Linux shell最大长度通过getconf ARG_MAX获取，一般有几十万，暂不处理
//        String aaptPath = par.buildTools.getPath(BuildToolInfo.PathId.AAPT)
//        if (Os.isFamily(Os.FAMILY_WINDOWS)) {
//            List<String> resSet = new ArrayList<>()
//            int len = 0
//            for (String resName : updatedResources) {
//                len += resName.length()
//                resSet.add(resName)
//                if (len >= 6000) {
//                    println "too much updatedResources, handle part first"
//                    addUpdatedResources(aaptPath, resourcesDir, apFile, resSet)
//                    // clear to zero
//                    len = 0
//                    resSet.clear()
//                }
//            }
//
//            if (resSet.size() > 0) {
//                addUpdatedResources(aaptPath, resourcesDir, apFile, resSet)
//            }
//        } else {
//            addUpdatedResources(aaptPath, resourcesDir, apFile, updatedResources)
//        }
//
//        updateRJava(aapt, par.sourceOutputDir, variant, resourceCollector)
    }

    private boolean isAapt2Enable(ProcessAndroidResources processResTask) {
        if (processResTask) {
            try {
                // Add in AGP-3.0.0
                return processResTask.isAapt2Enabled()
            } catch (Throwable t) {
                t.printStackTrace()
            }
        }

        try {
            // AGP 2.x
            return AndroidGradleOptions.isAapt2Enabled(project)
        } catch (Throwable t) {
            t.printStackTrace()
        }
        return false
    }

    private void handleAapt2(MergeResources mergeResTask) {
        File aapt2File = getAapt2File(mergeResTask)
        int i = 0
        android.sourceSets.main.res.srcDirs.each { File resDir ->
            File srcFile = new File(resDir, 'values/public.xml')
            if (srcFile.exists()) {
                def name = i++ == 0 ? "public.xml" : "public_${i}.xml"
                File dstFile = new File(resDir, "values/${name}")
                srcFile.renameTo(dstFile)
                String[] commands = [
                        aapt2File.absolutePath, 'compile', '--legacy', '-o', mergeResTask.outputDir, dstFile.path
                ]
                commands.execute()
            }
        }
    }

    private void handleAapt(MergeResources mergeResTask) {
        project.copy {
            int i = 0
            from(project.android.sourceSets.main.res.srcDirs) {
                include 'values/public.xml'
                rename 'public.xml', (i++ == 0 ? "public.xml" : "public_${i}.xml")
            }
            into(mergeResTask.outputDir)
        }
    }

    private File getAapt2File(MergeResources task) {
        String path = null
        try {
            def buildToolInfo = task.getBuilder().getBuildToolInfo()
            Map paths = buildToolInfo.mPaths
            def entry = paths.find { key, value ->
                (key.name().equalsIgnoreCase('AAPT2') || key.name().equalsIgnoreCase('DAEMON_AAPT2')) &&
                        key.isPresentIn(buildToolInfo.revision)
            }
            path = entry?.value
        } catch (Exception ignore) {
        }
        if (path == null) {
            path = "${project.android.sdkDirectory}${File.separator}" +
                    "build-tools${File.separator}" +
                    "${project.android.buildToolsVersion}${File.separator}" +
                    "aapt2${Os.isFamily(Os.FAMILY_WINDOWS) ? '.exe' : ''}"
        }
        Log.i "afterEvaluate ==> mergeResTask.doLast : aapt2Path: $path"
        File aapt2File = new File(path)
        if (!aapt2File.exists()) {
            throw new GradleException('aapt2 is missing')
        }
        return aapt2File
    }

    private QYPluginExtension getPluginExtension() {
        return project.neptune
    }
}