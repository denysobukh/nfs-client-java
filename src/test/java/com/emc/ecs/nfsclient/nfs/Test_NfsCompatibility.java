package com.emc.ecs.nfsclient.nfs;

import static org.junit.Assert.*;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.ToolProvider;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class Test_NfsCompatibility {
  @Rule public TemporaryFolder temporary = new TemporaryFolder();

  @Test
  public void oldImplementationCompilesUnchangedAndItsOldBytecodeUsesTheDefaultMethod()
      throws Exception {
    Path baseline = temporary.newFolder("baseline").toPath();
    Path oldInterface = baseline.resolve("Nfs.java");
    try (InputStream source =
        Test_NfsCompatibility.class.getResourceAsStream("/compatibility/Nfs.java")) {
      assertNotNull(source);
      Files.copy(source, oldInterface);
    }
    String mainClasses =
        Path.of(Nfs.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
    compile(mainClasses, baseline, oldInterface);

    String implementation;
    // Load the frozen old API to generate a concrete implementation of exactly its methods.
    try (URLClassLoader oldApi =
        new URLClassLoader(new URL[] {baseline.toUri().toURL()}, getClass().getClassLoader()) {
          @Override
          protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.equals(Nfs.class.getName())) return super.loadClass(name, resolve);
            Class<?> type = findLoadedClass(name);
            if (type == null) type = findClass(name);
            if (resolve) resolveClass(type);
            return type;
          }
        }) {
      Class<?> legacyInterface = oldApi.loadClass(Nfs.class.getName());
      StringBuilder source =
          new StringBuilder("public class LegacyNfs implements com.emc.ecs.nfsclient.nfs.Nfs {\n");
      for (Method method : legacyInterface.getDeclaredMethods()) {
        if (!Modifier.isAbstract(method.getModifiers())) continue;
        source
            .append("public ")
            .append(method.getReturnType().getCanonicalName())
            .append(' ')
            .append(method.getName())
            .append('(');
        Class<?>[] parameters = method.getParameterTypes();
        for (int i = 0; i < parameters.length; i++) {
          if (i != 0) source.append(',');
          source.append(parameters[i].getCanonicalName()).append(" p").append(i);
        }
        source.append(") {");
        Class<?> returns = method.getReturnType();
        if (returns != void.class) {
          source
              .append("return ")
              .append(returns == boolean.class ? "false" : returns.isPrimitive() ? "0" : "null")
              .append(';');
        }
        source.append("}\n");
      }
      implementation = source.append("}\n").toString();
    }

    Path legacySource = baseline.resolve("LegacyNfs.java");
    Files.writeString(legacySource, implementation);
    compile(baseline + File.pathSeparator + mainClasses, baseline, legacySource);
    // Parent-first loading binds bytecode compiled against the old API to today's interface.
    try (URLClassLoader oldBinary =
        new URLClassLoader(new URL[] {baseline.toUri().toURL()}, getClass().getClassLoader())) {
      Nfs<?> legacy = (Nfs<?>) oldBinary.loadClass("LegacyNfs").getConstructor().newInstance();
      assertEquals(0, legacy.getPort());
      assertEquals(0, legacy.getMountPort());
      assertTrue(Nfs.class.getMethod("getMountPort").isDefault());
    }

    // Compile precisely the same source against today's API, without the old interface.
    Path current = temporary.newFolder("current").toPath();
    Path currentSource = current.resolve("LegacyNfs.java");
    Files.writeString(currentSource, implementation);
    compile(mainClasses, current, currentSource);
  }

  private static void compile(String classpath, Path destination, Path source) {
    assertNotNull(
        "Compatibility tests require a JDK compiler", ToolProvider.getSystemJavaCompiler());
    assertEquals(
        "Compilation failed for " + source,
        0,
        ToolProvider.getSystemJavaCompiler()
            .run(
                null,
                null,
                null,
                "--release",
                "17",
                "-classpath",
                classpath,
                "-d",
                destination.toString(),
                source.toString()));
  }
}
