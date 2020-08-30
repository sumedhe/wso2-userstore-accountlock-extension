# wso2-userstore-accountlock-extension

## Setting up

#### Step 1 - Setup Userstore Accountlock Handler

1. Run `mvn clean install` and build the project

2. Copy `target/org.wso2.sample.accountlock.handler-1.0.jar` to the `<IS_HOME>/repository/components/dropins` folder.

3. Open `<IS_HOME>/repository/conf/identity/identity-event.properties` file and update the configurations of
`account.lock.handler` to `userstore.account.lock.handler` as follows
    ```
   module.name.1=userstore.account.lock.handler
   userstore.account.lock.handler.subscription.1=PRE_AUTHENTICATION
   userstore.account.lock.handler.subscription.2=POST_AUTHENTICATION
   userstore.account.lock.handler.subscription.2=PRE_SET_USER_CLAIMS
   userstore.account.lock.handler.subscription.2=POST_SET_USER_CLAIMS
   userstore.account.lock.handler.enable=true
   ```
#### Step 2 - Setup Userstore Accountlock Filter

1. Run `mvn clean install` and build the project

2. Copy `target/org.wso2.sample.accountlock.filter-1.0.jar` to the `<IS_HOME>/repository/components/lib` folder.

3. Open `<IS_HOME>/repository/conf/tomcat/web.xml` and enable the UserStoreAccountLockFilter by adding following lines

    ```
    <filter>
        <filter-name>CustomUserstoreAccountLockFilter</filter-name>
        <filter-class>org.wso2.sample.accountlock.filter.UserStoreAccountLockFilter</filter-class>
    </filter>

    <filter-mapping>
        <filter-name>CustomUserstoreAccountLockFilter</filter-name>
        <url-pattern>/*</url-pattern>
        <dispatcher>REQUEST</dispatcher>
        <dispatcher>FORWARD</dispatcher>
    </filter-mapping>
    ```

## Notes
* Tested with WSO2 Identity Server 5.7.0
