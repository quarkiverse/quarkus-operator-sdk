# IDE Config and Code Style

This project has a strictly enforced code style. Code formatting is done by the Eclipse code
formatter, using the config files found in the `contributing` directory. By
default when you run `mvn install` the code will be formatted automatically. When submitting a
pull request the CI build will fail if running the formatter results in any code changes, so it is
recommended that you always run a full Maven build before submitting a pull request.

If you want to run the formatting without doing a full build, you can run `mvn process-sources`.

#### Eclipse Setup

Open the *Preferences* window, and then navigate to _Java_ -> _Code Style_ -> _Formatter_. Click _
Import_ and then select the `eclipse-format.xml` file in the `contrib`
directory.

Next navigate to _Java_ -> _Code Style_ -> _Organize Imports_. Click _Import_ and select
the `eclipse.importorder` file.

#### IDEA Setup

Open the _Preferences_ window (or _Settings_ depending on your edition), navigate to _Plugins_ and
install
the [Eclipse Code Formatter Plugin](https://plugins.jetbrains.com/plugin/6546-eclipse-code-formatter)
from the Marketplace.

Restart your IDE, open the *Preferences* (or *Settings*) window again and navigate to _Other
Settings_ -> _Eclipse Code Formatter_.

Select _Use the Eclipse Code Formatter_, then change the _Eclipse Java Formatter Config File_ to
point to the
`eclipse-format.xml` file in the `contributing` directory. Make sure the _
Optimize Imports_ box is ticked, and select the `eclipse.importorder` file as the import order
config file.

Next, disable wildcard imports:
navigate to _Editor_ -> _Code Style_ -> _Java_ -> _Imports_
and set _Class count to use import with '\*'_ to `999`. Do the same with _Names count to use static
import with '\*'_.