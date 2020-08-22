package org.wso2.sample.accountlock.handler.internal;

import org.apache.log4j.Logger;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.wso2.carbon.identity.event.handler.AbstractEventHandler;
import org.wso2.carbon.user.core.UserStoreException;
import org.wso2.sample.accountlock.handler.UserStoreAccountLockHandler;

@Component(name = "org.wso2.sample.accountlock.handler.internal.component",
        immediate = true)
public class UserStoreAccountLockServiceComponent {

    private static final Logger log = Logger.getLogger(UserStoreAccountLockServiceComponent.class);

    @Activate
    protected void activate(ComponentContext context) throws UserStoreException {

        try {
            BundleContext bundleContext = context.getBundleContext();

            bundleContext.registerService(AbstractEventHandler.class.getName(),
                    new UserStoreAccountLockHandler(), null);

            if (log.isDebugEnabled()) {
                log.debug("CustomAccountLockHandler is registered");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Deactivate
    protected void deactivate(ComponentContext context) {

        if (log.isDebugEnabled()) {
            log.debug("Test account bundle de-activated");
        }
    }


}
