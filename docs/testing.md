# Testing
GetFile unit tests are available and can be invoked directly via Gradle.
I often prefer to use Eclipse IDE for integrated debugging tools such as
breakpoints and stacktraces.

You can choose to use Gradle directly and view test results as follows:
```
gradle test -i
open build/reports/tests/test/index.html
```

Do note that the unit tests leverage package-private methods that aren't available
to end-users. If you wish to see examples of how you can use GetFile, consider
the [getfile-demo repository](https://github.com/abhatthal/getfile-demo).
