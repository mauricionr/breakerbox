Next
----
* Tenacity 0.6.8

0.2.5
-----
* Tenacity 0.6.7
* Dropwizard-auth-ldap 0.2.0

0.2.4
-----
* Fixed issue where Breakerbox incorrectly configured `Turbine`.

~~0.2.3~~ Broke the initial configuration
-----
* Tenacity 0.6.4
* H2 1.4.187

0.2.2
-----
* Tenacity 0.6.3. Resolved the memory-leak in `TenacityJerseyClient` by leveraging a workaround.

0.2.1
-----
* Removing use of `TenacityJerseyClient`. We have identified a memory-leak with its implementation when using Jersey 2.x and dropwizard 0.8.x

0.2.0
-----
* Dropwizard 0.8.1
* Tenacity 0.6.2
* Timed out metrics on dashboard for semaphore commands
* Ability to live configure `executionIsolationStrategy` for any `TenacityPropertyKey`

0.1.4
-----
* `TenacityObservableCommand` leverages the semaphore execution isolation strategy. Breakerbox now has support for configuring this through the web interface.
* Tenacity 0.5.3 - This brings ability to configure Semaphore configurations (`SemaphoreConfiguration`).
* Adjusting timeout on authentication requests to enhance the user experience (5s to 30s)
* Updating `breakerbox-dashboard` to include support for semaphore rejection and better error messages when problems occur
* Removing a bug where breakerbox would load two different versions, it now only loads one

0.1.3
-----
* Tenacity 0.4.5
* H2 1.4.186
* Postgresql 9.4-1201-jdbc41

0.1.2
-----
* Tenacity 0.4.4
* H2 1.4.185
* Adding new configuration parameter to define the default dashboard.

0.1.1
--------------
* Builds against Java8 now
* Dropwizard 0.7.1
* Tenacity 0.3.6
* Turbine 1.0.0
* Changing default refresh rate from 2s to 1s
* Moving sync refresh rate from 5s to 30s (but may need to be based on number of hosts)
* Moving sync state block to bottom of page until resizing problem is fixed

0.1.0
-----
* Initial release
