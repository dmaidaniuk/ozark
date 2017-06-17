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
package org.glassfish.ozark.cdi;

import java.lang.annotation.Annotation;
import org.glassfish.ozark.MvcContextImpl;
import org.glassfish.ozark.OzarkConfig;
import org.glassfish.ozark.binding.BindingResultImpl;
import org.glassfish.ozark.binding.ConstraintViolationTranslator;
import org.glassfish.ozark.core.*;
import org.glassfish.ozark.engine.FaceletsViewEngine;
import org.glassfish.ozark.engine.JspViewEngine;
import org.glassfish.ozark.engine.ViewEngineFinder;
import org.glassfish.ozark.event.*;
import org.glassfish.ozark.locale.DefaultLocaleResolver;
import org.glassfish.ozark.locale.LocaleRequestFilter;
import org.glassfish.ozark.locale.LocaleResolverChain;
import org.glassfish.ozark.security.CsrfImpl;
import org.glassfish.ozark.security.CsrfProtectFilter;
import org.glassfish.ozark.security.CsrfValidateInterceptor;
import org.glassfish.ozark.security.EncodersImpl;
import org.glassfish.ozark.util.CdiUtils;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessObserverMethod;
import javax.mvc.annotation.RedirectScoped;
import javax.mvc.event.MvcEvent;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.enterprise.inject.spi.WithAnnotations;
import javax.mvc.annotation.Controller;
import javax.validation.executable.ExecutableType;
import javax.validation.executable.ValidateOnExecution;
import javax.ws.rs.Path;
import static org.glassfish.ozark.binding.BindingResultUtils.hasBindingResultFieldOrProperty;
import static org.glassfish.ozark.util.AnnotationUtils.hasAnnotation;
import static org.glassfish.ozark.util.AnnotationUtils.hasAnnotationOnClassOrMethod;
import static org.glassfish.ozark.util.RuntimeAnnotations.putAnnotation;

/**
 * Class OzarkCdiExtension. Initialize redirect scope as CDI scope. Collect information
 * about all MVC events being observed by the application to optimize event creation
 * and firing.
 *
 * @author Santiago Pericas-Geertsen
 * @author Manfred Riem
 */
@SuppressWarnings("unchecked")
public class OzarkCdiExtension implements Extension {

    private static final Logger LOG = Logger.getLogger(OzarkCdiExtension.class.getName());
    private static Set<Class<? extends MvcEvent>> observedEvents;
    private static final Map<String, Object> VALIDATE_ON_EXECUTION_PARAMS =
            Collections.singletonMap("type", new ExecutableType[] {ExecutableType.NONE});

    /**
     * Before bean discovery.
     *
     * @param event the event.
     * @param beanManager the bean manager.
     */
    public void beforeBeanDiscovery(@Observes final BeforeBeanDiscovery event, BeanManager beanManager) {
        LOG.log(Level.FINE, "Starting OzarkCdiExtension...");
        event.addScope(RedirectScoped.class, true, true);

        CdiUtils.addAnnotatedTypes(event, beanManager,

                // .
                MvcContextImpl.class,
                OzarkConfig.class,

                // binding
                BindingResultImpl.class,
                ValidationInterceptor.class,
                ConstraintViolationTranslator.class,

                // core
                Messages.class,
                ModelsImpl.class,
                ViewableWriter.class,
                ViewRequestFilter.class,
                ViewResponseFilter.class,

                // engine
                FaceletsViewEngine.class,
                JspViewEngine.class,
                ViewEngineFinder.class,

                // security
                CsrfImpl.class,
                CsrfProtectFilter.class,
                CsrfValidateInterceptor.class,
                EncodersImpl.class,

                // util
                CdiUtils.class,

                // cdi
                RedirectScopeManager.class,

                //event
                AfterControllerEventImpl.class,
                AfterProcessViewEventImpl.class,
                BeforeControllerEventImpl.class,
                BeforeProcessViewEventImpl.class,
                ControllerRedirectEventImpl.class,
                MvcEventImpl.class,

                //locale
                LocaleRequestFilter.class,
                LocaleResolverChain.class,
                DefaultLocaleResolver.class

        );
    }

    /**
     * After bean discovery.
     *
     * @param event the event.
     * @param beanManager the bean manager.
     */
    public void afterBeanDiscovery(@Observes final AfterBeanDiscovery event, BeanManager beanManager) {
        event.addContext(new RedirectScopeContext());
    }

    /**
     * Search for {@link javax.mvc.annotation.Controller} annotation on class level and mirror it with 
     * @{link org.glassfish.ozark.cdi.MvcValidation}.
     * 
     * @param <T>
     * @param processAnnotatedType 
     */
    public <T> void processAnnotatedType(
            @Observes @WithAnnotations({Controller.class}) ProcessAnnotatedType<T> processAnnotatedType) {

        AnnotatedType<T> annotatedType = processAnnotatedType
                .getAnnotatedType();
        Class<T> targetClass = annotatedType.getJavaClass();
        
        LOG.log(Level.FINE, "Scanning class {0} for MVC Controller annotation", targetClass.getName());

        if (hasAnnotation(targetClass, Controller.class) && hasAnnotationOnClassOrMethod(targetClass, Path.class)
                && hasBindingResultFieldOrProperty(targetClass)) {

            if (!hasAnnotation(targetClass, ValidateOnExecution.class)) {
                LOG.log(Level.INFO, "Attaching annotation ValidateOnExecution for class {0}", targetClass.getName());
                putAnnotation(targetClass, ValidateOnExecution.class, VALIDATE_ON_EXECUTION_PARAMS);
            }

            LOG.log(Level.INFO, "Attaching annotation MvcValidation for class {0}", targetClass.getName());
            Annotation validateAnnotation = () -> MvcValidation.class;

            AnnotatedTypeWrapper<T> wrapper = new AnnotatedTypeWrapper<>(
                    annotatedType, annotatedType.getAnnotations());
            wrapper.addAnnotation(validateAnnotation);

            processAnnotatedType.setAnnotatedType(wrapper);
        }

    }

    /**
     * Gather set of event types that are observed by MVC application. This info is later
     * used to optimize event creation and firing.
     *
     * @param pom process observer method object.
     * @param beanManager the bean manager.
     * @param <T> the type of the event being observed.
     * @param <X> the bean type containing the observer method.
     */
    public <T, X> void processObserverMethod(@Observes ProcessObserverMethod<T, X> pom, BeanManager beanManager) {
        final Type type = pom.getObserverMethod().getObservedType();
        if (type instanceof Class<?>) {
            final Class<?> clazz = (Class<?>) type;
            if (MvcEvent.class.isAssignableFrom(clazz)) {
                addObservedEvent((Class<? extends MvcEvent>) type);
            }
        }
    }

    /**
     * Add MVC event type to set of observed events.
     *
     * @param eventType event type.
     */
    public static synchronized void addObservedEvent(Class<? extends MvcEvent> eventType) {
        if (observedEvents == null) {
            observedEvents = new HashSet<>();
        }
        observedEvents.add(eventType);
    }

    /**
     * Determine if an event type is being observed.
     *
     * @param eventType event type.
     * @return outcome of test.
     */
    public static synchronized boolean isEventObserved(Class<? extends MvcEvent> eventType) {
        return observedEvents == null ? false : observedEvents.contains(eventType);
    }
}
