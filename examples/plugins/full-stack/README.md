# Full stack ZIP example

This optional example installs one React page plus **Python and Kotlin backend code from the same ZIP**. No code is registered in the application's source or images. Python validates and saves private notes through the host; Kotlin counts the caller's visible notes. The host manages the table, RBAC, ownership, search and encrypted settings.

Build from the repository root with Java 21, Maven, Node and Python installed:

```bash
npm ci --prefix frontend
mvn -f examples/plugins/full-stack/pom.xml package
node examples/plugins/full-stack/build.mjs
python examples/plugins/full-stack/package.py
```

Upload `target/full-stack.zip` from this directory in Administration → Plugins. Review its routes and grants, configure optional settings, then enable it. New packages have search disabled. The private plugin runner and both worker images must be deployed; see [runtime setup](../../../docs/plugins/backend-runtime.md). Nothing is seeded or sent externally.

The ZIP includes the source and build inputs alongside a self-contained browser bundle and executable JAR. Python uses the worker's `meshable` SDK; Kotlin includes the [Kotlin SDK](../../../plugin-runtime/sdk/kotlin/PluginSdk.kt) and its dependencies in the JAR. The example follows the app's [plugin licensing policy](../../../docs/plugins/licensing.md).
