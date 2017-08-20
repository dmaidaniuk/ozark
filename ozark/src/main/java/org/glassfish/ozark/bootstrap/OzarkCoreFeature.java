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
package org.glassfish.ozark.bootstrap;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletContext;
import javax.ws.rs.ConstrainedTo;
import javax.ws.rs.RuntimeType;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;
import javax.ws.rs.ext.Provider;
import org.glassfish.ozark.core.ViewResponseFilter;
import static org.glassfish.ozark.servlet.OzarkContainerInitializer.OZARK_ENABLE_FEATURES_KEY;

/**
 * JAX-RS feature to initialize Ozark. Please note that automatic initialization will
 * only work if the application class doesn't specify instances and singletons explicitly.
 *
 * @author Christian Kaltepoth
 */
@Provider
@ConstrainedTo(RuntimeType.SERVER)
public class OzarkCoreFeature implements Feature {

    private static final Logger LOG = Logger.getLogger(OzarkCoreFeature.class.getName());

    @Context
    private ServletContext servletContext;

    @Override
    public boolean configure(FeatureContext context) {
        LOG.log(Level.INFO, "Started feature configuration for {0}", getClass().getName());
        final Configuration config = context.getConfiguration();
        if (config.isRegistered(ViewResponseFilter.class)) {
            LOG.log(Level.FINE, "Ozark providers already registered. Skipping.");
            return false;     // already registered!
        }

        boolean enableOzark = (Boolean) servletContext.getAttribute(OZARK_ENABLE_FEATURES_KEY);

        LOG.log(Level.FINE, "Is Ozark need to be enabled: {0}", enableOzark);
        if (enableOzark) {
            OzarkInitializer.initialize(context);
        }
        return true;
    }

}
