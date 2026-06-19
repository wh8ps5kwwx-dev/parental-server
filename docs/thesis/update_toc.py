# -*- coding: utf-8 -*-
"""Update Table of Contents in the thesis Word file."""

from __future__ import annotations

import os
import sys

DOC = os.path.join(os.path.dirname(__file__), "مشروع_التخرج_نظام_الرقابة_الأبوية.docx")


def main() -> int:
    if not os.path.isfile(DOC):
        print(f"File not found: {DOC}")
        return 1

    try:
        import win32com.client
    except ImportError:
        print("Install pywin32: pip install pywin32")
        return 1

    word = win32com.client.Dispatch("Word.Application")
    word.Visible = False
    doc = word.Documents.Open(DOC)

    # Update TOC fields (wdFieldTOC = 37)
    updated = 0
    for field in doc.Fields:
        try:
            if field.Type == 37:  # TOC
                field.Update()
                updated += 1
        except Exception:
            pass
    doc.Fields.Update()

    if updated == 0:
        # Insert TOC after "فهرس المحتويات" if missing
        find = doc.Content.Find
        find.ClearFormatting()
        if find.Execute(FindText="فهرس المحتويات"):
            rng = find.Parent
            rng.Collapse(0)  # wdCollapseEnd
            rng.InsertParagraphAfter()
            toc_range = rng.Paragraphs(1).Range
            doc.TablesOfContents.Add(
                Range=toc_range,
                UseHeadingStyles=True,
                UpperHeadingLevel=1,
                LowerHeadingLevel=3,
            )
            doc.TablesOfContents(1).Update()
            updated = 1
            print("Inserted new TOC")

    doc.Save()
    pages = doc.ComputeStatistics(2)
    doc.Close(False)
    word.Quit()
    print(f"TOC updated. Pages: {pages}")
    print(f"Saved: {DOC}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
