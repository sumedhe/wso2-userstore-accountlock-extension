package org.wso2.sample.accountlock.handler;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.identity.application.common.model.Property;
import org.wso2.carbon.identity.core.model.IdentityErrorMsgContext;
import org.wso2.carbon.identity.core.util.IdentityCoreConstants;
import org.wso2.carbon.identity.core.util.IdentityUtil;
import org.wso2.carbon.identity.event.IdentityEventConstants;
import org.wso2.carbon.identity.event.IdentityEventException;
import org.wso2.carbon.identity.event.event.Event;
import org.wso2.carbon.identity.governance.common.IdentityConnectorConfig;
import org.wso2.carbon.identity.handler.event.account.lock.AccountLockHandler;
import org.wso2.carbon.identity.handler.event.account.lock.constants.AccountConstants;
import org.wso2.carbon.identity.handler.event.account.lock.exception.AccountLockException;
import org.wso2.carbon.identity.handler.event.account.lock.internal.AccountServiceDataHolder;
import org.wso2.carbon.user.core.UserCoreConstants;
import org.wso2.carbon.user.core.UserStoreException;
import org.wso2.carbon.user.core.UserStoreManager;

import java.util.HashMap;
import java.util.Map;

public class UserStoreAccountLockHandler extends AccountLockHandler implements IdentityConnectorConfig {

    private static final Log log = LogFactory.getLog(AccountLockHandler.class);

    private static final String UPDATED_ALL_USERSTORES_KEY = "UPDATED_ALL_USERSTORES";

    public String getName() {
        return "userstore.account.lock.handler";
    }

    @Override
    public void handleEvent(Event event) throws IdentityEventException {

        super.handleEvent(event);
    }

