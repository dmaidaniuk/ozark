/*
 * Copyright © 2017 Ivar Grimstad (ivar.grimstad@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glassfish.ozark.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utility class for adding annotation to class at runtime.
 *
 * @author Dmytro Maidaniuk
 */
public final class RuntimeAnnotations {

    private static final Constructor<?> AnnotationInvocationHandler_constructor;
    private static final Constructor<?> AnnotationData_constructor;
    private static final Method Class_annotationData;
    private static final Field Class_classRedefinedCount;
    private static final Field AnnotationData_annotations;
    private static final Field AnnotationData_declaredAnotations;
    private static final Method Atomic_casAnnotationData;
    private static final Class<?> ATOMIC_CLASS;

    static {
        // static initialization of necessary reflection Objects
        try {
            Class<?> AnnotationInvocationHandler_class = Class.forName(
                    "sun.reflect.annotation.AnnotationInvocationHandler");
            AnnotationInvocationHandler_constructor = AnnotationInvocationHandler_class.getDeclaredConstructor(
                    new Class[] {Class.class, Map.class});
            AnnotationInvocationHandler_constructor.setAccessible(true);

            ATOMIC_CLASS = Class.forName("java.lang.Class$Atomic");
            Class<?> AnnotationData_class = Class.forName("java.lang.Class$AnnotationData");

            AnnotationData_constructor = AnnotationData_class.getDeclaredConstructor(new Class[] {Map.class, Map.class,
                int.class});
            AnnotationData_constructor.setAccessible(true);
            Class_annotationData = Class.class.getDeclaredMethod("annotationData");
            Class_annotationData.setAccessible(true);

            Class_classRedefinedCount = Class.class.getDeclaredField("classRedefinedCount");
            Class_classRedefinedCount.setAccessible(true);

            AnnotationData_annotations = AnnotationData_class.getDeclaredField("annotations");
            AnnotationData_annotations.setAccessible(true);
            AnnotationData_declaredAnotations = AnnotationData_class.getDeclaredField("declaredAnnotations");
            AnnotationData_declaredAnotations.setAccessible(true);

            Atomic_casAnnotationData = ATOMIC_CLASS.getDeclaredMethod("casAnnotationData", Class.class,
                                                                      AnnotationData_class, AnnotationData_class);
            Atomic_casAnnotationData.setAccessible(true);

        }
        catch (ClassNotFoundException | NoSuchMethodException | SecurityException | NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }

    public static <T extends Annotation> void putAnnotation(Class<?> c, Class<T> annotationClass,
            Map<String, Object> valuesMap) {
        putAnnotation(c, annotationClass, annotationForMap(annotationClass, valuesMap));
    }

    public static <T extends Annotation> void putAnnotation(Class<?> c, Class<T> annotationClass, T annotation) {
        try {
            while (true) { // retry loop
                int classRedefinedCount = Class_classRedefinedCount.getInt(c);
                Object /*AnnotationData*/ annotationData = Class_annotationData.invoke(c);
                // null or stale annotationData -> optimistically create new instance
                Object newAnnotationData = createAnnotationData(c, annotationData, annotationClass, annotation,
                                                                classRedefinedCount);
                // try to install it
                if ((boolean) Atomic_casAnnotationData.invoke(ATOMIC_CLASS, c, annotationData, newAnnotationData)) {
                    // successfully installed new AnnotationData
                    break;
                }
            }
        }
        catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException | InstantiationException e) {
            throw new IllegalStateException(e);
        }

    }

    @SuppressWarnings("unchecked")
    private static <T extends Annotation> Object /*AnnotationData*/ createAnnotationData(Class<?> c,
                    Object /*AnnotationData*/ annotationData, Class<T> annotationClass, T annotation,
                    int classRedefinedCount) throws InstantiationException, IllegalAccessException,
                                                    IllegalArgumentException, InvocationTargetException {
        Map<Class<? extends Annotation>, Annotation> annotations = (Map<Class<? extends Annotation>, Annotation>) AnnotationData_annotations
                .get(annotationData);
        Map<Class<? extends Annotation>, Annotation> declaredAnnotations = (Map<Class<? extends Annotation>, Annotation>) AnnotationData_declaredAnotations
                .get(annotationData);

        Map<Class<? extends Annotation>, Annotation> newDeclaredAnnotations = new LinkedHashMap<>(annotations);
        newDeclaredAnnotations.put(annotationClass, annotation);
        Map<Class<? extends Annotation>, Annotation> newAnnotations;
        if (declaredAnnotations == annotations) {
            newAnnotations = newDeclaredAnnotations;
        }
        else {
            newAnnotations = new LinkedHashMap<>(annotations);
            newAnnotations.put(annotationClass, annotation);
        }
        return AnnotationData_constructor.newInstance(newAnnotations, newDeclaredAnnotations, classRedefinedCount);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Annotation> T annotationForMap(final Class<T> annotationClass,
            final Map<String, Object> valuesMap) {
        return (T) AccessController.doPrivileged((PrivilegedAction<Annotation>) () -> {
            InvocationHandler handler;
            try {
                handler = (InvocationHandler) AnnotationInvocationHandler_constructor
                        .newInstance(annotationClass, new HashMap<>(valuesMap));
            }
            catch (InstantiationException | IllegalAccessException 
                    | IllegalArgumentException | InvocationTargetException e) {
                throw new IllegalStateException(e);
            }
            return (Annotation) Proxy.newProxyInstance(annotationClass.getClassLoader(), new Class[] {
                annotationClass}, handler);
        });
    }
}