package com.plugin.hello

import com.android.build.api.transform.Format
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformException
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.utils.FileUtils
import javassist.ClassPool
import javassist.CtClass
import javassist.bytecode.ClassFile
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.IOUtils
import org.gradle.api.Project

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

class HelloTransform extends Transform {

    private ClassPool mClassPoll = ClassPool.getDefault()

    HelloTransform(Project project) {
        // 为了能够查找到 android 相关的类，需要把 android.jar包的路径添加到 classPool 类搜索路径中去
        mClassPoll.appendClassPath(project.android.bootClasspath[0].toString())

        // 由于要在 onCreate 方法中打印 Toast，参数的类型
        mClassPoll.importPackage("android.os.Bundle")
        mClassPoll.importPackage("android.widget.Toast")
        mClassPoll.importPackage("android.app.Activity")
    }

    @Override
    String getName() {
        return "HelloTransform"
    }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        // CLASS : 全部的Class文件
        // RESOURCE: assets目录下的资源，而不是res下的资源
        return TransformManager.CONTENT_CLASS
    }

    @Override
    Set<? super QualifiedContent.Scope> getScopes() {
        // 该Transform工作的作用域
        return TransformManager.SCOPE_FULL_PROJECT
    }

    @Override
    void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        super.transform(transformInvocation)

        // 1. 对inputs --> directory --> class 文件进行遍历
        // 2. 对inputs --> jar --> class 进行遍历
        // 3. 符合项目的包名，并且class文件的路径包含 Activity.class 结尾，还不能是 buildconfig.class, R.class, $.class

        def outputProvider = transformInvocation.outputProvider
        transformInvocation.inputs.each { input ->

            input.directoryInputs.each { dirInput ->
                println("dirInput file path: " + dirInput.file.absolutePath)

                handleDirectory(dirInput.file)

                // 把 input -> dir -> class -> dest 目标目录中去
                // name: 随意定义
                def location = outputProvider.getContentLocation(dirInput.name, dirInput.contentTypes,
                        dirInput.scopes, Format.DIRECTORY)
                FileUtils.copyDirectory(dirInput.file, location)
                println("ClassSource: ${dirInput.file.absolutePath} \t ClassDest: ${location.absolutePath}")
            }

            input.jarInputs.each { jarInput ->
                println("jarInput file path: " + jarInput.file.absolutePath)

                def file = handleJar(jarInput.file)

                def name = jarInput.name
                def md5 = DigestUtils.md5Hex(jarInput.file.absolutePath)
                if (name.endsWith(".jar")) {
                    name = name.substring(0, name.length() - 4)
                }
                def location = outputProvider.getContentLocation(md5 + name, jarInput.contentTypes,
                        jarInput.scopes, Format.JAR)
                println(md5 + name)
                println("JarSource: ${file.absolutePath} \t JarDest: ${location.absolutePath}")
                FileUtils.copyFile(file, location)
            }
        }

    }

    @Override
    boolean isIncremental() {
        return false
    }

    void handleDirectory(File dir) {
        mClassPoll.appendClassPath(dir.absolutePath)
        if (dir.isDirectory()) {
            dir.eachFileRecurse { file ->
                def filePath = file.absolutePath
                println("handleDirectory file path: " + filePath)
                if (shouldModifyClass(filePath)) {
                    println("开始修改字节码~: $filePath")
                    def ctClass = modifyClass(new FileInputStream(file))
                    println("ctClass write file: ${file.name} + \t dir: ${dir.name}")
                    ctClass.writeFile(dir.absolutePath)
                    ctClass.detach()
                    println("修改完毕: ${file.name} + \t dir: ${dir.name}")
                }
            }
        }
    }

    boolean shouldModifyClass(String path) {
        return path.contains("com/example/pluginground") &&
                path.endsWith("Activity.class") &&
                !path.contains("R.class") &&
                !path.contains("\$") &&
                !path.contains("R\$") &&
                !path.contains("BuildConfig.class")

    }

    CtClass modifyClass(InputStream stream) {
        def classFile = new ClassFile(new DataInputStream(new BufferedInputStream(stream)))
        CtClass ctClass = mClassPoll.get(classFile.name)

        if (ctClass.isFrozen()) {
            ctClass.defrost()
        }

        println("modifyClass CtClass: ${classFile.name}")

        def bundle = mClassPoll.get("android.os.Bundle")
        CtClass[] params = Arrays.asList(bundle).toArray()
        def method = ctClass.getDeclaredMethod("onCreate", params)

        def message = classFile.name
        method.insertAfter("Toast.makeText(this," + "\"" + message + "\"" + ", Toast.LENGTH_SHORT).show();")

        return ctClass
    }

    File handleJar(File jarFile) {
        mClassPoll.appendClassPath(jarFile.absolutePath)

        def inputJarFile = new JarFile(jarFile)
        def enumeration = inputJarFile.entries()

        def outputJarFile = new File(jarFile.parentFile, "temp_${jarFile.name}")
        if (outputJarFile.exists()) {
            outputJarFile.delete()
        }
        println("OutputJarFile: ${outputJarFile.absolutePath}")

        def jarOutputStream = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(outputJarFile)))
        while (enumeration.hasMoreElements()) {
            JarEntry inputJarEntry = enumeration.nextElement()
            def inputJarEntryName = inputJarEntry.getName()

            def outputJarEntry = new JarEntry(inputJarEntryName)
            jarOutputStream.putNextEntry(outputJarEntry)

            def inputStream = inputJarFile.getInputStream(inputJarEntry)
//            println("inputJarEntryName: ${inputJarEntryName}")
            if (!shouldModifyClass(inputJarEntryName)) {
                jarOutputStream.write(IOUtils.toByteArray(inputStream))
//                println("复制文件: $inputJarEntryName")
                inputStream.close()
                continue
            }

            println("开始添加字节码Jar: $inputJarEntryName")

            def ctClass = modifyClass(inputStream)
            def bytecode = ctClass.toBytecode()
            ctClass.detach()
            inputStream.close()

            jarOutputStream.write(bytecode)
            jarOutputStream.flush()
        }

        inputJarFile.close()
        jarOutputStream.closeEntry()
        jarOutputStream.flush()
        jarOutputStream.close()
        return outputJarFile
    }
}