    @Override
    protected boolean handlePostAuthentication(Event event, String userName, UserStoreManager userStoreManager,
                                               String userStoreDomainName, String tenantDomain,
                                               Property[] identityProperties, int maximumFailedAttempts,
                                               String accountLockTime, double unlockTimeRatio)
            throws AccountLockException {

        if (isAccountLock(userName, userStoreManager)) {
            if (isAccountLockByPassForUser(userStoreManager, userName)) {
                if (log.isDebugEnabled()) {
                    String bypassMsg = String.format("Account locking is bypassed as lock bypass role: %s is " +
                            "assigned to the user %s", AccountConstants.ACCOUNT_LOCK_BYPASS_ROLE, userName);
                    log.debug(bypassMsg);
                }
                return true;
            }

            // User account is locked. If the current time is not exceeded user unlock time, send a error message
            // saying user is locked, otherwise users can try to authenticate and unlock their account upon a
            // successful authentication.

            long unlockTime = getUnlockTime(userName, userStoreManager);

            if (System.currentTimeMillis() < unlockTime || unlockTime == 0) {

                String message;
                if (StringUtils.isNotBlank(userStoreDomainName)) {
                    message = "Account is locked for user " + userName + " in user store "
                            + userStoreDomainName + " in tenant " + tenantDomain + ". Cannot login until the " +
                            "account is unlocked.";
                } else {
                    message = "Account is locked for user " + userName + " in tenant " + tenantDomain + ". Cannot" +
                            " login until the account is unlocked.";
                }

                if (log.isDebugEnabled()) {
                    log.debug(message);
                }

                IdentityErrorMsgContext customErrorMessageContext =
                        new IdentityErrorMsgContext(UserCoreConstants.ErrorCode.USER_IS_LOCKED);
                IdentityUtil.setIdentityErrorMsg(customErrorMessageContext);
                throw new AccountLockException(UserCoreConstants.ErrorCode.USER_IS_LOCKED, message);
            }
        }

        Map<String, String> claimValues = null;
        int currentFailedAttempts = 0;
        try {
            claimValues = userStoreManager.getUserClaimValues(userName, new String[]{
                            AccountConstants.FAILED_LOGIN_LOCKOUT_COUNT_CLAIM,
                            AccountConstants.FAILED_LOGIN_ATTEMPTS_CLAIM,
                            AccountConstants.ACCOUNT_UNLOCK_TIME_CLAIM,
                            AccountConstants.ACCOUNT_LOCKED_CLAIM}
                    , UserCoreConstants.DEFAULT_PROFILE);

            String currentFailedAttemptCount = claimValues.get(AccountConstants.FAILED_LOGIN_ATTEMPTS_CLAIM);
            if (StringUtils.isNotBlank(currentFailedAttemptCount)) {
                currentFailedAttempts = Integer.parseInt(currentFailedAttemptCount);
            }
        } catch (UserStoreException e) {
            throw new AccountLockException("Error occurred while retrieving "
                    + AccountConstants.ACCOUNT_UNLOCK_TIME_CLAIM + " , "
                    + AccountConstants.FAILED_LOGIN_ATTEMPTS_CLAIM + " and "
                    + AccountConstants.FAILED_LOGIN_LOCKOUT_COUNT_CLAIM, e);
        }

        if ((Boolean) event.getEventProperties().get(IdentityEventConstants.EventProperty.OPERATION_STATUS)) {
            //User is authenticated, Need to check the unlock time to verify whether the user is previously locked.

            long unlockTime = 0;
            String userClaimValue = claimValues.get(AccountConstants.ACCOUNT_UNLOCK_TIME_CLAIM);
            String accountLockClaim = claimValues.get(AccountConstants.ACCOUNT_LOCKED_CLAIM);
            if (NumberUtils.isNumber(userClaimValue)) {
                unlockTime = Long.parseLong(userClaimValue);
            }
            if (isUserUnlock(userName, userStoreManager, currentFailedAttempts, unlockTime, accountLockClaim)) {
                Map<String, String> newClaims = new HashMap<>();
                newClaims.put(AccountConstants.FAILED_LOGIN_ATTEMPTS_CLAIM, "0");
                newClaims.put(AccountConstants.ACCOUNT_UNLOCK_TIME_CLAIM, "0");
                newClaims.put(AccountConstants.ACCOUNT_LOCKED_CLAIM, Boolean.FALSE.toString());
                newClaims.put(AccountConstants.FAILED_LOGIN_LOCKOUT_COUNT_CLAIM, "0");

                try {
                    setUserClaimsToAllUserStores(userName, newClaims);
                    if (log.isDebugEnabled()) {
                        log.debug(String.format("User %s is unlocked after exceeding the account locked time or " +
                                "account lock bypassing is enabled", userName));
                    }
                } catch (UserStoreException e) {
                    throw new AccountLockException("Error occurred while storing "
                            + AccountConstants.FAILED_LOGIN_ATTEMPTS_CLAIM + ", "
                            + AccountConstants.ACCOUNT_UNLOCK_TIME_CLAIM + ", "
                            + AccountConstants.FAILED_LOGIN_LOCKOUT_COUNT_CLAIM + " and "
                            + AccountConstants.ACCOUNT_LOCKED_CLAIM, e);
                }
            }
        } else {
            // user authentication failed

            int failedLoginLockoutCountValue = 0;
            String loginAttemptCycles = claimValues.get(AccountConstants.FAILED_LOGIN_LOCKOUT_COUNT_CLAIM);
            if (NumberUtils.isNumber(loginAttemptCycles)) {
                failedLoginLockoutCountValue = Integer.parseInt(loginAttemptCycles);
            }

            currentFailedAttempts += 1;
            Map<String, String> newClaims = new HashMap<>();
            newClaims.put(AccountConstants.FAILED_LOGIN_ATTEMPTS_CLAIM, currentFailedAttempts + "");

            if (isAccountLockByPassForUser(userStoreManager, userName)) {
                IdentityErrorMsgContext customErrorMessageContext =
                        new IdentityErrorMsgContext(UserCoreConstants.ErrorCode.INVALID_CREDENTIAL,
                                currentFailedAttempts, maximumFailedAttempts);
                IdentityUtil.setIdentityErrorMsg(customErrorMessageContext);
                if (log.isDebugEnabled()) {
                    String msg = String.format("Login attempt failed. Bypassing account locking for user %s", userName);
                    log.debug(msg);
                }
                return true;
            } else if (currentFailedAttempts >= maximumFailedAttempts) {
                //Current failed attempts exceeded maximum allowed attempts. So their user should be locked.

                newClaims.put(AccountConstants.ACCOUNT_LOCKED_CLAIM, "true");
                if (NumberUtils.isNumber(accountLockTime)) {
                    long unlockTimePropertyValue = Integer.parseInt(accountLockTime);
                    if (unlockTimePropertyValue != 0) {

                        if (log.isDebugEnabled()) {
                            log.debug("Set account unlock time for user:" + userName + " of tenant domain: " +
                                    tenantDomain + " userstore domain: " + userStoreDomainName + " adding account " +
                                    "unlock time out: " + unlockTimePropertyValue + ", account lock timeout increment" +
                                    " factor: " + unlockTimeRatio + " raised to the power of failed login attempt " +
                                    "cycles: " + failedLoginLockoutCountValue);
                        }

                        /*
                         * If account unlock time out is configured, calculates the account unlock time as below.
                         * account unlock time =
                         *      current system time + (account unlock time out configured + account lock time out
                         *      increment factor raised to the power of failed login attempt cycles)
                         */
                        unlockTimePropertyValue = (long) (unlockTimePropertyValue * 1000 * 60 * Math.pow
                                (unlockTimeRatio, failedLoginLockoutCountValue));
                        long unlockTime = System.currentTimeMillis() + unlockTimePropertyValue;
                        newClaims.put(AccountConstants.ACCOUNT_UNLOCK_TIME_CLAIM, unlockTime + "");
                    }
                }

                failedLoginLockoutCountValue = failedLoginLockoutCountValue + 1;
                newClaims.put(AccountConstants.FAILED_LOGIN_LOCKOUT_COUNT_CLAIM, failedLoginLockoutCountValue + "");
                newClaims.put(AccountConstants.FAILED_LOGIN_ATTEMPTS_CLAIM, "0");

                IdentityErrorMsgContext customErrorMessageContext = new IdentityErrorMsgContext(UserCoreConstants
                        .ErrorCode.USER_IS_LOCKED, currentFailedAttempts, maximumFailedAttempts);
                IdentityUtil.setIdentityErrorMsg(customErrorMessageContext);
                IdentityUtil.threadLocalProperties.get().put(IdentityCoreConstants.USER_ACCOUNT_STATE,
                        UserCoreConstants.ErrorCode.USER_IS_LOCKED);

                if (log.isDebugEnabled()) {
                    log.debug(String.format("User %s is locked since he/she exceeded the maximum allowed failed attempts", userName));
                }

            } else {
                IdentityErrorMsgContext customErrorMessageContext = new IdentityErrorMsgContext(UserCoreConstants.ErrorCode.INVALID_CREDENTIAL,
                        currentFailedAttempts, maximumFailedAttempts);
                IdentityUtil.setIdentityErrorMsg(customErrorMessageContext);
            }
            try {
                setUserClaimsToAllUserStores(userName, newClaims);
            } catch (UserStoreException e) {
                throw new AccountLockException("Error occurred while locking user account", e);
            } catch (NumberFormatException e) {
                throw new AccountLockException("Error occurred while parsing config values", e);
            }
        }
        return true;
    }

