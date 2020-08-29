package org.wso2.sample.accountlock.handler.internal;

import org.apache.log4j.Logger;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;
import org.wso2.carbon.identity.event.handler.AbstractEventHandler;
import org.wso2.carbon.user.core.UserStoreException;
import org.wso2.carbon.user.core.service.RealmService;
import org.wso2.sample.accountlock.handler.UserStoreAccountLockHandler;

/**
 * @scr.component name="org.wso2.sample.accountlock.handler.internal.component" immediate=true
 * @scr.reference name="realm.service"
 * interface="org.wso2.carbon.user.core.service.RealmService"cardinality="1..1"
 * policy="dynamic" bind="setRealmService" unbind="unsetRealmService"
 */
public class UserStoreAccountLockServiceComponent {

    private static final Logger log = Logger.getLogger(UserStoreAccountLockServiceComponent.class);

    protected void activate(ComponentContext context) throws UserStoreException {

        try {
            BundleContext bundleContext = context.getBundleContext();

            bundleContext.registerService(AbstractEventHandler.class.getName(),
                    new UserStoreAccountLockHandler(), null);

            if (log.isDebugEnabled()) {
                log.debug("CustomAccountLockHandler is registered");
            }
        } catch (Throwable e) {
            log.error("Error occurred while activating the bundle: CustomAccountLockHandler" + e);
        }
    }

    protected void deactivate(ComponentContext context) {

        if (log.isDebugEnabled()) {
            log.debug("Test account bundle de-activated");
        }
    }

    protected void setRealmService(RealmService realmService) {

        if (log.isDebugEnabled()) {
            log.debug("Setting the Realm Service");
        }
        UserStoreAccountLockServiceDataHolder.getInstance().setRealmService(realmService);
    }

    protected void unsetRealmService(RealmService realmService) {

        if (log.isDebugEnabled()) {
            log.debug("UnSetting the Realm Service");
        }
        UserStoreAccountLockServiceDataHolder.getInstance().setRealmService(null);
    }
}
