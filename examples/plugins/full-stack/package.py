"""Package built frontend + Kotlin artifacts and their corresponding source. No data is seeded."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

root = Path(__file__).resolve().parent
with ZipFile(root / 'target/full-stack.zip', 'w', ZIP_DEFLATED) as archive:
    for path in ('plugin.json', 'author.json', 'frontend/index.html', 'frontend/App.tsx', 'backend/notes.py', 'backend/Plugin.kt', 'pom.xml', 'build.mjs', 'package.py'):
        archive.write(root / path, path)
    archive.write(root / 'target/app.js', 'frontend/app.js')
    archive.write(root / 'target/zip-full-stack-1.0.0-jar-with-dependencies.jar', 'backend/summary.jar')
    archive.write(root.parents[2] / 'plugin-runtime/sdk/kotlin/PluginSdk.kt', 'backend/PluginSdk.kt')
    archive.write(root.parents[2] / 'LICENSE', 'LICENSE')
print('Built target/full-stack.zip')
