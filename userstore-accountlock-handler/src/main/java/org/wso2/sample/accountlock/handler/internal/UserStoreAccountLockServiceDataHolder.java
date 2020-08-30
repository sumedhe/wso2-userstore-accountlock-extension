package org.wso2.sample.accountlock.handler.internal;

import org.wso2.carbon.user.core.service.RealmService;
import org.wso2.sample.accountlock.handler.UserStoreAccountLockHandler;

public class UserStoreAccountLockServiceDataHolder {

    private static RealmService realmService;
    private static volatile UserStoreAccountLockServiceDataHolder dataHolder;
    private static UserStoreAccountLockHandler userStoreAccountLockHandler;

    private UserStoreAccountLockServiceDataHolder() {

    }

    public static UserStoreAccountLockServiceDataHolder getInstance() {

        if (dataHolder == null) {

            synchronized (UserStoreAccountLockServiceDataHolder.class) {
                if (dataHolder == null) {
                    dataHolder = new UserStoreAccountLockServiceDataHolder();
                    userStoreAccountLockHandler = new UserStoreAccountLockHandler();
                }
            }

        }

        return dataHolder;
    }

    public void setRealmService(RealmService realmService) {

        this.realmService = realmService;
    }

    public RealmService getRealmService() {

        return realmService;
    }

    public UserStoreAccountLockHandler getCustomUserOperationEventListener() {

        return userStoreAccountLockHandler;
    }
}
