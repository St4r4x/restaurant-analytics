"""Fill the table of contents of a .docx with LibreOffice (headless), so it shows without a field update.

Runs with the system python3 that ships LibreOffice's `uno` module: /usr/bin/python3 update_toc.py in.docx out.docx
"""
import pathlib
import subprocess
import sys
import time

import uno
from com.sun.star.beans import PropertyValue


def prop(name, value):
    p = PropertyValue()
    p.Name, p.Value = name, value
    return p


def main(src: str, dst: str):
    port = 2083
    office = subprocess.Popen(["soffice", "--headless", "--invisible", "--norestore",
                               f"--accept=socket,host=127.0.0.1,port={port};urp;"])
    try:
        local = uno.getComponentContext()
        resolver = local.ServiceManager.createInstanceWithContext("com.sun.star.bridge.UnoUrlResolver", local)
        ctx = None
        for _ in range(60):
            try:
                ctx = resolver.resolve(f"uno:socket,host=127.0.0.1,port={port};urp;StarOffice.ComponentContext")
                break
            except Exception:
                time.sleep(0.5)
        desktop = ctx.ServiceManager.createInstanceWithContext("com.sun.star.frame.Desktop", ctx)
        doc = desktop.loadComponentFromURL(pathlib.Path(src).resolve().as_uri(), "_blank", 0, (prop("Hidden", True),))
        indexes = doc.getDocumentIndexes()
        for i in range(indexes.getCount()):
            indexes.getByIndex(i).update()
        doc.storeToURL(pathlib.Path(dst).resolve().as_uri(), (prop("FilterName", "MS Word 2007 XML"),))
        print("indexes updated:", indexes.getCount())
        doc.close(True)
    finally:
        office.terminate()
        office.wait(timeout=30)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
