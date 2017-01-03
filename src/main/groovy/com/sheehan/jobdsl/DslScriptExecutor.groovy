package com.sheehan.jobdsl

import javaposse.jobdsl.dsl.DslScriptLoader
import javaposse.jobdsl.dsl.MemoryJobManagement
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.runtime.StackTraceUtils

class DslScriptExecutor implements ScriptExecutor {

    ScriptResult execute(String scriptText) {

        def stackTrace = new StringWriter()
        def errWriter = new PrintWriter(stackTrace)

        def emcEvents = []
        def listener = { MetaClassRegistryChangeEvent event ->
            emcEvents << event
        } as MetaClassRegistryChangeEventListener

        GroovySystem.metaClassRegistry.addMetaClassRegistryChangeEventListener listener

        ScriptResult scriptResult = new ScriptResult()
        try {
            CustomSecurityManager.restrictThread()
            MemoryJobManagement jm = new MemoryJobManagement()

            new DslScriptLoader(jm).runScript(scriptText)

            scriptResult.results = jm.savedConfigs.collect { [name: it.key, xml: it.value] }
            scriptResult.results += jm.savedViews.collect { [name: it.key, xml: it.value] }
        } catch (MultipleCompilationErrorsException e) {
            stackTrace.append(e.message - 'startup failed, Script1.groovy: ')
        } catch (Throwable t) {
            StackTraceUtils.deepSanitize t
            t.printStackTrace errWriter
        } finally {
            GroovySystem.metaClassRegistry.removeMetaClassRegistryChangeEventListener listener
            emcEvents.each { MetaClassRegistryChangeEvent event ->
                GroovySystem.metaClassRegistry.removeMetaClass event.classToUpdate
            }
            CustomSecurityManager.unrestrictThread()
        }

        scriptResult.stacktrace = stackTrace.toString()
        scriptResult
    }
}
