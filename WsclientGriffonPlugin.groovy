/*
 * Copyright 2009-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * @author Andres Almiray
 */
class WsclientGriffonPlugin {
    // the plugin version
    String version = '0.6'
    // the version or versions of Griffon the plugin is designed for
    String griffonVersion = '0.9.5 > *'
    // the other plugins this plugin depends on
    Map dependsOn = [:]
    // resources that are included in plugin packaging
    List pluginIncludes = []
    // the plugin license
    String license = 'Apache Software License 2.0'
    // Toolkit compatibility. No value means compatible with all
    // Valid values are: swing, javafx, swt, pivot, gtk
    List toolkits = []
    // Platform compatibility. No value means compatible with all
    // Valid values are:
    // linux, linux64, windows, windows64, macosx, macosx64, solaris
    List platforms = []
    // URL where documentation can be found
    String documentation = ''
    // URL where source can be found
    String source = 'https://github.com/griffon/griffon-wsclient-plugin'

    List authors = [
        [
            name: 'Andres Almiray',
            email: 'aalmiray@yahoo.com'
        ]
    ]
    String title = 'Dynamic WS client & libraries'

    String description = '''
The Wsclient plugin adds a remoting client capable of communicating via SOAP. It is compatible with [Grails' Xfire plugin 0.8.1][1].

Usage
-----
The plugin will inject the following dynamic methods:

* `withWs(Map params, Closure stmts)` - executes stmts issuing SOAP calls to a remote server.

Where params may contain

| *Property*  | *Type*      | *Required* | *Notes*                                          |
| ----------- | ----------- | ---------- | ------------------------------------------------ |
| wsdl        | String      | yes        | WSDL location |                                  |
| soapVersion | String      | no         | either "1.1" or "1.2". Defaults to "1.1"         |
| classLoader | ClassLoader | no         | classloader used for proxies classes             |
| timeout     | long        | no         |                                                  |
| mtom        | boolean     | no         | enable mtom                                      |
| basicAuth   | Map         | no         | must define values for `username` and `password` |
| proxy       | Map         | no         | proxy settings                                   |
| ssl         | Map         | no         | ssl settings                                     |

Keys for both `proxy` and `ssl` must match values from `groovyx.net.ws.cxf.SettingsConstants` reproduced here for your convenience

    /** Http proxy user */
    public static final String HTTP_PROXY_USER = "http.proxy.user";
    /** Http proxy password */
    public static final String HTTP_PROXY_PASSWORD = "http.proxy.password";
    /** Http proxy host */
    public static final String HTTP_PROXY_HOST = "http.proxyHost";
    /** Http proxy port */
    public static final String HTTP_PROXY_PORT = "http.proxyPort";
    /** SSL truststore */
    public static final String HTTPS_TRUSTSTORE = "https.truststore";
    /** SSL truststore password */
    public static final String HTTPS_TRUSTSTORE_PASS = "https.truststore.pass";
    /** SSL keystore */
    public static final String HTTPS_KEYSTORE = "https.keystore";
    /** SSL keystore password */
    public static final String HTTPS_KEYSTORE_PASS = "https.keystore.pass";
    /** Http basic authentication user */
    public static final String HTTP_USER = "http.user";
    /** Http basic authentication password */
    public static final String HTTP_PASSWORD = "http.password";

All dynamic methods will create a new client when invoked unless you define an `id:` attribute.
When this attribute is supplied the client will be stored in a cache managed by the `WsclientProvider` that
handled the call.

These methods are also accessible to any component through the singleton `griffon.plugins.wsclient.WsclientEnhancer`.
You can inject these methods to non-artifacts via metaclasses. Simply grab hold of a particular metaclass and call
`WsclientEnhancer.enhance(metaClassInstance)`.

Configuration
-------------
### Dynamic method injection

Dynamic methods will be added to controllers by default. You can
change this setting by adding a configuration flag in `griffon-app/conf/Config.groovy`

    griffon.ws.injectInto = ['controller', 'service']

### Example

This example relies on [Grails][2] as the service provider. Follow these steps to configure the service on the Grails side:

1. Download a copy of [Grails][3] and install it.
2. Create a new Grails application. We'll pick 'exporter' as the application name.

        grails create-app exporter

3. Change into the application's directory. Install the xfire plugin.

        grails install-plugin xfire

4. Create an implementation of the `Calculator` interface as a service

        grails create-service calculator
    
5. Paste the following code in `grails-app/services/exporter/CalculatorService.groovy`

        package exporter
        class CalculatorService {
            boolean transactional = false
            static expose = ['xfire']
 
            double add(double a, double b){
                println "add($a, $b)" // good old println() for quick debugging
                return a + b
            }
        }

6. Run the application

        grails run-app
    
Now we're ready to build the Griffon application

1. Create a new Griffon application. We'll pick `calculator` as the application name

        griffon create-app calculator
    
2. Install the wsclient plugin

        griffon install-plugin wsclient

3. Fix the view script to look like this

        package calculator
        application(title: 'Wsclient Plugin Example',
          pack: true,
          locationByPlatform: true,
          iconImage: imageIcon('/griffon-icon-48x48.png').image,
          iconImages: [imageIcon('/griffon-icon-48x48.png').image,
                       imageIcon('/griffon-icon-32x32.png').image,
                       imageIcon('/griffon-icon-16x16.png').image]) {
            gridLayout(cols: 2, rows: 4)
            label('Num1:')
            textField(columns: 20, text: bind(target: model, targetProperty: 'num1'))
            label('Num2:')
            textField(columns: 20, text: bind(target: model, targetProperty: 'num2'))
            label('Result:')
            label(text: bind{model.result})
            button('Calculate', enabled: bind{model.enabled}, actionPerformed: controller.calculate)
        }

4. Let's add required properties to the model

        package calculator
        @Bindable
        class CalculatorModel {
           String num1
           String num2
           String result
           boolean enabled = true
        }

5. Now for the controller code. Notice that there is minimal error handling in place. If the user
types something that is not a number the client will surely break, but the code is sufficient for now.

        package calculator
        class CalculatorController {
            def model
 
            def calculate = { evt = null ->
                double a = model.num1.toDouble()
                double b = model.num2.toDouble()
                execInsideUISync { model.enabled = false }
                try {
                    def result = withWs(wsdl: "http://localhost:8080/exporter/services/calculator?wsdl") {
                        add(a, b)
                    }
                    execInsideUIAsync { model.result = result.toString() }
                } finally {
                    execInsideUIAsync { model.enabled = true }
                }
            }
        }
    
6. Run the application

        griffon run-app

Testing
-------
Dynamic methods will not be automatically injected during unit testing, because addons are simply not initialized
for this kind of tests. However you can use `WsclientEnhancer.enhance(metaClassInstance, rmiProviderInstance)` where 
`rmiProviderInstance` is of type `griffon.plugins.rmi.WsclientProvider`. The contract for this interface looks like this

    public interface WsclientProvider {
        Object withWs(Map params, Closure closure);
        <T> T withWs(Map params, CallableWithArgs<T> callable);
    }

It's up to you define how these methods need to be implemented for your tests. For example, here's an implementation that never
fails regardless of the arguments it receives

    class MyWsclientProvider implements WsclientProvider {
        Object withWs(Map params, Closure closure) { null }
        public <T> T withWs(Map params, CallableWithArgs<T> callable) { null }
    }
    
This implementation may be used in the following way

    class MyServiceTests extends GriffonUnitTestCase {
        void testSmokeAndMirrors() {
            MyService service = new MyService()
            WsclientEnhancer.enhance(service.metaClass, new MyWsclientProvider())
            // exercise service methods
        }
    }


[1]: http://grails.org/plugin/xfire
[2]: http://grails.org
[3]: http://grails.org/Download
'''
}
