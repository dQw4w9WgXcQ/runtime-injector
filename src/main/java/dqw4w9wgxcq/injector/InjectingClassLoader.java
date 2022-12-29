package dqw4w9wgxcq.injector;

import lombok.SneakyThrows;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.security.cert.Certificate;

//similar to https://github.com/gradle/gradle/blob/ce13c520144d2b979d413c082b4bd7b96178185e/subprojects/base-services/src/main/java/org/gradle/internal/classloader/TransformingClassLoader.java
public abstract class InjectingClassLoader extends URLClassLoader {
    public InjectingClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    protected abstract boolean shouldInject(String className);

    protected abstract byte[] inject(String className, byte[] bytes);

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (!shouldInject(name)) {
            return super.findClass(name);
        }

        var resourceName = name.replace('.', '/') + ".class";
        var resource = findResource(resourceName);
        if (resource == null) {
            throw new ClassNotFoundException(name);
        }

        byte[] bytes;
        CodeSource codeSource;
        try {
            bytes = inject(name, loadBytecode(resource));
            var codeBase = getClasspathForResource(resource, resourceName).toURI().toURL();
            //signers are stripped.  if the vanilla class is signed, SecurityException will be thrown upon load of other classes in the same package
            codeSource = new CodeSource(codeBase, (Certificate[]) null);
        } catch (Exception e) {
            throw new RuntimeException(String.format("Could not load class '%s' from %s.", name, resource), e);
        }

        if (bytes == null) {
            throw new ClassNotFoundException(name);
        }

        var clazz = defineClass(name, bytes, 0, bytes.length, codeSource);

        var pkgName = name.substring(0, name.lastIndexOf("."));
        @SuppressWarnings("deprecation") var pkg = getPackage(pkgName);
        if (pkg == null) {
            //gradle's implementaion creates a dummy package, however, I believe it can only be null when generating totally new classes
            throw new IllegalStateException("Package " + pkgName + " not found");
        }

        return clazz;
    }

    private byte[] loadBytecode(URL resource) throws IOException {
        try (var inputStream = resource.openStream()) {
            return inputStream.readAllBytes();
        }
    }

    //https://github.com/gradle/gradle/blob/ce13c520144d2b979d413c082b4bd7b96178185e/subprojects/base-services/src/main/java/org/gradle/internal/classloader/ClasspathUtil.java#L124
    @SneakyThrows
    private static File getClasspathForResource(URL resource, String name) {
        var location = toURI(resource);
        var path = location.getPath();
        if (location.getScheme().equals("file")) {
            assert path.endsWith("/" + name);
            return new File(path.substring(0, path.length() - (name.length() + 1)));
        } else if (location.getScheme().equals("jar")) {
            var schemeSpecificPart = location.getRawSchemeSpecificPart();
            var pos = schemeSpecificPart.indexOf("!");
            if (pos > 0) {
                assert schemeSpecificPart.substring(pos + 1).equals("/" + name);
                var jarFile = new URI(schemeSpecificPart.substring(0, pos));
                if (jarFile.getScheme().equals("file")) {
                    return new File(jarFile.getPath());
                }
            }
        }

        throw new RuntimeException(String.format("Cannot determine classpath for resource '%s' from location '%s'.", name, location));
    }

    @SneakyThrows
    private static URI toURI(URL url) {
        try {
            return url.toURI();
        } catch (URISyntaxException e) {
            return new URL(url.getProtocol(),
                    url.getHost(),
                    url.getPort(),
                    url.getFile().replace(" ", "%20")).toURI();
        }
    }
}
