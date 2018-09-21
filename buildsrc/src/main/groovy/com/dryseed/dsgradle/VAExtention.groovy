package com.dryseed.dsgradle

/**
 * VirtualApk extension for plugin projects.
 *
 * @author zhengtao
 */
class VAExtention {

    /** Custom defined resource package Id **/
    int packageId
    /** Local host application directory or Jenkins build number, fetch config files from here **/
    String targetHost
    /** Apply Host Proguard Mapping or not**/
    boolean applyHostMapping = true
    /** Exclude dependent aar or jar **/
    Collection<String> excludes = new HashSet<>()
    boolean forceUseHostDependences
    ArrayList<String> warningList = new ArrayList<>()
    /**  host dependence file - version.txt*/
    File hostDependenceFile
    //group:artifact -> group:artifact:version
    Map hostDependencies

    HashSet<String> flagTable = new HashSet<>()


    public Map getHostDependencies() {
        if (hostDependencies == null) {
            hostDependencies = [] as LinkedHashMap
            hostDependenceFile.splitEachLine('\\s+', { columns ->
                String id = columns[0]
                int index1 = id.indexOf(':')
                int index2 = id.lastIndexOf(':')
                def module = [group: 'unspecified', name: 'unspecified', version: 'unspecified']

                if (index1 < 0 || index2 < 0 || index1 == index2) {
                    Log.e('Dependencies', "Parsed error: [${id}] -> ${module}")
                    return
                }

                if (index1 > 0) {
                    module.group = id.substring(0, index1)
                }
                if (index2 - index1 > 0) {
                    module.name = id.substring(index1 + 1, index2)
                }
                if (id.length() - index2 > 1) {
                    module.version = id.substring(index2 + 1)
                }

                hostDependencies.put("${module.group}:${module.name}", module)
            })
        }
        return hostDependencies
    }

}