    private void setUserClaimsToAllUserStores(String userName, Map<String, String> claims)
            throws UserStoreException, AccountLockException {

        if (!(IdentityUtil.threadLocalProperties.get().containsKey(UPDATED_ALL_USERSTORES_KEY)
                && Boolean.valueOf(IdentityUtil.threadLocalProperties.get().get(UPDATED_ALL_USERSTORES_KEY).toString()))) {
            UserStoreManager userStoreManager = AccountServiceDataHolder.getInstance().getRealmService().
                    getBootstrapRealm().getUserStoreManager();
            while (userStoreManager != null) {
                if (isUserExistsInDomain(userStoreManager, userName)) {
                    Map<String, String> claimsCopy = new HashMap<>(claims);
                    userStoreManager.setUserClaimValues(userName, claimsCopy, null);
                }
                userStoreManager = userStoreManager.getSecondaryUserStoreManager();
            }
            IdentityUtil.threadLocalProperties.get().put(UPDATED_ALL_USERSTORES_KEY, "True");
        }
    }

    private boolean isUserExistsInDomain(UserStoreManager userStoreManager, String userName) throws AccountLockException {

        boolean isExists = false;
        try {
            if (userStoreManager.isExistingUser(userName)) {
                isExists = true;
            }
        } catch (UserStoreException e) {
            throw new AccountLockException("Error occurred while check user existence: " + userName, e);
        }
        return isExists;
    }

    private boolean isAccountLockByPassForUser(UserStoreManager userStoreManager, String userName) throws AccountLockException {

        try {
            String[] roleList = userStoreManager.getRoleListOfUser(userName);
            if (!ArrayUtils.isEmpty(roleList)) {
                return ArrayUtils.contains(roleList, AccountConstants.ACCOUNT_LOCK_BYPASS_ROLE);
            }
        } catch (UserStoreException e) {
            throw new AccountLockException("Error occurred while listing user role: " + userName, e);
        }
        return false;
    }

    private boolean isUserUnlock(String userName, UserStoreManager userStoreManager, int currentFailedAttempts,
                                 long unlockTime, String accountLockClaim) throws AccountLockException {

        return (unlockTime != 0 && System.currentTimeMillis() >= unlockTime)
                || currentFailedAttempts > 0
                || ((Boolean.parseBoolean(accountLockClaim) && isAccountLockByPassForUser(userStoreManager, userName)));
    }
}
