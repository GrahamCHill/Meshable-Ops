"""Build a ready-to-install ZIP without installing any dependencies."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

root = Path(__file__).resolve().parent
target = root / 'target'
target.mkdir(exist_ok=True)
files = ['plugin.json', 'author.json', 'README.md', 'package.py', 'frontend/index.html',
         'frontend/guide.html', 'frontend/app.js', 'frontend/style.css', 'backend/app.py']
with ZipFile(target / 'custom-page-plugin.zip', 'w', ZIP_DEFLATED) as archive:
    for name in files:
        archive.write(root / name, name)
    archive.write(root.parents[2] / 'LICENSE', 'LICENSE')
print('Built target/custom-page-plugin.zip')
