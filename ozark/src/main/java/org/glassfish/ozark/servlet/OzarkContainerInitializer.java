/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.glassfish.ozark.servlet;

import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.HandlesTypes;
import javax.ws.rs.ApplicationPath;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.mvc.annotation.Controller;
import javax.ws.rs.Path;

import static org.glassfish.ozark.util.AnnotationUtils.getAnnotation;
import static org.glassfish.ozark.util.AnnotationUtils.hasAnnotationOnClassOrMethod;

/**
 * Initializes the Mvc class with the application and context path. Note that the
 * application path is only initialized if there is an application sub-class that
 * is annotated by {@link javax.ws.rs.ApplicationPath}.
 *
 * @author Santiago Pericas-Geertsen
 */
@HandlesTypes({ ApplicationPath.class, Path.class })
public class OzarkContainerInitializer implements ServletContainerInitializer {

    public static final String APP_PATH_CONTEXT_KEY = OzarkContainerInitializer.class.getName() + ".APP_PATH";
    public static final String OZARK_ENABLE_FEATURES_KEY = "ozark.enableFeatures";
    private static final Logger LOG = Logger.getLogger(OzarkContainerInitializer.class.getName());

    @Override
    public void onStartup(Set<Class<?>> classes, ServletContext servletContext) throws ServletException {
        servletContext.setAttribute(OZARK_ENABLE_FEATURES_KEY, false);
        if (classes != null && !classes.isEmpty()) {
            LOG.log(Level.INFO, "Ozark version {0} started", getClass().getPackage().getImplementationVersion());
            for (Class<?> clazz : classes) {
                final ApplicationPath ap = getAnnotation(clazz, ApplicationPath.class);
                if (ap != null) {
                    if (servletContext.getAttribute(APP_PATH_CONTEXT_KEY) != null) {
                        // must be a singleton
                        throw new IllegalStateException("More than one JAX-RS ApplicationPath detected!");
                    }
                    servletContext.setAttribute(APP_PATH_CONTEXT_KEY, ap.value());
                }
                if (hasAnnotationOnClassOrMethod(clazz, Path.class) 
                        && hasAnnotationOnClassOrMethod(clazz, Controller.class)) {
                    servletContext.setAttribute(OZARK_ENABLE_FEATURES_KEY, true);
                }
            }
        }
    }

}
