/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.gradle

import org.elasticsearch.gradle.fixtures.AbstractGradleFuncTest
import org.gradle.testkit.runner.GradleRunner
import spock.lang.IgnoreIf
import spock.lang.Unroll

import static org.elasticsearch.gradle.fixtures.DistributionDownloadFixture.withChangedClasspathMockedDistributionDownload
import static org.elasticsearch.gradle.fixtures.DistributionDownloadFixture.withChangedConfigMockedDistributionDownload
import static org.elasticsearch.gradle.fixtures.DistributionDownloadFixture.withMockedDistributionDownload

/**
 * We do not have coverage for the test cluster startup on windows yet.
 * One step at a time...
 * */
@IgnoreIf({ os.isWindows() })
class TestClustersPluginFuncTest extends AbstractGradleFuncTest {

    def setup() {
        buildFile << """
            import org.elasticsearch.gradle.testclusters.TestClustersAware
            import org.elasticsearch.gradle.testclusters.ElasticsearchCluster
            plugins {
                id 'elasticsearch.testclusters'
            }

            class SomeClusterAwareTask extends DefaultTask implements TestClustersAware {

                private Collection<ElasticsearchCluster> clusters = new HashSet<>();
            
                @Override
                @Nested
                public Collection<ElasticsearchCluster> getClusters() {
                    return clusters;
                }
                
                @OutputFile
                Provider<RegularFile> outputFile
             
                @Inject
                SomeClusterAwareTask(ProjectLayout projectLayout) {
                    outputFile = projectLayout.getBuildDirectory().file("someclusteraware.txt")
                }
   
                @TaskAction void doSomething() {
                    outputFile.get().getAsFile().text = "done"
                    println 'SomeClusterAwareTask executed'
                    
                }
            }
        """
    }

    def "test cluster distribution is configured and started"() {
        given:
        buildFile << """
            testClusters {
              myCluster {
                testDistribution = 'default'
              }
            }

            tasks.register('myTask', SomeClusterAwareTask) {
                useCluster testClusters.myCluster
            }
        """

        when:
        def result = withMockedDistributionDownload(gradleRunner("myTask", '-i')) {
            build()
        }

        then:
        result.output.contains("elasticsearch-keystore script executed!")
        assertEsLogContains("myCluster", "Starting Elasticsearch process")
        assertEsLogContains("myCluster", "Stopping node")
        assertNoCustomDistro('myCluster')
    }

    @Unroll
    def "test cluster #inputProperty change is detected"() {
        given:
        buildFile << """
            testClusters {
              myCluster {
                testDistribution = 'default'
              }
            }

            tasks.register('myTask', SomeClusterAwareTask) {
                useCluster testClusters.myCluster
            }
        """

        when:
        def runner = gradleRunner("myTask", '-i', '-g', 'guh')
        def runningClosure = { GradleRunner r -> r.build() }
        withMockedDistributionDownload(runner, runningClosure)
        def result = inputProperty == "distributionClasspath" ?
                withChangedClasspathMockedDistributionDownload(runner, runningClosure) :
                withChangedConfigMockedDistributionDownload(runner, runningClosure)

        then:
        normalized(result.output).contains("Task ':myTask' is not up-to-date because:\n  Input property 'clusters.myCluster\$0.nodes.\$0.$inputProperty'")
        result.output.contains("elasticsearch-keystore script executed!")
        assertEsLogContains("myCluster", "Starting Elasticsearch process")
        assertEsLogContains("myCluster", "Stopping node")
        assertNoCustomDistro('myCluster')

        where:
        inputProperty << ["distributionClasspath", "distributionFiles"]
    }

    @Unroll
    def "test cluster modules #propertyName change is detected"() {
        given:
        addSubProject("test-module") << """
            plugins {
                id 'elasticsearch.esplugin'
            }
            // do not hassle with resolving predefined dependencies
            configurations.compileOnly.dependencies.clear()
            configurations.testImplementation.dependencies.clear()
            
            esplugin {
                name = 'test-module'
                classname 'org.acme.TestModule'
                description = "test module description"
            }
            
            version = "1.0"
            group = 'org.acme'
        """
        buildFile << """
            testClusters {
              myCluster {
                testDistribution = 'default'
                module ':test-module'
              }
            }

            tasks.register('myTask', SomeClusterAwareTask) {
                useCluster testClusters.myCluster
            }
        """

        when:
        withMockedDistributionDownload(gradleRunner("myTask", '-g', 'guh')) {
            build()
        }
        fileChange.delegate = this
        fileChange.call(this)
        def result = withMockedDistributionDownload(gradleRunner("myTask", '-i', '-g', 'guh')) {
            build()
        }

        then:
        normalized(result.output).contains("Task ':myTask' is not up-to-date because:\n" +
                "  Input property 'clusters.myCluster\$0.nodes.\$0.$propertyName'")
        result.output.contains("elasticsearch-keystore script executed!")
        assertEsLogContains("myCluster", "Starting Elasticsearch process")
        assertEsLogContains("myCluster", "Stopping node")

        where:
        propertyName         | fileChange
        "installedFiles"     | { def testClazz -> testClazz.file("test-module/src/main/plugin-metadata/someAddedConfig.txt") << "new resource file" }
        "installedClasspath" | { def testClazz -> testClazz.file("test-module/src/main/java/SomeClass.java") << "class SomeClass {}" }
    }

    def "can declare test cluster in lazy evaluated task configuration block"() {
        given:
        buildFile << """
            tasks.register('myTask', SomeClusterAwareTask) {
                testClusters {
                    myCluster {
                        testDistribution = 'default'
                    }
                }
                useCluster testClusters.myCluster
            }
        """

        when:
        def result = withMockedDistributionDownload(gradleRunner("myTask", '-i')) {
            build()
        }

        then:
        result.output.contains("elasticsearch-keystore script executed!")
        assertEsLogContains("myCluster", "Starting Elasticsearch process")
        assertEsLogContains("myCluster", "Stopping node")
        assertNoCustomDistro('myCluster')
    }

    def "custom distro folder created for tweaked cluster distribution"() {
        given:
        buildFile << """
            testClusters {
              myCluster {
                testDistribution = 'default'
                extraJarFile(file('${someJar().absolutePath}'))
              }
            }

            tasks.register('myTask', SomeClusterAwareTask) {
                useCluster testClusters.myCluster
            }
        """

        when:
        def result = withMockedDistributionDownload(gradleRunner("myTask", '-i')) {
            build()
        }

        then:
        result.output.contains("elasticsearch-keystore script executed!")
        assertEsLogContains("myCluster", "Starting Elasticsearch process")
        assertEsLogContains("myCluster", "Stopping node")
        assertCustomDistro('myCluster')
    }

    boolean assertEsLogContains(String testCluster, String expectedOutput) {
        assert new File(testProjectDir.root,
                "build/testclusters/${testCluster}-0/logs/${testCluster}.log").text.contains(expectedOutput)
        true
    }

    boolean assertCustomDistro(String clusterName) {
        assert customDistroFolder(clusterName).exists()
        true
    }

    boolean assertNoCustomDistro(String clusterName) {
        assert customDistroFolder(clusterName).exists() == false
        true
    }

    private File customDistroFolder(String clusterName) {
        new File(testProjectDir.root, "build/testclusters/${clusterName}-0/distro")
    }
}
